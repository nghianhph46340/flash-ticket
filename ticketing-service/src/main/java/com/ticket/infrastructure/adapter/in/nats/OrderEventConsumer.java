package com.ticket.infrastructure.adapter.in.nats;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticket.core.domain.event.TicketReservedEvent;
import com.ticket.core.domain.model.enums.OrderStatus;
import com.ticket.core.domain.model.enums.SeatStatus;
import com.ticket.core.domain.model.enums.UserRole;
import com.ticket.infrastructure.adapter.out.persistence.entity.*;
import com.ticket.infrastructure.adapter.out.persistence.repository.*;
import com.ticket.infrastructure.adapter.out.websocket.SeatStatusBroadcaster;
import io.nats.client.*;
import io.nats.client.api.ConsumerConfiguration;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderEventConsumer {

    private final Connection natsConnection;
    private final ObjectMapper objectMapper;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final SeatRepository seatRepository;
    private final UserRepository userRepository;
    private final EventRepository eventRepository;
    private final VenueRepository venueRepository;
    private final TicketTierRepository ticketTierRepository;
    private final SeatStatusBroadcaster seatStatusBroadcaster;

    private Dispatcher dispatcher;
    private JetStreamSubscription subscription;

    private static final BigDecimal DEFAULT_SEAT_PRICE = new BigDecimal("250000.00"); // 250k VND
    private static final Object LOCK = new Object();

    @PostConstruct
    public void startConsuming() {
        try {
            JetStream js = natsConnection.jetStream();
            dispatcher = natsConnection.createDispatcher();

            PushSubscribeOptions options = PushSubscribeOptions.builder()
                    .stream("TICKETS")
                    .durable("ticket-order-worker")
                    .configuration(ConsumerConfiguration.builder()
                            .ackWait(Duration.ofSeconds(10))
                            .maxDeliver(3)
                            .build())
                    .build();

            subscription = js.subscribe("tickets.reserved", dispatcher, msg -> {
                try {
                    handleTicketReserved(msg);
                } catch (Exception e) {
                    log.error("Error processing ticket.reserved message: {}", e.getMessage(), e);
                    msg.nak();
                }
            }, false, options);

            log.info("NATS JetStream OrderEventConsumer started listening on 'tickets.reserved'");
        } catch (Exception e) {
            log.error("Failed to start NATS OrderEventConsumer: {}", e.getMessage(), e);
        }
    }

    @Transactional
    public void handleTicketReserved(Message msg) {
        try {
            String json = new String(msg.getData(), StandardCharsets.UTF_8);
            TicketReservedEvent event = objectMapper.readValue(json, TicketReservedEvent.class);

            log.info("Processing NATS event for orderCode: {}", event.getOrderCode());

            // 1. Kiểm tra tính Idempotent: Nếu đơn hàng đã tồn tại trong Postgres, chỉ cần ACK
            if (orderRepository.existsByOrderCode(event.getOrderCode())) {
                log.info("Order {} already exists in DB. Skipping duplicate processing.", event.getOrderCode());
                msg.ack();
                return;
            }

            UUID userUuid = safeUuid(event.getUserId());
            UUID eventUuid = safeUuid(event.getEventId());

            // Đảm bảo dữ liệu quan hệ (User, Venue, Event, TicketTier) tồn tại trước khi tạo Order
            Prerequisites prereqs = ensurePrerequisites(userUuid, eventUuid);

            BigDecimal totalAmount = DEFAULT_SEAT_PRICE.multiply(new BigDecimal(event.getSeatIds().size()));

            // 2. Ghi bản ghi Order PENDING vào PostgreSQL
            OrderEntity order = OrderEntity.builder()
                    .orderCode(event.getOrderCode())
                    .userId(prereqs.userId)
                    .eventId(prereqs.eventId)
                    .totalAmount(totalAmount)
                    .status(OrderStatus.PENDING)
                    .expiresAt(event.getExpiresAt())
                    .build();

            OrderEntity savedOrder = orderRepository.save(order);

            // 3. Ghi các bản ghi OrderItems và cập nhật Seat
            List<OrderItemEntity> items = new ArrayList<>();

            for (String seatIdStr : event.getSeatIds()) {
                UUID seatUuid = safeUuid(seatIdStr);

                // Đảm bảo ghế tồn tại trong bảng seats (nếu chưa có thì tạo mới)
                SeatEntity seat = seatRepository.findById(seatUuid)
                        .or(() -> seatRepository.findByVenueIdAndSectionAndRowNumAndSeatNum(
                                prereqs.venueId, "STANDARD", "A", seatIdStr))
                        .orElse(null);

                if (seat != null) {
                    seat.setStatus(SeatStatus.HELD);
                    seat.setReservationExpiresAt(event.getExpiresAt());
                    seat = seatRepository.saveAndFlush(seat);
                } else {
                    SeatEntity newSeat = SeatEntity.builder()
                            .id(seatUuid)
                            .venueId(prereqs.venueId)
                            .ticketTierId(prereqs.tierId)
                            .section("STANDARD")
                            .rowNum("A")
                            .seatNum(seatIdStr)
                            .status(SeatStatus.HELD)
                            .reservationExpiresAt(event.getExpiresAt())
                            .build();
                    try {
                        seat = seatRepository.saveAndFlush(newSeat);
                    } catch (Exception e) {
                        seat = seatRepository.findByVenueIdAndSectionAndRowNumAndSeatNum(
                                prereqs.venueId, "STANDARD", "A", seatIdStr).orElse(null);
                        if (seat != null) {
                            seat.setStatus(SeatStatus.HELD);
                            seat.setReservationExpiresAt(event.getExpiresAt());
                            seat = seatRepository.saveAndFlush(seat);
                        } else {
                            throw e;
                        }
                    }
                }

                OrderItemEntity item = OrderItemEntity.builder()
                        .orderId(savedOrder.getId())
                        .seatId(seat.getId())
                        .ticketTierId(prereqs.tierId)
                        .price(DEFAULT_SEAT_PRICE)
                        .build();
                items.add(item);
            }

            orderItemRepository.saveAll(items);

            // 4. Xác nhận đã xử lý xong thông điệp cho NATS Broker
            msg.ack();

            // 5. Broadcast WebSocket STOMP tới tất cả trình duyệt cập nhật ghế chuyển sang HELD
            seatStatusBroadcaster.broadcastSeatStatus(
                    event.getEventId(),
                    event.getSeatIds(),
                    "HELD",
                    event.getUserId()
            );

            log.info("Successfully persisted order {} with {} items to PostgreSQL.", event.getOrderCode(), items.size());

        } catch (Exception e) {
            log.error("Failed to persist order from NATS event: {}", e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }

    private Prerequisites ensurePrerequisites(UUID userUuid, UUID eventUuid) {
        synchronized (LOCK) {
            // 1. User
            UserEntity user = userRepository.findById(userUuid).orElse(null);
            if (user == null) {
                String email = "user-" + userUuid + "@flashticket.com";
                user = userRepository.findByEmail(email).orElse(null);
                if (user == null) {
                    try {
                        user = userRepository.saveAndFlush(
                                UserEntity.builder()
                                        .id(userUuid)
                                        .email(email)
                                        .passwordHash("hashed_secret")
                                        .fullName("Customer " + userUuid.toString().substring(0, 8))
                                        .role(UserRole.CUSTOMER)
                                        .build()
                        );
                    } catch (Exception e) {
                        user = userRepository.findByEmail(email)
                                .or(() -> userRepository.findById(userUuid))
                                .orElse(null);
                    }
                }
            }
            UUID actualUserId = user != null ? user.getId() : userUuid;

            // 2. Venue
            VenueEntity venue = venueRepository.findAll().stream().findFirst().orElse(null);
            if (venue == null) {
                try {
                    venue = venueRepository.saveAndFlush(
                            VenueEntity.builder()
                                    .id(UUID.randomUUID())
                                    .name("Sân Vận Động Mỹ Đình")
                                    .address("Đại Lộ Thăng Long, Hà Nội")
                                    .city("Hà Nội")
                                    .totalCapacity(50000)
                                    .build()
                    );
                } catch (Exception e) {
                    venue = venueRepository.findAll().stream().findFirst().orElseThrow();
                }
            }

            // 3. Event
            EventEntity event = eventRepository.findById(eventUuid).orElse(null);
            if (event == null) {
                String slug = "event-" + eventUuid;
                event = eventRepository.findBySlug(slug).orElse(null);
                if (event == null) {
                    try {
                        event = eventRepository.saveAndFlush(
                                EventEntity.builder()
                                        .id(eventUuid)
                                        .venueId(venue.getId())
                                        .title("Flash Concert " + eventUuid.toString().substring(0, 6))
                                        .slug(slug)
                                        .startTime(Instant.now().plusSeconds(86400))
                                        .endTime(Instant.now().plusSeconds(90000))
                                        .salesStartTime(Instant.now().minusSeconds(3600))
                                        .salesEndTime(Instant.now().plusSeconds(86400))
                                        .isPublished(true)
                                        .version(0L)
                                        .build()
                        );
                    } catch (Exception e) {
                        event = eventRepository.findBySlug(slug)
                                .or(() -> eventRepository.findById(eventUuid))
                                .orElseThrow();
                    }
                }
            }
            UUID actualEventId = event.getId();

            // 4. TicketTier
            TicketTierEntity tier = ticketTierRepository.findByEventId(actualEventId).stream().findFirst().orElse(null);
            if (tier == null) {
                try {
                    tier = ticketTierRepository.saveAndFlush(
                            TicketTierEntity.builder()
                                    .id(UUID.randomUUID())
                                    .eventId(actualEventId)
                                    .name("Standard")
                                    .price(DEFAULT_SEAT_PRICE)
                                    .totalQuantity(5000)
                                    .availableQuantity(5000)
                                    .build()
                    );
                } catch (Exception e) {
                    tier = ticketTierRepository.findByEventId(actualEventId).stream().findFirst().orElseThrow();
                }
            }

            return new Prerequisites(venue.getId(), tier.getId(), actualEventId, actualUserId);
        }
    }

    private record Prerequisites(UUID venueId, UUID tierId, UUID eventId, UUID userId) {}

    @PreDestroy
    public void cleanup() {
        if (dispatcher != null && subscription != null) {
            try {
                dispatcher.unsubscribe(subscription);
            } catch (Exception ignored) {
            }
        }
    }

    public static UUID safeUuid(String str) {
        try {
            return UUID.fromString(str);
        } catch (Exception e) {
            return UUID.nameUUIDFromBytes(str.getBytes(StandardCharsets.UTF_8));
        }
    }
}

package com.ticket.core.service;

import com.ticket.core.domain.event.TicketReservedEvent;
import com.ticket.core.domain.exception.SeatUnavailableException;
import com.ticket.infrastructure.adapter.in.web.dto.ReserveTicketRequest;
import com.ticket.infrastructure.adapter.in.web.dto.ReserveTicketResponse;
import com.ticket.infrastructure.adapter.out.nats.NatsTicketEventPublisher;
import com.ticket.infrastructure.adapter.out.redis.RedisSeatCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TicketReservationService {

    private final RedisSeatCacheService redisSeatCacheService;
    private final NatsTicketEventPublisher natsTicketEventPublisher;
    private final OrphanSeatValidator orphanSeatValidator;

    private static final long HOLD_DURATION_SECONDS = 600; // 10 phút giữ chỗ

    /**
     * Giữ chỗ ghế nguyên tử theo chuẩn Zero Overselling (All-or-Nothing)
     */
    public ReserveTicketResponse reserveSeats(ReserveTicketRequest request) {
        Instant now = Instant.now();
        Instant expiresAt = now.plusSeconds(HOLD_DURATION_SECONDS);
        long expireEpochSeconds = expiresAt.getEpochSecond();

        String orderCode = "ORD-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();

        // 0. Kiểm tra quy tắc chống ghế lẻ (Orphan Seat Prevention)
        Map<String, String> seatStatuses = redisSeatCacheService.getAllSeatStatuses(request.getEventId())
                .entrySet().stream()
                .collect(Collectors.toMap(
                        e -> e.getKey().toString(),
                        e -> e.getValue().toString()
                ));
        Optional<OrphanSeatValidator.OrphanViolationResult> orphanViolation =
                orphanSeatValidator.validateRequest(seatStatuses, request.getSeatIds());
        if (orphanViolation.isPresent()) {
            throw new SeatUnavailableException(orphanViolation.get().message());
        }

        // 1. Thực thi Redis Lua Script kiểm tra và khóa ghế nguyên tử
        RedisSeatCacheService.ReservationResult result = redisSeatCacheService.reserveSeats(
                request.getEventId(),
                request.getUserId(),
                expireEpochSeconds,
                request.getSeatIds()
        );

        if (!result.isSuccess()) {
            throw new SeatUnavailableException(result.getConflictingSeatId());
        }

        // 2. Tạo Event và phát tán sang NATS JetStream với Deduplication Header
        Map<String, Integer> concessionMap = null;
        if (request.getConcessions() != null && !request.getConcessions().isEmpty()) {
            concessionMap = request.getConcessions().stream()
                    .collect(Collectors.toMap(
                            ReserveTicketRequest.ConcessionItem::getConcessionId,
                            ReserveTicketRequest.ConcessionItem::getQuantity
                    ));
        }

        TicketReservedEvent event = TicketReservedEvent.builder()
                .orderCode(orderCode)
                .eventId(request.getEventId())
                .userId(request.getUserId())
                .seatIds(request.getSeatIds())
                .expiresAt(expiresAt)
                .createdAt(now)
                .concessions(concessionMap)
                .build();

        try {
            // Chờ JetStream xác nhận (Ack) đã ghi tin nhắn an toàn vào WAL stream (thường < 5ms)
            natsTicketEventPublisher.publishTicketReservedEvent(event).get(java.time.Duration.ofSeconds(5).toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            // Cơ chế phòng thủ Saga: Nếu NATS publish văng lỗi, nhả lại ghế trên Redis ngay lập tức
            log.error("NATS publish failed for order {}. Triggering compensating rollback on Redis.", orderCode, e);
            redisSeatCacheService.releaseSeats(request.getEventId(), request.getUserId(), request.getSeatIds());
            throw new RuntimeException("Lỗi hệ thống khi truyền thông điệp đặt vé", e);
        }

        return ReserveTicketResponse.builder()
                .orderCode(orderCode)
                .eventId(request.getEventId())
                .userId(request.getUserId())
                .seatIds(request.getSeatIds())
                .expiresAt(expiresAt)
                .status("PENDING")
                .build();
    }
}

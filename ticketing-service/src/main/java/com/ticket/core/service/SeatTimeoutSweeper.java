package com.ticket.core.service;

import com.ticket.core.domain.model.enums.OrderStatus;
import com.ticket.core.domain.model.enums.SeatStatus;
import com.ticket.infrastructure.adapter.out.persistence.entity.OrderEntity;
import com.ticket.infrastructure.adapter.out.persistence.entity.OrderItemEntity;
import com.ticket.infrastructure.adapter.out.persistence.repository.OrderItemRepository;
import com.ticket.infrastructure.adapter.out.persistence.repository.OrderRepository;
import com.ticket.infrastructure.adapter.out.persistence.repository.SeatRepository;
import com.ticket.infrastructure.adapter.out.websocket.SeatStatusBroadcaster;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class SeatTimeoutSweeper {

    private final StringRedisTemplate stringRedisTemplate;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final SeatRepository seatRepository;
    private final SeatStatusBroadcaster seatStatusBroadcaster;

    /**
     * Tác vụ định kỳ (mỗi 5 giây) quét và thu hồi ghế quá hạn 10 phút
     */
    @Scheduled(fixedDelay = 5000)
    @Transactional
    public void scheduledSweep() {
        sweepExpiredReservations();
    }

    /**
     * Thu hồi ghế quá hạn trên Redis Sorted Set và cập nhật đơn hàng thành EXPIRED trong Postgres
     */
    public int sweepExpiredReservations() {
        long currentEpochSeconds = Instant.now().getEpochSecond();
        int recoveredCount = 0;

        // 1. Quét Redis Sorted Sets để thu hồi trên RAM
        Set<String> expirationKeys = stringRedisTemplate.keys("{event:*}:expirations");
        if (expirationKeys != null) {
            for (String expKey : expirationKeys) {
                // Trích xuất eventId từ key format: {event:eventId}:expirations
                int start = expKey.indexOf("{event:") + 7;
                int end = expKey.indexOf("}:expirations");
                if (start >= 7 && end > start) {
                    String eventId = expKey.substring(start, end);
                    String seatHashKey = "{event:" + eventId + "}:seats";

                    // Tìm tất cả seatIds có expireTimestamp <= currentEpochSeconds
                    Set<String> expiredSeats = stringRedisTemplate.opsForZSet()
                            .rangeByScore(expKey, 0, currentEpochSeconds);

                    if (expiredSeats != null && !expiredSeats.isEmpty()) {
                        List<String> releasedSeats = new ArrayList<>();
                        for (String seatId : expiredSeats) {
                            Object currentStatus = stringRedisTemplate.opsForHash().get(seatHashKey, seatId);
                            // Chỉ thu hồi nếu ghế vẫn đang HELD (chưa thanh toán thành SOLD)
                            if (currentStatus != null && currentStatus.toString().startsWith("HELD:")) {
                                stringRedisTemplate.opsForHash().put(seatHashKey, seatId, "AVAILABLE");
                                releasedSeats.add(seatId);
                                recoveredCount++;
                            }
                            // Xóa khỏi ZSet expiration
                            stringRedisTemplate.opsForZSet().remove(expKey, seatId);
                        }

                        if (!releasedSeats.isEmpty()) {
                            log.info("Redis Sweeper: Released {} expired seats for eventId: {}", releasedSeats.size(), eventId);
                            seatStatusBroadcaster.broadcastSeatStatus(eventId, releasedSeats, "AVAILABLE", null);
                        }
                    }
                }
            }
        }

        // 2. Quét PostgreSQL để cập nhật đơn hàng quá hạn PENDING -> EXPIRED
        List<OrderEntity> expiredOrders = orderRepository.findExpiredOrders(OrderStatus.PENDING, Instant.now());
        for (OrderEntity order : expiredOrders) {
            order.setStatus(OrderStatus.EXPIRED);
            orderRepository.save(order);

            List<OrderItemEntity> items = orderItemRepository.findByOrderId(order.getId());
            List<UUID> seatIds = items.stream().map(OrderItemEntity::getSeatId).toList();
            if (!seatIds.isEmpty()) {
                seatRepository.updateStatusForSeats(seatIds, SeatStatus.AVAILABLE, null);
            }
            log.info("Database Sweeper: Marked order {} as EXPIRED and reverted seats to AVAILABLE.", order.getOrderCode());
        }

        return recoveredCount;
    }
}

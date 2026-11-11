package com.ticket.infrastructure.adapter.out.websocket;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class SeatStatusBroadcaster {

    private final SimpMessagingTemplate messagingTemplate;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SeatStatusUpdateMessage {
        private String eventId;
        private List<String> seatIds;
        private String status; // HELD, AVAILABLE, SOLD
        private String userId;
        private Instant timestamp;
    }

    /**
     * Broadcast cập nhật trạng thái ghế đến tất cả client đang theo dõi sự kiện
     */
    public void broadcastSeatStatus(String eventId, List<String> seatIds, String status, String userId) {
        String destination = "/topic/events/" + eventId + "/seats";
        SeatStatusUpdateMessage message = SeatStatusUpdateMessage.builder()
                .eventId(eventId)
                .seatIds(seatIds)
                .status(status)
                .userId(userId)
                .timestamp(Instant.now())
                .build();

        try {
            messagingTemplate.convertAndSend(destination, message);
            log.info("Broadcasted seat status [{}] for seats {} to destination: {}", status, seatIds, destination);
        } catch (Exception e) {
            log.error("Failed to broadcast seat status to {}: {}", destination, e.getMessage());
        }
    }
}

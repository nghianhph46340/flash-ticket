package com.ticket.core.domain.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketReservedEvent {
    private String orderCode;
    private String eventId;
    private String userId;
    private List<String> seatIds;
    private Instant expiresAt;
    private Instant createdAt;
}

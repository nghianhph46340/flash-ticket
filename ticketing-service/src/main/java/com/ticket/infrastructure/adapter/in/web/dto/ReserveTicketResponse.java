package com.ticket.infrastructure.adapter.in.web.dto;

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
public class ReserveTicketResponse {
    private String orderCode;
    private String eventId;
    private String userId;
    private List<String> seatIds;
    private Instant expiresAt;
    private String status;
}

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
public class TicketInfoResponse {
    private String ticketCode;
    private String qrHash;
    private String status;
    private Instant checkinTime;
    private String seatNum;
    private String movieTitle;
    private String showtimeStart;
    private String auditoriumName;
}

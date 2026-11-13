package com.ticket.infrastructure.adapter.in.web.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CheckInResponse {
    private boolean success;
    private String ticketCode;
    private String seatNum;
    private String movieTitle;
    private String showtimeStart;
    private String auditoriumName;
    private String status;
    private Instant checkinTime;
    private String message;
}

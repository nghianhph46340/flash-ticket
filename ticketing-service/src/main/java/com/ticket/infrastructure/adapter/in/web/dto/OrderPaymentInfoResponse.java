package com.ticket.infrastructure.adapter.in.web.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderPaymentInfoResponse {
    private String orderCode;
    private String eventId;
    private BigDecimal amount;
    private String status;
    private Instant expiresAt;
    private List<String> seatIds;
    private String bankName;
    private String accountNumber;
    private String accountName;
    private String qrImageUrl;
}

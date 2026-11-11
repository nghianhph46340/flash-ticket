package com.ticket.infrastructure.adapter.in.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentWebhookRequest {

    @NotBlank(message = "transactionCode is required")
    private String transactionCode;

    @NotBlank(message = "orderCode is required")
    private String orderCode;

    @NotNull(message = "amount is required")
    private BigDecimal amount;

    private String paymentMethod; // "VIETQR", "PAYOS", "VNPAY"

    @NotBlank(message = "idempotencyKey is required")
    private String idempotencyKey;

    private String signature;
}

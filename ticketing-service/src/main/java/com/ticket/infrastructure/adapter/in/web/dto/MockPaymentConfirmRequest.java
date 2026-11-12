package com.ticket.infrastructure.adapter.in.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Request body cho endpoint giả lập thanh toán (chỉ dùng trong môi trường dev/mock).
 * Frontend gửi request này để trigger luồng xử lý webhook thanh toán thành công.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MockPaymentConfirmRequest {

    @NotBlank(message = "orderCode không được để trống")
    private String orderCode;

    @NotNull(message = "amount không được để trống")
    private BigDecimal amount;

    /**
     * Kết quả giả lập: "SUCCESS" (mặc định) hoặc "FAILED" để test Saga rollback
     */
    private String result;
}

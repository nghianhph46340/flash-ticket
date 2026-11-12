package com.ticket.infrastructure.adapter.in.web;

import com.ticket.core.service.PaymentService;
import com.ticket.infrastructure.adapter.in.web.dto.MockPaymentConfirmRequest;
import com.ticket.infrastructure.adapter.in.web.dto.PaymentWebhookRequest;
import com.ticket.infrastructure.adapter.in.web.dto.PaymentWebhookResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Mock Payment Controller — CHỈ active khi payment.provider=mock (dev/test).
 *
 * Cung cấp 2 endpoint giả lập:
 * - POST /api/v1/payments/mock-confirm  : Giả lập thanh toán thành công
 * - POST /api/v1/payments/mock-fail     : Giả lập thanh toán thất bại (test Saga rollback)
 *
 * Controller này sẽ KHÔNG được load trong môi trường production (payment.provider=payos).
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "payment.provider", havingValue = "mock", matchIfMissing = true)
public class MockPaymentController {

    private final PaymentService paymentService;

    /**
     * Giả lập webhook thanh toán thành công từ cổng thanh toán.
     *
     * Frontend gọi endpoint này sau khi người dùng bấm "Xác nhận thanh toán"
     * trong modal demo thay vì quét QR thật.
     *
     * Endpoint này gọi thẳng vào PaymentService.processWebhook() —
     * tức là toàn bộ nghiệp vụ: Idempotency, cập nhật Redis, xuất vé QR,
     * broadcast WebSocket đều chạy y chang production.
     */
    @PostMapping("/mock-confirm")
    public ResponseEntity<PaymentWebhookResponse> mockConfirm(
            @Valid @RequestBody MockPaymentConfirmRequest request) {

        log.info("[MockPayment] Giả lập thanh toán THÀNH CÔNG cho orderCode={}", request.getOrderCode());

        // Tạo idempotency key mới cho mỗi lần giả lập
        String idempotencyKey = "MOCK-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase();
        String transactionCode = "MOCK-TXN-" + System.currentTimeMillis();

        PaymentWebhookRequest webhookRequest = PaymentWebhookRequest.builder()
                .orderCode(request.getOrderCode())
                .transactionCode(transactionCode)
                .amount(request.getAmount())
                .paymentMethod("MOCK")
                .idempotencyKey(idempotencyKey)
                .signature("MOCK_SIG") // MockPaymentGateway.verifyWebhookSignature() luôn trả true
                .build();

        PaymentWebhookResponse response = paymentService.processWebhook(webhookRequest);
        return ResponseEntity.ok(response);
    }

    /**
     * Giả lập hủy thanh toán / hết hạn — dùng để test luồng Saga rollback
     * (nhả ghế tự động, chuyển order sang CANCELLED).
     */
    @PostMapping("/mock-cancel/{orderCode}")
    public ResponseEntity<PaymentWebhookResponse> mockCancel(@PathVariable String orderCode) {
        log.info("[MockPayment] Giả lập HỦY thanh toán cho orderCode={}", orderCode);

        PaymentWebhookResponse response = PaymentWebhookResponse.builder()
                .status("CANCELLED")
                .orderCode(orderCode)
                .message("Thanh toán bị hủy (mock). Ghế sẽ tự động được nhả sau khi hết hạn TTL.")
                .ticketCodes(java.util.Collections.emptyList())
                .build();

        return ResponseEntity.ok(response);
    }
}

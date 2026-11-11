package com.ticket.infrastructure.adapter.in.web;

import com.ticket.core.service.PaymentService;
import com.ticket.infrastructure.adapter.in.web.dto.OrderPaymentInfoResponse;
import com.ticket.infrastructure.adapter.in.web.dto.PaymentWebhookRequest;
import com.ticket.infrastructure.adapter.in.web.dto.PaymentWebhookResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    /**
     * Webhook tiếp nhận thông báo thanh toán từ PayOS / VietQR Sandbox
     */
    @PostMapping("/payments/webhook")
    public ResponseEntity<PaymentWebhookResponse> handlePaymentWebhook(@Valid @RequestBody PaymentWebhookRequest request) {
        PaymentWebhookResponse response = paymentService.processWebhook(request);
        return ResponseEntity.ok(response);
    }

    /**
     * Lấy thông tin thanh toán & link VietQR động cho đơn hàng
     */
    @GetMapping("/orders/{orderCode}/payment-info")
    public ResponseEntity<OrderPaymentInfoResponse> getPaymentInfo(@PathVariable String orderCode) {
        OrderPaymentInfoResponse response = paymentService.getPaymentInfo(orderCode);
        return ResponseEntity.ok(response);
    }
}

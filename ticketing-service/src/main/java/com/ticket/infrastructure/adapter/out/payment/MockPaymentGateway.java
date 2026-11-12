package com.ticket.infrastructure.adapter.out.payment;

import com.ticket.core.port.out.PaymentGateway;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Mock Payment Gateway — dùng cho môi trường dev và test.
 *
 * Kích hoạt khi: payment.provider=mock (mặc định trong application.yml dev)
 *
 * Không cần webhook thật từ bên ngoài. Frontend sẽ gọi API
 * POST /api/v1/payments/mock-confirm để giả lập thanh toán thành công.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "payment.provider", havingValue = "mock", matchIfMissing = true)
public class MockPaymentGateway implements PaymentGateway {

    private static final String MOCK_BANK    = "MOCK_BANK";
    private static final String MOCK_ACCOUNT = "0000000000";
    private static final String MOCK_NAME    = "FLASHTICKET DEMO";

    @Override
    public PaymentInitResult createPayment(String orderCode, BigDecimal amount, String description) {
        log.info("[MockPayment] Khởi tạo thanh toán ảo: orderCode={}, amount={}", orderCode, amount);

        // Tạo QR giả dùng placeholder image có thể show trên UI
        String qrImageUrl = String.format(
                "https://api.qrserver.com/v1/create-qr-code/?size=200x200&data=MOCK_PAYMENT:%s:%s",
                orderCode, amount.toPlainString()
        );

        return new PaymentInitResult(
                null,           // Không redirect, frontend tự show modal
                qrImageUrl,
                MOCK_BANK,
                MOCK_ACCOUNT,
                MOCK_NAME,
                "MOCK"
        );
    }

    @Override
    public boolean verifyWebhookSignature(String payload, String signature) {
        // Mock gateway luôn chấp nhận chữ ký "MOCK_SIG" hoặc bất kỳ giá trị nào
        log.debug("[MockPayment] Bỏ qua xác thực chữ ký trong môi trường mock");
        return true;
    }
}

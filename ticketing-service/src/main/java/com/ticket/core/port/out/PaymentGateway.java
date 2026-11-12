package com.ticket.core.port.out;

import java.math.BigDecimal;

/**
 * Port (Interface) cho cổng thanh toán.
 * Theo Hexagonal Architecture: Core domain không phụ thuộc vào implementation cụ thể.
 *
 * Có 2 adapter implement interface này:
 * - MockPaymentGateway: Dùng cho môi trường dev/test (không cần webhook thật)
 * - PayOsPaymentGateway: Dùng cho môi trường production (tích hợp PayOS thật)
 */
public interface PaymentGateway {

    /**
     * Khởi tạo giao dịch thanh toán và trả về thông tin hiển thị cho khách.
     */
    PaymentInitResult createPayment(String orderCode, BigDecimal amount, String description);

    /**
     * Xác thực chữ ký từ webhook callback (HMAC hoặc mock).
     * @return true nếu chữ ký hợp lệ
     */
    boolean verifyWebhookSignature(String payload, String signature);

    // DTO kết quả khởi tạo thanh toán
    record PaymentInitResult(
            String paymentUrl,      // URL redirect đến trang thanh toán (hoặc null nếu mock)
            String qrImageUrl,      // URL ảnh QR Code
            String bankName,
            String accountNumber,
            String accountName,
            String provider         // "MOCK", "PAYOS", "VIETQR"
    ) {}
}

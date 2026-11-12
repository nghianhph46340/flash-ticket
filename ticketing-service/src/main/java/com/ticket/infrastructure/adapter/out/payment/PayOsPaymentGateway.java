package com.ticket.infrastructure.adapter.out.payment;

import com.ticket.core.port.out.PaymentGateway;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

/**
 * PayOS Payment Gateway — dùng cho môi trường production.
 *
 * Kích hoạt khi: payment.provider=payos
 * Cần cấu hình: payment.payos.client-id, payment.payos.api-key, payment.payos.checksum-key
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "payment.provider", havingValue = "payos")
public class PayOsPaymentGateway implements PaymentGateway {

    private static final String BANK_ID      = "MB";
    private static final String ACCOUNT_NO   = "8888999966";
    private static final String ACCOUNT_NAME = "FLASHTICKET VIETNAM";

    @Value("${payment.payos.checksum-key:changeme}")
    private String checksumKey;

    @Override
    public PaymentInitResult createPayment(String orderCode, BigDecimal amount, String description) {
        log.info("[PayOS] Khởi tạo thanh toán PayOS: orderCode={}, amount={}", orderCode, amount);

        // Tạo QR VietQR chuẩn NAPAS
        String qrImageUrl = String.format(
                "https://img.vietqr.io/image/%s-%s-compact2.png?amount=%s&addInfo=%s&accountName=%s",
                BANK_ID, ACCOUNT_NO,
                amount.toPlainString(),
                "TICKET+" + orderCode,
                ACCOUNT_NAME.replace(" ", "%20")
        );

        return new PaymentInitResult(
                null,
                qrImageUrl,
                BANK_ID,
                ACCOUNT_NO,
                ACCOUNT_NAME,
                "PAYOS"
        );
    }

    @Override
    public boolean verifyWebhookSignature(String payload, String signature) {
        if (signature == null || signature.isBlank()) {
            log.warn("[PayOS] Thiếu chữ ký webhook");
            return false;
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(checksumKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] computed = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            String computedHex = HexFormat.of().formatHex(computed);
            boolean valid = computedHex.equalsIgnoreCase(signature);
            if (!valid) {
                log.warn("[PayOS] Chữ ký không hợp lệ. expected={}, got={}", computedHex, signature);
            }
            return valid;
        } catch (Exception e) {
            log.error("[PayOS] Lỗi xác thực chữ ký: {}", e.getMessage());
            return false;
        }
    }
}

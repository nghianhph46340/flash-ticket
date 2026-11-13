package com.ticket.core.service;

import com.ticket.core.domain.model.enums.OrderStatus;
import com.ticket.core.domain.model.enums.PaymentStatus;
import com.ticket.core.domain.model.enums.SeatStatus;
import com.ticket.core.domain.model.enums.TicketStatus;
import com.ticket.core.port.out.PaymentGateway;
import com.ticket.infrastructure.adapter.in.web.dto.OrderPaymentInfoResponse;
import com.ticket.infrastructure.adapter.in.web.dto.PaymentWebhookRequest;
import com.ticket.infrastructure.adapter.in.web.dto.PaymentWebhookResponse;
import com.ticket.infrastructure.adapter.out.persistence.entity.OrderEntity;
import com.ticket.infrastructure.adapter.out.persistence.entity.OrderItemEntity;
import com.ticket.infrastructure.adapter.out.persistence.entity.PaymentEntity;
import com.ticket.infrastructure.adapter.out.persistence.entity.SeatEntity;
import com.ticket.infrastructure.adapter.out.persistence.entity.TicketEntity;
import com.ticket.infrastructure.adapter.out.persistence.repository.*;
import com.ticket.infrastructure.adapter.out.websocket.SeatStatusBroadcaster;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final SeatRepository seatRepository;
    private final TicketRepository ticketRepository;
    private final StringRedisTemplate stringRedisTemplate;
    private final SeatStatusBroadcaster seatStatusBroadcaster;
    private final PaymentGateway paymentGateway;

    private static final String BANK_ID = "MB";
    private static final String ACCOUNT_NO = "8888999966";
    private static final String ACCOUNT_NAME = "FLASHTICKET VIETNAM";

    /**
     * Xử lý Payment Webhook từ cổng thanh toán PayOS / VietQR với Idempotency Key
     */
    @Transactional
    public PaymentWebhookResponse processWebhook(PaymentWebhookRequest request) {
        log.info("Received Payment Webhook: orderCode={}, transactionCode={}, idempotencyKey={}",
                request.getOrderCode(), request.getTransactionCode(), request.getIdempotencyKey());

        // 1. KIỂM TRA TÍNH BẤT BIẾN (IDEMPOTENCY): Chống ghi nhận thanh toán trùng lặp
        Optional<PaymentEntity> existingPayment = paymentRepository.findByIdempotencyKey(request.getIdempotencyKey());
        if (existingPayment.isPresent()) {
            log.warn("Idempotent duplicate payment webhook detected for key: {}. Skipping.", request.getIdempotencyKey());
            List<TicketEntity> existingTickets = ticketRepository.findByOrderId(existingPayment.get().getOrderId());
            List<String> ticketCodes = existingTickets.stream().map(TicketEntity::getTicketCode).toList();

            return PaymentWebhookResponse.builder()
                    .status("SUCCESS")
                    .orderCode(request.getOrderCode())
                    .message("Idempotent duplicate transaction acknowledged")
                    .ticketCodes(ticketCodes)
                    .build();
        }

        // 2. TÌM ĐƠN HÀNG TRONG CƠ SỞ DỮ LIỆU
        OrderEntity order = orderRepository.findByOrderCode(request.getOrderCode())
                .orElseThrow(() -> new IllegalArgumentException("Order not found: " + request.getOrderCode()));

        // Nếu đơn hàng đã hết hạn hoặc bị hủy
        if (order.getStatus() == OrderStatus.EXPIRED || order.getStatus() == OrderStatus.CANCELLED) {
            log.warn("Payment arrived for {} order: {}. Recording FAILED payment.", order.getStatus(), order.getOrderCode());

            PaymentEntity failedPayment = PaymentEntity.builder()
                    .orderId(order.getId())
                    .transactionCode(request.getTransactionCode())
                    .paymentMethod(request.getPaymentMethod() != null ? request.getPaymentMethod() : "VIETQR")
                    .amount(request.getAmount())
                    .status(PaymentStatus.FAILED)
                    .idempotencyKey(request.getIdempotencyKey())
                    .paidAt(Instant.now())
                    .build();
            paymentRepository.save(failedPayment);

            return PaymentWebhookResponse.builder()
                    .status("ORDER_EXPIRED")
                    .orderCode(order.getOrderCode())
                    .message("Order expired prior to payment receipt. Transaction logged for refund.")
                    .ticketCodes(Collections.emptyList())
                    .build();
        }

        // 3. CẬP NHẬT ĐƠN HÀNG SANG TRẠNG THÁI PAID
        order.setStatus(OrderStatus.PAID);
        orderRepository.save(order);

        // 4. LƯU BẢN GHI THANH TOÁN THÀNH CÔNG VÀO BẢNG PAYMENTS
        PaymentEntity payment = PaymentEntity.builder()
                .orderId(order.getId())
                .transactionCode(request.getTransactionCode())
                .paymentMethod(request.getPaymentMethod() != null ? request.getPaymentMethod() : "VIETQR")
                .amount(request.getAmount())
                .status(PaymentStatus.SUCCESS)
                .idempotencyKey(request.getIdempotencyKey())
                .paidAt(Instant.now())
                .build();
        paymentRepository.save(payment);

        // 5. SINH VÉ ĐIỆN TỬ CHÍNH THỨC VÀ CẬP NHẬT TRẠNG THÁI GHẾ
        List<OrderItemEntity> items = orderItemRepository.findByOrderId(order.getId());
        List<String> ticketCodes = new ArrayList<>();
        List<String> seatIdStrings = new ArrayList<>();

        String eventIdStr = order.getEventId().toString();
        String seatHashKey = "{event:" + eventIdStr + "}:seats";
        String expirationZSetKey = "{event:" + eventIdStr + "}:expirations";

        for (OrderItemEntity item : items) {
            String ticketCode = "TKT-" + UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase();
            String qrHash = generateQrHash(ticketCode, order.getOrderCode(), item.getSeatId().toString());

            TicketEntity ticket = TicketEntity.builder()
                    .ticketCode(ticketCode)
                    .orderId(order.getId())
                    .seatId(item.getSeatId())
                    .userId(order.getUserId())
                    .eventId(order.getEventId())
                    .qrHash(qrHash)
                    .status(TicketStatus.VALID)
                    .build();

            ticketRepository.save(ticket);
            ticketCodes.add(ticketCode);

            // Cập nhật trạng thái ghế trong PostgreSQL
            seatRepository.findById(item.getSeatId()).ifPresent(seat -> {
                seat.setStatus(SeatStatus.SOLD);
                seat.setReservationExpiresAt(null);
                seatRepository.save(seat);
                seatIdStrings.add(seat.getSeatNum());

                // Cập nhật trạng thái ghế trên Redis In-Memory: Chuyển sang SOLD và xóa khỏi ZSet hết hạn
                stringRedisTemplate.opsForHash().put(seatHashKey, seat.getSeatNum(), "SOLD");
                stringRedisTemplate.opsForZSet().remove(expirationZSetKey, seat.getSeatNum());
                stringRedisTemplate.opsForHash().put(seatHashKey, seat.getId().toString(), "SOLD");
                stringRedisTemplate.opsForZSet().remove(expirationZSetKey, seat.getId().toString());
            });
        }

        // 6. PHÁT TÍN HIỆU WEBSOCKET STOMP: Cập nhật màu ghế sang SOLD trên toàn bộ client
        seatStatusBroadcaster.broadcastSeatStatus(
                eventIdStr,
                seatIdStrings.isEmpty() ? items.stream().map(i -> i.getSeatId().toString()).toList() : seatIdStrings,
                "SOLD",
                order.getUserId().toString()
        );

        log.info("Payment successful for order {}. Generated {} tickets: {}", order.getOrderCode(), ticketCodes.size(), ticketCodes);

        return PaymentWebhookResponse.builder()
                .status("SUCCESS")
                .orderCode(order.getOrderCode())
                .message("Payment processed successfully. Tickets issued.")
                .ticketCodes(ticketCodes)
                .build();
    }

    /**
     * Lấy thông tin thanh toán VietQR cho một đơn hàng
     */
    @Transactional(readOnly = true)
    public OrderPaymentInfoResponse getPaymentInfo(String orderCode) {
        OrderEntity order = orderRepository.findByOrderCode(orderCode)
                .orElseThrow(() -> new IllegalArgumentException("Order not found: " + orderCode));

        List<OrderItemEntity> items = orderItemRepository.findByOrderId(order.getId());
        List<String> seatIds = items.stream()
                .map(i -> seatRepository.findById(i.getSeatId())
                        .map(SeatEntity::getSeatNum)
                        .orElse(i.getSeatId().toString()))
                .toList();

        // Tạo thông tin thanh toán qua PaymentGateway
        PaymentGateway.PaymentInitResult payResult = paymentGateway.createPayment(
                order.getOrderCode(), order.getTotalAmount(), "FlashTicket " + order.getOrderCode()
        );

        return OrderPaymentInfoResponse.builder()
                .orderCode(order.getOrderCode())
                .eventId(order.getEventId().toString())
                .amount(order.getTotalAmount())
                .status(order.getStatus().name())
                .expiresAt(order.getExpiresAt())
                .seatIds(seatIds)
                .bankName(payResult.bankName())
                .accountNumber(payResult.accountNumber())
                .accountName(payResult.accountName())
                .qrImageUrl(payResult.qrImageUrl())
                .build();
    }

    private String generateQrHash(String ticketCode, String orderCode, String seatId) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String raw = ticketCode + ":" + orderCode + ":" + seatId + ":" + System.currentTimeMillis();
            byte[] hash = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception e) {
            return UUID.randomUUID().toString();
        }
    }
}

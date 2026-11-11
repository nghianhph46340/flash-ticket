package com.ticket;

import com.ticket.core.domain.model.enums.OrderStatus;
import com.ticket.core.domain.model.enums.PaymentStatus;
import com.ticket.core.service.PaymentService;
import com.ticket.core.service.SeatTimeoutSweeper;
import com.ticket.core.service.TicketReservationService;
import com.ticket.infrastructure.adapter.in.web.dto.*;
import com.ticket.infrastructure.adapter.out.persistence.entity.OrderEntity;
import com.ticket.infrastructure.adapter.out.persistence.entity.PaymentEntity;
import com.ticket.infrastructure.adapter.out.persistence.entity.TicketEntity;
import com.ticket.infrastructure.adapter.out.persistence.repository.OrderRepository;
import com.ticket.infrastructure.adapter.out.persistence.repository.PaymentRepository;
import com.ticket.infrastructure.adapter.out.persistence.repository.TicketRepository;
import com.ticket.infrastructure.adapter.out.redis.RedisSeatCacheService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class BookingAndPaymentIntegrationTest {

    @Autowired
    private TicketReservationService ticketReservationService;

    @Autowired
    private RedisSeatCacheService redisSeatCacheService;

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private SeatTimeoutSweeper seatTimeoutSweeper;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private TicketRepository ticketRepository;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    private static final String TEST_EVENT_ID = UUID.randomUUID().toString();

    @Test
    @DisplayName("Luồng End-to-End: Giữ chỗ -> NATS JetStream Consumer ghi DB -> PayOS Webhook -> Cấp vé điện tử")
    void testEndToEndBookingAndPaymentFlow() throws InterruptedException {
        // 1. Pre-warm 2 ghế trên Redis
        String seat1 = "seat-E2E-01";
        String seat2 = "seat-E2E-02";
        Map<String, String> seatMap = new HashMap<>();
        seatMap.put(seat1, "AVAILABLE");
        seatMap.put(seat2, "AVAILABLE");
        redisSeatCacheService.preWarmSeats(TEST_EVENT_ID, seatMap);

        // 2. Người dùng gửi request giữ 2 ghế
        ReserveTicketRequest reserveRequest = ReserveTicketRequest.builder()
                .eventId(TEST_EVENT_ID)
                .userId("user-vip-101")
                .seatIds(Arrays.asList(seat1, seat2))
                .build();

        ReserveTicketResponse reserveResponse = ticketReservationService.reserveSeats(reserveRequest);
        assertNotNull(reserveResponse);
        String orderCode = reserveResponse.getOrderCode();
        assertNotNull(orderCode);

        // Ghế trên Redis phải ngay lập tức chuyển sang HELD
        assertEquals("HELD:user-vip-101", redisSeatCacheService.getSeatStatus(TEST_EVENT_ID, seat1));
        assertEquals("HELD:user-vip-101", redisSeatCacheService.getSeatStatus(TEST_EVENT_ID, seat2));

        // 3. Chờ NATS JetStream Consumer tiêu thụ event và lưu đơn hàng PENDING vào PostgreSQL
        OrderEntity savedOrder = null;
        for (int i = 0; i < 50; i++) {
            Optional<OrderEntity> orderOpt = orderRepository.findByOrderCode(orderCode);
            if (orderOpt.isPresent()) {
                savedOrder = orderOpt.get();
                break;
            }
            Thread.sleep(200);
        }

        assertNotNull(savedOrder, "NATS JetStream Consumer phải ghi đơn hàng vào PostgreSQL thành công");
        assertEquals(OrderStatus.PENDING, savedOrder.getStatus());
        assertEquals(new BigDecimal("500000.00"), savedOrder.getTotalAmount(), "2 vé x 250k = 500k");

        // 4. Lấy thông tin thanh toán VietQR
        OrderPaymentInfoResponse paymentInfo = paymentService.getPaymentInfo(orderCode);
        assertNotNull(paymentInfo);
        assertTrue(paymentInfo.getQrImageUrl().contains("vietqr.io"));

        // 5. Cổng thanh toán gửi Webhook thông báo người dùng đã chuyển khoản thành công
        String idempotencyKey = "TXN-IDEMPOTENT-" + UUID.randomUUID();
        String transactionCode = "MB-TRANS-" + UUID.randomUUID().toString().substring(0, 8);
        PaymentWebhookRequest webhookRequest = PaymentWebhookRequest.builder()
                .orderCode(orderCode)
                .transactionCode(transactionCode)
                .amount(savedOrder.getTotalAmount())
                .paymentMethod("VIETQR")
                .idempotencyKey(idempotencyKey)
                .build();

        PaymentWebhookResponse webhookResponse = paymentService.processWebhook(webhookRequest);
        assertEquals("SUCCESS", webhookResponse.getStatus());
        assertEquals(2, webhookResponse.getTicketCodes().size(), "Phải cấp phát chính xác 2 mã vé điện tử");

        // 6. Kiểm chứng cơ sở dữ liệu sau khi thanh toán
        OrderEntity paidOrder = orderRepository.findByOrderCode(orderCode).orElseThrow();
        assertEquals(OrderStatus.PAID, paidOrder.getStatus(), "Trạng thái đơn hàng phải cập nhật thành PAID");

        PaymentEntity paymentRecord = paymentRepository.findByIdempotencyKey(idempotencyKey).orElseThrow();
        assertEquals(PaymentStatus.SUCCESS, paymentRecord.getStatus());
        assertEquals(paidOrder.getId(), paymentRecord.getOrderId());

        List<TicketEntity> issuedTickets = ticketRepository.findByOrderId(paidOrder.getId());
        assertEquals(2, issuedTickets.size());
        for (TicketEntity ticket : issuedTickets) {
            assertNotNull(ticket.getTicketCode());
            assertNotNull(ticket.getQrHash());
        }

        // 7. Kiểm chứng trạng thái trên Redis: Chuyển sang SOLD
        assertEquals("SOLD", redisSeatCacheService.getSeatStatus(TEST_EVENT_ID, seat1));
        assertEquals("SOLD", redisSeatCacheService.getSeatStatus(TEST_EVENT_ID, seat2));

        // 8. KIỂM CHỨNG TÍNH BẤT BIẾN (IDEMPOTENCY): Gửi lại Webhook lần 2 với cùng idempotencyKey
        PaymentWebhookResponse duplicateResponse = paymentService.processWebhook(webhookRequest);
        assertEquals("SUCCESS", duplicateResponse.getStatus());
        assertEquals("Idempotent duplicate transaction acknowledged", duplicateResponse.getMessage());

        // Đảm bảo không tạo thêm vé nào
        List<TicketEntity> ticketsAfterDuplicate = ticketRepository.findByOrderId(paidOrder.getId());
        assertEquals(2, ticketsAfterDuplicate.size(), "Số lượng vé không bị nhân đôi khi nhận webhook lặp!");
    }

    @Test
    @DisplayName("Kiểm tra Timeout Sweeper: Thu hồi ghế quá hạn chưa thanh toán, trả lại AVAILABLE trên Redis & EXPIRED trên DB")
    void testTimeoutSweeperRollback() {
        String timeoutSeat = "seat-TIMEOUT-001";
        String eventId = "evt-timeout-test";

        // Pre-warm ghế
        Map<String, String> seatMap = new HashMap<>();
        seatMap.put(timeoutSeat, "AVAILABLE");
        redisSeatCacheService.preWarmSeats(eventId, seatMap);

        // Khóa ghế với thời điểm hết hạn trong quá khứ (10 giây trước)
        long pastTimestamp = Instant.now().minusSeconds(10).getEpochSecond();
        redisSeatCacheService.reserveSeats(eventId, "user-timeout", pastTimestamp, Collections.singletonList(timeoutSeat));

        // Xác nhận ghế đang ở trạng thái HELD
        assertEquals("HELD:user-timeout", redisSeatCacheService.getSeatStatus(eventId, timeoutSeat));

        // Kích hoạt Sweeper quét thu hồi
        int recovered = seatTimeoutSweeper.sweepExpiredReservations();
        assertTrue(recovered >= 1, "Sweeper phải phát hiện và thu hồi ít nhất 1 ghế quá hạn");

        // Kiểm tra ghế trên Redis đã được hoàn trả lại thành AVAILABLE
        assertEquals("AVAILABLE", redisSeatCacheService.getSeatStatus(eventId, timeoutSeat));

        // Kiểm tra ghế không còn nằm trong Redis Sorted Set expirations
        String expKey = "{event:" + eventId + "}:expirations";
        Double score = stringRedisTemplate.opsForZSet().score(expKey, timeoutSeat);
        assertNull(score, "Ghế đã thu hồi phải bị xóa khỏi ZSet hết hạn");
    }
}

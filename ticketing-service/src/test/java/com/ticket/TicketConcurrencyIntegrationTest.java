package com.ticket;

import com.ticket.core.domain.exception.SeatUnavailableException;
import com.ticket.core.service.TicketReservationService;
import com.ticket.infrastructure.adapter.in.web.dto.ReserveTicketRequest;
import com.ticket.infrastructure.adapter.in.web.dto.ReserveTicketResponse;
import com.ticket.infrastructure.adapter.out.redis.RedisSeatCacheService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class TicketConcurrencyIntegrationTest {

    @Autowired
    private TicketReservationService ticketReservationService;

    @Autowired
    private RedisSeatCacheService redisSeatCacheService;

    private static final String TEST_EVENT_ID = "evt-race-test-001";
    private static final String TARGET_SEAT = "seat-VIP-01";

    @BeforeEach
    void setUp() {
        // Pre-warm đúng 1 ghế VIP để 100 threads cùng tranh mua
        Map<String, String> seats = new HashMap<>();
        seats.put(TARGET_SEAT, "AVAILABLE");
        seats.put("seat-VIP-02", "AVAILABLE");
        redisSeatCacheService.preWarmSeats(TEST_EVENT_ID, seats);
    }

    @Test
    @DisplayName("Xác minh Zero Overselling: 100 luồng đồng thời tranh chấp 1 ghế, chỉ DUY NHẤT 1 luồng thành công")
    void test100ThreadsConcurrentRaceCondition() throws InterruptedException {
        int threadCount = 100;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);

        CountDownLatch readyLatch = new CountDownLatch(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);
        AtomicInteger otherErrorCount = new AtomicInteger(0);

        List<ReserveTicketResponse> successfulOrders = Collections.synchronizedList(new ArrayList<>());

        for (int i = 1; i <= threadCount; i++) {
            final String userId = "user-" + i;
            executorService.submit(() -> {
                readyLatch.countDown();
                try {
                    // Chờ tín hiệu xuất phát đồng loạt từ startLatch
                    startLatch.await();

                    ReserveTicketRequest request = ReserveTicketRequest.builder()
                            .eventId(TEST_EVENT_ID)
                            .userId(userId)
                            .seatIds(Collections.singletonList(TARGET_SEAT))
                            .build();

                    ReserveTicketResponse response = ticketReservationService.reserveSeats(request);
                    successCount.incrementAndGet();
                    successfulOrders.add(response);
                } catch (SeatUnavailableException e) {
                    // Xung đột ghế đã có người giữ trước (Tương đương HTTP 409 Conflict)
                    conflictCount.incrementAndGet();
                } catch (Exception e) {
                    otherErrorCount.incrementAndGet();
                }
            });
        }

        // Chờ 100 threads sẵn sàng
        readyLatch.await(5, TimeUnit.SECONDS);

        // Bắn súng xuất phát! Cả 100 threads đồng loạt gọi API trong cùng 1 mili-giây
        long startTime = System.currentTimeMillis();
        startLatch.countDown();

        executorService.shutdown();
        assertTrue(executorService.awaitTermination(10, TimeUnit.SECONDS));
        long duration = System.currentTimeMillis() - startTime;

        System.out.println("==================================================");
        System.out.println("⚡ KẾT QUẢ KIỂM THỬ CHỊU TẢI 100 THREADS TRANH GHẾ:");
        System.out.println("⏱️ Tổng thời gian xử lý: " + duration + " ms");
        System.out.println("✅ Số đơn hàng thành công (HTTP 200): " + successCount.get());
        System.out.println("❌ Số đơn bị từ chối do xung đột (HTTP 409): " + conflictCount.get());
        System.out.println("⚠️ Lỗi khác: " + otherErrorCount.get());
        System.out.println("==================================================");

        // KIỂM CHỨNG TOÀN VẸN DỮ LIỆU TUYỆT ĐỐI (ZERO OVERSELLING)
        assertEquals(1, successCount.get(), "Chính xác DUY NHẤT 1 người dùng được phép giữ ghế thành công!");
        assertEquals(99, conflictCount.get(), "99 người dùng còn lại phải bị từ chối vì ghế đã có người lấy!");
        assertEquals(0, otherErrorCount.get(), "Không có bất kỳ lỗi 500 nào phát sinh!");

        // Kiểm tra trạng thái lưu trên Redis Hash
        String seatStatusInRedis = redisSeatCacheService.getSeatStatus(TEST_EVENT_ID, TARGET_SEAT);
        assertNotNull(seatStatusInRedis);
        assertTrue(seatStatusInRedis.startsWith("HELD:user-"), "Ghế phải có trạng thái HELD bởi user chiến thắng");

        ReserveTicketResponse winningOrder = successfulOrders.get(0);
        assertEquals("HELD:" + winningOrder.getUserId(), seatStatusInRedis);
    }

    @Test
    @DisplayName("Xác minh All-or-Nothing: Đặt 2 ghế nhưng 1 ghế đã bị giữ thì không giữ ghế còn lại")
    void testAllOrNothingAtomicity() {
        // Đặt ghế 1 trước
        ReserveTicketRequest req1 = ReserveTicketRequest.builder()
                .eventId(TEST_EVENT_ID)
                .userId("user-early-bird")
                .seatIds(Collections.singletonList(TARGET_SEAT))
                .build();
        ticketReservationService.reserveSeats(req1);

        // User 2 muốn đặt cả TARGET_SEAT và seat-VIP-02
        ReserveTicketRequest req2 = ReserveTicketRequest.builder()
                .eventId(TEST_EVENT_ID)
                .userId("user-late")
                .seatIds(Arrays.asList(TARGET_SEAT, "seat-VIP-02"))
                .build();

        assertThrows(SeatUnavailableException.class, () -> ticketReservationService.reserveSeats(req2));

        // Ghế VIP-02 không được phép bị khóa dở dang, phải vẫn là AVAILABLE
        String seat2Status = redisSeatCacheService.getSeatStatus(TEST_EVENT_ID, "seat-VIP-02");
        assertEquals("AVAILABLE", seat2Status, "Nguyên lý All-or-Nothing: Ghế phụ không được bị khóa khi đơn hàng thất bại!");
    }
}

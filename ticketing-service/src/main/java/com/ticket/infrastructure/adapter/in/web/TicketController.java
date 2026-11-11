package com.ticket.infrastructure.adapter.in.web;

import com.ticket.core.service.TicketReservationService;
import com.ticket.infrastructure.adapter.in.web.dto.ReserveTicketRequest;
import com.ticket.infrastructure.adapter.in.web.dto.ReserveTicketResponse;
import com.ticket.infrastructure.adapter.out.redis.RedisSeatCacheService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class TicketController {

    private final TicketReservationService ticketReservationService;
    private final RedisSeatCacheService redisSeatCacheService;

    /**
     * Endpoint cốt lõi chịu tải cao: Giữ chỗ vé sự kiện
     */
    @PostMapping("/tickets/reserve")
    public ResponseEntity<ReserveTicketResponse> reserveTickets(@Valid @RequestBody ReserveTicketRequest request) {
        ReserveTicketResponse response = ticketReservationService.reserveSeats(request);
        return ResponseEntity.ok(response);
    }

    /**
     * Endpoint Pre-warm: Nạp danh sách ghế khả dụng vào Redis trước giờ mở bán
     */
    @PostMapping("/events/{eventId}/prewarm")
    public ResponseEntity<Map<String, Object>> preWarmSeats(
            @PathVariable String eventId,
            @RequestBody List<String> seatIds
    ) {
        Map<String, String> seatMap = new HashMap<>();
        for (String seatId : seatIds) {
            seatMap.put(seatId, "AVAILABLE");
        }
        redisSeatCacheService.preWarmSeats(eventId, seatMap);

        Map<String, Object> result = new HashMap<>();
        result.put("eventId", eventId);
        result.put("prewarmedCount", seatIds.size());
        result.put("status", "SUCCESS");
        return ResponseEntity.ok(result);
    }

    /**
     * Lấy toàn bộ danh sách và trạng thái ghế của một sự kiện (nếu chưa có thì tự động khởi tạo sơ đồ mẫu 100 ghế)
     */
    @GetMapping("/events/{eventId}/seats")
    public ResponseEntity<Map<Object, Object>> getAllSeats(@PathVariable String eventId) {
        Map<Object, Object> seats = redisSeatCacheService.getAllSeatStatuses(eventId);
        if (seats.isEmpty()) {
            Map<String, String> defaultMap = new HashMap<>();
            String[] rows = {"A", "B", "C", "D", "E", "F", "G", "H"};
            for (String row : rows) {
                for (int i = 1; i <= 10; i++) {
                    String seatId = row + i;
                    defaultMap.put(seatId, "AVAILABLE");
                }
            }
            redisSeatCacheService.preWarmSeats(eventId, defaultMap);
            seats = redisSeatCacheService.getAllSeatStatuses(eventId);
        }
        return ResponseEntity.ok(seats);
    }

    /**
     * Kiểm tra trạng thái hiện thời của một ghế
     */
    @GetMapping("/events/{eventId}/seats/{seatId}/status")
    public ResponseEntity<Map<String, String>> getSeatStatus(
            @PathVariable String eventId,
            @PathVariable String seatId
    ) {
        String status = redisSeatCacheService.getSeatStatus(eventId, seatId);
        Map<String, String> response = new HashMap<>();
        response.put("eventId", eventId);
        response.put("seatId", seatId);
        response.put("status", status != null ? status : "NOT_FOUND");
        return ResponseEntity.ok(response);
    }
}

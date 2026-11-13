package com.ticket.infrastructure.adapter.in.web;

import com.ticket.core.domain.model.enums.TicketStatus;
import com.ticket.infrastructure.adapter.in.web.dto.CheckInRequest;
import com.ticket.infrastructure.adapter.in.web.dto.CheckInResponse;
import com.ticket.infrastructure.adapter.out.persistence.entity.*;
import com.ticket.infrastructure.adapter.out.persistence.repository.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;

/**
 * Check-in Controller — Soát vé tại cửa rạp.
 *
 * Endpoint này mô phỏng máy quét QR tại cổng vào:
 * 1. Nhận mã vé (ticketCode) + QR hash từ scanner
 * 2. Verify QR hash khớp với DB (chống vé giả)
 * 3. Chuyển trạng thái vé sang CHECKED_IN
 * 4. Trả về thông tin vé để nhân viên rạp đối chiếu
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/tickets")
@RequiredArgsConstructor
public class CheckInController {

    private final TicketRepository ticketRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final SeatRepository seatRepository;
    private final ShowtimeRepository showtimeRepository;
    private final AuditoriumRepository auditoriumRepository;
    private final MovieRepository movieRepository;

    @PostMapping("/checkin")
    public ResponseEntity<CheckInResponse> checkIn(@Valid @RequestBody CheckInRequest request) {
        log.info("Check-in request: ticketCode={}", request.getTicketCode());

        // 1. Tìm vé theo ticketCode
        TicketEntity ticket = ticketRepository.findByTicketCode(request.getTicketCode())
                .orElse(null);

        if (ticket == null) {
            return ResponseEntity.ok(CheckInResponse.builder()
                    .success(false)
                    .ticketCode(request.getTicketCode())
                    .message("Vé không tồn tại trong hệ thống")
                    .build());
        }

        // 2. Xác thực QR hash (chống vé giả/mạo danh)
        if (!ticket.getQrHash().equals(request.getQrHash())) {
            log.warn("Check-in FAILED: QR hash mismatch for ticket {}", request.getTicketCode());
            return ResponseEntity.ok(CheckInResponse.builder()
                    .success(false)
                    .ticketCode(request.getTicketCode())
                    .message("Mã QR không hợp lệ. Vé có thể bị làm giả.")
                    .build());
        }

        // 3. Kiểm tra vé đã check-in chưa
        if (ticket.getStatus() == TicketStatus.CHECKED_IN) {
            return ResponseEntity.ok(CheckInResponse.builder()
                    .success(false)
                    .ticketCode(request.getTicketCode())
                    .status(ticket.getStatus().name())
                    .checkinTime(ticket.getCheckinTime())
                    .message("Vé đã được sử dụng lúc " +
                            (ticket.getCheckinTime() != null
                                    ? ticket.getCheckinTime().toString()
                                    : "không xác định"))
                    .build());
        }

        // 4. Kiểm tra vé có bị thu hồi không
        if (ticket.getStatus() == TicketStatus.REVOKED) {
            return ResponseEntity.ok(CheckInResponse.builder()
                    .success(false)
                    .ticketCode(request.getTicketCode())
                    .status(ticket.getStatus().name())
                    .message("Vé đã bị thu hồi. Vui lòng liên hệ quầy hỗ trợ.")
                    .build());
        }

        // 5. Đổi trạng thái sang CHECKED_IN
        ticket.setStatus(TicketStatus.CHECKED_IN);
        ticket.setCheckinTime(Instant.now());
        ticketRepository.save(ticket);

        // 6. Lấy thông tin ghế để trả về
        SeatEntity seat = seatRepository.findById(ticket.getSeatId()).orElse(null);
        String seatNum = seat != null ? seat.getSeatNum() : "N/A";

        // 7. Lấy thông tin suất chiếu và phim (qua order → event → showtime)
        OrderEntity order = orderRepository.findById(ticket.getOrderId()).orElse(null);
        String movieTitle = "N/A";
        String showtimeStart = "N/A";
        String auditoriumName = "N/A";

        if (order != null && order.getShowtimeId() != null) {
            ShowtimeEntity showtime = showtimeRepository.findById(order.getShowtimeId()).orElse(null);
            if (showtime != null) {
                showtimeStart = showtime.getStartTime() != null ? showtime.getStartTime().toString() : "N/A";
                MovieEntity movie = movieRepository.findById(showtime.getMovieId()).orElse(null);
                if (movie != null) movieTitle = movie.getTitle();
                AuditoriumEntity aud = auditoriumRepository.findById(showtime.getAuditoriumId()).orElse(null);
                if (aud != null) auditoriumName = aud.getName();
            }
        } else if (order != null) {
            // Fallback: dùng eventId nếu showtimeId null
            showtimeStart = order.getExpiresAt() != null ? order.getExpiresAt().toString() : "N/A";
        }

        log.info("Check-in SUCCESS: ticket={}, seat={}, movie={}", request.getTicketCode(), seatNum, movieTitle);

        return ResponseEntity.ok(CheckInResponse.builder()
                .success(true)
                .ticketCode(request.getTicketCode())
                .seatNum(seatNum)
                .movieTitle(movieTitle)
                .showtimeStart(showtimeStart)
                .auditoriumName(auditoriumName)
                .status(TicketStatus.CHECKED_IN.name())
                .checkinTime(ticket.getCheckinTime())
                .message("Check-in thành công! Chúc bạn xem phim vui vẻ.")
                .build());
    }
}

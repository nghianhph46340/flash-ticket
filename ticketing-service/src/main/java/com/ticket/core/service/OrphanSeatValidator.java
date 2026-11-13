package com.ticket.core.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Thuật toán kiểm tra Quy tắc Chống Ghế Lẻ (Orphan Seat Prevention)
 *
 * Mục đích: Ngăn khách hàng chọn ghế tạo ra đúng 1 ghế trống đơn độc
 * không thể bán được cho người khác (gây lãng phí doanh thu rạp).
 *
 * Hai trường hợp bị chặn:
 * 1. Ghế lẻ sát vách/lối đi: [_][X][HELD]... để lại [_] đơn độc
 * 2. Ghế lẻ ở giữa: [SOLD][_][X][SOLD] để lại [_] đơn độc
 *
 * Thuật toán chạy tại tầng Backend (Java) TRƯỚC khi gọi Redis Lua Script,
 * tuyệt đối KHÔNG nhồi vào Lua để tránh block Redis single-threaded.
 *
 * Ngoại lệ hợp lệ: Nếu hàng ghế chỉ còn đúng 1 ghế trống từ trước,
 * khách hàng đơn lẻ được phép mua.
 */
@Slf4j
@Component
public class OrphanSeatValidator {

    /**
     * Kiểm tra xem việc chọn các ghế trong request có tạo ra ghế lẻ không.
     *
     * @param rowSeatStatuses Map<seatNumber, status> của toàn bộ ghế trong 1 hàng.
     *                        Status: "AVAILABLE", "HELD:userId", "SOLD", "BLOCKED"
     * @param requestedSeatNumbers Danh sách số ghế mà khách đang muốn chọn (cùng hàng)
     * @return Số ghế vi phạm nếu có, hoặc empty nếu hợp lệ
     */
    public Optional<Integer> findOrphanViolation(
            Map<Integer, String> rowSeatStatuses,
            Set<Integer> requestedSeatNumbers) {

        if (rowSeatStatuses == null || rowSeatStatuses.isEmpty()) {
            return Optional.empty();
        }

        // Sắp xếp số ghế theo thứ tự vật lý
        List<Integer> allSeats = new ArrayList<>(rowSeatStatuses.keySet());
        Collections.sort(allSeats);

        // Tạo "bản đồ chiếm chỗ" sau khi apply request:
        // true = ghế bị chiếm (SOLD, HELD, BLOCKED, hoặc người dùng đang chọn)
        // false = ghế còn trống
        Map<Integer, Boolean> occupiedAfterRequest = new HashMap<>();
        for (Map.Entry<Integer, String> entry : rowSeatStatuses.entrySet()) {
            String status = entry.getValue();
            boolean isOccupied = !status.equals("AVAILABLE");
            occupiedAfterRequest.put(entry.getKey(), isOccupied);
        }
        // Apply request: đánh dấu các ghế được chọn là chiếm chỗ
        for (Integer seatNum : requestedSeatNumbers) {
            occupiedAfterRequest.put(seatNum, true);
        }

        // Đếm số ghế trống còn lại trong hàng (trước khi apply request)
        long totalAvailableBefore = rowSeatStatuses.values().stream()
                .filter(s -> s.equals("AVAILABLE"))
                .count();

        // Ngoại lệ: Nếu hàng chỉ còn đúng 1 ghế trống thì cho phép chọn
        if (totalAvailableBefore == 1) {
            return Optional.empty();
        }

        // Kiểm tra từng ghế được chọn có tạo ra ghế lẻ không
        for (Integer seatNum : requestedSeatNumbers) {
            Optional<Integer> orphan = checkCreatesOrphan(allSeats, occupiedAfterRequest, seatNum);
            if (orphan.isPresent()) {
                log.debug("Ghế lẻ bị phát hiện: Chọn ghế {} tạo ra ghế {} đơn độc",
                        seatNum, orphan.get());
                return orphan;
            }
        }

        return Optional.empty();
    }

    /**
     * Kiểm tra xem sau khi chiếm ghế `targetSeat`, có ghế nào bị cô lập (đúng 1 ghế) không.
     *
     * Logic: Duyệt toàn bộ hàng và tìm các "chuỗi ghế trống liên tiếp" (gap).
     * Nếu có gap nào có độ dài = 1, đó là ghế lẻ → vi phạm.
     */
    private Optional<Integer> checkCreatesOrphan(
            List<Integer> allSeats,
            Map<Integer, Boolean> occupiedMap,
            Integer targetSeat) {

        // Tìm các khoảng ghế trống liên tiếp sau khi chiếm targetSeat
        List<List<Integer>> gaps = findAvailableGaps(allSeats, occupiedMap);

        for (List<Integer> gap : gaps) {
            if (gap.size() == 1) {
                // Đây là ghế lẻ đơn độc — vi phạm
                return Optional.of(gap.get(0));
            }
        }
        return Optional.empty();
    }

    /**
     * Tìm tất cả các chuỗi ghế trống liên tiếp trong hàng.
     * Ví dụ: [O O _ _ O _ O] → [[3,4], [6]]  (O=occupied, _=available)
     */
    private List<List<Integer>> findAvailableGaps(
            List<Integer> allSeats,
            Map<Integer, Boolean> occupiedMap) {

        List<List<Integer>> gaps = new ArrayList<>();
        List<Integer> currentGap = new ArrayList<>();

        for (Integer seat : allSeats) {
            Boolean occupied = occupiedMap.getOrDefault(seat, false);
            if (!occupied) {
                currentGap.add(seat);
            } else {
                if (!currentGap.isEmpty()) {
                    gaps.add(new ArrayList<>(currentGap));
                    currentGap.clear();
                }
            }
        }
        if (!currentGap.isEmpty()) {
            gaps.add(currentGap);
        }

        return gaps;
    }

    /**
     * Kiểm tra toàn bộ request (có thể chọn nhiều ghế ở nhiều hàng khác nhau).
     *
     * @param showtimeSeatMap Map<"seatId", status> của toàn bộ showtime.
     *                        seatId format: "A1", "B12", "G5" (row + seatNum liền nhau)
     * @param requestedSeatKeys Danh sách seatId được chọn (format: "A1", "B2"...)
     * @return Thông tin vi phạm nếu có
     */
    public Optional<OrphanViolationResult> validateRequest(
            Map<String, String> showtimeSeatMap,
            List<String> requestedSeatKeys) {

        // Group ghế theo hàng — seatId format: "A1", "B12", "G5"
        Map<String, Map<Integer, String>> seatsByRow = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : showtimeSeatMap.entrySet()) {
            String key = entry.getKey();
            // Tách row (chữ cái đầu) và seatNum (phần số) từ "A1" → row="A", seatNum=1
            if (key.length() >= 2) {
                String row = key.substring(0, 1);
                try {
                    int seatNum = Integer.parseInt(key.substring(1));
                    seatsByRow.computeIfAbsent(row, k -> new TreeMap<>())
                            .put(seatNum, entry.getValue());
                } catch (NumberFormatException ignored) {}
            }
        }

        // Group ghế được chọn theo hàng
        Map<String, Set<Integer>> requestedByRow = new LinkedHashMap<>();
        for (String key : requestedSeatKeys) {
            if (key.length() >= 2) {
                String row = key.substring(0, 1);
                try {
                    int seatNum = Integer.parseInt(key.substring(1));
                    requestedByRow.computeIfAbsent(row, k -> new HashSet<>()).add(seatNum);
                } catch (NumberFormatException ignored) {}
            }
        }

        // Kiểm tra từng hàng có ghế được chọn
        for (Map.Entry<String, Set<Integer>> entry : requestedByRow.entrySet()) {
            String row = entry.getKey();
            Set<Integer> requested = entry.getValue();
            Map<Integer, String> rowStatuses = seatsByRow.getOrDefault(row, Collections.emptyMap());

            Optional<Integer> violation = findOrphanViolation(rowStatuses, requested);
            if (violation.isPresent()) {
                return Optional.of(new OrphanViolationResult(
                        row,
                        violation.get(),
                        String.format("Chọn ghế ở hàng %s sẽ để lại ghế %s%d đơn độc không thể bán cho người khác",
                                row, row, violation.get())
                ));
            }
        }

        return Optional.empty();
    }

    /**
     * Kết quả vi phạm quy tắc ghế lẻ
     */
    public record OrphanViolationResult(
            String rowLabel,
            int orphanSeatNumber,
            String message
    ) {}
}

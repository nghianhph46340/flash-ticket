-- ==============================================================================
-- FlashTicket - KỊCH BẢN XÁC THỰC TÍNH TOÀN VẸN DỮ LIỆU (ZERO OVERSELLING)
-- ==============================================================================

-- 1. Kiểm tra phát hiện trùng lặp ghế trong các đơn hàng hợp lệ theo từng sự kiện
-- TIÊU CHUẨN ĐẠT: 0 bản ghi (Duplicate count = 0)
SELECT 
    o.event_id,
    oi.seat_id, 
    COUNT(*) AS duplicate_order_count,
    array_agg(o.order_code) AS related_orders
FROM order_items oi
JOIN orders o ON oi.order_id = o.id
WHERE o.status IN ('PENDING', 'PAID')
GROUP BY o.event_id, oi.seat_id
HAVING COUNT(*) > 1;

-- 2. Thống kê tổng số đơn hàng theo trạng thái của sự kiện test k6
SELECT 
    event_id,
    status, 
    COUNT(*) AS total_orders, 
    SUM(total_amount) AS total_revenue
FROM orders
WHERE event_id = 'e1010000-0000-0000-0000-000000000002'
GROUP BY event_id, status;

-- 3. Kiểm tra số lượng order_items đã được ghi bất đồng bộ (Write-Behind) vào PostgreSQL
SELECT 
    o.event_id,
    COUNT(DISTINCT oi.seat_id) AS total_unique_seats_booked,
    COUNT(oi.id) AS total_order_items_persisted
FROM order_items oi
JOIN orders o ON oi.order_id = o.id
WHERE o.event_id = 'e1010000-0000-0000-0000-000000000002'
  AND o.status IN ('PENDING', 'PAID')
GROUP BY o.event_id;

-- ==============================================================================
-- FlashTicket - Tối ưu hóa chỉ mục hiệu năng cao (Database Performance Optimization)
-- ==============================================================================

-- 1. Index cho bảng orders theo sự kiện (Hỗ trợ báo cáo doanh thu & dashboard BTC)
CREATE INDEX IF NOT EXISTS idx_orders_event_id ON orders(event_id);

-- 2. Index cho bảng order_items theo seat_id (Hỗ trợ tra cứu ghế và kiểm tra toàn vẹn)
CREATE INDEX IF NOT EXISTS idx_order_items_seat_id ON order_items(seat_id);

-- 3. Index cho bảng tickets (Hỗ trợ tra cứu vé theo đơn hàng, người dùng và sự kiện check-in)
CREATE INDEX IF NOT EXISTS idx_tickets_order_id ON tickets(order_id);
CREATE INDEX IF NOT EXISTS idx_tickets_user_id ON tickets(user_id);
CREATE INDEX IF NOT EXISTS idx_tickets_event_id ON tickets(event_id);

-- 4. Index cho bảng payments theo order_id (Hỗ trợ tra cứu trạng thái thanh toán theo đơn)
CREATE INDEX IF NOT EXISTS idx_payments_order_id ON payments(order_id);

-- 5. Index cho bảng ticket_tiers theo event_id (Hỗ trợ load cấu hình phân hạng vé theo sự kiện)
CREATE INDEX IF NOT EXISTS idx_ticket_tiers_event_id ON ticket_tiers(event_id);

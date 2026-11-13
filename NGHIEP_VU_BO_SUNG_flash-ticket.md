# TÀI LIỆU ĐẶC TẢ NGHIỆP VỤ TOÀN DIỆN HỆ THỐNG ĐẶT VÉ XEM PHIM
**Dự án:** FlashTicket - Cinema High-Concurrency Ticketing Platform  
**Định dạng kiến trúc:** Modular Monolith / Event-Driven (Spring Boot + NATS JetStream + Redis + PostgreSQL + Vue 3)  
**Trạng thái hạ tầng:** Đã triển khai hoàn tất môi trường Podman VPS & Core Concurrency Reservation  

---

## MỤC LỤC
1. [Sơ Đồ Tổng Thể Toàn Bộ Vòng Đời Nghiệp Vụ](#1-sơ-đồ-tổng-thể-toàn-bộ-vòng-đời-nghiệp-vụ)
2. [Nhóm 1: Quản Trị Danh Mục & Lịch Chiếu (Catalog & Master Data)](#2-nhóm-1-quản-trị-danh-mục--lịch-chiếu-catalog--master-data)
3. [Nhóm 2: Nghiệp Vụ Sơ Đồ Ghế & Quy Tắc Rạp Phim (Cinema Seat Rules)](#3-nhóm-2-nghiệp-vụ-sơ-đồ-ghế--quy-tắc-rạp-phim-cinema-seat-rules)
4. [Nhóm 3: Dịch Vụ Bán Kèm (F&B Concessions)](#4-nhóm-3-dịch-vụ-bán-kèm-fb-concessions)
5. [Nhóm 4: Tầng Lõi Giữ Chỗ & Xung Đột Concurrency (Reservation Engine)](#5-nhóm-4-tầng-lõi-giữ-chỗ--xung-đột-concurrency-reservation-engine)
6. [Nhóm 5: Khuyến Mãi & Voucher (Promotions & Coupons)](#6-nhóm-5-khuyến-mãi--voucher-promotions--coupons)
7. [Nhóm 6: Thanh Toán, Idempotency & Xuất Vé (Payment & Ticketing)](#7-nhóm-6-thanh-toán-idempotency--xuất-vé-payment--ticketing)
8. [Nhóm 7: Xử Lý Ngoại Lệ & Thu Hồi Tự Động (Saga Rollback Engine)](#8-nhóm-7-xử-lý-ngoại-lệ--thu-hồi-tự-động-saga-rollback-engine)
9. [Nhóm 8: Nghiệp Vụ Tại Rạp (Check-in Scanner & On-site POS)](#9-nhóm-8-nghiệp-vụ-tại-rạp-check-in-scanner--on-site-pos)
10. [Nhóm 9: Chính Sách Hủy & Đổi Vé (Refund & Exchange)](#10-nhóm-9-chính-sách-hủy--đổi-vé-refund--exchange)
11. [Nhóm 10: Báo Cáo Doanh Thu & Vận Hành (Backoffice Analytics)](#11-nhóm-10-báo-cáo-doanh-thu--vận-hành-backoffice-analytics)
12. [Bảng Ánh Xạ Kỹ Thuật (Data Structure & Event Mapping)](#12-bảng-ánh-xạ-kỹ-thuật-data-structure--event-mapping)

---

## 1. SƠ ĐỒ TỔNG THỂ TOÀN BỘ VÒNG ĐỜI NGHIỆP VỤ

```text
[Khách hàng]
     │
     ├──► 1. Xem Danh mục Phim & Chọn Cụm Rạp / Ngày / Suất Chiếu
     │
     ├──► 2. Tải Sơ Đồ Ghế Thời Gian Thực (WebSocket STOMP Active)
     │
     ├──► 3. Chọn Ghế (Kiểm tra Quy tắc Chống Ghế Lẻ + Ghế Đôi Sweetbox)
     │
     ├──► 4. Bấm "Giữ Chỗ" ──► [Redis Lua Atomic Check & Lock] ──► Giữ trong 10 phút
     │                               │
     │                               ├──► NATS Publish ("tickets.reserved")
     │                               └──► WebSocket Broadcast đổi màu ghế sang VÀNG
     │
     ├──► 5. Chọn Bắp Nước (F&B) & Nhập Mã Giảm Giá (Voucher)
     │
     ├──► 6. Xác Nhận & Thanh Toán (VietQR / PayOS)
     │         │
     │         ├──► [Thành công] ──► Webhook Verify (Idempotency) ──► Xuất vé điện tử QR Code
     │         │                                                        └──► Broadcast ghế sang XÁM (SOLD)
     │         │
     │         └──► [Hết hạn 10p / Hủy] ──► Expiration Worker (Redis ZSET) ──► Nhả ghế về AVAILABLE
     │                                                                           └──► Broadcast ghế về TRẮNG
     ▼
[Check-in tại Cửa Rạp]
     │
     └──► Nhân viên quét mã QR ──► Backend Verify HMAC & Chuyển trạng thái CHECKED_IN
```

---

## 2. NHÓM 1: QUẢN TRỊ DANH MỤC & LỊCH CHIẾU (CATALOG & MASTER DATA)

### 2.1. Quản lý Phim (Movies)
* **Nghiệp vụ con:**
  * **Thông tin cơ bản:** Tiêu đề phim, thời lượng (phút), đạo diễn, diễn viên, ngày khởi chiếu, ngày kết thúc dự kiến, tóm tắt nội dung (synopsis), trailer URL.
  * **Phân loại độ tuổi (Age Rating):**
    * `P` (Phổ biến mọi lứa tuổi).
    * `K` (Khán giả dưới 13 tuổi có người giám hộ đi cùng).
    * `T13` (Khán giả từ 13 tuổi trở lên).
    * `T16` (Khán giả từ 16 tuổi trở lên).
    * `T18` (Khán giả từ 18 tuổi trở lên).
    * *Ràng buộc:* Khi đặt vé phim `T16`/`T18`, client phải hiển thị popup xác nhận độ tuổi và lưu cờ đồng thuận của người mua.
  * **Định dạng & Ngôn ngữ:** Bản gốc phụ đề (`Original + Sub`), Thuyết minh (`Dubbed`), định dạng 2D, 3D, IMAX, 4DX.

### 2.2. Quản lý Cụm Rạp & Phòng Chiếu (Cinemas & Auditoriums)
* **Nghiệp vụ con:**
  * **Cụm rạp (Cinema Branch):** Tên chi nhánh, tỉnh/thành phố, địa chỉ, số hotline, tọa độ GPS.
  * **Phòng chiếu (Auditorium):** Mã phòng (`Phòng 01`, `IMAX Hall`), loại phòng (2D Standard, Dolby Atmos, IMAX), tổng sức chứa.
  * **Sơ đồ bố trí vật lý (Physical Seat Map):** Ma trận tọa độ $(X, Y)$ hàng ghế, lối đi (`Walkway`), màn chiếu (`Screen`).

### 2.3. Lập Lịch Suất Chiếu (Showtimes Management)
* **Nghiệp vụ con:**
  * **Điều kiện lập suất:** Gắn `movie_id` vào `auditorium_id` tại thời điểm `start_time`.
  * **Quy tắc Chống Trùng Lịch Phòng Chiếu (Overlap Prevention):**
    $$\text{end\_time} = \text{start\_time} + \text{movie.duration} + \text{cleaning\_buffer\_time}$$
    *Thời gian đệm dọn rạp (`cleaning_buffer_time`)* thường từ 15 đến 20 phút. Hệ thống phải từ chối mọi suất chiếu mới có khoảng thời gian $[\text{start\_time}, \text{end\_time}]$ giao thoa với bất kỳ suất chiếu nào đã tồn tại trong cùng phòng chiếu.
  * **Trạng thái suất chiếu:** `DRAFT` (Bản nháp), `PUBLISHED` (Đang mở bán), `CANCELLED` (Hủy suất), `ARCHIVED` (Đã chiếu xong).

### 2.4. Ma Trận Giá Vé Động (Dynamic Pricing Matrix)
* **Nghiệp vụ con:** Giá vé được tính toán theo công thức đa biến:
  $$\text{FinalPrice} = \text{BasePrice} + \Delta_{\text{SeatType}} + \Delta_{\text{DayOfWeek}} + \Delta_{\text{TimeSlot}} + \Delta_{\text{Format}}$$
  * **Loại ghế:** Ghế thường (chuẩn), Ghế VIP (+$15.000\text{ đ}$ - $25.000\text{ đ}$), Ghế đôi Sweetbox (x2 giá thường hoặc tính vé combo đôi).
  * **Ngày chiếu:** Ngày thường (Thứ 2 - Thứ 5) vs Cuối tuần (Thứ 6 - Chủ Nhật) vs Ngày lễ/Tết.
  * **Khung giờ chiếu:** Suất sáng sớm (trước 10h), Suất tiêu chuẩn (10h - 17h), Suất giờ vàng (17h - 22h), Suất đêm muộn (sau 22h).
  * **Định dạng phòng chiếu:** 2D (mặc định), 3D (+20%), IMAX (+50% đến 80%).

---

## 3. NHÓM 2: NGHIỆP VỤ SƠ ĐỒ GHẾ & QUY TẮC RẠP PHIM (CINEMA SEAT RULES)

Khác với vé hòa nhạc, đặt vé xem phim có các quy tắc nghiệp vụ rạp chiếu vô cùng đặc thù:

### 3.1. Phân loại Ghế (Seat Types)
* **Ghế Thường (Standard):** Các hàng ghế phía trước và sát rìa.
* **Ghế VIP:** Khu vực trung tâm, góc nhìn tối ưu từ màn chiếu.
* **Ghế Đôi (Couple / Sweetbox):** Luôn bố trí ở hàng cuối cùng, thiết kế thành từng cặp 2 ghế liền kề không có tay vịn ở giữa.
* **Ghế Bảo Trì / Khóa Cố Định (Blocked/Maintenance):** Ghế bị hỏng hoặc dành riêng cho kỹ thuật viên/khách mời đặc biệt, luôn hiển thị trạng thái `BLOCKED` và cấm chọn.

### 3.2. Quy Tắc Chống Ghế Lẻ (Orphan Seat / Gap Rule)
* **Mục đích:** Ngăn chặn việc khách hàng bỏ trống đúng **1 chiếc ghế đơn độc** giữa các ghế đã đặt hoặc sát vách tường/lối đi, gây lãng phí không thể bán được cho người sau.
* **Thuật toán kiểm tra (Validating Logic):**
  Trên cùng một hàng ghế vật lý:
  1. **Lẻ sát vách:** Không cho phép chọn ghế để lại đúng 1 ghế trống ngoài cùng sát lối đi hoặc vách tường. (Ví dụ: Dãy có ghế 1, 2, 3 trống; khách chọn ghế 2 $\rightarrow$ Bị chặn vì để lại ghế 1 đơn độc).
  2. **Lẻ ở giữa:** Không cho phép chọn ghế tạo ra khoảng trống đúng 1 ghế giữa ghế vừa chọn và một ghế đã bị đặt/giữ. (Ví dụ: Ghế 5 đã bán, ghế 6, 7, 8 trống; khách chọn ghế 7 $\rightarrow$ Bị chặn vì để lại ghế 6 đơn độc).
* **Ngoại lệ:** Nếu toàn bộ hàng ghế chỉ còn duy nhất 1 ghế trống đó từ trước, khách hàng đơn lẻ được phép mua.

### 3.3. Quy Tắc Ghế Đôi (Sweetbox Binding Rule)
* Ghế đôi luôn có cặp mã định danh liên kết (ví dụ: `SW-01` và `SW-02`).
* **Ràng buộc:** Khách hàng không thể chọn riêng lẻ 1 ghế trong cặp Sweetbox. Nếu click chọn 1 ghế, hệ thống tự động khóa và tính tiền cả 2 ghế.

### 3.4. Giới Hạn Đặt Chỗ (Seat Quota per Transaction)
* Giới hạn tối đa **6 đến 8 ghế** trong một phiên giao dịch của một tài khoản để hạn chế tối đa tình trạng "ôm vé", đầu cơ vé trong các dịp bom tấn.

---

## 4. NHÓM 3: DỊCH VỤ BÁN KÈM (F&B CONCESSIONS)

### 4.1. Danh Mục Combo Bắp Nước
* **Nghiệp vụ con:**
  * Khởi tạo các gói sản phẩm: `Combo 1 Bắp 1 Nước (Single)`, `Combo 1 Bắp 2 Nước (Couple)`, `Combo Gia Đình`, `Vị Bắp Upgrade` (Caramel, Phô mai, Socola).
  * Đơn vị tính, hình ảnh minh họa, giá niêm yết.

### 4.2. Ràng Buộc Nghiệp Vụ Bắp Nước
* Bắp nước **chỉ được phép bán kèm khi đã có ít nhất 1 vé xem phim** trong giỏ hàng (không hỗ trợ đặt bắp nước riêng lẻ trực tuyến).
* Bắp nước không cần cơ chế atomic lock phức tạp như ghế (vì tồn kho nguyên vật liệu rạp thường lớn), chỉ cần kiểm tra cờ `is_active` và ghi trực tiếp vào `order_items` khi tạo đơn hàng.

---

## 5. TẦNG LÕI GIỮ CHỖ & XUNG ĐỘT CONCURRENCY (RESERVATION ENGINE)

Đây là nghiệp vụ cốt lõi bạn đã triển khai thành công, áp dụng vào rạp phim như sau:

### 5.1. Khóa Ghế Nguyên Tử (Redis Atomic Reservation)
* **Phạm vi phân định (Key Scope):** Khóa theo từng suất chiếu:
  * Seat Map Hash: `showtime:{showtimeId}:seats`
  * Expiration Sorted Set: `showtime:{showtimeId}:expirations`
* **Quy trình Lua Script:**
  1. Kiểm tra toàn bộ danh sách ghế trong request.
  2. Nếu có bất kỳ ghế nào khác trạng thái `AVAILABLE` $\rightarrow$ Trả về mã lỗi `409 Conflict` kèm ID ghế bị tranh chấp.
  3. Nếu tất cả hợp lệ $\rightarrow$ Chuyển trạng thái sang `HELD:{userId}`, nạp `timestamp_expire = now + 600` (10 phút) vào ZSET.
* **Thời gian phản hồi:** Dưới $15\text{ ms}$.

### 5.2. Phát Tán Sự Kiện Thời Gian Thực (Realtime Pipeline)
* Ngay sau khi Lua script trả về `SUCCESS`:
  1. Backend phát event sang **NATS JetStream** topic `tickets.reserved` kèm header `Nats-Msg-Id: RES-{orderCode}`.
  2. Consumer nhận event, đẩy bản tin STOMP qua WebSocket tới `/topic/showtime/{showtimeId}/seats`.
  3. Màn hình các người dùng khác ngay lập tức đổi màu các ghế này sang **Màu Vàng (Đang giữ)**.

---

## 6. NHÓM 5: KHUYẾN MÃI & VOUCHER (PROMOTIONS & COUPONS)

### 6.1. Quản lý Mã Giảm Giá
* **Thuộc tính Voucher:**
  * Mã code (ví dụ: `CHIEUCONG50K`, `CHAOBANMOI`).
  * Loại giảm trừ: Giảm theo số tiền cố định (`FIXED_AMOUNT`) hoặc theo phần trăm (`PERCENTAGE`, có mức trần giảm tối đa).
  * Điều kiện áp dụng: Tổng giá trị đơn tối thiểu, áp dụng cho rạp cụ thể, hoặc theo khung giờ.
  * Giới hạn sử dụng: Tổng lượt sử dụng toàn hệ thống (`global_usage_limit`), giới hạn lượt/người dùng (`per_user_limit`).

### 6.2. Kiểm Tra & Trừ Lượt Voucher Đồng Thời (Concurrency Coupon Check)
* Để tránh việc 1 voucher có giới hạn 100 lượt bị 1.000 người dùng cùng lúc:
  * Lưu trữ tồn kho voucher trên Redis: `DECR voucher:{code}:remaining`.
  * Nếu kết quả $< 0$, hoàn lại bằng `INCR` và báo lỗi "Mã giảm giá đã hết lượt sử dụng".

---

## 7. NHÓM 6: THANH TOÁN, IDEMPOTENCY & XUẤT VÉ (PAYMENT & TICKETING)

### 7.1. Khởi Tạo Giao Dịch Thanh Toán
* Màn hình thanh toán hiển thị:
  * Chi tiết đơn: Suất chiếu, danh sách ghế, combo bắp nước, giảm trừ voucher, tổng tiền thanh toán.
  * Đồng hồ đếm ngược: 10 phút (tính từ thời điểm giữ ghế thành công).
  * Mã VietQR động tích hợp cổng thanh toán (PayOS): Chứa nội dung chuyển khoản duy nhất `TICKET {orderCode}`.

### 7.2. Xử Lý Webhook Cổng Thanh Toán & Nguyên Lý Bất Biến (Idempotency)
* Khi ngân hàng/cổng thanh toán gọi callback về:
  1. **Xác thực chữ ký số (HMAC Signature):** Đảm bảo request thực sự đến từ nhà cung cấp dịch vụ PayOS.
  2. **Idempotency Guard:** Kiểm tra `idempotency_key` (hoặc `transaction_code`) trong bảng `payments`.
     * Nếu đã tồn tại bản ghi thành công $\rightarrow$ Bỏ qua, trả ngay HTTP `200 OK` (chống xử lý lặp do mạng chập chờn gửi lại webhook).
     * Nếu chưa $\rightarrow$ Bắt đầu xử lý nghiệp vụ xuất vé trong Transaction.

### 7.3. Quy Trình Xuất Vé Điện Tử (Ticket Issuance)
* Cập nhật `orders.status = 'PAID'`.
* Cập nhật Redis Hash: Đổi trạng thái ghế từ `HELD:{userId}` sang `SOLD`.
* Xóa ghế khỏi Redis ZSET `showtime:{showtimeId}:expirations`.
* Sinh các bản ghi trong bảng `tickets`:
  * `ticket_code`: Mã vé ngẫu nhiên 8 ký tự dễ đọc (dành cho in vé tại quầy).
  * `qr_hash`: Chuỗi ký số bảo mật HMAC SHA-256 chứa `ticket_id`, `showtime_id`, và secret key.
* Gửi email biên nhận đơn hàng kèm link xem vé có mã QR.
* Bắn WebSocket cập nhật ghế sang **Màu Xám (Đã bán)** trên tất cả màn hình client.

---

## 8. NHÓM 7: XỬ LÝ NGOẠI LỆ & THU HỒI TỰ ĐỘNG (SAGA ROLLBACK ENGINE)

Một hệ thống phân tán chịu tải cao phải có cơ chế bù trừ (Compensating Transaction) tự phục hồi dữ liệu khi có lỗi xảy ra.

### 8.1. Thu Hồi Ghế Quá Hạn (Timeout Expiration Worker)
* **Trigger:** Khách hàng giữ vé nhưng không thanh toán trong vòng 10 phút.
* **Cơ chế:**
  * Worker chạy nền mỗi 5–10 giây, thực thi câu lệnh Redis:
    ```text
    ZRANGEBYSCORE showtime:{showtimeId}:expirations 0 {currentTimeInSeconds}
    ```
  * Lấy danh sách toàn bộ các ghế đã hết hạn.
  * **Hành động bù trừ (Compensating Actions):**
    1. Chuyển trạng thái các ghế đó trên Redis Hash trở về `AVAILABLE`.
    2. Xóa các phần tử đó khỏi Redis ZSET.
    3. Cập nhật trạng thái đơn hàng trong PostgreSQL: `orders.status = 'EXPIRED'`.
    4. Hoàn trả lượt dùng mã voucher (nếu đơn hàng có áp mã).
    5. Bắn thông điệp qua WebSocket để ghế tự động chuyển lại thành **Màu Trắng (Ghế trống)** cho người khác đặt.

### 8.2. Khách Hàng Chủ Động Hủy Phiên Đặt Vé
* Nếu khách hàng bấm nút "Hủy / Quay lại", frontend gọi API `POST /api/v1/tickets/cancel`:
  * Backend thực thi ngay logic thu hồi mà không cần chờ hết 10 phút.

---

## 9. NHÓM 8: NGHIỆP VỤ TẠI RẠP (CHECK-IN SCANNER & ON-SITE POS)

### 9.1. Soát Vé Điện Tử Tại Cửa Phòng Chiếu (Gate Check-in)
* **Người thực hiện:** Nhân viên rạp dùng điện thoại/máy quét POS chuyên dụng.
* **Luồng xử lý:**
  1. Quét mã QR trên màn hình điện thoại của khách.
  2. Client gửi payload quét được về API: `POST /api/v1/tickets/checkin`.
  3. **Kiểm tra tính hợp lệ:**
     * Giải mã và xác thực chữ ký số `qr_hash`.
     * Kiểm tra trạng thái vé: Phải là `VALID`. Nếu là `CHECKED_IN`, báo cảnh báo đỏ: *"Vé đã được soát vào lúc HH:mm:ss!"* (chống 1 vé chụp màn hình gửi nhiều người vào).
     * Kiểm tra thời gian: Chỉ cho phép soát vé trước giờ chiếu tối đa 30 phút và sau giờ chiếu tối đa 20 phút.
     * Kiểm tra đúng phòng chiếu và cụm rạp.
  4. Cập nhật `tickets.status = 'CHECKED_IN'`, lưu `checkin_time = NOW()`.
  5. Màn hình máy quét hiển thị màu xanh kèm thông tin: Tên phim, Hàng ghế, Số người, Phân loại độ tuổi (nhắc nhân viên đối chiếu CMND/CCCD nếu là phim `T18`).

### 9.2. In Vé Cứng Tại Quầy / Kiosk Tự Phục Vụ
* Khách nhập số điện thoại hoặc mã đơn hàng (`order_code`) tại Kiosk.
* Kiosk gọi API lấy danh sách vé hợp lệ và gửi lệnh in ra máy in nhiệt tại chỗ.

---

## 10. NHÓM 9: CHÍNH SÁCH HỦY & ĐỔI VÉ (REFUND & EXCHANGE)

### 10.1. Điều Kiện Hủy Vé Trực Tuyến
* Chỉ áp dụng cho khách hàng thành viên có đăng nhập khi mua vé.
* **Thời gian tối thiểu:** Yêu cầu hủy trước giờ suất chiếu bắt đầu ít nhất **60 đến 120 phút**.
* Vé phải ở trạng thái `VALID` (chưa từng quét check-in tại rạp).

### 10.2. Quy Trình Hoàn Tiền / Cấp Điểm
* Không hoàn tiền mặt trực tiếp vào tài khoản ngân hàng (để tránh chi phí giao dịch và gian lận).
* Hoàn tiền thành **Điểm thưởng rạp (Points)** hoặc **Voucher xem phim** có giá trị tương đương vào ví tài khoản của khách.
* Cập nhật vé thành `REVOKED`, đơn hàng thành `REFUNDED`.
* Nhả ghế trên PostgreSQL và Redis về `AVAILABLE` để hệ thống mở bán lại cho người khác.

---

## 11. NHÓM 10: BÁO CÁO DOANH THU & VẬN HÀNH (BACKOFFICE ANALYTICS)

### 11.1. Báo Cáo Hiệu Suất Suất Chiếu (Occupancy Rate)
* Tính toán tỷ lệ lấp đầy rạp:
  $$\text{Tỷ lệ lấp đầy (\%)} = \left( \frac{\text{Số ghế SOLD}}{\text{Tổng số ghế phòng chiếu}} \right) \times 100$$
* Giúp bộ phận quản lý rạp phát hiện các khung giờ ế để chạy khuyến mãi hoặc giảm bớt số suất chiếu.

### 11.2. Thống Kê Doanh Thu Realtime
* Doanh thu theo từng bộ phim (Box Office Revenue).
* Tỷ trọng doanh thu: Doanh thu vé vs Doanh thu Bắp nước (F&B chiếm trung bình 30-40% lợi nhuận rạp).
* Báo cáo đối soát doanh thu với cổng thanh toán PayOS định kỳ mỗi ngày.

---

## 12. BẢNG ÁNH XẠ KỸ THUẬT (DATA STRUCTURE & EVENT MAPPING)

Để biến toàn bộ các nghiệp vụ trên thành mã nguồn thực tế trên hạ tầng hiện tại của bạn:

### 12.1. Cấu Trúc Khóa Trên Redis 7

| Nghiệp vụ | Cấu trúc Redis Key | Kiểu dữ liệu | Ý nghĩa |
|---|---|---|---|
| **Trạng thái ghế** | `showtime:{id}:seats` | `HASH` | Field: `seatId`, Value: `AVAILABLE`, `HELD:{userId}`, `SOLD`, `BLOCKED` |
| **Quản lý hết hạn giữ** | `showtime:{id}:expirations` | `ZSET` | Score: `expireTimestamp`, Member: `seatId` |
| **Hạn mức Voucher** | `voucher:{code}:remaining` | `STRING (Integer)` | Lưu số lượt dùng còn lại, thao tác bằng lệnh nguyên tử `DECR` |
| **Khóa Idempotency** | `idemp:pay:{orderCode}` | `STRING` | TTL 120s, chống spam click nút "Xác nhận thanh toán" |

### 12.2. Danh Sách Sự Kiện Trên NATS JetStream (Stream: `CINEMA`)

| Tên Subject | Payload chính | Consumer Worker xử lý |
|---|---|---|
| `tickets.reserved` | `{ orderCode, showtimeId, userId, seatIds, expiresAt }` | 1. Ghi đơn hàng PENDING vào Postgres.<br>2. Bắn STOMP WebSocket đổi màu VÀNG. |
| `tickets.expired` | `{ orderCode, showtimeId, seatIds }` | 1. Update đơn hàng thành EXPIRED.<br>2. Bắn STOMP WebSocket đổi màu TRẮNG. |
| `tickets.paid` | `{ orderCode, showtimeId, transactionCode, amount }` | 1. Update đơn hàng PAID, tạo mã vé.<br>2. Bắn STOMP WebSocket đổi màu XÁM.<br>3. Gửi email xác nhận kèm QR. |
| `tickets.checked_in` | `{ ticketCode, auditoriumId, scannedAt }` | Ghi log kiểm soát tại rạp, thống kê khách vào rạp. |

### 12.3. Cập Nhật Bảng Cơ Sở Dữ Liệu PostgreSQL

So với 12 bảng hiện tại của FlashTicket, bạn thực hiện chuyển đổi như sau:
1. Đổi bảng `events` $\rightarrow$ Tách thành 2 bảng:
   * **`movies`**: Thông tin phim, độ tuổi, thời lượng.
   * **`showtimes`**: Chứa `movie_id`, `auditorium_id`, `start_time`, `end_time`, `format` (2D/3D).
2. Đổi bảng `venues` $\rightarrow$ Tách thành:
   * **`cinemas`**: Chi nhánh rạp.
   * **`auditoriums`**: Phòng chiếu trực thuộc rạp.
3. Thêm bảng **`concessions`** (Bắp nước) và bảng **`order_concessions`** (Chi tiết bắp nước thuộc đơn hàng).
4. Giữ nguyên toàn bộ logic của: `orders`, `payments`, `tickets`, `outbox_events` vì các bảng này đã thiết kế chuẩn hóa và dùng chung hoàn hảo cho cả concert lẫn cinema!
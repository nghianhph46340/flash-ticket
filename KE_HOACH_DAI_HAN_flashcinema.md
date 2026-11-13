# Kế Hoạch Dài Hạn — FlashTicket Cinema Platform

> **Mục tiêu**: Biến FlashTicket từ demo kỹ thuật thành sản phẩm đặt vé xem phim hoàn chỉnh, đủ để showcase trên CV và phỏng vấn System Design.

---

## Trạng thái hiện tại (Snapshot 2026-11-13)

### Đã hoàn thành

- [x] Zero Overselling (Redis Lua atomic lock, < 2ms)
- [x] Write-Behind (NATS JetStream → PostgreSQL)
- [x] Saga Timeout Rollback (nhả ghế tự động sau 10 phút)
- [x] Orphan Seat Prevention (chống ghế lẻ)
- [x] WebSocket realtime (STOMP broadcast HELD/SOLD/AVAILABLE)
- [x] Mock Payment Gateway (Hexagonal Architecture, PaymentGateway interface)
- [x] F&B Concessions tích hợp vào đơn hàng
- [x] QR vé điện tử (HMAC-SHA256)
- [x] Check-in soát vé (POST /api/v1/tickets/checkin)
- [x] Cinema Schema (V4: movies, cinemas, auditoriums, showtimes, cinema_seats, concessions)
- [x] Frontend Cinema UI (MovieSelector → ShowtimeSelector → SeatMap → Checkout → Tickets)
- [x] CI/CD Pipeline (GitHub Actions: Tests + Frontend Build + GraalVM Native 62MB)
- [x] Docker/Podman Compose stack local (Postgres, Redis, NATS, MinIO, Backend, Frontend)

### Đã sửa (bug fixes)

- [x] Lua Script nil check (ghế chưa pre-warm bị từ chối)
- [x] initSeats() race condition (hiển thị AVAILABLE trước khi fetch Redis)
- [x] cancelOrder() không reconnect WebSocket
- [x] TicketPassModal dùng cancelOrder() thay vì bookAnother()
- [x] OrphanSeatValidator wired vào TicketReservationService
- [x] showtime_id set trong OrderEventConsumer (trước đó NULL)
- [x] payment-info trả tên ghế thay vì UUID
- [x] PaymentService dùng PaymentGateway interface thay vì hardcode

---

## Phase 1 — Nền tảng người dùng & quản trị

> **Mục tiêu**: Hệ thống có đăng nhập, phân quyền, và quản lý nội dung qua UI.

### 1.1 Authentication & Authorization

**Backend:**
- [ ] Thêm dependency `spring-boot-starter-security` vào `pom.xml`
- [ ] Tạo `SecurityConfig.java` — cấu hình JWT filter chain
- [ ] Tạo `JwtUtil.java` — sinh token (HS256), verify, extract claims
- [ ] Tạo `JwtAuthenticationFilter.java` — intercept request, verify token
- [ ] Tạo `AuthController.java`:
  - [ ] `POST /api/v1/auth/register` — đăng ký (email, password, fullName)
  - [ ] `POST /api/v1/auth/login` — đăng nhập, trả JWT + refreshToken
  - [ ] `POST /api/v1/auth/refresh` — đổi refresh token lấy JWT mới
  - [ ] `GET /api/v1/auth/me` — thông tin user hiện tại từ token
- [ ] Hash password bằng `BCryptPasswordEncoder`
- [ ] Phân quyền: `CUSTOMER` (đặt vé), `ORGANIZER`/`ADMIN` (quản lý)
- [ ] Public endpoints: `/auth/**`, `GET /movies/**`, `GET /showtimes/**`, `GET /concessions`
- [ ] Protected endpoints: `/tickets/**`, `/orders/**`, `/payments/**`, `/tickets/checkin`
- [ ] Admin endpoints: `/admin/**` yêu cầu role ADMIN

**Frontend:**
- [ ] Tạo `authStore.ts` (Pinia) — lưu JWT, user info, login/logout actions
- [ ] Tạo `LoginPage.vue` — form đăng nhập/đăng ký
- [ ] Tạo axios interceptor — tự động attach `Authorization: Bearer <token>`
- [ ] Tạo route guard — redirect về login nếu chưa đăng nhập khi đặt vé
- [ ] Thêm dropdown user trong `Header.vue` (avatar, tên, đăng xuất)
- [ ] Thay `userId` random bằng userId từ JWT token

**GraalVM:**
- [ ] Thêm `@RegisterReflectionForBinding` cho JWT DTO classes
- [ ] Kiểm查 native build không lỗi reflection với Spring Security

**Test:**
- [ ] Unit test: `JwtUtil` sinh/verify token đúng, reject token giả
- [ ] Integration test: register → login → access protected endpoint → 403 without token

**Thời gian ước tính**: 2-3 buổi

---

### 1.2 Admin CRUD — Quản lý phim & suất chiếu

**Backend:**
- [ ] Tạo `AdminMovieController.java`:
  - [ ] `POST /api/v1/admin/movies` — thêm phim mới
  - [ ] `PUT /api/v1/admin/movies/{id}` — sửa thông tin phim
  - [ ] `DELETE /api/v1/admin/movies/{id}` — xóa (soft delete, set `is_active=false`)
  - [ ] `POST /api/v1/admin/movies/{id}/poster` — upload poster lên MinIO
- [ ] Tạo `AdminShowtimeController.java`:
  - [ ] `POST /api/v1/admin/showtimes` — tạo suất chiếu (kiểm tra overlap)
  - [ ] `PUT /api/v1/admin/showtimes/{id}` — sửa (chỉ khi chưa có vé bán)
  - [ ] `DELETE /api/v1/admin/showtimes/{id}` — hủy suất (CHỈ khi chưa có vé)
  - [ ] `PATCH /api/v1/admin/showtimes/{id}/status` — đổi DRAFT → PUBLISHED
- [ ] Tạo `AdminCinemaController.java`:
  - [ ] CRUD cinemas, auditoriums
  - [ ] `POST /api/v1/admin/auditoriums/{id}/seats` — import layout ghế
- [ ] Tạo `AdminConcessionController.java`:
  - [ ] CRUD concessions (thêm/sửa/xóa bắp nước)
- [ ] Tất cả endpoint yêu cầu role `ADMIN`

**Frontend:**
- [ ] Tạo `AdminLayout.vue` — sidebar navigation (Phim, Suất chiếu, Rạp, F&B)
- [ ] Tạo `AdminMoviesPage.vue`:
  - [ ] Table list phim + nút Thêm/Sửa/Xóa
  - [ ] Form thêm phim (title, duration, age_rating, poster upload)
- [ ] Tạo `AdminShowtimesPage.vue`:
  - [ ] Form tạo suất: chọn phim → chọn phòng → chọn giờ → giá
  - [ ] Calendar view xem lịch chiếu theo ngày
- [ ] Tạo `AdminCinemasPage.vue` — quản lý rạp & phòng chiếu
- [ ] Tạo `AdminConcessionsPage.vue` — quản lý bắp nước

**Test:**
- [ ] Integration test: admin tạo phim → tạo suất → publish → khách thấy trên API
- [ ] Test overlap prevention: 2 suất cùng phòng, giờ trùng → 400 error

**Thời gian ước tính**: 3-4 buổi

---

### 1.3 Lịch sử đơn hàng & "Vé của tôi"

**Backend:**
- [ ] Thêm `GET /api/v1/orders?userId={id}&page=0&size=10` — danh sách đơn theo user, có phân trang
- [ ] Thêm `GET /api/v1/orders/{orderCode}` — chi tiết 1 đơn (items + concessions + payment status)
- [ ] Thêm `GET /api/v1/tickets?userId={id}` — danh sách vé đã mua
- [ ] Thêm `GET /api/v1/tickets/{ticketCode}` — chi tiết 1 vé (phim, ghế, suất, QR hash, status)

**Frontend:**
- [ ] Tạo `MyTicketsPage.vue`:
  - [ ] Tab "Vé sắp xem" (status=VALID, showtime > now)
  - [ ] Tab "Lịch sử" (tất cả vé, có filter theo trạng thái)
  - [ ] Mỗi vé: poster phim, tên, ghế, suất chiếu, mã vé, nút xem QR
- [ ] Tạo `OrderHistoryPage.vue`:
  - [ ] Danh sách đơn hàng: mã đơn, ngày đặt, tổng tiền, trạng thái
  - [ ] Click vào xem chi tiết: ghế, F&B, thanh toán
- [ ] Thêm vào route: `/profile/tickets`, `/profile/orders`

**Test:**
- [ ] Đặt vé → thanh toán → mở "Vé của tôi" → thấy vé
- [ ] Check-in vé → vé chuyển sang "Đã sử dụng"

**Thời gian ước tính**: 1-2 buổi

---

### 1.4 Sơ đồ ghế động từ DB

**Backend:**
- [ ] Tạo `GET /api/v1/auditoriums/{id}/layout` — trả về layout ghế từ `cinema_seats` table:
  ```json
  {
    "auditoriumId": "...",
    "name": "Phòng 1 - Standard",
    "screenFormat": "STANDARD_2D",
    "seats": [
      {"id": "...", "rowLabel": "A", "seatNumber": 1, "seatType": "STANDARD", "pairSeatId": null},
      {"id": "...", "rowLabel": "G", "seatNumber": 1, "seatType": "SWEETBOX", "pairSeatId": "..."}
    ]
  }
  ```
- [ ] Seed dữ liệu `cinema_seats` cho 2 phòng chiếu trong V5 migration:
  - [ ] Phòng 1 (Standard 2D): 8 hàng × 10 ghế = 80 ghế (A-B: VIP, C-F: STANDARD, G-H: SWEETBOX)
  - [ ] Phòng 2 (IMAX): 6 hàng × 10 ghế = 60 ghế (A-B: VIP, C-D: STANDARD, E-F: SWEETBOX)
  - [ ] Set `pair_seat_id` cho Sweetbox (G1↔G2, G3↔G4, ...)

**Frontend:**
- [ ] `selectShowtime()` → gọi `GET /auditoriums/{id}/layout` thay vì hardcode
- [ ] `initSeats()` — render ghế từ layout API, map sang object Seat
- [ ] Sweetbox: tự động set `pairSeatId` khi load, toggle chọn cả cặp
- [ ] VIP/Standard/Sweetbox hiển thị màu khác đúng theo `seatType` từ DB

**Test:**
- [ ] Mở phòng Standard (80 ghế) → thấy đúng 8 hàng × 10
- [ ] Mở phòng IMAX (60 ghế) → thấy đúng 6 hàng × 10
- [ ] Sweetbox: bấm G1 → G2 tự chọn, bấm lại → cả 2 bỏ

**Thời gian ước tính**: 1-2 buổi

---

## Phase 2 — Nghiệp vụ rạp phim đầy đủ

> **Mục tiêu**: Đủ chức năng để hoạt động như rạp phim thật.

### 2.1 Trang chi tiết phim

**Backend:**
- [ ] `GET /api/v1/movies/{slug}` — trả về phim + danh sách suất chiếu + cast + trailer

**Frontend:**
- [ ] Tạo `MovieDetailPage.vue`:
  - [ ] Hero section: poster lớn + backdrop
  - [ ] Trailer embed (YouTube iframe từ `trailer_url`)
  - [ ] Thông tin: đạo diễn, diễn viên, thời lượng, độ tuổi, ngày khởi chiếu
  - [ ] Synopsis đầy đủ
  - [ ] Danh sách suất chiếu (reuse `ShowtimeSelector`)
  - [ ] Nút "Đặt vé ngay" → scroll xuống suất chiếu
- [ ] Click phim trên `MovieSelector` → navigate đến `/movies/{slug}` thay vì thẳng suất chiếu

**Thời gian ước tính**: 1 buổi

---

### 2.2 Hủy vé & Hoàn tiền

**Backend:**
- [ ] Tạo `POST /api/v1/orders/{orderCode}/cancel`:
  - [ ] Kiểm tra order status = PAID
  - [ ] Kiểm tra suất chiếu chưa bắt đầu (chỉ hủy trước giờ chiếu)
  - [ ] Gọi `PaymentGateway.refund()` (interface method mới)
  - [ ] Cập nhật order status = REFUNDED
  - [ ] Trả ghế về AVAILABLE trên Redis
  - [ ] Cập nhật seat status trong PostgreSQL
  - [ ] Revoke tickets (status = REVOKED)
  - [ ] Broadcast WebSocket: ghế → AVAILABLE
- [ ] Thêm `refund()` method vào `PaymentGateway` interface
- [ ] `MockPaymentGateway.refund()` — log + trả success
- [ ] `PayOsPaymentGateway.refund()` — gọi PayOS refund API

**Frontend:**
- [ ] Trong "Vé của tôi" → nút "Hủy vé" (chỉ hiện khi suất chưa chiếu)
- [ ] Modal xác nhận hủy: "Bạn có chắc muốn hủy vé? Tiền sẽ được hoàn trong 3-5 ngày"
- [ ] Hiển thị trạng thái REFUNDED

**Test:**
- [ ] Đặt vé → thanh toán → hủy vé → ghế về AVAILABLE → vé REVOKED
- [ ] Hủy vé sau giờ chiếu → 400 error
- [ ] Hủy vé đã check-in → 400 error

**Thời gian ước tính**: 2 buổi

---

### 2.3 Email xác nhận vé

**Backend:**
- [ ] Thêm dependency `spring-boot-starter-mail`
- [ ] Tạo `EmailService.java`:
  - [ ] `sendTicketConfirmation(to, orderCode, tickets, movieTitle, showtime)`
  - [ ] `sendShowtimeReminder(to, movieTitle, showtimeTime)` — cronjob gửi trước 1 tiếng
- [ ] Tạo email template (HTML inline, không cần template engine nặng)
- [ ] Config SMTP trong `application.yml`:
  ```yaml
  spring:
    mail:
      host: ${SMTP_HOST:smtp.gmail.com}
      port: ${SMTP_PORT:587}
      username: ${SMTP_USER:}
      password: ${SMTP_PASS:}
      properties:
        mail.smtp.starttls.enable: true
  ```
- [ ] Gửi email sau khi `processWebhook()` thành công (async, không block response)
- [ ] Cronjob `@Scheduled` quét vé có showtime trong 1 giờ tới → gửi reminder

**Frontend:**
- [ ] Hiển thị "Email xác nhận đã gửi đến {email}" sau khi thanh toán

**Test:**
- [ ] Thanh toán → nhận email với mã vé + QR
- [ ] Test với Mailtrap (dev) / Gmail SMTP (prod)

**Thời gian ước tính**: 1-2 buổi

---

### 2.4 Tìm kiếm & lọc

**Backend:**
- [ ] `GET /api/v1/movies?search={keyword}&genre={genre}&page=0&size=12` — full text search trên title
- [ ] `GET /api/v1/showtimes?movieId={id}&date={YYYY-MM-DD}&format={2D|IMAX}` — filter theo ngày/format

**Frontend:**
- [ ] Search bar trên `MovieSelector` — input debounced 300ms
- [ ] Filter chips: theo ngày (Hôm nay, Ngày mai, Cuối tuần), theo format (2D, IMAX, 4DX)
- [ ] Loading skeleton khi search

**Thời gian ước tính**: 1 buổi

---

## Phase 3 — Trải nghiệm nâng cao

> **Mục tiêu**: Thêm tính năng khiến sản phẩm nổi bật.

### 3.1 Mã khuyến mãi / Voucher

**Database:**
- [ ] Migration V5: bảng `promotions`:
  ```sql
  CREATE TABLE promotions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code VARCHAR(50) NOT NULL UNIQUE,
    description TEXT,
    discount_type VARCHAR(20) NOT NULL, -- PERCENTAGE, FIXED_AMOUNT
    discount_value NUMERIC(12,2) NOT NULL,
    min_order_amount NUMERIC(12,2) DEFAULT 0,
    max_discount_amount NUMERIC(12,2),
    usage_limit INT,
    used_count INT DEFAULT 0,
    valid_from TIMESTAMP WITH TIME ZONE NOT NULL,
    valid_until TIMESTAMP WITH TIME ZONE NOT NULL,
    is_active BOOLEAN DEFAULT TRUE
  );
  ```

**Backend:**
- [ ] `POST /api/v1/promotions/validate` — kiểm tra mã hợp lệ, trả về số tiền giảm
- [ ] Áp dụng trong `processWebhook()` hoặc `reserveSeats()` — trừ vào totalAmount
- [ ] Tăng `used_count` khi áp dụng (atomic update)

**Frontend:**
- [ ] Thêm input "Mã khuyến mãi" trong `CheckoutModal`
- [ ] Hiển thị số tiền giảm breakdown

**Thời gian ước tính**: 2 buổi

---

### 3.2 Dashboard doanh thu (Admin)

**Backend:**
- [ ] `GET /api/v1/admin/stats/revenue?from={date}&to={date}` — doanh thu theo ngày/tuần/tháng
- [ ] `GET /api/v1/admin/stats/occupancy?showtimeId={id}` — tỷ lệ lấp đầy ghế
- [ ] `GET /api/v1/admin/stats/top-movies?limit=10` — top phim doanh thu
- [ ] `GET /api/v1/admin/stats/concessions?from={date}&to={date}` — F&B bán chạy nhất

**Frontend:**
- [ ] Tạo `AdminDashboardPage.vue`:
  - [ ] Card: tổng doanh thu hôm nay, số vé bán, tỷ lệ lấp đầy
  - [ ] Biểu đồ doanh thu (Chart.js hoặc Recharts)
  - [ ] Top 5 phim bán chạy
  - [ ] Top 5 F&B bán chạy

**Thời gian ước tính**: 2 buổi

---

### 3.3 Rating & Review phim

**Database:**
- [ ] Migration V6: bảng `reviews`:
  ```sql
  CREATE TABLE reviews (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    movie_id UUID NOT NULL REFERENCES movies(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    rating INT NOT NULL CHECK (rating BETWEEN 1 AND 5),
    comment TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_review_user_movie UNIQUE (movie_id, user_id)
  );
  ```

**Backend:**
- [ ] `POST /api/v1/movies/{id}/reviews` — viết review (yêu cầu đã mua vé)
- [ ] `GET /api/v1/movies/{id}/reviews?page=0&size=10` — list reviews
- [ ] `GET /api/v1/movies/{id}/rating` — điểm trung bình + số lượt

**Frontend:**
- [ ] Trong `MovieDetailPage`: hiển thị điểm đánh giá + form viết review
- [ ] Chỉ cho phép review khi đã mua vé (check API)

**Thời gian ước tính**: 1-2 buổi

---

### 3.4 Tích điểm thành viên

**Database:**
- [ ] Migration V7: bảng `loyalty_points`:
  ```sql
  CREATE TABLE loyalty_points (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    points INT NOT NULL DEFAULT 0,
    tier VARCHAR(20) DEFAULT 'SILVER', -- SILVER, GOLD, PLATINUM
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
  );
  ```

**Backend:**
- [ ] Cộng điểm sau khi thanh toán (1 điểm / 1000đ)
- [ ] `GET /api/v1/loyalty/me` — số điểm, hạng thành viên
- [ ] Đổi điểm lấy voucher

**Frontend:**
- [ ] Hiển thị điểm trong profile
- [ ] Bảng xếp hạng hạng thành viên

**Thời gian ước tính**: 2 buổi

---

## Phase 4 — Production-ready

> **Mục tiêu**: Deploy production thật, vận hành ổn định.

### 4.1 Thanh toán thật (PayOS)

- [ ] Đăng ký PayOS account, lấy client_id, api_key, checksum_key
- [ ] Cấu hình domain + Nginx SSL (Let's Encrypt) trên VPS
- [ ] `PayOsPaymentGateway.createPayment()` — gọi PayOS API tạo payment link
- [ ] `POST /api/v1/payments/webhook` — endpoint public cho PayOS callback
- [ ] Verify webhook signature bằng HMAC-SHA256 (checksum_key)
- [ ] Test end-to-end với thẻ ngân hàng thật
- [ ] Config env production: `PAYMENT_PROVIDER=payos`

**Thời gian ước tính**: 2-3 buổi (bao gồm chờ PayOS duyệt)

---

### 4.2 Deploy VPS production

- [ ] VPS: `3.25.83.72` (đã có SSH key)
- [ ] Cài đặt: Docker/Podman, Nginx, Certbot (SSL)
- [ ] Deploy stack:
  - [ ] PostgreSQL 16 (data volume)
  - [ ] Redis 7.2
  - [ ] NATS JetStream
  - [ ] Backend (GraalVM Native binary, ~50MB RAM)
  - [ ] Frontend (Nginx serve static)
- [ ] Nginx reverse proxy:
  - [ ] `flashcinema.vn` → frontend (port 3000)
  - [ ] `/api/*` → backend (port 8080)
  - [ ] `/ws/*` → backend WebSocket
  - [ ] SSL/TLS bằng Let's Encrypt
- [ ] Backup database tự động (cronjob pg_dump hàng ngày)
- [ ] Health check endpoint `/api/v1/health`
- [ ] Log rotation

**Thời gian ước tính**: 1-2 buổi

---

### 4.3 Monitoring & Alerting

- [ ] Spring Boot Actuator: `/actuator/health`, `/actuator/metrics`
- [ ] Structured logging (JSON format) thay vì plain text
- [ ] Grafana + Prometheus hoặc đơn giản hơn: Uptime Kuma
- [ ] Alert khi: backend down, Redis down, NATS disconnected, DB connection pool full
- [ ] Sentry hoặc đơn giản hơn: log errors vào file + email alert

**Thời gian ước tính**: 1 buổi

---

### 4.4 Hiệu năng & Tối ưu

- [ ] Load test với `k6` hoặc `wrk`: 1000 RPS, đo P99 latency
- [ ] Redis connection pool tuning
- [ ] PostgreSQL: thêm index nếu query chậm, `EXPLAIN ANALYZE` các query chính
- [ ] Frontend: lazy load images, code splitting, CDN cho poster
- [ ] Backend: thêm Caffeine cache layer cho movies/showtimes (read-heavy, ít thay đổi)
- [ ] GraalVM: tối ưu `native-image` config, giảm binary size

**Thời gian ước tính**: 1-2 buổi

---

### 4.5 CI/CD deploy tự động

- [ ] Thêm job `deploy` trong `ci.yml` (chạy sau khi build native pass):
  - [ ] SCP binary lên VPS
  - [ ] SSH restart service
  - [ ] Health check sau deploy
- [ ] Hoặc: build Docker image, push lên registry, pull trên VPS
- [ ] Rollback mechanism: giữ 2 bản binary, swap nếu health check fail

**Thời gian ước tính**: 1 buổi

---

## Tổng hợp checklist (priority order)

| # | Task | Phase | Priority | Effort |
|---|------|-------|----------|--------|
| 1 | Authentication (JWT + Spring Security) | 1.1 | 🔴 Critical | 2-3 buổi |
| 2 | Admin CRUD (movies/showtimes/cinemas) | 1.2 | 🔴 Critical | 3-4 buổi |
| 3 | Lịch sử đơn hàng & "Vé của tôi" | 1.3 | 🔴 Critical | 1-2 buổi |
| 4 | Sơ đồ ghế động từ DB | 1.4 | 🔴 Critical | 1-2 buổi |
| 5 | Trang chi tiết phim | 2.1 | 🟡 High | 1 buổi |
| 6 | Hủy vé & Hoàn tiền | 2.2 | 🟡 High | 2 buổi |
| 7 | Email xác nhận vé | 2.3 | 🟡 High | 1-2 buổi |
| 8 | Tìm kiếm & lọc | 2.4 | 🟡 High | 1 buổi |
| 9 | Mã khuyến mãi / Voucher | 3.1 | 🟢 Medium | 2 buổi |
| 10 | Dashboard doanh thu | 3.2 | 🟢 Medium | 2 buổi |
| 11 | Rating & Review | 3.3 | 🟢 Medium | 1-2 buổi |
| 12 | Tích điểm thành viên | 3.4 | 🟢 Medium | 2 buổi |
| 13 | Thanh toán thật PayOS | 4.1 | 🔵 Prod | 2-3 buổi |
| 14 | Deploy VPS production | 4.2 | 🔵 Prod | 1-2 buổi |
| 15 | Monitoring & Alerting | 4.3 | 🔵 Prod | 1 buổi |
| 16 | Load test & Tối ưu | 4.4 | 🔵 Prod | 1-2 buổi |
| 17 | CI/CD deploy tự động | 4.5 | 🔵 Prod | 1 buổi |

**Tổng effort ước tính**: ~25-35 buổi làm việc

---

## Lưu ý kỹ thuật xuyên suốt

### GraalVM Native Image
Mỗi Entity/DTO mới cần `@RegisterReflectionForBinding` hoặc thêm vào `NativeRuntimeHints.java` để tránh lỗi Reflection khi AOT compile.

### Flyway Migration
- Mọi thay đổi schema phải qua migration file mới (V5, V6, V7...) — KHÔNG sửa migration cũ.
- Test migration trên CI trước deploy.

### Redis Key Scope
- Giữ nguyên key format: `{event:{showtimeId}}:seats` và `{event:{showtimeId}}:expirations`
- Khi thêm feature mới dùng Redis, dùng prefix riêng (vd: `loyalty:{userId}`).

### NATS Stream
- Stream name hiện tại: `TICKETS` — nếu thêm event type mới (refund, review), tạo stream riêng hoặc thêm subject.

### Security
- Mọi admin endpoint phải có `@PreAuthorize("hasRole('ADMIN')")`
- Mọi user-facing endpoint phải verify `userId` trong JWT khớp với resource owner.
- Validate input (SQL injection, XSS) — Spring Validation + escaping.

### Testing
- Mỗi feature mới cần integration test chạy trên CI
- Maintain test coverage > 60% cho core business logic

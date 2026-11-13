# ⚡ FlashTicket Cinema

**Hệ thống đặt vé rạp phim chịu tải cao** — thiết kế để không bao giờ bán trùng vé dù có hàng nghìn người bấm cùng một lúc.

> **Stack**: Spring Boot 3 · Redis 7 Lua Script · NATS JetStream · Vue 3 · WebSocket STOMP · PostgreSQL 16 · GraalVM Native Image

---

## Tại sao dự án này tồn tại?

Bài toán Flash Sale vé sự kiện có một vấn đề cực kỳ khó: **trong vài giây đầu mở bán, hàng nghìn người cùng bấm vào cùng một ghế**. Nếu xử lý sai, hệ thống sẽ bán một ghế cho nhiều người — điều không thể chấp nhận.

Giải pháp thông thường (check DB rồi update) thất bại vì khoảng thời gian giữa "check" và "update" đủ để một request khác chen vào. FlashTicket giải quyết bài toán này bằng cách đẩy toàn bộ logic kiểm tra và khóa ghế vào **một Lua Script chạy nguyên tử trên Redis** — không có khoảng thời gian nào để chen vào.

---

## Kết quả đo được

| Chỉ số | Kết quả |
|--------|---------|
| API latency (P99) | **4–6ms** |
| Zero Overselling | **100%** (1000 request tranh 1 ghế → đúng 1 người thắng) |
| RAM Backend (JVM) | ~338 MB |
| RAM Backend (Native Binary) | ~50–60 MB |
| Native Binary size | 62.3 MB |
| Thời gian khởi động | <1 giây (native) |

---

## Chạy thử ngay (1 lệnh)

```bash
git clone https://github.com/nghianhph46340/flash-ticket.git
cd flash-ticket
podman compose up -d   # hoặc: docker compose up -d
```

Mở trình duyệt: **http://localhost:3000**

> **Yêu cầu**: Docker hoặc Podman

---

## Luồng nghiệp vụ hoàn chỉnh

```
Người dùng
    │
    ├─[1]─► Chọn phim đang chiếu (poster, độ tuổi, thời lượng)
    │
    ├─[2]─► Chọn suất chiếu (Standard 2D / IMAX / Dolby, giờ chiếu, giá vé)
    │
    ├─[3]─► Mở sơ đồ ghế SVG realtime
    │        • Ghế TRẮNG = còn trống
    │        • Ghế VÀNG  = đang bị giữ bởi người khác (WebSocket live)
    │        • Ghế XÁM   = đã bán
    │        • Ghế HỒNG  = Sweetbox (chọn 1 lấy 2)
    │
    ├─[4]─► Chọn ghế + thêm Combo Bắp Nước (F&B)
    │
    ├─[5]─► Bấm "Giữ chỗ ngay"
    │        └─► Redis Lua Script khóa ghế nguyên tử (< 2ms)
    │             • Thành công → ghế chuyển VÀNG trên mọi tab đang mở
    │             • Thất bại   → báo ngay "Ghế đã có người", không tốn thêm thời gian
    │
    ├─[6]─► Thanh toán trong 10 phút (đếm ngược realtime)
    │        • Mock Payment: bấm nút xác nhận để demo
    │        • Production: quét QR VietQR NAPAS
    │
    ├─[7]─► Nhận vé điện tử với mã QR (HMAC-SHA256)
    │        └─► Ghế chuyển XÁM trên sơ đồ của tất cả khách đang xem
    │
    └─[8]─► Check-in tại cửa rạp bằng mã QR
```

---

## Kiến trúc hệ thống

```
┌─────────────────────────────────────────────────────────────┐
│                        CLIENT LAYER                         │
│                                                             │
│   Browser                                                   │
│   ┌──────────────────────────────────────────────────────┐  │
│   │  Vue 3 + TypeScript + Tailwind CSS                   │  │
│   │  • MovieSelector → ShowtimeSelector → SeatMap        │  │
│   │  • WebSocket STOMP (cập nhật ghế realtime)           │  │
│   │  • Mock Payment Modal + Countdown Timer              │  │
│   └───────────────────┬──────────────────────────────────┘  │
└───────────────────────│─────────────────────────────────────┘
                        │ HTTP / WebSocket
┌───────────────────────▼─────────────────────────────────────┐
│                      API GATEWAY LAYER                      │
│                     Nginx (port 3000)                       │
│          /api/v1/* → proxy to backend:8080                  │
│          /ws/*     → proxy WebSocket to backend:8080        │
└───────────────────────┬─────────────────────────────────────┘
                        │
┌───────────────────────▼─────────────────────────────────────┐
│                     BACKEND LAYER                           │
│           Spring Boot 3 + GraalVM Native Image              │
│                      port 8080                              │
│                                                             │
│  ┌─────────────────┐    ┌─────────────────┐                 │
│  │  REST Controllers│    │  WebSocket STOMP│                 │
│  │  /movies        │    │  Broker         │                 │
│  │  /showtimes     │    │  /topic/events/ │                 │
│  │  /tickets       │    │  {id}/seats     │                 │
│  │  /payments      │    └────────┬────────┘                 │
│  └────────┬────────┘             │                          │
│           │                      │                          │
│  ┌────────▼──────────────────────▼──────────┐               │
│  │              Services                    │               │
│  │  TicketReservationService                │               │
│  │  PaymentService (MockGateway/PayOSGateway)│              │
│  │  CinemaService                           │               │
│  │  OrphanSeatValidator                     │               │
│  │  SeatTimeoutSweeper (Scheduled)          │               │
│  └────┬─────────────┬────────────┬──────────┘               │
└───────│─────────────│────────────│────────────────────────  ┘
        │             │            │
        ▼             ▼            ▼
┌───────────┐  ┌──────────┐  ┌────────────────────────────────┐
│  Redis 7  │  │   NATS   │  │        PostgreSQL 16           │
│  port 6379│  │ JetStream│  │           port 5432            │
│           │  │ port 4222│  │                                │
│ Hash:     │  │          │  │  movies, cinemas, auditoriums  │
│ seats     │  │ Stream:  │  │  showtimes, cinema_seats       │
│ status    │  │ TICKETS  │  │  orders, order_items, payments │
│           │  │          │  │  tickets, concessions          │
│ ZSet:     │  │ Consumer:│  │  users, events, seats, venues  │
│ expirations│ │ Worker   │  │                                │
│ TTL 600s  │  │ (Write-  │  │  Flyway migrations: V1 → V4   │
│           │  │ Behind)  │  │                                │
└───────────┘  └──────────┘  └────────────────────────────────┘
```

---

## Cơ chế cốt lõi: Zero Overselling

Đây là phần kỹ thuật quan trọng nhất. Khi nhiều người cùng bấm giữ cùng một ghế:

```
User A ──────────────────────────────► Redis Lua Script
User B ──────────────────────────────► (single-threaded, atomic)
User C ──────────────────────────────►
  ...
User 1000 ───────────────────────────►

           Bên trong Lua Script (< 2ms):
           ┌─────────────────────────────────────────┐
           │ 1. HGET seats "A5" → nil/AVAILABLE?     │
           │    • nil hoặc AVAILABLE → tiếp tục      │
           │    • HELD:xxx hoặc SOLD → từ chối ngay  │
           │                                         │
           │ 2. Nếu tất cả ghế OK:                   │
           │    HSET seats "A5" "HELD:UserA"         │
           │    ZADD expirations (now+600) "A5"       │
           │    → Trả về SUCCESS                     │
           └─────────────────────────────────────────┘

Kết quả: User A thắng (HTTP 200), 999 người còn lại nhận HTTP 409
Không có lock DB, không có race condition, không có overselling.
```

**File:** `ticketing-service/src/main/resources/scripts/reserve_seat.lua`

---

## Cơ chế: Write-Behind (Ghi bất đồng bộ)

Thay vì ghi thẳng vào PostgreSQL (gây nghẽn khi 10.000 request/giây), hệ thống dùng pattern Write-Behind:

```
Request     Redis       NATS            PostgreSQL
  │           │           │                 │
  ├──lock──►  │           │                 │
  │        HELD           │                 │
  │◄─200─── │            │                 │
  │          ├─publish──► │                 │
  │          │      tickets.reserved        │
  │          │            ├──consume──────► │
  │          │            │            INSERT order
  │          │            │            INSERT order_items
  │          │            │            UPDATE seat status
  │          │            │◄────ACK──────── │
```

Khách hàng nhận phản hồi ngay sau bước Redis (< 10ms). PostgreSQL được ghi bất đồng bộ trong vài trăm mili-giây sau.

---

## Cơ chế: Saga Timeout Rollback (Nhả ghế tự động)

Nếu khách không thanh toán trong 10 phút, hệ thống tự động nhả ghế:

```
Khi giữ chỗ:
  ZADD {event:id}:expirations  <now + 600>  "A5"

SeatTimeoutSweeper (chạy mỗi 5 giây):
  ZRANGEBYSCORE expirations 0 <currentTime>
      → Tìm thấy "A5" đã quá hạn
      → HDEL seats "A5"
      → Cập nhật order = EXPIRED trong PostgreSQL  
      → WebSocket broadcast: A5 → AVAILABLE (ghế về màu TRẮNG)
      → Khách khác có thể đặt ngay
```

---

## Cơ chế: Orphan Seat Prevention (Chống ghế lẻ)

Khi khách chọn ghế, hệ thống kiểm tra xem việc chọn đó có để lại ghế đơn độc không thể bán cho ai không:

```
Hàng C: [SOLD][SOLD][   ][   ][SOLD][   ][   ][   ][SOLD][SOLD]
                     C3   C4         C6   C7   C8

Nếu khách chọn C4 và C6, C7, C8:
Hàng C sau đó: [SOLD][SOLD][   ][SEL][SOLD][SEL][SEL][SEL][SOLD][SOLD]
                             C3        → C3 bị kẹp một mình ← VI PHẠM

Hệ thống từ chối và báo: "Chọn ghế này sẽ để lại C3 đơn độc không thể bán"
```

**File:** `ticketing-service/src/main/java/com/ticket/core/service/OrphanSeatValidator.java`

---

## Cơ chế: Payment Gateway (Hexagonal Architecture)

```java
// Port (interface) — Core không biết implementation nào đang chạy
public interface PaymentGateway {
    PaymentInitResult createPayment(String orderCode, BigDecimal amount, String description);
    boolean verifyWebhookSignature(String payload, String signature);
}

// Adapter 1: Mock (dev/test) — active khi PAYMENT_PROVIDER=mock
@ConditionalOnProperty(name = "payment.provider", havingValue = "mock", matchIfMissing = true)
public class MockPaymentGateway implements PaymentGateway { ... }

// Adapter 2: PayOS (production) — active khi PAYMENT_PROVIDER=payos
@ConditionalOnProperty(name = "payment.provider", havingValue = "payos")
public class PayOsPaymentGateway implements PaymentGateway { ... }
```

Switch giữa môi trường chỉ bằng env var, không cần sửa code.

---

## Cấu trúc dự án

```
flash-tickets/
├── ticketing-service/          # Backend Spring Boot 3
│   ├── src/main/java/com/ticket/
│   │   ├── core/
│   │   │   ├── domain/         # Entities, Events, Exceptions
│   │   │   ├── port/out/       # PaymentGateway interface (Hexagonal)
│   │   │   └── service/        # Business logic
│   │   │       ├── TicketReservationService.java
│   │   │       ├── PaymentService.java
│   │   │       ├── CinemaService.java
│   │   │       ├── OrphanSeatValidator.java
│   │   │       └── SeatTimeoutSweeper.java
│   │   └── infrastructure/
│   │       ├── adapter/in/
│   │       │   ├── nats/       # OrderEventConsumer (Write-Behind worker)
│   │       │   └── web/        # REST Controllers + DTOs
│   │       └── adapter/out/
│   │           ├── payment/    # MockPaymentGateway, PayOsPaymentGateway
│   │           ├── persistence/ # JPA Entities + Repositories
│   │           ├── redis/      # RedisSeatCacheService + Lua scripts
│   │           └── websocket/  # SeatStatusBroadcaster
│   └── src/main/resources/
│       ├── db/migration/       # Flyway V1 → V4
│       └── scripts/
│           └── reserve_seat.lua # Atomic lock script
│
├── ticketing-frontend/         # Vue 3 + TypeScript
│   └── src/
│       ├── components/
│       │   ├── MovieSelector.vue
│       │   ├── ShowtimeSelector.vue
│       │   ├── SeatMap.vue
│       │   ├── BookingSummary.vue
│       │   ├── ConcessionCart.vue
│       │   ├── CheckoutModal.vue
│       │   └── TicketPassModal.vue
│       ├── stores/
│       │   └── ticketStore.ts  # Pinia store (toàn bộ state management)
│       └── services/
│           ├── api.ts          # Axios client
│           └── websocket.ts    # STOMP WebSocket service
│
├── docker-compose.yml          # Stack local: Postgres, Redis, NATS, MinIO
└── .github/workflows/
    └── ci.yml                  # CI: Tests + Frontend Build + GraalVM Native
```

---

## API Reference

### Cinema

| Method | Endpoint | Mô tả |
|--------|----------|-------|
| GET | `/api/v1/movies` | Danh sách phim đang chiếu |
| GET | `/api/v1/movies/{id}/showtimes` | Lịch chiếu theo phim |
| GET | `/api/v1/concessions` | Danh mục bắp nước F&B |

### Đặt vé

| Method | Endpoint | Mô tả |
|--------|----------|-------|
| GET | `/api/v1/events/{showtimeId}/seats` | Trạng thái ghế hiện tại (từ Redis) |
| POST | `/api/v1/tickets/reserve` | Giữ chỗ nguyên tử (Lua Script) |
| GET | `/api/v1/orders/{orderCode}/payment-info` | Thông tin thanh toán + QR |

### Thanh toán

| Method | Endpoint | Mô tả |
|--------|----------|-------|
| POST | `/api/v1/payments/mock-confirm` | Giả lập thanh toán thành công (dev only) |
| POST | `/api/v1/payments/webhook` | Nhận webhook từ cổng thanh toán thật |

### WebSocket

```
ws://localhost:8080/ws
Subscribe: /topic/events/{showtimeId}/seats
Message: { seatIds: ["A1","A2"], status: "HELD"|"AVAILABLE"|"SOLD", userId, timestamp }
```

---

## Cấu hình môi trường

```bash
# Backend (application.yml / env vars)
DB_HOST=localhost
DB_PORT=5432
REDIS_HOST=localhost
NATS_URL=nats://localhost:4222
PAYMENT_PROVIDER=mock          # hoặc: payos

# Chỉ cần khi PAYMENT_PROVIDER=payos
PAYOS_CLIENT_ID=xxx
PAYOS_API_KEY=xxx
PAYOS_CHECKSUM_KEY=xxx
```

---

## Kiểm tra Zero Overselling

Chạy script này để tái hiện bài toán flash sale: 50 người tranh 1 ghế cùng lúc.

```bash
SHOWTIME_ID="00000000-0000-0000-0004-000000000001"

for i in $(seq 1 50); do
  curl -s -X POST http://localhost:8080/api/v1/tickets/reserve \
    -H "Content-Type: application/json" \
    -d "{\"eventId\":\"$SHOWTIME_ID\",\"userId\":\"user-$i\",\"seatIds\":[\"E5\"]}" \
    -o /tmp/r_$i.json &
done
wait

echo "Thành công:"
grep -l '"status":"PENDING"' /tmp/r_*.json | wc -l

echo "Thất bại (409):"
grep -l '"SEAT_UNAVAILABLE"' /tmp/r_*.json | wc -l
```

**Kết quả mong đợi:** 1 thành công, 49 thất bại — không bao giờ có 2 người cùng giữ 1 ghế.

---

## CI/CD Pipeline

Mỗi push lên `main` tự động chạy 2 jobs song song:

```
Push to main
    │
    ├──► Job 1: Unit & Integration Tests (45s)
    │    ├── Start Postgres 16, Redis 7, NATS JetStream
    │    ├── Run Maven Tests
    │    ├── Verify Flyway V1–V4 migrations
    │    └── Build Vue 3 Frontend (TypeScript type-check)
    │
    └──► Job 2: GraalVM Native Image Build (11 phút)  [chạy sau Job 1]
         ├── Compile AOT với GraalVM Community 21
         ├── Output: ticketing-service binary 62.3MB
         └── Upload artifact (retention 7 days)
```

---

## Seed data có sẵn

Hệ thống tự động seed dữ liệu khi khởi động lần đầu:

**Phim:**
- Avengers: Doomsday (T13 · 148 phút)
- Lật Mặt 8: Vòng Tay Nắng (T16 · 125 phút)
- Minecraft: The Movie (P · 101 phút)

**Rạp:** FlashCinema Galaxy, 116 Nguyễn Du, Q.1, TP.HCM

**Phòng chiếu:**
- Phòng 1 — Standard 2D (80 chỗ) · Từ 85.000đ
- Phòng 2 — IMAX (60 chỗ) · Từ 120.000đ

**F&B:** Combo bắp nước, bắp phô mai, bắp caramel, nước ngọt

---

## Hướng dẫn deploy lên VPS (Production)

```bash
# 1. Tải native binary từ GitHub Actions artifact

# 2. Upload lên VPS
scp -i vps.pem ticketing-service user@server:~/

# 3. Chạy với môi trường production
export PAYMENT_PROVIDER=payos
export DB_HOST=your-postgres-host
export REDIS_HOST=your-redis-host
export NATS_URL=nats://your-nats-host:4222
./ticketing-service

# RAM tiêu thụ: ~50–60 MB (so với ~338MB khi chạy JVM thông thường)
# Khởi động: < 1 giây
```

---

## Tech Stack

| Thành phần | Công nghệ | Lý do chọn |
|------------|-----------|------------|
| Backend | Spring Boot 3.3 + Java 21 | Ecosystem mạnh, GraalVM AOT |
| In-Memory Lock | Redis 7.2 + Lua Script | Atomic, single-threaded, < 1ms |
| Message Queue | NATS JetStream | Lightweight, deduplication native, 3MB binary |
| Database | PostgreSQL 16 | JSONB, enum native, pg_isready |
| Runtime | GraalVM Native Image | 62MB binary, < 1s startup, ~50MB RAM |
| Frontend | Vue 3 + TypeScript + Tailwind | Reactive, type-safe |
| Realtime | WebSocket STOMP | Broadcast ghế instantly |
| Container | Docker / Podman Rootless | Bảo mật, không cần root |
| CI/CD | GitHub Actions | Native build, test automation |

---

*FlashTicket — Built for learning System Design under real constraints.*

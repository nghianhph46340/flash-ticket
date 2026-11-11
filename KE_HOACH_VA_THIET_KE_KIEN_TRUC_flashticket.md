# TÀI LIỆU THIẾT KẾ KIẾN TRÚC & QUY HOẠCH TOÀN DIỆN DỰ ÁN FLASHTICKET
**Hệ thống Đặt vé & Flash Sale Tải cao (High-Concurrency Ticketing System)**  
*Kiến trúc Cloud-Native siêu nhẹ tối ưu cho VPS 3–4GB RAM | NATS JetStream + Redis + Spring Boot GraalVM + Vue 3*

---

## 1. TỔNG QUAN HỆ THỐNG & ĐỊNH VỊ CHUYÊN MÔN

### 1.1. Bối cảnh & Bài toán nghiệp vụ
**FlashTicket** mô phỏng nền tảng phân phối vé hòa nhạc và sự kiện thể thao quy mô lớn (tương tự Ticketmaster/Ticketbox). Hệ thống giải quyết trực diện bài toán lưu lượng truy cập tăng vọt đột biến (traffic spike) tại thời điểm mở bán, khi hàng chục nghìn người dùng cùng tranh chấp một tập hợp tài nguyên giới hạn trong một khoảng thời gian cực ngắn.

### 1.2. Mục tiêu kỹ thuật cốt lõi
* **Zero Overselling (Không bán âm vé):** Đảm bảo tính toàn vẹn dữ liệu tuyệt đối ($100\%$) trong điều kiện xung đột tranh chấp (race condition) cao.
* **Độ trễ thấp (Sub-20ms Latency):** Phản hồi trạng thái giữ vé thành công/thất bại cho người dùng trong vòng dưới $20\text{ ms}$.
* **Khả năng chịu tải (Throughput):** Đạt ngưỡng $3.000 - 5.000+\text{ TPS}$ trên tài nguyên phần cứng giới hạn.
* **Cập nhật thời gian thực (Zero-Stale State):** Tự động đồng bộ trạng thái ghế (Trống $\rightarrow$ Đang giữ $\rightarrow$ Đã bán) qua WebSocket tới tất cả client đang mở màn hình.
* **Kiến trúc nhận thức tài nguyên (Resource-Aware Architecture):** Vận hành ổn định trên **VPS chỉ 3–4GB RAM**, giải phóng bộ nhớ để máy chủ sẵn sàng chạy đồng thời các dự án phụ trợ bằng C#/.NET Core và Rust.

---

## 2. THIẾT KẾ KIẾN TRÚC HẠ TẦNG TỐI ƯU TÀI NGUYÊN (FINOPS)

Hệ thống kết hợp giữa việc tối ưu hóa runtime nội bộ và offload tải sang các dịch vụ đám mây miễn phí (Free-Tier Edge Services).

### 2.1. Sơ đồ phân bổ tải tổng thể

```text
[Người Dùng Cuối] ──── (HTTPS / WSS) ────► [Cloudflare Edge Network]
                                                      │
         ┌────────────────────────────────────────────┼────────────────────────────────────────────┐
         ▼                                            ▼                                            ▼
[Frontend: Vue 3 + TS]                     [Media & Assets Storage]                    [Core Backend API]
Deploy: Vercel / Cloudflare Pages          Lưu trữ: Cloudflare R2                      VPS 3-4GB RAM (Ubuntu 24.04)
Chiếm dụng VPS: 0 MB RAM                   Chiếm dụng VPS: 0 MB RAM                    Spring Boot 3 (GraalVM Native)
                                           (Tương thích chuẩn S3 API)                  Mức ăn RAM: ~40MB - 60MB
```

### 2.2. Phân công trách nhiệm bộ ba dữ liệu: Redis vs NATS JetStream vs PostgreSQL

Thay vì dồn toàn bộ chức năng vào Redis hay sử dụng cụm Kafka cồng kềnh, hệ thống áp dụng nguyên lý tách biệt trách nhiệm (Separation of Concerns):

1. **Redis 7.2 (In-Memory Atomic State & Locking):**
   * Đóng vai trò là tấm khiên phòng ngự đầu tiên tại tầng ứng dụng.
   * Chạy **Lua Script** nguyên tử để kiểm tra và trừ tồn kho ghế trực tiếp trên RAM trong thời gian $\approx 2 - 5\text{ ms}$.
   * Quản lý TTL phiên giữ chỗ ($600\text{ giây}$). Không dùng để lưu trữ lâu dài.
2. **NATS JetStream (Cloud-Native Event Streaming Broker):**
   * Được viết hoàn toàn bằng Go, binary siêu nhẹ, chỉ chiếm **~35MB – 50MB RAM** (so với 1.2GB của Kafka).
   * Tiếp nhận các event giữ chỗ thành công từ Redis để thực hiện streaming bất đồng bộ.
   * Hỗ trợ **Message Deduplication** native dựa trên `Nats-Msg-Id` để loại bỏ trùng lặp giao dịch.
   * Cung cấp **Consumer Groups** phân phối tải cho các worker ghi dữ liệu và bắn WebSocket.
3. **PostgreSQL 16 (Durable Source of Truth):**
   * Lưu trữ dữ liệu quan hệ, lịch sử giao dịch đơn hàng, thông tin thanh toán và trạng thái vé chính thức.
   * Nhận dữ liệu bất đồng bộ từ NATS JetStream worker (Write-Behind Pattern), giải phóng DB khỏi áp lực nghẽn khóa dòng (Lock Contention) trong giờ cao điểm.

### 2.3. Bảng dự toán phân bổ RAM trên VPS 3–4GB

| Thành phần tiến trình | RAM dự kiến | Ghi chú tối ưu hóa |
|---|---|---|
| **OS Core Linux + Docker Daemon** | ~400MB – 450MB | Ubuntu LTS, cấu hình 4GB Swap file SSD |
| **PostgreSQL 16** | ~200MB – 250MB | Cấu hình `shared_buffers = 128MB`, `max_connections = 60` |
| **Redis 7.2** | ~60MB – 90MB | Chỉ lưu state vé tạm thời và cache danh mục |
| **NATS JetStream** | **~35MB – 50MB** | Chạy cờ `-js`, lưu trữ stream xuống thư mục file trên đĩa |
| **Backend API (GraalVM Native)** | **~40MB – 60MB** | Loại bỏ JVM, khởi động trong 0.08 giây |
| *(Dự án phụ: C# Minimal API)* | ~100MB – 150MB | Chạy độc lập trên cùng docker network |
| *(Dự án phụ: Rust Axum Service)* | ~25MB – 40MB | Chạy song song không tốn tài nguyên |
| **TỔNG TIÊU THỤ HỆ THỐNG** | **~860MB – 1.1GB** | **An toàn tuyệt đối trên VPS 3–4GB** |
| **RAM DỰ TRỮ PHÒNG NGỪA SPIKE** | **> 2.2GB RAM** | Đảm bảo hệ thống không bao giờ chạm ngưỡng OOM |

---

## 3. LUỒNG XỬ LÝ ĐẶT VÉ CHỊU TẢI CAO (CORE CONCURRENCY FLOW)

```text
[Vue 3 Client] 
      │ 
      ▼ (HTTP POST /api/v1/tickets/reserve)
[Spring Boot Native API]
      │
      ├── (1) Thực thi Redis Lua Script (Atomic Check & Lock)
      │         │
      │         ├──► [Ghế đã bị giữ/bán] ──► Trả về HTTP 409 Conflict (< 10ms)
      │         │
      │         └──► [Thành công] ──► Gán trạng thái 'HELD:{userId}' + TTL 600s
      │
      ├── (2) Publish Event sang NATS JetStream (Subject: "tickets.reserved")
      │         └──► Kèm Header "Nats-Msg-Id" để chống gửi lặp (Deduplication)
      │
      ▼ (3) Phản hồi Client HTTP 200 OK (Chuyển sang màn hình đếm ngược thanh toán)
      │
[NATS JetStream Engine]
      │
      ├──► [Consumer Group: Persistence-Worker]
      │         └──► Ghi đơn hàng 'PENDING' vào PostgreSQL (Batch Insert nếu tải cao)
      │
      ├──► [Consumer Group: Realtime-Broadcaster]
      │         └──► Đẩy frame STOMP qua WebSocket, đổi màu ghế sang Vàng trên client khác
      │
      └──► [Consumer Group: Expiration-Scheduler]
                └──► Đặt lịch tự động kiểm tra sau 10 phút để thu hồi ghế nếu chưa thanh toán
```

---

## 4. BẢN THIẾT KẾ CƠ SỞ DỮ LIỆU BAN ĐẦU (POSTGRESQL SCHEMA)

Toàn bộ lược đồ được cấu trúc hóa theo chuẩn 3NF, tích hợp đầy đủ khóa ngoại, ràng buộc toàn vẹn và B-Tree Index:

```sql
-- ============================================================================
-- FLASHTICKET DATABASE SCHEMA INITIALIZATION (V1__init_schema.sql)
-- ============================================================================

CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- 1. ĐỊNH NGHĨA CÁC ENUM TYPE
CREATE TYPE user_role AS ENUM ('CUSTOMER', 'ORGANIZER', 'ADMIN');
CREATE TYPE seat_status AS ENUM ('AVAILABLE', 'HELD', 'RESERVED', 'SOLD', 'BLOCKED');
CREATE TYPE order_status AS ENUM ('PENDING', 'PAID', 'EXPIRED', 'CANCELLED', 'REFUNDED');
CREATE TYPE payment_status AS ENUM ('PENDING', 'SUCCESS', 'FAILED', 'REFUNDED');
CREATE TYPE ticket_status AS ENUM ('VALID', 'CHECKED_IN', 'REVOKED');

-- 2. BẢNG USERS (Người dùng hệ thống)
CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    full_name VARCHAR(150) NOT NULL,
    phone_number VARCHAR(20),
    role user_role NOT NULL DEFAULT 'CUSTOMER',
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 3. BẢNG VENUES (Địa điểm tổ chức & Layout)
CREATE TABLE venues (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name VARCHAR(255) NOT NULL,
    address TEXT NOT NULL,
    city VARCHAR(100) NOT NULL,
    total_capacity INT NOT NULL CHECK (total_capacity > 0),
    seat_map_layout_url VARCHAR(500), -- File SVG/JSON lưu trên Cloudflare R2
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 4. BẢNG EVENTS (Thông tin sự kiện & Flash Sale)
CREATE TABLE events (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    venue_id UUID NOT NULL REFERENCES venues(id) ON DELETE RESTRICT,
    title VARCHAR(255) NOT NULL,
    slug VARCHAR(255) NOT NULL UNIQUE,
    description TEXT,
    poster_url VARCHAR(500),   -- URL Cloudflare R2
    banner_url VARCHAR(500),   -- URL Cloudflare R2
    trailer_url VARCHAR(500),  -- Embed link YouTube
    start_time TIMESTAMP WITH TIME ZONE NOT NULL,
    end_time TIMESTAMP WITH TIME ZONE NOT NULL,
    sales_start_time TIMESTAMP WITH TIME ZONE NOT NULL, -- Thời điểm mở bán vé
    sales_end_time TIMESTAMP WITH TIME ZONE NOT NULL,
    is_published BOOLEAN DEFAULT FALSE,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 5. BẢNG TICKET_TIERS (Hạng vé & Giá)
CREATE TABLE ticket_tiers (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    event_id UUID NOT NULL REFERENCES events(id) ON DELETE CASCADE,
    name VARCHAR(100) NOT NULL, -- VIP, Zone A, Standard...
    price NUMERIC(12, 2) NOT NULL CHECK (price >= 0),
    total_quantity INT NOT NULL CHECK (total_quantity >= 0),
    available_quantity INT NOT NULL CHECK (available_quantity >= 0),
    color_code VARCHAR(10) DEFAULT '#3B82F6',
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 6. BẢNG SEATS (Chi tiết vị trí ghế trên sơ đồ)
CREATE TABLE seats (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    venue_id UUID NOT NULL REFERENCES venues(id) ON DELETE CASCADE,
    ticket_tier_id UUID REFERENCES ticket_tiers(id) ON DELETE SET NULL,
    section VARCHAR(50) NOT NULL,
    row_num VARCHAR(10) NOT NULL,
    seat_num VARCHAR(10) NOT NULL,
    coordinate_x DOUBLE PRECISION,  -- Tọa độ render trên SVG map
    coordinate_y DOUBLE PRECISION,
    status seat_status NOT NULL DEFAULT 'AVAILABLE',
    reservation_expires_at TIMESTAMP WITH TIME ZONE,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_seat_location UNIQUE (venue_id, section, row_num, seat_num)
);

-- 7. BẢNG ORDERS (Đơn hàng giao dịch)
CREATE TABLE orders (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    order_code VARCHAR(32) NOT NULL UNIQUE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    event_id UUID NOT NULL REFERENCES events(id) ON DELETE RESTRICT,
    total_amount NUMERIC(12, 2) NOT NULL CHECK (total_amount >= 0),
    status order_status NOT NULL DEFAULT 'PENDING',
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL, -- Thời điểm hết hạn giữ ghế (now + 10m)
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 8. BẢNG ORDER_ITEMS (Chi tiết ghế thuộc đơn hàng)
CREATE TABLE order_items (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    order_id UUID NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    seat_id UUID NOT NULL REFERENCES seats(id) ON DELETE RESTRICT,
    ticket_tier_id UUID NOT NULL REFERENCES ticket_tiers(id) ON DELETE RESTRICT,
    price NUMERIC(12, 2) NOT NULL,
    CONSTRAINT uq_order_seat UNIQUE (order_id, seat_id)
);

-- 9. BẢNG PAYMENTS (Lịch sử thanh toán VietQR / PayOS)
CREATE TABLE payments (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    order_id UUID NOT NULL REFERENCES orders(id) ON DELETE RESTRICT,
    transaction_code VARCHAR(100) UNIQUE,
    payment_method VARCHAR(50) NOT NULL,
    amount NUMERIC(12, 2) NOT NULL,
    status payment_status NOT NULL DEFAULT 'PENDING',
    idempotency_key VARCHAR(100) NOT NULL UNIQUE, -- Chống xử lý trùng webhook
    payload JSONB,
    paid_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 10. BẢNG TICKETS (Vé điện tử chính thức)
CREATE TABLE tickets (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    ticket_code VARCHAR(50) NOT NULL UNIQUE,
    order_id UUID NOT NULL REFERENCES orders(id) ON DELETE RESTRICT,
    seat_id UUID NOT NULL REFERENCES seats(id) ON DELETE RESTRICT,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    event_id UUID NOT NULL REFERENCES events(id) ON DELETE RESTRICT,
    qr_hash TEXT NOT NULL UNIQUE, -- Hash bí mật HMAC để Vue client tự sinh QR
    status ticket_status NOT NULL DEFAULT 'VALID',
    checkin_time TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 11. BẢNG TRANSACTIONAL OUTBOX (Đảm bảo At-least-once Delivery)
CREATE TABLE outbox_events (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    aggregate_type VARCHAR(100) NOT NULL,
    aggregate_id VARCHAR(100) NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    payload JSONB NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    processed_at TIMESTAMP WITH TIME ZONE,
    is_processed BOOLEAN DEFAULT FALSE
);

-- 12. CHỈ MỤC INDEX TỐI ƯU TRUY VẤN
CREATE INDEX idx_events_sales_window ON events (sales_start_time, sales_end_time);
CREATE INDEX idx_seats_lookup ON seats (venue_id, status);
CREATE INDEX idx_orders_user ON orders (user_id, status);
CREATE INDEX idx_orders_expiration ON orders (expires_at) WHERE status = 'PENDING';
CREATE INDEX idx_outbox_unprocessed ON outbox_events (created_at) WHERE is_processed = FALSE;
CREATE INDEX idx_tickets_verification ON tickets (ticket_code, qr_hash);
```

---

## 5. THIẾT KẾ CONCURRENCY ENGINE & TÍCH HỢP NATS JETSTREAM

### 5.1. Redis Lua Script Trừ Vé Nguyên Tử (`reserve_seat.lua`)

```lua
-- KEYS[1]: event seat map hash (vd: "event:101:seats")
-- KEYS[2]: event expiration zset (vd: "event:101:expirations")
-- ARGV[1]: userId
-- ARGV[2]: expireTimestamp (Unix timestamp giây)
-- ARGV[3...N]: Danh sách ID ghế cần giữ chỗ

local seatHashKey = KEYS[1]
local expirationZSetKey = KEYS[2]
local userId = ARGV[1]
local expireTime = tonumber(ARGV[2])

-- 1. Kiểm tra trạng thái toàn bộ danh sách ghế trước khi thao tác
for i = 3, #ARGV do
    local currentStatus = redis.call('HGET', seatHashKey, ARGV[i])
    if currentStatus ~= 'AVAILABLE' and currentStatus ~= false then
        -- Có ít nhất 1 ghế không hợp lệ -> Hủy toàn bộ giao dịch ngay lập tức
        return {0, ARGV[i]} 
    end
end

-- 2. Khi tất cả ghế đều trống, thực hiện khóa đồng loạt
for i = 3, #ARGV do
    local seatId = ARGV[i]
    redis.call('HSET', seatHashKey, seatId, 'HELD:' .. userId)
    redis.call('ZADD', expirationZSetKey, expireTime, seatId)
end

return {1, 'SUCCESS'}
```

### 5.2. Mã nguồn Java tích hợp NATS JetStream với Deduplication

Khai báo dependency trong `pom.xml`:
```xml
<dependency>
    <groupId>io.nats</groupId>
    <artifactId>jnats</artifactId>
    <version>2.20.0</version>
</dependency>
```

Class phát tán sự kiện (Publisher) với cơ chế chống trùng lặp tin nhắn:
```java
package com.ticket.infrastructure.adapter.out.nats;

import io.nats.client.Connection;
import io.nats.client.JetStream;
import io.nats.client.impl.Headers;
import io.nats.client.impl.NatsMessage;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Component
public class NatsTicketEventPublisher {

    private final Connection natsConnection;

    public NatsTicketEventPublisher(Connection natsConnection) {
        this.natsConnection = natsConnection;
    }

    public void publishTicketReservedEvent(String orderCode, String jsonPayload) {
        try {
            JetStream js = natsConnection.jetStream();

            // Thiết lập Nats-Msg-Id: NATS tự động drop message nếu cùng ID trong deduplication window
            Headers headers = new Headers();
            headers.add("Nats-Msg-Id", "ORD-RESERVE-" + orderCode);

            io.nats.client.Message msg = NatsMessage.builder()
                    .subject("tickets.reserved")
                    .headers(headers)
                    .data(jsonPayload.getBytes(StandardCharsets.UTF_8))
                    .build();

            js.publishAsync(msg);
        } catch (Exception e) {
            throw new RuntimeException("Lỗi phát tán sự kiện sang NATS JetStream", e);
        }
    }
}
```

---

## 6. ĐÓNG GÓI GRAALVM NATIVE IMAGE (RAM DƯỚI 50MB)

### 6.1. Cấu hình Maven Plugin trong `pom.xml`

```xml
<profiles>
    <profile>
        <id>native</id>
        <build>
            <plugins>
                <plugin>
                    <groupId>org.graalvm.buildtools</groupId>
                    <artifactId>native-maven-plugin</artifactId>
                    <configuration>
                        <buildArgs>
                            <buildArg>-J-Xmx6g</buildArg>
                            <buildArg>--no-fallback</buildArg>
                            <buildArg>-march=compatibility</buildArg>
                        </buildArgs>
                    </configuration>
                    <executions>
                        <execution>
                            <id>build-native</id>
                            <goals>
                                <goal>compile-no-fork</goal>
                            </goals>
                            <phase>package</phase>
                        </execution>
                    </executions>
                </plugin>
            </plugins>
        </build>
    </profile>
</profiles>
```

### 6.2. Multi-Stage Dockerfile hoàn chỉnh

```dockerfile
# ==========================================
# STAGE 1: Builder (Thực hiện trên máy Local / GitHub Actions)
# ==========================================
FROM ghcr.io/graalvm/native-image-community:21 AS builder

WORKDIR /build

RUN microdnf install -y findutils gcc glibc-devel zlib-devel

COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .

RUN chmod +x mvnw
RUN ./mvnw dependency:go-offline -B

COPY src src

# Biên dịch tĩnh AOT ra mã máy nhị phân
RUN ./mvnw -Pnative native:compile -DskipTests -B

# ==========================================
# STAGE 2: Distroless Minimal Runtime (Dưới 50MB RAM)
# ==========================================
FROM gcr.io/distroless/base-debian12:nonroot

WORKDIR /app

# Copy duy nhất file binary sang image không chứa JVM
COPY --from=builder /build/target/ticketing-service /app/application

EXPOSE 8080

USER nonroot:nonroot

ENTRYPOINT ["/app/application"]
```

---

## 7. KIẾN TRÚC FRONTEND VUE 3 + TYPESCRIPT (INTERACTIVE SVG SEAT MAP)

### 7.1. Cấu trúc thư mục nguồn Frontend

```text
ticketing-frontend/
├── src/
│   ├── assets/                     # Tailwind CSS direct, SVGs tĩnh
│   ├── components/
│   │   ├── ui/                     # Shadcn-Vue (Button, Dialog, Badge, Tooltip)
│   │   ├── seatmap/
│   │   │   ├── InteractiveSeatMap.vue # Khung SVG hỗ trợ pan, zoom và ma trận ghế
│   │   │   ├── SeatItem.vue        # Component từng ghế (màu sắc, click event)
│   │   │   └── SeatLegend.vue      # Chú thích: Ghế VIP, Thường, Đang giữ, Đã bán
│   │   └── checkout/
│   │       ├── CountdownClock.vue  # Đồng hồ đếm ngược 10 phút giữ chỗ
│   │       └── QRPaymentModal.vue  # Render mã VietQR từ hash trên Canvas
│   ├── composables/
│   │   ├── useSeatMap.ts           # State tính toán tọa độ và danh sách chọn
│   │   ├── useTicketSocket.ts      # Quản lý kênh STOMP/WebSocket hai chiều
│   │   └── useCountdown.ts         # Quản lý timer và trigger giải phóng ghế
│   ├── stores/
│   │   └── useBookingStore.ts      # Pinia store lưu giỏ vé tạm thời
│   ├── services/
│   │   └── api.ts                  # Axios client với Interceptors
│   ├── views/
│   │   ├── EventListView.vue       # Danh sách sự kiện & Đồng hồ Flash sale
│   │   ├── SeatSelectionView.vue   # Màn hình tương tác chọn chỗ
│   │   └── CheckoutView.vue        # Xác nhận đơn và cổng thanh toán
│   ├── App.vue
│   └── main.ts
```

### 7.2. Luồng Optimistic UI kết hợp STOMP WebSocket

1. **Thao tác chọn:** Người dùng nhấp vào một chiếc ghế trống. `SeatItem.vue` lập tức đổi màu sang cam (*Đang chọn*) và ghi vào Pinia store trước khi gửi API (Optimistic Update).
2. **Xác nhận giữ chỗ:** Bấm "Giữ vé" $\rightarrow$ Gửi request `/api/v1/tickets/reserve`. Nếu nhận lỗi 409 (bị mua trước), component lập tức chuyển sang màu xám và hiện thông báo Toast.
3. **Phát tín hiệu Realtime:** Khi backend xác nhận qua NATS JetStream, WebSocket STOMP bắn thông điệp tới topic `/topic/event/{id}/seats`. Toàn bộ các client khác trên hệ thống lập tức thấy các vị trí đó chuyển thành màu vàng (*Đang bị người khác giữ*) mà không cần F5 trình duyệt.

---

## 8. LỘ TRÌNH THỰC THI DỰ ÁN TRONG 6 TUẦN (WEEKLY MILESTONES)

```text
[Tuần 1] Khởi tạo Hạ tầng, Docker Compose (NATS + Redis + Postgres), DB Schema Flyway
   │
[Tuần 2] Xây dựng Engine Concurrency: Redis Lua Script, Testcontainers Race Condition Test
   │
[Tuần 3] Luồng NATS JetStream: Stream Subject, Consumer Groups, Xử lý Deduplication & Webhook
   │
[Tuần 4] Frontend Vue 3: Sơ đồ ghế SVG tương tác, WebSocket STOMP Sync, Countdown Timer
   │
[Tuần 5] Load Testing k6 (5.000 - 10.000 VUs), Tối ưu Connection Pool, Chứng minh Zero Overselling
   │
[Tuần 6] CI/CD GitHub Actions Build GraalVM, Deploy VPS + Vercel + R2, Hoàn thiện Portfolio
```

* **Tuần 1: Thiết lập kiến trúc & Schema Database**
  * Dựng file `docker-compose.yml` local: PostgreSQL 16, Redis 7.2, NATS JetStream, MinIO.
  * Viết script Flyway migration tạo đầy đủ 12 bảng và indexes.
  * Khởi tạo dự án Vue 3 + Vite + Tailwind + TypeScript.
* **Tuần 2: Xây dựng Concurrency Engine & Kiểm thử Race Condition**
  * Cài đặt script Lua nguyên tử trên Redis.
  * Viết test tích hợp bằng **Testcontainers**: Giả lập 100 threads đồng thời tranh mua 1 ghế, xác minh chỉ 1 thread thành công.
* **Tuần 3: Tích hợp NATS JetStream & Cổng thanh toán**
  * Cấu hình NATS Stream `TICKETS` với subject `tickets.*`.
  * Viết worker tiêu thụ event để ghi đơn hàng vào PostgreSQL và đẩy thông báo WebSocket.
  * Cài đặt tác vụ định kỳ quét và thu hồi ghế quá hạn 10 phút.
  * Tích hợp Webhook cổng thanh toán PayOS (Sandbox).
* **Tuần 4: Xây dựng Giao diện Frontend tương tác cao**
  * Hoàn thiện component SVG tương tác chọn ghế, hỗ trợ zoom/pan mượt mà.
  * Kết nối WebSocket STOMP nhận tín hiệu đồng bộ đổi màu ghế.
  * Tích hợp component render mã VietQR tự động trên trình duyệt.
* **Tuần 5: Load Testing & Đo lường hiệu năng**
  * Viết kịch bản test chịu tải bằng **k6**: Mô phỏng $5.000 - 10.000$ VUs đồng loạt gửi request giữ vé.
  * Kiểm tra DB: Xác nhận $100\%$ không bị bán âm vé, không sinh race condition.
  * Tối ưu HikariCP connection pool và chỉ số latency P95/P99.
* **Tuần 6: Tối ưu đóng gói, Triển khai & Hoàn thiện CV**
  * Viết workflow GitHub Actions build GraalVM Native Image và push lên GHCR.
  * Deploy Backend lên VPS (giới hạn RAM 80MB), Frontend lên Cloudflare Pages/Vercel, Media lên Cloudflare R2.
  * Viết file `README.md` chuyên nghiệp: Sơ đồ kiến trúc Mermaid, video demo GIF, biểu đồ benchmark k6.

---

## 9. CẤU HÌNH DOCKER COMPOSE MÔI TRƯỜNG LOCAL DEVELOPMENT

File cấu hình khởi chạy toàn bộ hạ tầng backend tại môi trường máy phát triển cá nhân:

```yaml
version: '3.8'

services:
  # 1. Cơ sở dữ liệu quan hệ
  postgres:
    image: postgres:16-alpine
    container_name: flashticket-postgres
    environment:
      POSTGRES_DB: flashticket_db
      POSTGRES_USER: ticket_admin
      POSTGRES_PASSWORD: secure_password_123
    ports:
      - "5432:5432"
    volumes:
      - postgres_data:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U ticket_admin -d flashticket_db"]
      interval: 5s
      timeout: 5s
      retries: 5

  # 2. In-Memory Atomic Lock & Fast Cache
  redis:
    image: redis:7.2-alpine
    container_name: flashticket-redis
    ports:
      - "6379:6379"
    command: ["redis-server", "--appendonly", "yes"]
    volumes:
      - redis_data:/data

  # 3. Cloud-Native Message Streaming Broker
  nats:
    image: nats:latest
    container_name: flashticket-nats
    command: ["-js", "-sd", "/data"] # Kích hoạt JetStream với file persistence
    ports:
      - "4222:4222"   # Client communication port
      - "8222:8222"   # HTTP Monitoring Dashboard port
    volumes:
      - nats_data:/data

  # 4. Mock Object Storage tại local (Môi trường production dùng trực tiếp Cloudflare R2)
  minio:
    image: minio/minio:RELEASE.2024-01-18T22-51-28Z
    container_name: flashticket-minio
    ports:
      - "9000:9000"
      - "9001:9001"
    environment:
      MINIO_ROOT_USER: minio_admin
      MINIO_ROOT_PASSWORD: minio_password_123
    command: server /data --console-address ":9001"
    volumes:
      - minio_data:/data

volumes:
  postgres_data:
  redis_data:
  nats_data:
  minio_data:
```
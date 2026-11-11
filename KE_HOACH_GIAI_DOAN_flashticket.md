# TÀI LIỆU QUY HOẠCH KỸ THUẬT & KẾ HOẠCH TRIỂN KHAI TOÀN DIỆN DỰ ÁN FLASHTICKET

**Dự án:** Hệ thống Đặt vé & Flash Sale Tải cao (High-Concurrency Ticketing System)  
**Kiến trúc:** Cloud-Native Modular Monolith | Event-Driven với NATS JetStream  
**Hạ tầng:** Podman Rootless trên VPS 3–4GB RAM (Debian 13) | GraalVM Native Image | Vue 3 SPA  
**Tác giả & Kiến trúc sư:** Nguyễn Hữu Nghĩa  

---

## MỤC LỤC
1. [Tổng Quan Nghiệp Vụ & Định Vị Năng Lực Kỹ Sư](#1-tổng-quan-nghiệp-vụ--định-vị-năng-lực-kỹ-sư)
2. [Kiến Trúc Hạ Tầng Tối Ưu Tài Nguyên (FinOps Trên VPS 3–4GB)](#2-kiến-trúc-hạ-tầng-tối-ưu-tài-nguyên-finops-trên-vps-34gb)
3. [Thiết Lập Hạ Tầng Podman Rootless Trên Debian 13](#3-thiết-lập-hạ-tầng-podman-rootless-trên-debian-13)
4. [Lược Đồ Cơ Sở Dữ Liệu PostgreSQL (12 Bảng & Index)](#4-lược-đồ-cơ-sở-dữ-liệu-postgresql-12-bảng--index)
5. [Tầng Lõi Concurrency: Redis Lua Script & NATS JetStream](#5-tầng-lõi-concurrency-redis-lua-script--nats-jetstream)
6. [Đóng Gói GraalVM Native Image (Dưới 50MB RAM)](#6-đóng-gói-graalvm-native-image-dưới-50mb-ram)
7. [Kiến Trúc Frontend Vue 3, Interactive SVG Seat Map & Real-time State](#7-kiến-trúc-frontend-vue-3-interactive-svg-seat-map--real-time-state)
8. [Kế Hoạch Triển Khai Chi Tiết 5 Giai Đoạn (Lộ Trình 6 Tuần)](#8-kế-hoạch-triển-khai-chi-tiết-5-giai-đoạn-lộ-trình-6-tuần)
9. [Kịch Bản Kiểm Thử Chịu Tải k6 & Tiêu Chuẩn Nghiệm Thu](#9-kịch-bản-kiểm-thử-chịu-tải-k6--tiêu-chuẩn-nghiệm-thu)

---

## 1. TỔNG QUAN NGHIỆP VỤ & ĐỊNH VỊ NĂNG LỰC KỸ SƯ

### 1.1. Bối cảnh bài toán
FlashTicket là nền tảng bán vé trực tuyến chuyên biệt cho các concert ca nhạc, sự kiện thể thao và flash sale giới hạn. Nghiệp vụ cốt lõi không phân tán vào hàng chục tính năng quản trị phụ trợ, mà tập trung giải quyết bài toán: **Hàng chục nghìn người dùng đồng loạt truy cập tại một thời điểm chính xác (giờ mở bán) để tranh chấp một số lượng ghế ngồi cố định và có hạn.**

### 1.2. Các bài toán kỹ thuật cần chứng minh trên CV Fullstack
* **Zero Overselling (Bán âm vé = 0):** Loại bỏ hoàn toàn Race Condition khi hàng nghìn luồng đồng thời tranh chấp cùng 1 chiếc ghế.
* **Xử lý bất đồng bộ phân tán (Event-Driven Architecture):** Tách rời tầng tiếp nhận request (In-Memory) khỏi tầng ghi đĩa (Database) bằng NATS JetStream với cơ chế Deduplication.
* **Distributed Transactions & Compensating Logic:** Quản lý vòng đời giữ vé (TTL 10 phút), tự động giải phóng ghế khi hết hạn thanh toán theo mô hình Saga.
* **Tối ưu hóa tài nguyên phần cứng (Resource-Aware Engineering):** Vận hành toàn bộ hệ sinh thái tải cao trên **VPS chỉ 3–4GB RAM**, không để sập tiến trình, sẵn sàng tài nguyên cho các dự án C# và Rust tiếp theo.
* **Frontend Real-time State Management:** Dựng sơ đồ ghế SVG hàng trăm vị trí, cập nhật trạng thái thời gian thực qua WebSocket mà không bị re-render thắt cổ chai.

---

## 2. KIẾN TRÚC HẠ TẦNG TỐI ƯU TÀI NGUYÊN (FINOPS TRÊN VPS 3–4GB)

### 2.1. Mô hình phân tách tải (Edge Offloading)
Để chạy được nhiều hệ thống trên một VPS 3–4GB RAM, toàn bộ thành phần tĩnh và media được đẩy ra rìa mạng (Edge):

```text
                        [Client Web Browser]
                                 │
           ┌─────────────────────┴─────────────────────┐
           │ (HTTPS Static Files)                      │ (WSS / REST API)
           ▼                                           ▼
[Cloudflare Pages / Vercel]                [Cloudflare Proxy / CDN]
• Hosting Vue 3 SPA                        • SSL Termination, DDoS Shield
• Chiếm dụng VPS: 0 MB RAM                 • Rate Limiting tầng mạng
                                                       │
                                                       ▼
                                            [VPS Debian 13 (Trixie)]
                                            (Chạy Podman Rootless)
                                                       │
                   ┌───────────────────────────────────┼───────────────────────────────────┐
                   ▼                                   ▼                                   ▼
          [Core Backend API]                 [NATS JetStream]                      [Data Layer]
          Spring Boot Native (AOT)           Streaming Broker (Go)                 • Redis 7 (In-Memory)
          RAM: ~40MB - 60MB                  RAM: ~35MB - 50MB                     • PostgreSQL 16
                                                                                   RAM: ~250MB - 300MB
```

### 2.2. Bảng phân bổ dung lượng RAM chi tiết trên VPS Debian 13

| Tiến trình / Dịch vụ | Công nghệ | Mức RAM ước tính | Vai trò kiến trúc |
|---|---|---|---|
| **Hệ điều hành + Podman** | **Debian 13 (cgroups v2, không snapd)** | **~200MB – 250MB** | Tối giản, ổn định cao, tiết kiệm RAM hơn Ubuntu ~150MB |
| **PostgreSQL 16** | Container (Alpine) | ~200MB – 250MB | Durable DB (`shared_buffers = 128MB`) |
| **Redis 7** | Container (Alpine) | ~60MB – 90MB | In-memory atomic locking & fast cache |
| **NATS JetStream** | Container (Official Go binary) | ~35MB – 50MB | Streaming broker, persistence on disk |
| **Backend Service** | Spring Boot 3.3 (GraalVM Native) | ~40MB – 60MB | Business logic, REST, WebSocket |
| **Dự án C# phụ (Tương lai)** | ASP.NET Core 8 Minimal API | ~100MB – 150MB | Microservice độc lập trên cùng network |
| **Dự án Rust phụ (Tương lai)** | Rust (Axum / Actix-web) | ~25MB – 40MB | Service hiệu năng cao xử lý tác vụ nền |
| **TỔNG TIÊU THỤ DỰ KIẾN** | — | **~660MB – 900MB** | **Chưa tới 25% tổng tài nguyên VPS 3–4GB** |
| **BỘ NHỚ DỰ TRỮ (BUFFER)** | **Swap có sẵn 2GB + Free RAM** | **> 2.5GB RAM** | Hấp thụ thoải mái các đợt lưu lượng đột biến |

---

## 3. THIẾT LẬP HẠ TẦNG PODMAN ROOTLESS TRÊN DEBIAN 13

### 3.1. Cấu hình hệ điều hành Host (Debian 13)

Debian 13 sử dụng `cgroups v2` mặc định. Các bước cấu hình tối ưu tài nguyên và quyền rootless:

```bash
# 1. Cài đặt Podman, công cụ compose và các gói phụ trợ
sudo apt update && sudo apt install -y podman podman-compose uidmap slirp4netns curl

# 2. Tối ưu thông số Swappiness cho phân vùng Swap 2GB hiện có
# Giúp hệ điều hành ưu tiên RAM vật lý, chỉ swap khi dung lượng thực tế chạm ngưỡng nguy hiểm
sudo sysctl vm.swappiness=10
sudo sysctl vm.vfs_cache_pressure=50
echo 'vm.swappiness=10' | sudo tee -a /etc/sysctl.d/99-memory-tuning.conf
echo 'vm.vfs_cache_pressure=50' | sudo tee -a /etc/sysctl.d/99-memory-tuning.conf

# 3. Cho phép người dùng thường mở port từ 80 trở lên mà không cần quyền root
sudo sysctl net.ipv4.ip_unprivileged_port_start=80
echo 'net.ipv4.ip_unprivileged_port_start=80' | sudo tee -a /etc/sysctl.d/99-podman-ports.conf

# 4. Kích hoạt Podman User Socket (cho Dev Tools & Testcontainers)
systemctl --user enable --now podman.socket
loginctl enable-linger $USER
```

### 3.2. File `podman-compose.yml` hoàn chỉnh

```yaml
version: '3.8'

services:
  postgres:
    image: docker.io/library/postgres:16-alpine
    container_name: flashticket-postgres
    restart: always
    environment:
      POSTGRES_DB: flashticket_db
      POSTGRES_USER: ticket_admin
      POSTGRES_PASSWORD: secure_password_123
    ports:
      - "127.0.0.1:5432:5432"
    volumes:
      - ./infra/postgres/data:/var/lib/postgresql/data:Z
    command: postgres -c shared_buffers=128MB -c max_connections=60 -c work_mem=4MB
    networks:
      - ticket-net

  redis:
    image: docker.io/library/redis:7-alpine
    container_name: flashticket-redis
    restart: always
    command: redis-server --appendonly yes --maxmemory 120mb --maxmemory-policy noeviction
    ports:
      - "127.0.0.1:6379:6379"
    volumes:
      - ./infra/redis/data:/data:Z
    networks:
      - ticket-net

  nats:
    image: docker.io/library/nats:latest
    container_name: flashticket-nats
    restart: always
    command: ["-js", "-sd", "/data"]
    ports:
      - "127.0.0.1:4222:4222"   # NATS client port
      - "127.0.0.1:8222:8222"   # HTTP monitoring dashboard
    volumes:
      - ./infra/nats/data:/data:Z
    networks:
      - ticket-net

networks:
  ticket-net:
    driver: bridge
```
*(Ghi chú: Cờ `:Z` ở phần volumes đảm bảo Podman tự động cấu hình SELinux labeling chính xác trên thư mục lưu trữ).*

---

## 4. LƯỢC ĐỒ CƠ SỞ DỮ LIỆU POSTGRESQL (12 BẢNG & INDEX)

Lược đồ được thiết kế theo chuẩn 3NF, phân tách rõ ràng giữa cấu trúc khán phòng, sự kiện, giao dịch giữ chỗ, thanh toán và vé điện tử chính thức:

```sql
-- ============================================================================
-- FLASHTICKET DATABASE INITIAL SCHEMA (V1__init_schema.sql)
-- ============================================================================

CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- 1. ENUM TYPES
CREATE TYPE user_role AS ENUM ('CUSTOMER', 'ORGANIZER', 'ADMIN');
CREATE TYPE seat_status AS ENUM ('AVAILABLE', 'HELD', 'RESERVED', 'SOLD', 'BLOCKED');
CREATE TYPE order_status AS ENUM ('PENDING', 'PAID', 'EXPIRED', 'CANCELLED', 'REFUNDED');
CREATE TYPE payment_status AS ENUM ('PENDING', 'SUCCESS', 'FAILED', 'REFUNDED');
CREATE TYPE ticket_status AS ENUM ('VALID', 'CHECKED_IN', 'REVOKED');

-- 2. USERS
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

-- 3. VENUES
CREATE TABLE venues (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name VARCHAR(255) NOT NULL,
    address TEXT NOT NULL,
    city VARCHAR(100) NOT NULL,
    total_capacity INT NOT NULL CHECK (total_capacity > 0),
    seat_map_layout_url VARCHAR(500), -- JSON/SVG file on Cloudflare R2
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 4. EVENTS
CREATE TABLE events (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    venue_id UUID NOT NULL REFERENCES venues(id) ON DELETE RESTRICT,
    title VARCHAR(255) NOT NULL,
    slug VARCHAR(255) NOT NULL UNIQUE,
    description TEXT,
    poster_url VARCHAR(500),
    banner_url VARCHAR(500),
    trailer_url VARCHAR(500), -- YouTube URL
    start_time TIMESTAMP WITH TIME ZONE NOT NULL,
    end_time TIMESTAMP WITH TIME ZONE NOT NULL,
    sales_start_time TIMESTAMP WITH TIME ZONE NOT NULL,
    sales_end_time TIMESTAMP WITH TIME ZONE NOT NULL,
    is_published BOOLEAN DEFAULT FALSE,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 5. TICKET_TIERS
CREATE TABLE ticket_tiers (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    event_id UUID NOT NULL REFERENCES events(id) ON DELETE CASCADE,
    name VARCHAR(100) NOT NULL,
    price NUMERIC(12, 2) NOT NULL CHECK (price >= 0),
    total_quantity INT NOT NULL CHECK (total_quantity >= 0),
    available_quantity INT NOT NULL CHECK (available_quantity >= 0),
    color_code VARCHAR(10) DEFAULT '#3B82F6',
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 6. SEATS
CREATE TABLE seats (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    venue_id UUID NOT NULL REFERENCES venues(id) ON DELETE CASCADE,
    ticket_tier_id UUID REFERENCES ticket_tiers(id) ON DELETE SET NULL,
    section VARCHAR(50) NOT NULL,
    row_num VARCHAR(10) NOT NULL,
    seat_num VARCHAR(10) NOT NULL,
    coordinate_x DOUBLE PRECISION,
    coordinate_y DOUBLE PRECISION,
    status seat_status NOT NULL DEFAULT 'AVAILABLE',
    reservation_expires_at TIMESTAMP WITH TIME ZONE,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_seat_location UNIQUE (venue_id, section, row_num, seat_num)
);

-- 7. ORDERS
CREATE TABLE orders (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    order_code VARCHAR(32) NOT NULL UNIQUE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    event_id UUID NOT NULL REFERENCES events(id) ON DELETE RESTRICT,
    total_amount NUMERIC(12, 2) NOT NULL CHECK (total_amount >= 0),
    status order_status NOT NULL DEFAULT 'PENDING',
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 8. ORDER_ITEMS
CREATE TABLE order_items (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    order_id UUID NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    seat_id UUID NOT NULL REFERENCES seats(id) ON DELETE RESTRICT,
    ticket_tier_id UUID NOT NULL REFERENCES ticket_tiers(id) ON DELETE RESTRICT,
    price NUMERIC(12, 2) NOT NULL,
    CONSTRAINT uq_order_seat UNIQUE (order_id, seat_id)
);

-- 9. PAYMENTS
CREATE TABLE payments (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    order_id UUID NOT NULL REFERENCES orders(id) ON DELETE RESTRICT,
    transaction_code VARCHAR(100) UNIQUE,
    payment_method VARCHAR(50) NOT NULL,
    amount NUMERIC(12, 2) NOT NULL,
    status payment_status NOT NULL DEFAULT 'PENDING',
    idempotency_key VARCHAR(100) NOT NULL UNIQUE,
    payload JSONB,
    paid_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 10. TICKETS
CREATE TABLE tickets (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    ticket_code VARCHAR(50) NOT NULL UNIQUE,
    order_id UUID NOT NULL REFERENCES orders(id) ON DELETE RESTRICT,
    seat_id UUID NOT NULL REFERENCES seats(id) ON DELETE RESTRICT,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    event_id UUID NOT NULL REFERENCES events(id) ON DELETE RESTRICT,
    qr_hash TEXT NOT NULL UNIQUE,
    status ticket_status NOT NULL DEFAULT 'VALID',
    checkin_time TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 11. TRANSACTIONAL OUTBOX
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

-- 12. INDEXES
CREATE INDEX idx_events_sales_window ON events (sales_start_time, sales_end_time);
CREATE INDEX idx_seats_lookup ON seats (venue_id, status);
CREATE INDEX idx_orders_user ON orders (user_id, status);
CREATE INDEX idx_orders_expiration ON orders (expires_at) WHERE status = 'PENDING';
CREATE INDEX idx_outbox_unprocessed ON outbox_events (created_at) WHERE is_processed = FALSE;
CREATE INDEX idx_tickets_verification ON tickets (ticket_code, qr_hash);
```

---

## 5. TẦNG LÕI CONCURRENCY: REDIS LUA SCRIPT & NATS JETSTREAM

### 5.1. Redis Lua Script Trừ Vé Nguyên Tử (`reserve_seat.lua`)

Script thực hiện kiểm tra điều kiện và giữ toàn bộ danh sách ghế trong duy nhất 1 atomic roundtrip:

```lua
-- KEYS[1]: event seat map hash (vd: "event:e101:seats")
-- KEYS[2]: event expiration zset (vd: "event:e101:expirations")
-- ARGV[1]: userId
-- ARGV[2]: expireTimestamp (Unix timestamp in seconds)
-- ARGV[3...N]: List of seat IDs to reserve

local seatHashKey = KEYS[1]
local expirationZSetKey = KEYS[2]
local userId = ARGV[1]
local expireTime = tonumber(ARGV[2])

-- 1. Kiểm tra tính khả dụng của toàn bộ ghế được yêu cầu
for i = 3, #ARGV do
    local seatId = ARGV[i]
    local currentStatus = redis.call('HGET', seatHashKey, seatId)
    if currentStatus ~= 'AVAILABLE' and currentStatus ~= false then
        -- Có ít nhất 1 ghế đã bị giữ hoặc bán -> Báo lỗi xung đột ngay
        return {0, seatId}
    end
end

-- 2. Nếu tất cả đều trống -> Khóa đồng loạt
for i = 3, #ARGV do
    local seatId = ARGV[i]
    redis.call('HSET', seatHashKey, seatId, 'HELD:' .. userId)
    redis.call('ZADD', expirationZSetKey, expireTime, seatId)
end

return {1, 'SUCCESS'}
```

### 5.2. NATS JetStream Publisher với Message Deduplication

Mã nguồn Java sử dụng thư viện chính thức `jnats`, gắn header `Nats-Msg-Id` để đảm bảo idempotent stream:

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

    public void publishReservationEvent(String orderCode, String jsonPayload) {
        try {
            JetStream js = natsConnection.jetStream();

            // Gắn Nats-Msg-Id để NATS tự động loại bỏ message trùng trong DuplicateWindow
            Headers headers = new Headers();
            headers.add("Nats-Msg-Id", "RESERVE-ORD-" + orderCode);

            io.nats.client.Message msg = NatsMessage.builder()
                    .subject("tickets.reserved")
                    .headers(headers)
                    .data(jsonPayload.getBytes(StandardCharsets.UTF_8))
                    .build();

            // Publish bất đồng bộ, không block thread xử lý HTTP
            js.publishAsync(msg);
        } catch (Exception e) {
            throw new RuntimeException("Lỗi phát tán event sang NATS JetStream", e);
        }
    }
}
```

---

## 6. ĐÓNG GÓI GRAALVM NATIVE IMAGE (DƯỚI 50MB RAM)

### 6.1. Cấu hình `pom.xml` Maven Profile

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

### 6.2. Multi-Stage Containerfile (Tương thích Podman)

Biên dịch AOT tại máy build, đóng gói image thực thi siêu nhẹ:

```dockerfile
# ==========================================
# STAGE 1: Builder (Chạy tại Local PC hoặc GitHub Actions)
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

# Biên dịch tĩnh AOT ra native executable
RUN ./mvnw -Pnative native:compile -DskipTests -B

# ==========================================
# STAGE 2: Distroless Runtime (RAM < 50MB)
# ==========================================
FROM gcr.io/distroless/base-debian12:nonroot

WORKDIR /app

# Copy binary trực tiếp từ builder (không chứa JVM)
COPY --from=builder /build/target/ticketing-service /app/application

EXPOSE 8080

USER nonroot:nonroot

ENTRYPOINT ["/app/application"]
```

---

## 7. KIẾN TRÚC FRONTEND VUE 3, INTERACTIVE SVG SEAT MAP & REAL-TIME STATE

### 7.1. Cấu trúc thư mục mã nguồn

```text
ticketing-frontend/
├── src/
│   ├── assets/                     # Tailwind CSS direct, icon tĩnh
│   ├── components/
│   │   ├── ui/                     # Shadcn-Vue (Button, Dialog, Badge, Toast)
│   │   ├── seatmap/
│   │   │   ├── InteractiveSeatMap.vue # Khung SVG zoom, pan & render lưới ghế
│   │   │   ├── SeatItem.vue        # Component từng ghế (màu sắc, click event)
│   │   │   └── SeatLegend.vue      # Chú thích: VIP, Standard, Đang giữ, Đã bán
│   │   └── checkout/
│   │       ├── CountdownClock.vue  # Đồng hồ đếm ngược 10 phút
│   │       └── QRPaymentModal.vue  # Render mã VietQR trên Canvas từ qr_hash
│   ├── composables/
│   │   ├── useSeatMap.ts           # State tính toán vị trí và danh sách chọn
│   │   ├── useTicketSocket.ts      # Kênh kết nối WebSocket STOMP nhận event
│   │   └── useCountdown.ts         # Đồng hồ đếm ngược phiên đặt vé
│   ├── stores/
│   │   └── useBookingStore.ts      # Pinia store lưu trữ giỏ vé tạm thời
│   ├── services/
│   │   └── api.ts                  # Axios client với interceptors
│   ├── views/
│   │   ├── EventDetailView.vue     # Chi tiết sự kiện & sơ đồ ghế
│   │   └── CheckoutView.vue        # Màn hình thanh toán và nhận vé
│   ├── App.vue
│   └── main.ts
```

### 7.2. Tương tác Optimistic UI và Đồng bộ WebSocket

1. **User Action:** Khách hàng nhấp vào ghế `B-14`.
2. **Optimistic Rendering:** Component `SeatItem.vue` lập tức đổi màu sang `Đang chọn` (cam) và đưa vào Pinia Store trước khi server phản hồi.
3. **Request Dispatch:** Gọi API `POST /api/v1/tickets/reserve`.
   * *Trường hợp thành công:* Server trả về HTTP 200 kèm `orderCode`. Chuyển khách hàng sang màn hình đếm ngược 10 phút.
   * *Trường hợp thất bại (HTTP 409):* Đã có người mua trước, giao diện lập tức rollback màu ghế về `Đã bán` (xám) và bật Toast cảnh báo.
4. **Realtime Broadcast:** NATS JetStream gửi event qua WebSocket. Toàn bộ các client khác trên hệ thống lập tức thấy ghế `B-14` chuyển sang màu vàng (`Đang bị người khác giữ`) mà không cần tải lại trang.

---

## 8. KẾ HOẠCH TRIỂN KHAI CHI TIẾT 5 GIAI ĐOẠN (LỘ TRÌNH 6 TUẦN)

### GIAI ĐOẠN 1: HẠ TẦNG PODMAN ROOTLESS TRÊN DEBIAN 13, FLYWAY & NỀN TẢNG (TUẦN 1)
* **Mục tiêu giai đoạn:** Xây dựng xong nền móng hạ tầng containerized bằng Podman trên Debian 13, tối ưu Swap 2GB hiện có, tự động hóa schema DB và dựng khung mã nguồn backend/frontend.
* **Thời gian:** 7 ngày (Tuần 1).

#### Kế hoạch chi tiết theo ngày:
* **Ngày 1–2: Thiết lập môi trường Podman Rootless trên Debian 13**
  * Cài đặt Podman, podman-compose, cấu hình subuid/subgid trên Debian host.
  * Cấu hình `vm.swappiness = 10` tối ưu phân vùng Swap 2GB sẵn có.
  * Viết file `podman-compose.yml` khởi chạy Postgres 16, Redis 7 (Alpine), NATS JetStream với flag `:Z` trên volumes.
  * Kiểm tra kết nối mạng nội bộ giữa các container thông qua bridge network `ticket-net`.
* **Ngày 3–4: Thiết kế & Tự động hóa Cơ sở dữ liệu (Flyway)**
  * Khởi tạo dự án Spring Boot 3.3 (Java 21) với các dependencies: Spring Data JPA, Redis, Web, Flyway, PostgreSQL Driver.
  * Tạo file migration `V1__init_schema.sql` chứa đầy đủ 12 bảng và indexes.
  * Chạy ứng dụng để Flyway tự động migrate schema, verify bảng và ràng buộc trên DB.
* **Ngày 5–6: Khởi tạo Kiến trúc Mã nguồn Backend & Frontend**
  * Phân chia thư mục Backend theo chuẩn Hexagonal / Modular Monolith (`core/domain`, `core/port`, `infrastructure/adapter`).
  * Khởi tạo project Frontend: Vue 3 + Vite + TypeScript + Tailwind CSS + Pinia + Lucide Icons.
* **Ngày 7: Review & Checkpoint Giai đoạn 1**
  * Viết tài liệu hướng dẫn khởi chạy nhanh môi trường dev trong 1 lệnh duy nhất.

#### Tiêu chuẩn nghiệm thu (Definition of Done - DoD):
* Gõ `podman-compose up -d`, cả 3 container (Postgres, Redis, NATS) ở trạng thái `healthy`.
* Khởi động Spring Boot, Flyway log xác nhận áp dụng migration thành công, không có lỗi constraint.
* Frontend compile không có lỗi TypeScript, giao diện khung hiển thị chuẩn CSS Tailwind.

#### Lưu ý kỹ thuật sống còn:
* **SELinux/AppArmor Permission:** Luôn thêm `:Z` vào các dòng volume mount trong `podman-compose.yml` để tránh lỗi `Permission Denied` khi truy cập ổ cứng host.
* **DNS Resolution:** Container trong Podman rootless chỉ giao tiếp được bằng tên khi được gán vào cùng một custom bridge network (`ticket-net`).

---

### GIAI ĐOẠN 2: TẦNG LÕI CONCURRENCY, LUA SCRIPT & NATS STREAM (TUẦN 2)
* **Mục tiêu giai đoạn:** Xây dựng trái tim xử lý xung đột của hệ thống. Đảm bảo tốc độ giữ vé đạt dưới 15ms và loại bỏ hoàn toàn race condition.
* **Thời gian:** 7 ngày (Tuần 2).

#### Kế hoạch chi tiết theo ngày:
* **Ngày 8–9: Phát triển Redis In-Memory Engine**
  * Viết script Lua `reserve_seat.lua` tối ưu kiểm tra và cập nhật trạng thái nguyên tử.
  * Xây dựng `RedisSeatCacheService`: Tải trước toàn bộ sơ đồ ghế và trạng thái từ PostgreSQL lên Redis Hash trước giờ mở bán (Pre-warming).
* **Ngày 10–11: Tích hợp NATS JetStream Stream & Publisher**
  * Tích hợp thư viện `jnats` vào Spring Boot.
  * Khởi tạo NATS Stream `TICKETS` với subject `tickets.*` và cấu hình `DuplicateWindow = 3m`.
  * Viết class `NatsTicketEventPublisher` gửi event kèm header `Nats-Msg-Id`.
* **Ngày 12–13: Xây dựng REST API & Testcontainers Concurrency Test**
  * Hoàn thiện controller tiếp nhận request: `POST /api/v1/tickets/reserve`.
  * Viết Integration Test bằng Java: Giả lập 50–100 threads cùng gửi request tranh mua đúng 1 chiếc ghế trong cùng 1 mili-giây.
* **Ngày 14: Review & Checkpoint Giai đoạn 2**
  * Benchmark nhanh endpoint giữ vé tại local bằng công cụ `hey` hoặc `wrk`.

#### Tiêu chuẩn nghiệm thu (Definition of Done - DoD):
* Request giữ vé thành công phản hồi HTTP 200 trong thời gian **dưới 15ms**.
* Integration test chứng minh: 100 threads tranh mua 1 ghế $\rightarrow$ Chính xác 1 thread thành công (HTTP 200), 99 threads thất bại (HTTP 409).
* NATS JetStream nhận được message, hiển thị đúng subject trên NATS CLI (`nats stream info TICKETS`).

#### Lưu ý kỹ thuật sống còn:
* **Testcontainers trên Podman:** Xuất biến môi trường `export DOCKER_HOST="unix:///run/user/$UID/podman/podman.sock"` và `export TESTCONTAINERS_RYUK_DISABLED="true"` để Testcontainers kết nối được với Podman socket mà không bị lỗi cấp quyền.

---

### GIAI ĐOẠN 3: NGHIỆP VỤ BOOKING, SAGA ROLLBACK & PAYMENT WEBHOOK (TUẦN 3)
* **Mục tiêu giai đoạn:** Hoàn thiện luồng thanh toán bất đồng bộ và cơ chế tự động thu hồi vé quá hạn 10 phút theo mô hình Saga.
* **Thời gian:** 7 ngày (Tuần 3).

#### Kế hoạch chi tiết theo ngày:
* **Ngày 15–16: Xây dựng NATS JetStream Consumer Worker**
  * Viết `BookingWorker` lắng nghe subject `tickets.reserved`.
  * Ghi bản ghi `orders` (trạng thái `PENDING`) và `order_items` xuống PostgreSQL.
* **Ngày 17–18: Xây dựng Cơ chế Timeout Rollback (10 phút)**
  * Tận dụng Redis Sorted Set (ZSET) lưu `expireTime` của các ghế đang giữ.
  * Xây dựng Scheduled Worker (chạy mỗi 10 giây): Quét các ghế quá hạn, cập nhật trạng thái `orders` thành `EXPIRED`, nhả trạng thái ghế trên Redis về `AVAILABLE`.
* **Ngày 19–20: Tích hợp Cổng thanh toán Sandbox (PayOS / VietQR)**
  * Sinh mã VietQR kèm số tiền và mã đơn hàng duy nhất.
  * Xây dựng endpoint nhận Webhook: `POST /api/v1/payments/webhook`.
  * Cài đặt Idempotency Check: Kiểm tra `idempotency_key` trong bảng `payments` để chặn xử lý trùng lặp webhook.
  * Khi thanh toán thành công: Cập nhật order thành `PAID`, sinh chuỗi `qr_hash` HMAC và lưu vé chính thức vào bảng `tickets`.
* **Ngày 21: Review & Checkpoint Giai đoạn 3**
  * Kiểm thử luồng hoàn vé tự động: Giữ chỗ $\rightarrow$ Không thanh toán $\rightarrow$ Xác nhận ghế tự nhả sau 10 phút.

#### Tiêu chuẩn nghiệm thu (Definition of Done - DoD):
* Luồng nghiệp vụ end-to-end hoạt động trọn vẹn: Giữ chỗ $\rightarrow$ Tạo đơn $\rightarrow$ Webhook thanh toán $\rightarrow$ Xuất vé điện tử.
* Luồng Rollback tự động: Ghế bị giữ nhưng không thanh toán được giải phóng tự động sau 10 phút, người khác có thể chọn lại bình thường.

#### Lưu ý kỹ thuật sống còn:
* **Xử lý bất đồng bộ:** Không cập nhật PostgreSQL trực tiếp trong Controller request. Mọi thao tác ghi DB được đẩy qua NATS Consumer để giữ cho API Gateway luôn phản hồi tức thì.

---

### GIAI ĐOẠN 4: FRONTEND VUE 3, INTERACTIVE SEAT MAP & REAL-TIME STATE (TUẦN 4)
* **Mục tiêu giai đoạn:** Xây dựng giao diện chọn ghế trực quan bằng SVG, kết nối WebSocket đồng bộ trạng thái thời gian thực và trải nghiệm Optimistic UI mượt mà.
* **Thời gian:** 7 ngày (Tuần 4).

#### Kế hoạch chi tiết theo ngày:
* **Ngày 22–23: Xây dựng Component `InteractiveSeatMap.vue`**
  * Tạo sơ đồ khán phòng bằng vector SVG phân theo hàng/cột và khu vực (VIP, Regular).
  * Tích hợp tính năng zoom, pan và hiển thị tooltip thông tin giá vé từng ghế.
* **Ngày 24–25: State Management & Optimistic UI**
  * Cài đặt Pinia store `useBookingStore.ts` lưu danh sách ghế đang chọn (tối đa 4 ghế/phiên).
  * Cài đặt cơ chế Optimistic UI: Đổi màu ghế ngay lập tức khi click, tự động rollback nếu server phản hồi lỗi.
* **Ngày 26–27: Đồng bộ thời gian thực qua WebSocket & Màn hình Thanh toán**
  * Cài đặt STOMP client trên Vue 3 lắng nghe topic `/topic/event/{id}/seats`.
  * Khi nhận tín hiệu có ghế bị giữ: Đổi màu ghế sang vàng trên màn hình của tất cả client khác đang mở.
  * Xây dựng màn hình Checkout: Hiển thị đồng hồ đếm ngược 10 phút (`CountdownClock.vue`) và render mã VietQR trên Canvas từ `qr_hash`.
* **Ngày 28: Review & Checkpoint Giai đoạn 4**
  * Mở 2 trình duyệt độc lập để kiểm thử khả năng đồng bộ thời gian thực giữa 2 người dùng.

#### Tiêu chuẩn nghiệm thu (Definition of Done - DoD):
* Sơ đồ SVG render mượt mà, hỗ trợ tốt trên cả màn hình desktop và mobile.
* Khi User A bấm giữ ghế, màn hình User B tự động chuyển ghế đó sang màu xám/vàng trong **dưới 200ms** mà không cần F5 tải lại trang.
* Hết thời gian đếm ngược 10 phút, modal cảnh báo tự động bật lên và khóa nút thanh toán.

#### Lưu ý kỹ thuật sống còn:
* **Render Performance:** Không re-render lại toàn bộ SVG DOM mỗi khi 1 ghế đổi màu; chỉ cập nhật reactive property của riêng node component `SeatItem.vue` tương ứng.

---

### GIAI ĐOẠN 5: LOAD TESTING k6, GRAALVM BUILD & PRODUCTION DEPLOY (TUẦN 5–6)
* **Mục tiêu giai đoạn:** Đóng gói Native Image, kiểm thử chịu tải k6 chứng minh zero-overselling, triển khai toàn bộ hệ thống lên VPS Debian 13 và môi trường Edge.
* **Thời gian:** 10–14 ngày (Tuần 5–6).

#### Kế hoạch chi tiết theo ngày:
* **Ngày 29–31: Kiểm thử chịu tải k6 (Load Testing)**
  * Viết kịch bản k6 mô phỏng $3.000 - 5.000\text{ VUs}$ cùng bấm mua vé trong 30 giây.
  * Thu thập các chỉ số: TPS, Latency P95/P99, tỷ lệ lỗi HTTP.
  * Chạy câu lệnh SQL kiểm tra DB: Xác nhận chính xác số lượng vé bán ra bằng số lượng tồn kho (Zero Overselling).
* **Ngày 32–34: Đóng gói GraalVM Native Image qua GitHub Actions**
  * Viết workflow GitHub Actions: Tự động chạy test, biên dịch AOT Native Image trên runner miễn phí của GitHub (được cấp sẵn 7GB RAM).
  * Đẩy container image siêu nhẹ (dưới 80MB) lên GitHub Container Registry (GHCR).
* **Ngày 35–37: Triển khai Hạ tầng Production Thực tế trên Debian 13**
  * Kéo image từ GHCR về VPS: Chạy backend container với giới hạn RAM 80MB (`--memory=80m`).
  * Cấu hình Podman Systemd Quadlets (`~/.config/containers/systemd/`) để dịch vụ tự khởi động cùng OS.
  * Deploy Frontend tĩnh lên Vercel / Cloudflare Pages.
  * Cấu hình Cloudflare CDN làm Reverse Proxy và cấp chứng chỉ SSL miễn phí.
* **Ngày 38–42: Hoàn thiện Portfolio & Tài liệu GitHub**
  * Viết file `README.md` chuẩn kỹ sư cao cấp: Sơ đồ kiến trúc Mermaid, giải thích lý do lựa chọn công nghệ (ADR), video GIF demo và ảnh chụp biểu đồ benchmark k6.

#### Tiêu chuẩn nghiệm thu (Definition of Done - DoD):
* Báo cáo k6 chứng minh hệ thống không bị bán âm vé dưới áp lực $5.000\text{ VUs}$.
* Toàn bộ hệ thống chạy trên VPS Debian 13 chỉ tiêu tốn **dưới 900MB RAM**, hoạt động ổn định 24/7.
* Có đường dẫn live demo trực tuyến chạy qua HTTPS để gắn trực tiếp vào CV.

#### Lưu ý kỹ thuật sống còn:
* **Tuyệt đối không chạy lệnh build Native Image trên VPS:** Việc biên dịch AOT ngốn từ 4GB–8GB RAM và sẽ làm sập VPS. Luôn luôn build trên máy local hoặc GitHub Actions rồi mới kéo image về VPS chạy.

---

## 9. KỊCH BẢN KIỂM THỬ CHỊU TẢI k6 & TIÊU CHUẨN NGHIỆM THU

### 9.1. Kịch bản test chịu tải mô phỏng Flash Sale (`loadtest.js`)

Kịch bản mô phỏng 3.000 người dùng ảo đồng loạt tấn công vào đúng 1 sự kiện mở bán có 100 chiếc vé duy nhất:

```javascript
import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
  scenarios: {
    flash_sale_spike: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '5s', target: 500 },    // Khởi động lưu lượng
        { duration: '10s', target: 3000 },  // Đột biến lên 3000 users tại giờ mở bán
        { duration: '15s', target: 3000 },  // Duy trì tranh mua vé liên tục
        { duration: '5s', target: 0 },      // Hạ tải
      ],
      gracefulRampDown: '5s',
    },
  },
  thresholds: {
    http_req_duration: ['p(95)<150'], // 95% requests phải phản hồi dưới 150ms
    http_req_failed: ['rate<0.01'],    // Tỷ lệ lỗi mạng hệ thống dưới 1%
  },
};

const BASE_URL = 'http://127.0.0.1:8080/api/v1';

export default function () {
  // Chọn ngẫu nhiên 1 trong 100 ghế mở bán
  const randomSeatId = Math.floor(Math.random() * 100) + 1;
  const payload = JSON.stringify({
    eventId: 'e1010000-0000-0000-0000-000000000001',
    userId: `user-vu-${__VU}`,
    seatIds: [`seat-${randomSeatId}`],
  });

  const params = {
    headers: {
      'Content-Type': 'application/json',
      'Idempotency-Key': `k6-req-${__VU}-${__ITER}`,
    },
  };

  const res = http.post(`${BASE_URL}/tickets/reserve`, payload, params);

  // Xác minh tính hợp lệ của HTTP code: Chỉ chấp nhận 200 (Giữ được vé) hoặc 409 (Ghế đã bị mua)
  check(res, {
    'Status hợp lệ (200 OK hoặc 409 Conflict)': (r) => r.status === 200 || r.status === 409,
    'Không có lỗi server 500': (r) => r.status !== 500,
  });

  sleep(0.1);
}
```

### 9.2. Câu lệnh SQL xác thực tính toàn vẹn (Zero-Overselling Verification)

Sau khi bài test k6 kết thúc, chạy câu lệnh kiểm tra trực tiếp trong PostgreSQL:

```sql
-- 1. Kiểm tra không có bất kỳ ghế nào bị ghi đè nhiều hơn 1 lần trong đơn hàng hợp lệ
SELECT seat_id, COUNT(*) AS duplicate_count
FROM order_items oi
JOIN orders o ON oi.order_id = o.id
WHERE o.status IN ('PENDING', 'PAID')
GROUP BY seat_id
HAVING COUNT(*) > 1;
-- KẾT QUẢ ĐẠT CHUẨN: 0 bản ghi (Zero duplicates)

-- 2. Kiểm tra tổng số vé được tạo không vượt quá số lượng mở bán
SELECT 
    tt.total_quantity, 
    COUNT(t.id) AS sold_tickets,
    (tt.total_quantity - COUNT(t.id)) AS remaining
FROM ticket_tiers tt
LEFT JOIN tickets t ON tt.id = t.seat_id -- liên kết qua tier
GROUP BY tt.id, tt.total_quantity;
-- KẾT QUẢ ĐẠT CHUẨN: sold_tickets <= total_quantity
```

Khi kết quả SQL trả về **0 bản ghi trùng lặp** và **0 lỗi bán âm**, dự án đạt chuẩn nghiệm thu kỹ thuật cao nhất để tự tin giới thiệu với các nhà tuyển dụng và chuyên gia công nghệ.
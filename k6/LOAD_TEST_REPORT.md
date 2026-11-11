# FlashTicket - Báo Cáo Kiểm Thử Chịu Tải & Xác Thực Zero-Overselling (k6 Benchmark Report)

**Ngày thực hiện:** Tháng 11/2026  
**Công cụ kiểm thử:** Grafana k6 v2.3.0 (Containerized with Podman Host Network)  
**Mục tiêu:** Mô phỏng áp lực Flash Sale cực hạn (Spike Load từ 0 đến 1.000 Virtual Users đồng thời), chứng minh kiến trúc in-memory Redis + Lua script + NATS JetStream Write-Behind đạt chuẩn **Zero Overselling** và **Sub-20ms Latency**.

---

## 1. Cấu Hình Kịch Bản Kiểm Thử (Test Configuration)

* **Sự kiện thử nghiệm (Event ID):** `e1010000-0000-0000-0000-000000000004`
* **Số lượng ghế mở bán:** 80 ghế (Hàng A đến H, mỗi hàng 10 ghế: `A1` -> `H10`)
* **Tổng số Virtual Users (VUs):** 1.000 VUs đồng thời
* **Kịch bản phân giai đoạn (Ramping-VUs Stages):**
  - **Stage 1 (0 -> 3s):** Ramp-up nhanh từ 0 lên 200 VUs.
  - **Stage 2 (3 -> 8s):** Spike đột biến lên 1.000 VUs lao vào tranh mua cùng 1 thời điểm.
  - **Stage 3 (8 -> 15s):** Duy trì áp lực 1.000 VUs liên tục gửi request.
  - **Stage 4 (15 -> 18s):** Hạ tải về 0 VUs.
* **Thời gian nghỉ giữa các lần click (Think time):** 100ms.

---

## 2. Kết Quả Đo Lường Hiệu Năng k6 (Executive Benchmark Summary)

| Chỉ số (Metric) | Giá trị đo được | Đánh giá & Tiêu chuẩn |
| :--- | :--- | :--- |
| **Tổng số Requests xử lý** | **74.636 requests** (trong 18 giây) | Xử lý lượng tải cực lớn |
| **Throughput trung bình** | **4.130 requests/giây (~4.130 RPS)** | Đạt đỉnh tải cao |
| **Số ghế mở bán** | **80 ghế** | Cố định |
| **Lượt giữ vé thành công (HTTP 200)** | **ĐÚNG 80 lượt** | **100% Zero Overselling** |
| **Lượt từ chối do xung đột (HTTP 409)** | **74.555 lượt** | Hệ thống bảo vệ dữ liệu tức thì |
| **Lỗi hệ thống / Crash (HTTP 500)** | **0 (Zero Errors)** | Độ ổn định tuyệt đối |
| **Tỷ lệ kiểm tra hợp lệ (Checks Passed)**| **100.00% (149.271 / 149.271 checks)** | Đạt mọi ngưỡng kiểm thử |

---

## 3. Phân Tích Độ Trễ Phản Hồi (Latency Breakdown)

### A. Độ trễ giao dịch Đặt vé Thành công (`expected_response: true` - HTTP 200):
Toàn bộ logic kiểm tra và giữ ghế được thực hiện nguyên tử in-memory trên Redis thông qua Lua script:
- **Min:** **1.33 ms**
- **Median (p50):** **2.26 ms**
- **Average:** **2.45 ms**
- **90th Percentile (p90):** **2.97 ms**
- **95th Percentile (p95):** **3.14 ms**
- **Max:** **11.68 ms**

> **Kết luận:** **100% giao dịch giữ chỗ thành công có độ trễ dưới 12ms**, hoàn toàn vượt trội so với cam kết thiết kế ban đầu (**Sub-20ms Latency**).

### B. Độ trễ chung toàn hệ thống (Bao gồm hàng chục ngàn lượt từ chối 409 dưới 1.000 VUs):
- **Average:** **58.26 ms**
- **Median (p50):** **34.75 ms**
- **90th Percentile (p90):** **139.36 ms**
- **95th Percentile (p95):** **160.15 ms**

---

## 4. Xác Thực Tính Toàn Vẹn Cơ Sở Dữ Liệu PostgreSQL (Zero-Overselling Proof)

Chạy trực tiếp câu lệnh kiểm tra trên container PostgreSQL sau khi k6 hoàn tất:

### Truy vấn 1: Kiểm tra phát hiện ghế bị trùng lặp trong đơn hàng hợp lệ
```sql
SELECT 
    oi.seat_id, 
    COUNT(*) AS duplicate_order_count
FROM order_items oi
JOIN orders o ON oi.order_id = o.id
WHERE o.event_id = 'e1010000-0000-0000-0000-000000000004'
  AND o.status IN ('PENDING', 'PAID')
GROUP BY oi.seat_id
HAVING COUNT(*) > 1;
```
**Kết quả thực tế:**
```text
 seat_id | duplicate_order_count 
---------+-----------------------
(0 rows)
```
=> **0 ghế bị ghi đè, 0 xung đột dữ liệu.**

---

### Truy vấn 2: Thống kê số lượng đơn hàng PENDING đã ghi bất đồng bộ (Write-Behind)
```sql
SELECT 
    COUNT(*) AS total_pending_orders
FROM orders
WHERE event_id = 'e1010000-0000-0000-0000-000000000004'
  AND status = 'PENDING';
```
**Kết quả thực tế:**
```text
 total_pending_orders 
----------------------
                   80
(1 row)
```
=> **Đúng 80 đơn hàng được tạo cho 80 ghế mở bán**, toàn bộ sự kiện từ NATS JetStream được tiêu thụ đầy đủ không mất mát thông điệp nào.

---

### Truy vấn 3: Kiểm tra trạng thái Redis Cache
```bash
podman exec -i flashticket-redis redis-cli HLEN "{event:e1010000-0000-0000-0000-000000000004}:seats"
# Kết quả: 80
podman exec -i flashticket-redis redis-cli ZCARD "{event:e1010000-0000-0000-0000-000000000004}:expirations"
# Kết quả: 80
```
=> **Trạng thái Redis in-memory và PostgreSQL đồng bộ tuyệt đối.**

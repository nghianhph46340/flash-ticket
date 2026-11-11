import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Trend } from 'k6/metrics';

// Định nghĩa Custom Metrics để đo lường độ chính xác của Zero-Overselling
const successfulReservations = new Counter('successful_reservations');
const conflictedReservations = new Counter('conflicted_reservations');
const unexpectedErrors = new Counter('unexpected_errors');
const reservationLatency = new Trend('reservation_latency', true);

// Danh sách 80 ghế mở bán: Hàng A -> H, mỗi hàng 10 ghế (1 -> 10)
const ROWS = ['A', 'B', 'C', 'D', 'E', 'F', 'G', 'H'];
const SEATS_PER_ROW = 10;
const ALL_SEATS = [];
for (const row of ROWS) {
  for (let i = 1; i <= SEATS_PER_ROW; i++) {
    ALL_SEATS.push(`${row}${i}`);
  }
}

const EVENT_ID = __ENV.EVENT_ID || 'e1010000-0000-0000-0000-000000000001';
const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080/api/v1';

export const options = {
  scenarios: {
    flash_sale_spike: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '3s', target: 200 },    // Khởi động nhanh 200 VUs
        { duration: '5s', target: 1000 },   // Spike đột biến lên 1.000 VUs cùng tranh mua
        { duration: '7s', target: 1000 },   // Duy trì áp lực cao liên tục
        { duration: '3s', target: 0 },      // Giảm tải
      ],
      gracefulRampDown: '3s',
    },
  },
  thresholds: {
    // 95% requests phải phản hồi dưới 150ms
    http_req_duration: ['p(95)<150'],
    // Không có lỗi hạ tầng 500
    unexpected_errors: ['count==0'],
    // Số lượt giữ vé thành công phải tối đa bằng số ghế mở bán (Zero Overselling)
    successful_reservations: [`count<=${ALL_SEATS.length}`],
  },
};

// Setup Hook: Chạy 1 lần trước khi bài test bắt đầu để nạp trước (pre-warm) 80 ghế lên Redis
export function setup() {
  console.log(`[k6 Setup] Khởi tạo Pre-warm 80 ghế cho Event: ${EVENT_ID}...`);
  const payload = JSON.stringify(ALL_SEATS);
  const params = {
    headers: { 'Content-Type': 'application/json' },
  };

  const res = http.post(`${BASE_URL}/events/${EVENT_ID}/prewarm`, payload, params);
  const ok = check(res, {
    'Pre-warm seats thành công': (r) => r.status === 200,
  });

  if (!ok) {
    console.error(`[k6 Setup] Pre-warm thất bại! Mã HTTP: ${res.status}, body: ${res.body}`);
  } else {
    console.log(`[k6 Setup] Pre-warm 80 ghế thành công! Chuẩn bị mở bán...`);
  }

  return { eventId: EVENT_ID, totalSeats: ALL_SEATS.length };
}

export default function (data) {
  // Mỗi Virtual User chọn ngẫu nhiên 1 trong 80 ghế
  const randomSeatIndex = Math.floor(Math.random() * ALL_SEATS.length);
  const selectedSeat = ALL_SEATS[randomSeatIndex];

  const payload = JSON.stringify({
    eventId: data.eventId,
    userId: `k6-user-${__VU}`,
    seatIds: [selectedSeat],
  });

  const params = {
    headers: {
      'Content-Type': 'application/json',
      'Idempotency-Key': `k6-${data.eventId}-${__VU}-${__ITER}`,
    },
    timeout: '5s',
  };

  const start = Date.now();
  const res = http.post(`${BASE_URL}/tickets/reserve`, payload, params);
  const duration = Date.now() - start;
  reservationLatency.add(duration);

  // Phân tích trạng thái HTTP trả về
  if (res.status === 200) {
    successfulReservations.add(1);
  } else if (res.status === 409) {
    conflictedReservations.add(1);
  } else {
    unexpectedErrors.add(1);
    console.warn(`[VU ${__VU}] Nhận mã bất thường: ${res.status} | Body: ${res.body}`);
  }

  check(res, {
    'Mã HTTP hợp lệ (200 OK hoặc 409 Conflict)': (r) => r.status === 200 || r.status === 409,
    'Không xảy ra lỗi hệ thống (500 Internal Error)': (r) => r.status !== 500,
  });

  // Nghỉ 100ms giữa các lần thử theo đúng hành vi người dùng thực tế
  sleep(0.1);
}

// Teardown Hook: Tóm tắt kết quả sau bài test
export function teardown(data) {
  console.log(`=======================================================`);
  console.log(`🏁 BÀI KIỂM THỬ TẢI k6 HOÀN TẤT`);
  console.log(`Event ID: ${data.eventId}`);
  console.log(`Tổng số ghế mở bán: ${data.totalSeats}`);
  console.log(`=======================================================`);
}

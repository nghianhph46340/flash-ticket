import axios from 'axios'
import type { OrderPaymentInfo, PaymentWebhookResponse, ReserveTicketRequest, ReserveTicketResponse } from '../types'

const apiClient = axios.create({
  baseURL: '/api/v1',
  headers: {
    'Content-Type': 'application/json',
  },
  timeout: 10000,
})

export const ticketApi = {
  // Lấy toàn bộ trạng thái ghế của sự kiện
  async getSeats(eventId: string): Promise<Record<string, string>> {
    const res = await apiClient.get<Record<string, string>>(`/events/${eventId}/seats`)
    return res.data
  },

  // Giữ chỗ vé nguyên tử (Zero Overselling Sub-20ms)
  async reserveSeats(request: ReserveTicketRequest): Promise<ReserveTicketResponse> {
    const res = await apiClient.post<ReserveTicketResponse>('/tickets/reserve', request)
    return res.data
  },

  // Lấy thông tin thanh toán VietQR
  async getPaymentInfo(orderCode: string): Promise<OrderPaymentInfo> {
    const res = await apiClient.get<OrderPaymentInfo>(`/orders/${orderCode}/payment-info`)
    return res.data
  },

  // Giả lập webhook thanh toán PayOS / VietQR (Dùng cho Demo/Kiểm thử trực tiếp)
  async simulatePayment(orderCode: string, amount: number): Promise<PaymentWebhookResponse> {
    const idempotencyKey = `SIM-${Date.now()}-${Math.random().toString(36).substring(2, 8)}`
    const transactionCode = `TRANS-${Date.now()}`
    const res = await apiClient.post<PaymentWebhookResponse>('/payments/webhook', {
      orderCode,
      transactionCode,
      amount,
      paymentMethod: 'VIETQR',
      idempotencyKey,
    })
    return res.data
  },
}

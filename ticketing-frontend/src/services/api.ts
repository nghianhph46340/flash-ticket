import axios from 'axios'
import type {
  Concession,
  Movie,
  OrderPaymentInfo,
  PaymentWebhookResponse,
  ReserveTicketRequest,
  ReserveTicketResponse,
  Showtime,
} from '../types'

const apiClient = axios.create({
  baseURL: '/api/v1',
  headers: { 'Content-Type': 'application/json' },
  timeout: 10000,
})

// ── Cinema APIs ────────────────────────────────────────────────
export const cinemaApi = {
  async getMovies(): Promise<Movie[]> {
    const res = await apiClient.get<Movie[]>('/movies')
    return res.data
  },

  async getShowtimes(movieId: string): Promise<Showtime[]> {
    const res = await apiClient.get<Showtime[]>(`/movies/${movieId}/showtimes`)
    return res.data
  },

  async getConcessions(): Promise<Concession[]> {
    const res = await apiClient.get<Concession[]>('/concessions')
    return res.data
  },
}

// ── Ticket APIs (giữ nguyên) ───────────────────────────────────
export const ticketApi = {
  async getSeats(eventId: string): Promise<Record<string, string>> {
    const res = await apiClient.get<Record<string, string>>(`/events/${eventId}/seats`)
    return res.data
  },

  async reserveSeats(request: ReserveTicketRequest): Promise<ReserveTicketResponse> {
    const res = await apiClient.post<ReserveTicketResponse>('/tickets/reserve', request)
    return res.data
  },

  async getPaymentInfo(orderCode: string): Promise<OrderPaymentInfo> {
    const res = await apiClient.get<OrderPaymentInfo>(`/orders/${orderCode}/payment-info`)
    return res.data
  },

  // Mock payment — gọi endpoint dev, không cần webhook thật
  async mockConfirmPayment(orderCode: string, amount: number): Promise<PaymentWebhookResponse> {
    const res = await apiClient.post<PaymentWebhookResponse>('/payments/mock-confirm', {
      orderCode,
      amount,
    })
    return res.data
  },
}

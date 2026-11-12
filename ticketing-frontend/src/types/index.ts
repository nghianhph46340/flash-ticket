// ============================================================
// FlashTicket Cinema — Type Definitions
// ============================================================

// ── Seat (giữ nguyên hoàn toàn) ──────────────────────────────
export type SeatTier = 'VIP' | 'STANDARD' | 'SWEETBOX' | 'ECONOMY'
export type SeatStatus = 'AVAILABLE' | 'HELD' | 'SOLD' | 'SELECTED' | 'BLOCKED'

export interface Seat {
  id: string
  row: string
  number: number
  tier: SeatTier
  price: number
  status: SeatStatus
  heldBy?: string
  pairSeatId?: string  // Sweetbox
}

// ── Movie & Cinema (mới) ──────────────────────────────────────
export interface Movie {
  id: string
  title: string
  slug: string
  durationMinutes: number
  ageRating: 'P' | 'K' | 'T13' | 'T16' | 'T18'
  director?: string
  synopsis?: string
  posterUrl?: string
  trailerUrl?: string
  releaseDate?: string
}

export interface Showtime {
  id: string
  movieId: string
  auditoriumId: string
  auditoriumName?: string
  screenFormat?: string
  startTime: string   // ISO string
  endTime: string
  basePrice: number
  status: 'DRAFT' | 'PUBLISHED' | 'CANCELLED' | 'ARCHIVED'
}

export interface Concession {
  id: string
  name: string
  description?: string
  price: number
  imageUrl?: string
  category: string
}

export interface CartConcession {
  concession: Concession
  quantity: number
}

// ── Ticket Reservation (giữ nguyên) ──────────────────────────
export interface ReserveTicketRequest {
  eventId: string
  userId: string
  seatIds: string[]
}

export interface ReserveTicketResponse {
  orderCode: string
  status: string
  expiresAt: string
  reservedSeats: string[]
  totalAmount: number
  message?: string
}

// ── Payment (giữ nguyên) ──────────────────────────────────────
export interface OrderPaymentInfo {
  orderCode: string
  eventId: string
  amount: number
  status: string
  expiresAt: string
  seatIds: string[]
  bankName: string
  accountNumber: string
  accountName: string
  qrImageUrl: string
}

export interface PaymentWebhookResponse {
  status: string
  orderCode: string
  message: string
  ticketCodes: string[]
}

// ── WebSocket (giữ nguyên) ────────────────────────────────────
export interface WebSocketSeatUpdate {
  eventId: string
  seatIds: string[]
  status: 'HELD' | 'AVAILABLE' | 'SOLD'
  userId?: string
  timestamp: string
}

// ── EventInfo (giữ nguyên cho legacy) ────────────────────────
export interface EventInfo {
  id: string
  title: string
  subTitle: string
  venue: string
  date: string
  badge: string
  tagColor: string
}

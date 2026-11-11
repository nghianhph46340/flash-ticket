export type SeatTier = 'VIP' | 'STANDARD' | 'ECONOMY'

export type SeatStatus = 'AVAILABLE' | 'HELD' | 'SOLD' | 'SELECTED'

export interface EventInfo {
  id: string
  title: string
  subTitle: string
  venue: string
  date: string
  badge: string
  tagColor: string
}

export interface Seat {
  id: string
  row: string
  number: number
  tier: SeatTier
  price: number
  status: SeatStatus
  heldBy?: string
}

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

export interface WebSocketSeatUpdate {
  eventId: string
  seatIds: string[]
  status: 'HELD' | 'AVAILABLE' | 'SOLD'
  userId?: string
  timestamp: string
}

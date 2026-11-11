import { defineStore } from 'pinia'
import { ticketApi } from '../services/api'
import { stompService } from '../services/websocket'
import type { EventInfo, OrderPaymentInfo, ReserveTicketResponse, Seat, SeatTier, WebSocketSeatUpdate } from '../types'

export const AVAILABLE_EVENTS: EventInfo[] = [
  {
    id: 'flash-concert-2026',
    title: 'Flash Concert 2026: Neon Symphony',
    subTitle: 'Đêm diễn âm nhạc điện tử quy mô 20.000 khán giả',
    venue: 'Sân vận động Quốc gia Mỹ Đình, Hà Nội',
    date: '20:00 • 15/12/2026',
    badge: 'HOT FLASH SALE',
    tagColor: 'from-amber-500 to-rose-600',
  },
  {
    id: 'rock-storm-2026',
    title: 'Rock Storm Festival: Rực Lửa Đam Mê',
    subTitle: 'Đại tiệc Rock đỉnh cao quy tụ các ban nhạc huyền thoại',
    venue: 'Nhà thi đấu Phú Thọ, TP. Hồ Chí Minh',
    date: '19:30 • 28/12/2026',
    badge: 'OPENING',
    tagColor: 'from-rose-600 to-purple-600',
  },
  {
    id: 'tech-summit-2026',
    title: 'Vietnam Global AI & Tech Summit 2026',
    subTitle: 'Hội nghị Công nghệ & Trí tuệ Nhân tạo thế hệ mới',
    venue: 'Trung tâm Hội nghị Quốc gia NCC, Hà Nội',
    date: '08:30 • 10/01/2027',
    badge: 'COMING SOON',
    tagColor: 'from-cyan-500 to-blue-600',
  },
]

export const useTicketStore = defineStore('ticket', {
  state: () => ({
    availableEvents: AVAILABLE_EVENTS,
    eventId: 'flash-concert-2026',
    userId: 'user-' + Math.random().toString(36).substring(2, 8),
    seats: [] as Seat[],
    selectedSeatIds: [] as string[],
    wsConnected: false,
    loading: false,
    errorMessage: null as string | null,

    // Đơn hàng & Thanh toán
    currentOrder: null as ReserveTicketResponse | null,
    paymentInfo: null as OrderPaymentInfo | null,
    issuedTickets: [] as string[],
    currentView: 'SEAT_MAP' as 'SEAT_MAP' | 'CHECKOUT' | 'TICKETS',

    // Countdown Timer (10 phút = 600 giây)
    remainingSeconds: 600,
    timerInterval: null as any,
  }),

  getters: {
    selectedSeats(state): Seat[] {
      return state.seats.filter((s) => state.selectedSeatIds.includes(s.id))
    },
    totalAmount(): number {
      return this.selectedSeats.reduce((sum, s) => sum + s.price, 0)
    },
    formattedTime(state): string {
      const mins = Math.floor(state.remainingSeconds / 60)
      const secs = state.remainingSeconds % 60
      return `${mins.toString().padStart(2, '0')}:${secs.toString().padStart(2, '0')}`
    },
    currentEvent(state): EventInfo {
      return state.availableEvents.find((e) => e.id === state.eventId) || state.availableEvents[0]
    },
    availableCount(state): number {
      return state.seats.filter((s) => s.status === 'AVAILABLE').length
    },
    heldCount(state): number {
      return state.seats.filter((s) => s.status === 'HELD').length
    },
    soldCount(state): number {
      return state.seats.filter((s) => s.status === 'SOLD').length
    },
  },

  actions: {
    // Chuyển đổi sự kiện & cập nhật WebSocket Realtime
    async switchEvent(newEventId: string) {
      if (this.eventId === newEventId) return
      if (this.timerInterval) {
        clearInterval(this.timerInterval)
      }
      this.eventId = newEventId
      this.selectedSeatIds = []
      this.currentOrder = null
      this.paymentInfo = null
      this.issuedTickets = []
      this.currentView = 'SEAT_MAP'
      this.errorMessage = null

      stompService.disconnect()
      await this.initSeats()
      this.initWebSocket()
    },
    // 1. Khởi tạo danh sách ghế và nạp trạng thái từ Backend Redis
    async initSeats() {
      const generatedSeats: Seat[] = []
      const rows = ['A', 'B', 'C', 'D', 'E', 'F', 'G', 'H']

      for (const row of rows) {
        let tier: SeatTier = 'STANDARD'
        let price = 250000

        if (row === 'A' || row === 'B') {
          tier = 'VIP'
          price = 500000
        } else if (row === 'F' || row === 'G' || row === 'H') {
          tier = 'ECONOMY'
          price = 150000
        }

        for (let num = 1; num <= 10; num++) {
          const id = `${row}${num}`
          generatedSeats.push({
            id,
            row,
            number: num,
            tier,
            price,
            status: 'AVAILABLE',
          })
        }
      }

      this.seats = generatedSeats

      try {
        this.loading = true
        const statuses = await ticketApi.getSeats(this.eventId)
        for (const seat of this.seats) {
          const rawStatus = statuses[seat.id]
          if (rawStatus) {
            if (rawStatus === 'AVAILABLE') {
              seat.status = 'AVAILABLE'
            } else if (rawStatus === 'SOLD') {
              seat.status = 'SOLD'
            } else if (rawStatus.startsWith('HELD:')) {
              seat.status = 'HELD'
              seat.heldBy = rawStatus.replace('HELD:', '')
            }
          }
        }
      } catch (err: any) {
        console.error('Failed to load initial seats:', err)
        this.errorMessage = 'Không thể tải danh sách ghế từ máy chủ.'
      } finally {
        this.loading = false
      }
    },

    // 2. Kết nối STOMP WebSocket
    initWebSocket() {
      stompService.connect(
        this.eventId,
        (update: WebSocketSeatUpdate) => {
          this.handleWebSocketUpdate(update)
        },
        (connected: boolean) => {
          this.wsConnected = connected
        }
      )
    },

    // 3. Xử lý cập nhật ghế realtime từ WebSocket
    handleWebSocketUpdate(update: WebSocketSeatUpdate) {
      console.log('[STOMP] Received seat update:', update)
      for (const seatId of update.seatIds) {
        const seat = this.seats.find((s) => s.id === seatId)
        if (seat) {
          if (update.status === 'HELD') {
            seat.status = 'HELD'
            seat.heldBy = update.userId
            // Nếu ghế này đang được người dùng chọn nhưng người khác vừa mua trước
            if (this.selectedSeatIds.includes(seatId) && update.userId !== this.userId) {
              this.selectedSeatIds = this.selectedSeatIds.filter((id) => id !== seatId)
              alert(`Ghế ${seatId} vừa được một khán giả khác giữ trước! Vui lòng chọn ghế khác.`)
            }
          } else if (update.status === 'AVAILABLE') {
            seat.status = 'AVAILABLE'
            seat.heldBy = undefined
          } else if (update.status === 'SOLD') {
            seat.status = 'SOLD'
            seat.heldBy = undefined
            this.selectedSeatIds = this.selectedSeatIds.filter((id) => id !== seatId)
          }
        }
      }
    },

    // 4. Chọn hoặc bỏ chọn ghế
    toggleSeat(seatId: string) {
      const seat = this.seats.find((s) => s.id === seatId)
      if (!seat || seat.status === 'SOLD' || seat.status === 'HELD') {
        return
      }

      if (this.selectedSeatIds.includes(seatId)) {
        this.selectedSeatIds = this.selectedSeatIds.filter((id) => id !== seatId)
      } else {
        if (this.selectedSeatIds.length >= 4) {
          alert('Mỗi giao dịch chỉ được chọn tối đa 4 ghế!')
          return
        }
        this.selectedSeatIds.push(seatId)
      }
    },

    // 5. Gửi request giữ chỗ nguyên tử (Core Concurrency API)
    async reserveSeats() {
      if (this.selectedSeatIds.length === 0) return

      try {
        this.loading = true
        this.errorMessage = null

        const res = await ticketApi.reserveSeats({
          eventId: this.eventId,
          userId: this.userId,
          seatIds: this.selectedSeatIds,
        })

        this.currentOrder = res

        // Lấy thông tin thanh toán VietQR
        const payInfo = await ticketApi.getPaymentInfo(res.orderCode)
        this.paymentInfo = payInfo

        // Bắt đầu đếm ngược 10 phút
        this.startCountdown(res.expiresAt)

        // Chuyển sang màn hình thanh toán
        this.currentView = 'CHECKOUT'
      } catch (err: any) {
        if (err.response?.status === 409) {
          const msg = err.response?.data?.message || 'Có ghế vừa bị khán giả khác chọn trước.'
          alert(`Xung đột giữ chỗ: ${msg}\nVui lòng chọn lại ghế khả dụng!`)
          await this.initSeats()
          this.selectedSeatIds = []
        } else {
          this.errorMessage = err.response?.data?.message || 'Lỗi hệ thống khi giữ chỗ.'
        }
      } finally {
        this.loading = false
      }
    },

    // 6. Quản lý đồng hồ đếm ngược 10 phút
    startCountdown(expiresAtIso: string) {
      if (this.timerInterval) {
        clearInterval(this.timerInterval)
      }

      const targetTime = new Date(expiresAtIso).getTime()

      const updateRemaining = () => {
        const diff = Math.max(0, Math.floor((targetTime - Date.now()) / 1000))
        this.remainingSeconds = diff

        if (diff <= 0) {
          clearInterval(this.timerInterval)
          alert('Đơn hàng đã hết hạn giữ chỗ (10 phút). Ghế đã được tự động hoàn trả!')
          this.cancelOrder()
        }
      }

      updateRemaining()
      this.timerInterval = setInterval(updateRemaining, 1000)
    },

    // 7. Giả lập thanh toán thành công (Test VietQR webhook)
    async simulatePaymentSuccess() {
      if (!this.currentOrder) return

      try {
        this.loading = true
        const res = await ticketApi.simulatePayment(
          this.currentOrder.orderCode,
          this.paymentInfo?.amount || this.currentOrder.totalAmount
        )

        this.issuedTickets = res.ticketCodes
        if (this.timerInterval) {
          clearInterval(this.timerInterval)
        }

        // Chuyển sang màn hình vé điện tử
        this.currentView = 'TICKETS'
      } catch (err: any) {
        alert('Lỗi giả lập thanh toán: ' + (err.response?.data?.message || err.message))
      } finally {
        this.loading = false
      }
    },

    // 8. Hủy đơn hàng hoặc quay lại chọn ghế
    cancelOrder() {
      if (this.timerInterval) {
        clearInterval(this.timerInterval)
      }
      this.currentOrder = null
      this.paymentInfo = null
      this.issuedTickets = []
      this.selectedSeatIds = []
      this.currentView = 'SEAT_MAP'
      this.initSeats()
    },
  },
})

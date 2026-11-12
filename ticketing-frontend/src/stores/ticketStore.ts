import { defineStore } from 'pinia'
import { cinemaApi, ticketApi } from '../services/api'
import { stompService } from '../services/websocket'
import type {
  CartConcession,
  Concession,
  Movie,
  OrderPaymentInfo,
  ReserveTicketResponse,
  Seat,
  SeatTier,
  Showtime,
  WebSocketSeatUpdate,
} from '../types'

export const useTicketStore = defineStore('ticket', {
  state: () => ({
    // ── Cinema selection ───────────────────────────────────
    movies: [] as Movie[],
    selectedMovie: null as Movie | null,
    showtimes: [] as Showtime[],
    selectedShowtime: null as Showtime | null,
    concessions: [] as Concession[],
    cart: [] as CartConcession[],  // F&B cart

    // ── App flow ───────────────────────────────────────────
    // MOVIE_SELECT → SHOWTIME_SELECT → SEAT_MAP → CHECKOUT → TICKETS
    appView: 'MOVIE_SELECT' as 'MOVIE_SELECT' | 'SHOWTIME_SELECT' | 'SEAT_MAP' | 'CHECKOUT' | 'TICKETS',

    // ── Seat map (giữ nguyên) ──────────────────────────────
    userId: 'user-' + Math.random().toString(36).substring(2, 8),
    seats: [] as Seat[],
    selectedSeatIds: [] as string[],
    wsConnected: false,
    loading: false,
    errorMessage: null as string | null,

    // ── Order & Payment (giữ nguyên) ───────────────────────
    currentOrder: null as ReserveTicketResponse | null,
    paymentInfo: null as OrderPaymentInfo | null,
    issuedTickets: [] as string[],

    // ── Countdown Timer ────────────────────────────────────
    remainingSeconds: 600,
    timerInterval: null as ReturnType<typeof setInterval> | null,
  }),

  getters: {
    // Seat getters (giữ nguyên)
    selectedSeats(state): Seat[] {
      return state.seats.filter((s) => state.selectedSeatIds.includes(s.id))
    },
    seatAmount(): number {
      return this.selectedSeats.reduce((sum, s) => sum + s.price, 0)
    },
    concessionAmount(state): number {
      return state.cart.reduce((sum, c) => sum + c.concession.price * c.quantity, 0)
    },
    totalAmount(): number {
      return this.seatAmount + this.concessionAmount
    },
    formattedTime(state): string {
      const mins = Math.floor(state.remainingSeconds / 60)
      const secs = state.remainingSeconds % 60
      return `${mins.toString().padStart(2, '0')}:${secs.toString().padStart(2, '0')}`
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
    // Hiển thị key sự kiện cho Redis (dùng showtimeId hoặc fallback)
    eventId(state): string {
      return state.selectedShowtime?.id || 'default-showtime'
    },
    // view alias cho backward compat (CheckoutModal & TicketPassModal dùng currentView)
    currentView(state): string {
      return state.appView
    },
  },

  actions: {
    // ── Cinema flow ────────────────────────────────────────

    async loadMovies() {
      this.loading = true
      this.errorMessage = null
      try {
        this.movies = await cinemaApi.getMovies()
        this.concessions = await cinemaApi.getConcessions()
      } catch (err: any) {
        this.errorMessage = 'Không thể tải danh sách phim. Vui lòng thử lại.'
        console.error(err)
      } finally {
        this.loading = false
      }
    },

    async selectMovie(movie: Movie) {
      this.selectedMovie = movie
      this.appView = 'SHOWTIME_SELECT'
      this.loading = true
      try {
        this.showtimes = await cinemaApi.getShowtimes(movie.id)
      } catch (err: any) {
        this.errorMessage = 'Không thể tải lịch chiếu.'
        console.error(err)
      } finally {
        this.loading = false
      }
    },

    async selectShowtime(showtime: Showtime) {
      this.selectedShowtime = showtime
      this.selectedSeatIds = []
      this.cart = []
      this.appView = 'SEAT_MAP'
      await this.initSeats()
      this.initWebSocket()
    },

    backToMovies() {
      stompService.disconnect()
      this.selectedMovie = null
      this.selectedShowtime = null
      this.seats = []
      this.selectedSeatIds = []
      this.cart = []
      this.errorMessage = null
      this.appView = 'MOVIE_SELECT'
    },

    backToShowtimes() {
      stompService.disconnect()
      this.selectedShowtime = null
      this.seats = []
      this.selectedSeatIds = []
      this.cart = []
      this.appView = 'SHOWTIME_SELECT'
    },

    // ── F&B Cart ───────────────────────────────────────────

    addConcession(concession: Concession) {
      const existing = this.cart.find((c) => c.concession.id === concession.id)
      if (existing) {
        existing.quantity++
      } else {
        this.cart.push({ concession, quantity: 1 })
      }
    },

    removeConcession(concessionId: string) {
      const idx = this.cart.findIndex((c) => c.concession.id === concessionId)
      if (idx === -1) return
      if (this.cart[idx].quantity > 1) {
        this.cart[idx].quantity--
      } else {
        this.cart.splice(idx, 1)
      }
    },

    // ── Seat map (giữ nguyên logic) ────────────────────────

    async initSeats() {
      const showtimeId = this.selectedShowtime?.id || 'default-showtime'
      const basePrice = this.selectedShowtime?.basePrice || 85000

      const generatedSeats: Seat[] = []
      const rows = ['A', 'B', 'C', 'D', 'E', 'F', 'G', 'H']

      for (const row of rows) {
        let tier: SeatTier = 'STANDARD'
        let price = basePrice

        if (row === 'A' || row === 'B') {
          tier = 'VIP'
          price = Math.round(basePrice * 1.5)
        } else if (row === 'G' || row === 'H') {
          tier = 'SWEETBOX'
          price = Math.round(basePrice * 2)
        }

        const seatsInRow = row === 'G' || row === 'H' ? 5 : 10  // Sweetbox ít ghế hơn
        for (let num = 1; num <= seatsInRow; num++) {
          const id = `${row}${num}`
          generatedSeats.push({ id, row, number: num, tier, price, status: 'AVAILABLE' })
        }
      }

      this.seats = generatedSeats

      try {
        this.loading = true
        const statuses = await ticketApi.getSeats(showtimeId)
        for (const seat of this.seats) {
          const rawStatus = statuses[seat.id]
          if (rawStatus) {
            if (rawStatus === 'SOLD') seat.status = 'SOLD'
            else if (rawStatus.startsWith('HELD:')) {
              seat.status = 'HELD'
              seat.heldBy = rawStatus.replace('HELD:', '')
            }
          }
        }
      } catch (err: any) {
        console.error('Failed to load seat statuses:', err)
      } finally {
        this.loading = false
      }
    },

    initWebSocket() {
      const showtimeId = this.selectedShowtime?.id || 'default-showtime'
      stompService.connect(
        showtimeId,
        (update: WebSocketSeatUpdate) => { this.handleWebSocketUpdate(update) },
        (connected: boolean) => { this.wsConnected = connected },
      )
    },

    handleWebSocketUpdate(update: WebSocketSeatUpdate) {
      for (const seatId of update.seatIds) {
        const seat = this.seats.find((s) => s.id === seatId)
        if (!seat) continue
        if (update.status === 'AVAILABLE') {
          if (seat.status !== 'SELECTED') seat.status = 'AVAILABLE'
          seat.heldBy = undefined
        } else if (update.status === 'HELD') {
          if (!this.selectedSeatIds.includes(seatId)) {
            seat.status = 'HELD'
            seat.heldBy = update.userId
          }
        } else if (update.status === 'SOLD') {
          seat.status = 'SOLD'
          this.selectedSeatIds = this.selectedSeatIds.filter((id) => id !== seatId)
        }
      }
    },

    toggleSeat(seatId: string) {
      const seat = this.seats.find((s) => s.id === seatId)
      if (!seat || seat.status === 'SOLD' || seat.status === 'HELD' || seat.status === 'BLOCKED') return

      // Sweetbox: chọn 1 → tự động chọn cả 2
      if (seat.tier === 'SWEETBOX' && seat.pairSeatId) {
        const pair = this.seats.find((s) => s.id === seat.pairSeatId)
        if (this.selectedSeatIds.includes(seatId)) {
          this.selectedSeatIds = this.selectedSeatIds.filter(
            (id) => id !== seatId && id !== seat.pairSeatId,
          )
        } else {
          if (!this.selectedSeatIds.includes(seatId)) this.selectedSeatIds.push(seatId)
          if (pair && !this.selectedSeatIds.includes(pair.id)) this.selectedSeatIds.push(pair.id)
        }
        return
      }

      if (this.selectedSeatIds.includes(seatId)) {
        this.selectedSeatIds = this.selectedSeatIds.filter((id) => id !== seatId)
      } else {
        if (this.selectedSeatIds.length >= 8) {
          this.errorMessage = 'Tối đa 8 ghế mỗi giao dịch.'
          setTimeout(() => { this.errorMessage = null }, 3000)
          return
        }
        this.selectedSeatIds.push(seatId)
      }
    },

    // ── Reservation & Payment (cập nhật dùng mock-confirm) ─

    async reserveAndCheckout() {
      if (this.selectedSeatIds.length === 0) return
      this.loading = true
      this.errorMessage = null
      try {
        const response = await ticketApi.reserveSeats({
          eventId: this.eventId,
          userId: this.userId,
          seatIds: this.selectedSeatIds,
        })
        this.currentOrder = response
        const payInfo = await ticketApi.getPaymentInfo(response.orderCode)
        this.paymentInfo = payInfo
        this.appView = 'CHECKOUT'
        this.startCountdown(600)
      } catch (err: any) {
        this.errorMessage = err?.response?.data?.message || 'Không thể giữ chỗ. Vui lòng thử ghế khác.'
      } finally {
        this.loading = false
      }
    },

    async simulatePaymentSuccess() {
      if (!this.currentOrder) return
      this.loading = true
      try {
        const result = await ticketApi.mockConfirmPayment(
          this.currentOrder.orderCode,
          this.paymentInfo?.amount || this.totalAmount,
        )
        if (result.status === 'SUCCESS') {
          this.issuedTickets = result.ticketCodes
          this.appView = 'TICKETS'
          this.stopCountdown()
        } else {
          this.errorMessage = result.message || 'Thanh toán thất bại.'
        }
      } catch (err: any) {
        this.errorMessage = 'Lỗi xác nhận thanh toán.'
        console.error(err)
      } finally {
        this.loading = false
      }
    },

    cancelOrder() {
      this.stopCountdown()
      this.currentOrder = null
      this.paymentInfo = null
      this.issuedTickets = []
      this.selectedSeatIds = []
      this.cart = []
      this.appView = 'SEAT_MAP'
      this.initSeats()
    },

    // ── Timer ──────────────────────────────────────────────

    startCountdown(seconds: number) {
      this.remainingSeconds = seconds
      this.stopCountdown()
      this.timerInterval = setInterval(() => {
        if (this.remainingSeconds > 0) {
          this.remainingSeconds--
        } else {
          this.stopCountdown()
          this.cancelOrder()
          this.errorMessage = 'Phiên đặt vé đã hết hạn. Ghế đã được trả lại.'
        }
      }, 1000)
    },

    stopCountdown() {
      if (this.timerInterval) {
        clearInterval(this.timerInterval)
        this.timerInterval = null
      }
    },
  },
})

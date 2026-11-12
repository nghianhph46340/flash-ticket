<script setup lang="ts">
import { useTicketStore } from '../stores/ticketStore'
import { Zap, AlertCircle, ShoppingCart, ArrowLeft } from 'lucide-vue-next'
import ConcessionCart from './ConcessionCart.vue'

const store = useTicketStore()

const formatVND = (amount: number) =>
  new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND' }).format(amount)

const tierLabel: Record<string, string> = {
  VIP: '⭐ VIP',
  STANDARD: 'Standard',
  SWEETBOX: '💑 Sweetbox',
  ECONOMY: 'Economy',
}
</script>

<template>
  <div class="bg-slate-900/80 backdrop-blur-md rounded-3xl border border-slate-800 p-6 shadow-2xl flex flex-col gap-4">

    <!-- Header: Back + Title -->
    <div class="flex items-center justify-between pb-4 border-b border-slate-800">
      <div class="flex items-center gap-2.5">
        <ShoppingCart class="w-4 h-4 text-amber-400" />
        <h3 class="text-sm font-bold text-white">Giỏ vé của bạn</h3>
      </div>
      <button
        type="button"
        @click="store.backToShowtimes()"
        class="flex items-center gap-1 text-[11px] text-slate-400 hover:text-white transition-colors cursor-pointer"
      >
        <ArrowLeft class="w-3.5 h-3.5" />
        <span>Đổi suất</span>
      </button>
    </div>

    <!-- Showtime info pill -->
    <div v-if="store.selectedShowtime" class="p-3 rounded-xl bg-slate-800/60 border border-slate-700/40 text-xs text-slate-300 space-y-1">
      <p class="font-bold text-white truncate">{{ store.selectedMovie?.title }}</p>
      <p class="text-slate-400">
        {{ new Date(store.selectedShowtime.startTime).toLocaleString('vi-VN', { weekday: 'short', day:'2-digit', month:'2-digit', hour:'2-digit', minute:'2-digit' }) }}
        · {{ store.selectedShowtime.auditoriumName }}
      </p>
    </div>

    <!-- Danh sách ghế đã chọn -->
    <div>
      <div v-if="store.selectedSeats.length === 0" class="text-center py-8 text-slate-500 text-xs">
        Chưa chọn ghế nào.<br/>Bấm vào sơ đồ bên trái để chọn.
      </div>
      <div v-else class="space-y-2">
        <div
          v-for="seat in store.selectedSeats"
          :key="seat.id"
          class="flex items-center justify-between p-2.5 rounded-xl bg-slate-800/60 border border-slate-700/40"
        >
          <div class="flex items-center gap-2.5">
            <span class="w-8 h-8 rounded-lg bg-emerald-500/20 border border-emerald-500/40 text-emerald-400 font-mono font-black text-xs flex items-center justify-center">
              {{ seat.id }}
            </span>
            <div>
              <p class="text-xs font-bold text-white">Hàng {{ seat.row }} · Ghế {{ seat.number }}</p>
              <p class="text-[11px] text-slate-400">{{ tierLabel[seat.tier] || seat.tier }}</p>
            </div>
          </div>
          <div class="text-right">
            <span class="text-xs font-mono font-bold text-amber-400">{{ formatVND(seat.price) }}</span>
            <button
              type="button"
              @click="store.toggleSeat(seat.id)"
              class="block ml-auto text-[10px] text-rose-400 hover:text-rose-300 mt-0.5 cursor-pointer"
            >Xóa</button>
          </div>
        </div>
      </div>
    </div>

    <!-- F&B Concessions -->
    <ConcessionCart v-if="store.selectedSeats.length > 0" />

    <!-- Tổng tiền & Nút xác nhận -->
    <div class="pt-4 border-t border-slate-800 space-y-3">
      <!-- Breakdown -->
      <div v-if="store.cart.length > 0" class="space-y-1 text-xs">
        <div class="flex justify-between text-slate-400">
          <span>Vé ({{ store.selectedSeats.length }} ghế)</span>
          <span>{{ formatVND(store.seatAmount) }}</span>
        </div>
        <div class="flex justify-between text-slate-400">
          <span>Bắp nước</span>
          <span>{{ formatVND(store.concessionAmount) }}</span>
        </div>
      </div>

      <div class="flex items-center justify-between">
        <span class="text-xs text-slate-400">Tổng thanh toán:</span>
        <span class="text-xl font-black font-mono text-emerald-400">{{ formatVND(store.totalAmount) }}</span>
      </div>

      <div class="p-3 rounded-xl bg-amber-500/10 border border-amber-500/20 text-[11px] text-amber-300/80 flex items-start gap-2">
        <AlertCircle class="w-4 h-4 shrink-0 text-amber-400 mt-0.5" />
        <span>Redis Lua Script giữ ghế nguyên tử 10 phút · Zero Overselling</span>
      </div>

      <button
        type="button"
        :disabled="store.selectedSeats.length === 0 || store.loading"
        @click="store.reserveAndCheckout()"
        class="w-full py-3.5 px-4 rounded-2xl font-bold text-sm tracking-wide flex items-center justify-center gap-2 transition-all duration-200 shadow-xl cursor-pointer"
        :class="[
          store.selectedSeats.length > 0 && !store.loading
            ? 'bg-gradient-to-r from-amber-500 to-rose-600 hover:from-amber-400 hover:to-rose-500 text-white shadow-rose-500/25 active:scale-[0.99]'
            : 'bg-slate-800 text-slate-500 cursor-not-allowed opacity-60'
        ]"
      >
        <Zap v-if="!store.loading" class="w-4 h-4 text-amber-300" />
        <span v-if="store.loading" class="w-4 h-4 mr-1 border-2 border-white/30 border-t-white rounded-full animate-spin"></span>
        <span>{{ store.loading ? 'Đang giữ chỗ...' : 'GIỮ CHỖ NGAY (SUB-20MS)' }}</span>
      </button>
    </div>
  </div>
</template>

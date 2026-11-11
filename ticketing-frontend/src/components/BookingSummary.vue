<script setup lang="ts">
import { useTicketStore } from '../stores/ticketStore'
import { Zap, AlertCircle, ShoppingCart } from 'lucide-vue-next'

const store = useTicketStore()

const formatVND = (amount: number) => {
  return new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND' }).format(amount)
}
</script>

<template>
  <div class="bg-slate-900/80 backdrop-blur-md rounded-3xl border border-slate-800 p-6 shadow-2xl flex flex-col justify-between h-full">
    <div>
      <div class="flex items-center justify-between pb-4 border-b border-slate-800">
        <h3 class="text-base font-bold text-white flex items-center gap-2">
          <ShoppingCart class="w-4 h-4 text-amber-400" />
          Giỏ vé của bạn
        </h3>
        <span class="px-2.5 py-1 text-xs rounded-full font-mono font-medium" :class="store.selectedSeats.length > 0 ? 'bg-amber-500/20 text-amber-400 border border-amber-500/30' : 'bg-slate-800 text-slate-500'">
          {{ store.selectedSeats.length }} / 4 ghế
        </span>
      </div>

      <!-- Danh sách ghế đang chọn -->
      <div class="py-4">
        <div v-if="store.selectedSeats.length === 0" class="text-center py-8 text-slate-500 text-sm">
          Chưa có ghế nào được chọn.<br />
          Vui lòng bấm vào sơ đồ để chọn ghế ngồi.
        </div>

        <div v-else class="space-y-2.5">
          <div
            v-for="seat in store.selectedSeats"
            :key="seat.id"
            class="flex items-center justify-between p-3 rounded-2xl bg-slate-800/60 border border-slate-700/50"
          >
            <div class="flex items-center gap-3">
              <span class="w-8 h-8 rounded-xl bg-emerald-500/20 border border-emerald-500/40 text-emerald-400 font-mono font-black text-xs flex items-center justify-center">
                {{ seat.id }}
              </span>
              <div>
                <p class="text-xs font-bold text-white">Hàng {{ seat.row }} • Ghế {{ seat.number }}</p>
                <p class="text-[11px] text-slate-400">{{ seat.tier }}</p>
              </div>
            </div>
            <div class="text-right">
              <span class="text-xs font-mono font-bold text-amber-400">
                {{ formatVND(seat.price) }}
              </span>
              <button
                type="button"
                @click="store.toggleSeat(seat.id)"
                class="block ml-auto text-[10px] text-rose-400 hover:text-rose-300 mt-0.5 cursor-pointer"
              >
                Xóa
              </button>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- Tổng tiền & Nút xác nhận -->
    <div class="pt-4 border-t border-slate-800 space-y-4">
      <div class="flex items-center justify-between text-sm">
        <span class="text-slate-400">Tổng thanh toán:</span>
        <span class="text-xl font-black font-mono text-emerald-400">
          {{ formatVND(store.totalAmount) }}
        </span>
      </div>

      <div class="p-3 rounded-xl bg-amber-500/10 border border-amber-500/20 text-[11px] text-amber-300/80 flex items-start gap-2">
        <AlertCircle class="w-4 h-4 shrink-0 text-amber-400 mt-0.5" />
        <span>Hệ thống Redis Lua Script sẽ giữ ghế nguyên tử trong 10 phút để thanh toán qua VietQR.</span>
      </div>

      <button
        type="button"
        :disabled="store.selectedSeats.length === 0 || store.loading"
        @click="store.reserveSeats()"
        class="w-full py-3.5 px-4 rounded-2xl font-bold text-sm tracking-wide flex items-center justify-center gap-2 transition-all duration-200 shadow-xl cursor-pointer"
        :class="[
          store.selectedSeats.length > 0 && !store.loading
            ? 'bg-gradient-to-r from-amber-500 to-rose-600 hover:from-amber-400 hover:to-rose-500 text-white shadow-rose-500/25 active:scale-[0.99]'
            : 'bg-slate-800 text-slate-500 cursor-not-allowed opacity-60'
        ]"
      >
        <Zap v-if="!store.loading" class="w-4 h-4 text-amber-300" />
        <span v-if="store.loading" class="animate-spin mr-2">⏳</span>
        <span>{{ store.loading ? 'Đang xác nhận với Redis Lua...' : 'GIỮ CHỖ NGAY (SUB-20MS)' }}</span>
      </button>
    </div>
  </div>
</template>

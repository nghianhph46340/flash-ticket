<script setup lang="ts">
import { computed } from 'vue'
import { useTicketStore } from '../stores/ticketStore'
import type { Seat, SeatStatus } from '../types'
import { Lock } from 'lucide-vue-next'

const store = useTicketStore()

// Lấy danh sách hàng độc nhất: A -> H
const rows = computed(() => {
  const rowSet = new Set(store.seats.map((s) => s.row))
  return Array.from(rowSet).sort()
})

const getSeatsByRow = (row: string) => {
  return store.seats.filter((s) => s.row === row).sort((a, b) => a.number - b.number)
}

const getEffectiveStatus = (seat: Seat): SeatStatus => {
  if (store.selectedSeatIds.includes(seat.id)) {
    return 'SELECTED'
  }
  return seat.status
}

const getSeatColor = (seat: Seat) => {
  const status = getEffectiveStatus(seat)
  switch (status) {
    case 'SELECTED':
      return 'bg-emerald-500 border-emerald-400 text-slate-950 font-black shadow-lg shadow-emerald-500/40 ring-2 ring-emerald-300 ring-offset-2 ring-offset-slate-950 scale-105 z-10'
    case 'HELD':
      return 'bg-rose-500/25 border-rose-500 text-rose-300 font-bold animate-pulse cursor-not-allowed shadow-inner shadow-rose-500/30'
    case 'SOLD':
      return 'bg-slate-900/90 border-slate-800 text-slate-600 line-through opacity-45 cursor-not-allowed'
    case 'AVAILABLE':
    default:
      if (seat.tier === 'VIP') {
        return 'bg-amber-500/15 border-amber-500/40 text-amber-300 hover:bg-amber-500/30 hover:border-amber-400 hover:shadow-md hover:shadow-amber-500/20 hover:scale-105'
      } else if (seat.tier === 'STANDARD') {
        return 'bg-cyan-500/15 border-cyan-500/40 text-cyan-300 hover:bg-cyan-500/30 hover:border-cyan-400 hover:shadow-md hover:shadow-cyan-500/20 hover:scale-105'
      } else {
        return 'bg-purple-500/15 border-purple-500/40 text-purple-300 hover:bg-purple-500/30 hover:border-purple-400 hover:shadow-md hover:shadow-purple-500/20 hover:scale-105'
      }
  }
}

const formatVND = (amount: number) => {
  return new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND' }).format(amount)
}
</script>

<template>
  <div class="relative bg-slate-900/60 rounded-3xl border border-slate-800 p-6 shadow-2xl flex flex-col items-center">
    <!-- Sân Khấu Chính (Stage) -->
    <div class="w-full max-w-2xl mb-10 flex flex-col items-center">
      <div class="w-full h-11 rounded-b-3xl bg-gradient-to-b from-rose-500/30 via-purple-500/20 to-transparent border-t-2 border-rose-400/70 flex items-center justify-center shadow-lg shadow-rose-500/10">
        <span class="text-xs font-black tracking-widest text-rose-300 uppercase flex items-center gap-2">
          ✦ SÂN KHẤU TRUNG TÂM (MAIN STAGE) ✦
        </span>
      </div>
      <div class="w-3/4 h-3 bg-gradient-to-r from-transparent via-rose-500/40 to-transparent blur-sm"></div>
    </div>

    <!-- Sơ đồ ghế Tương tác -->
    <div class="w-full overflow-x-auto flex justify-center py-2">
      <div class="min-w-[760px] flex flex-col gap-3.5 items-center">
        <div
          v-for="row in rows"
          :key="row"
          class="flex items-center gap-3"
        >
          <!-- Nhãn hàng bên trái -->
          <div class="w-7 text-center text-xs font-mono font-bold text-slate-400 bg-slate-800/50 py-1 rounded-md border border-slate-700/50">
            {{ row }}
          </div>

          <!-- Dãy 10 ghế của hàng -->
          <div class="flex items-center gap-2.5">
            <!-- Phân chia 2 bên khán phòng tạo lối đi giữa (sau ghế số 5) -->
            <template v-for="(seat, idx) in getSeatsByRow(row)" :key="seat.id">
              <div v-if="idx === 5" class="w-8 flex items-center justify-center">
                <span class="w-1 h-6 bg-slate-800 rounded-full"></span>
              </div>

              <button
                type="button"
                :title="`Ghế ${seat.id} - ${seat.tier} (${formatVND(seat.price)})`"
                :disabled="seat.status === 'SOLD' || seat.status === 'HELD'"
                @click="store.toggleSeat(seat.id)"
                class="group relative w-10 h-10 rounded-xl border-2 flex items-center justify-center transition-all duration-200 transform focus:outline-none"
                :class="[
                  getSeatColor(seat),
                  seat.status === 'AVAILABLE' ? 'cursor-pointer active:scale-95' : '',
                ]"
              >
                <!-- Ký hiệu số ghế -->
                <span
                  class="text-[11px] font-mono font-bold"
                  :class="getEffectiveStatus(seat) === 'SELECTED' ? 'text-slate-950 font-black' : ''"
                >
                  {{ seat.id }}
                </span>

                <!-- Icon ổ khóa nhỏ khi ghế đang bị người khác giữ -->
                <Lock
                  v-if="seat.status === 'HELD' && getEffectiveStatus(seat) !== 'SELECTED'"
                  class="w-2.5 h-2.5 absolute top-0.5 right-0.5 text-rose-400"
                />

                <!-- Tooltip hover -->
                <div class="absolute bottom-full mb-2 hidden group-hover:flex flex-col items-center z-30 pointer-events-none">
                  <div class="bg-slate-950 border border-slate-700 text-slate-200 text-[11px] rounded-lg py-1.5 px-3 shadow-2xl whitespace-nowrap">
                    <p class="font-bold text-white flex items-center gap-1.5">
                      <span>Ghế {{ seat.id }}</span>
                      <span class="text-[10px] px-1.5 py-0.2 rounded font-normal bg-slate-800 text-slate-300">
                        {{ seat.tier }}
                      </span>
                    </p>
                    <p class="text-amber-400 font-mono font-bold">{{ formatVND(seat.price) }}</p>
                    <p v-if="seat.status === 'HELD'" class="text-rose-400 text-[10px] font-medium flex items-center gap-1 mt-0.5">
                      <span class="w-1.5 h-1.5 rounded-full bg-rose-500 animate-ping"></span>
                      Đang được giữ chỗ (Khóa 10 phút)
                    </p>
                    <p v-else-if="seat.status === 'SOLD'" class="text-slate-500 text-[10px] mt-0.5">
                      Đã bán hoàn tất
                    </p>
                    <p v-else-if="getEffectiveStatus(seat) === 'SELECTED'" class="text-emerald-400 text-[10px] font-medium mt-0.5">
                      Đang được bạn chọn
                    </p>
                    <p v-else class="text-emerald-400/80 text-[10px] mt-0.5">
                      Sẵn sàng đặt
                    </p>
                  </div>
                  <div class="w-2 h-2 bg-slate-950 border-r border-b border-slate-700 transform rotate-45 -mt-1"></div>
                </div>
              </button>
            </template>
          </div>

          <!-- Nhãn hàng bên phải -->
          <div class="w-7 text-center text-xs font-mono font-bold text-slate-400 bg-slate-800/50 py-1 rounded-md border border-slate-700/50">
            {{ row }}
          </div>
        </div>
      </div>
    </div>

    <!-- Chú thích Sơ đồ (Legend) -->
    <div class="mt-10 pt-6 border-t border-slate-800/80 w-full flex flex-wrap items-center justify-center gap-6 text-xs text-slate-400">
      <!-- Tiers -->
      <div class="flex items-center gap-2">
        <div class="w-4 h-4 rounded-md bg-amber-500/20 border-2 border-amber-500/50"></div>
        <span>VIP (500.000 đ)</span>
      </div>
      <div class="flex items-center gap-2">
        <div class="w-4 h-4 rounded-md bg-cyan-500/20 border-2 border-cyan-500/50"></div>
        <span>Standard (250.000 đ)</span>
      </div>
      <div class="flex items-center gap-2">
        <div class="w-4 h-4 rounded-md bg-purple-500/20 border-2 border-purple-500/50"></div>
        <span>Economy (150.000 đ)</span>
      </div>

      <div class="w-px h-4 bg-slate-800 hidden sm:block"></div>

      <!-- Statuses -->
      <div class="flex items-center gap-2">
        <div class="w-4 h-4 rounded-md bg-emerald-500 border-2 border-emerald-400 shadow-sm shadow-emerald-500/50"></div>
        <span class="text-emerald-400 font-medium">Bạn đang chọn</span>
      </div>
      <div class="flex items-center gap-2">
        <div class="w-4 h-4 rounded-md bg-rose-500/30 border-2 border-rose-500 animate-pulse"></div>
        <span class="text-rose-400 font-medium">Người khác đang giữ (10p)</span>
      </div>
      <div class="flex items-center gap-2">
        <div class="w-4 h-4 rounded-md bg-slate-900 border-2 border-slate-800 opacity-60"></div>
        <span class="text-slate-500">Đã bán</span>
      </div>
    </div>
  </div>
</template>

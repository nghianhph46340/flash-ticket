<script setup lang="ts">
import { useTicketStore } from '../stores/ticketStore'
import { Clock, Monitor, ArrowLeft, Ticket, Loader2, ChevronRight } from 'lucide-vue-next'

const store = useTicketStore()

const formatTime = (iso: string) => {
  const d = new Date(iso)
  return d.toLocaleTimeString('vi-VN', { hour: '2-digit', minute: '2-digit' })
}

const formatDate = (iso: string) => {
  const d = new Date(iso)
  return d.toLocaleDateString('vi-VN', { weekday: 'short', day: '2-digit', month: '2-digit' })
}

const formatVND = (amount: number) =>
  new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND' }).format(amount)

const screenFormatLabel: Record<string, string> = {
  STANDARD_2D: '2D',
  STANDARD_3D: '3D',
  IMAX: 'IMAX',
  DOLBY_ATMOS: 'Dolby',
  FOUR_DX: '4DX',
}

const screenFormatColor: Record<string, string> = {
  STANDARD_2D: 'bg-slate-700/60 text-slate-300 border-slate-600',
  STANDARD_3D: 'bg-cyan-500/20 text-cyan-300 border-cyan-500/40',
  IMAX:        'bg-amber-500/20 text-amber-300 border-amber-500/40',
  DOLBY_ATMOS: 'bg-purple-500/20 text-purple-300 border-purple-500/40',
  FOUR_DX:     'bg-rose-500/20 text-rose-300 border-rose-500/40',
}
</script>

<template>
  <div class="w-full">
    <!-- Back + Title -->
    <div class="flex items-center gap-4 mb-6">
      <button
        type="button"
        @click="store.backToMovies()"
        class="flex items-center gap-1.5 text-xs text-slate-400 hover:text-white transition-colors cursor-pointer"
      >
        <ArrowLeft class="w-4 h-4" />
        <span>Quay lại</span>
      </button>
      <div class="h-4 w-px bg-slate-700"></div>
      <div class="flex items-center gap-2.5">
        <!-- Mini Poster -->
        <img
          v-if="store.selectedMovie?.posterUrl"
          :src="store.selectedMovie.posterUrl"
          :alt="store.selectedMovie.title"
          class="w-8 h-12 rounded object-cover"
        />
        <div>
          <h2 class="text-sm font-black text-white">{{ store.selectedMovie?.title }}</h2>
          <p class="text-[11px] text-slate-400">
            {{ store.selectedMovie?.durationMinutes }} phút
            <span class="ml-1 px-1.5 py-0.5 rounded text-[10px] font-bold bg-rose-500/20 text-rose-400 border border-rose-500/30">
              {{ store.selectedMovie?.ageRating }}
            </span>
          </p>
        </div>
      </div>
    </div>

    <h3 class="text-xs font-bold text-slate-400 uppercase tracking-widest mb-4">Chọn suất chiếu</h3>

    <!-- Loading -->
    <div v-if="store.loading" class="flex items-center justify-center py-16 gap-3 text-slate-400">
      <Loader2 class="w-5 h-5 animate-spin" />
      <span class="text-sm">Đang tải lịch chiếu...</span>
    </div>

    <!-- Showtime List -->
    <div v-else class="grid grid-cols-1 sm:grid-cols-2 gap-3">
      <button
        v-for="showtime in store.showtimes"
        :key="showtime.id"
        type="button"
        @click="store.selectShowtime(showtime)"
        class="group text-left p-4 rounded-2xl bg-slate-900/80 border border-slate-800 hover:border-amber-500/60 hover:bg-slate-800/80 hover:shadow-lg hover:shadow-amber-500/5 transition-all duration-200 cursor-pointer"
      >
        <!-- Time & Format row -->
        <div class="flex items-center justify-between mb-3">
          <div class="flex items-center gap-2">
            <Clock class="w-4 h-4 text-amber-400" />
            <span class="text-lg font-black text-white">{{ formatTime(showtime.startTime) }}</span>
          </div>
          <span
            class="px-2 py-0.5 rounded-md text-[11px] font-bold border"
            :class="screenFormatColor[showtime.screenFormat || 'STANDARD_2D']"
          >
            {{ screenFormatLabel[showtime.screenFormat || 'STANDARD_2D'] || showtime.screenFormat }}
          </span>
        </div>

        <!-- Date & Auditorium -->
        <div class="text-xs text-slate-400 space-y-1 mb-3">
          <div class="flex items-center gap-1.5">
            <span class="font-mono text-slate-300">{{ formatDate(showtime.startTime) }}</span>
            <span class="text-slate-600">→</span>
            <span class="font-mono text-slate-400">{{ formatTime(showtime.endTime) }}</span>
          </div>
          <div class="flex items-center gap-1.5">
            <Monitor class="w-3 h-3" />
            <span>{{ showtime.auditoriumName || 'Phòng chiếu' }}</span>
          </div>
        </div>

        <!-- Price & CTA -->
        <div class="flex items-center justify-between pt-3 border-t border-slate-800">
          <div>
            <span class="text-[10px] text-slate-500 block">Từ</span>
            <span class="text-sm font-black text-emerald-400">{{ formatVND(showtime.basePrice) }}</span>
          </div>
          <div class="flex items-center gap-1 text-xs text-amber-400 font-semibold group-hover:gap-2 transition-all">
            <Ticket class="w-3.5 h-3.5" />
            <span>Chọn ghế</span>
            <ChevronRight class="w-3.5 h-3.5" />
          </div>
        </div>
      </button>
    </div>

    <!-- Empty -->
    <div v-if="!store.loading && store.showtimes.length === 0" class="text-center py-16 text-slate-500">
      <Clock class="w-10 h-10 mx-auto mb-3 opacity-30" />
      <p class="text-sm">Không có suất chiếu nào cho phim này</p>
    </div>
  </div>
</template>

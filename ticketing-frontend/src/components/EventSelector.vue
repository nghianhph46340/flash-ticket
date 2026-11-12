<script setup lang="ts">
import { useTicketStore } from '../stores/ticketStore'
import { Clapperboard, Film, Clock, Popcorn } from 'lucide-vue-next'

const store = useTicketStore()
</script>

<template>
  <!-- Breadcrumb điều hướng — chỉ hiện khi đang ở màn hình chọn ghế -->
  <div
    v-if="store.appView === 'SEAT_MAP'"
    class="w-full bg-slate-900/70 border border-slate-800 rounded-2xl p-4 flex flex-wrap items-center gap-3 text-xs"
  >
    <!-- Bước 1: Phim -->
    <button
      type="button"
      @click="store.backToMovies()"
      class="flex items-center gap-1.5 text-slate-400 hover:text-amber-400 transition-colors cursor-pointer"
    >
      <Film class="w-3.5 h-3.5" />
      <span class="font-semibold">{{ store.selectedMovie?.title || 'Chọn phim' }}</span>
    </button>

    <span class="text-slate-700">›</span>

    <!-- Bước 2: Suất chiếu -->
    <button
      type="button"
      @click="store.backToShowtimes()"
      class="flex items-center gap-1.5 text-slate-400 hover:text-amber-400 transition-colors cursor-pointer"
    >
      <Clock class="w-3.5 h-3.5" />
      <span>
        {{
          store.selectedShowtime
            ? new Date(store.selectedShowtime.startTime).toLocaleString('vi-VN', {
                day: '2-digit', month: '2-digit', hour: '2-digit', minute: '2-digit'
              })
            : 'Chọn suất'
        }}
      </span>
    </button>

    <span class="text-slate-700">›</span>

    <!-- Bước 3: Chọn ghế (active) -->
    <div class="flex items-center gap-1.5 text-amber-400 font-bold">
      <Clapperboard class="w-3.5 h-3.5" />
      <span>Chọn ghế</span>
    </div>

    <!-- Spacer -->
    <div class="flex-1"></div>

    <!-- Hiển thị phòng & format -->
    <div v-if="store.selectedShowtime" class="flex items-center gap-2 text-slate-500">
      <Popcorn class="w-3.5 h-3.5" />
      <span>{{ store.selectedShowtime.auditoriumName }}</span>
      <span class="px-1.5 py-0.5 rounded bg-slate-800 border border-slate-700 text-slate-400 font-mono text-[10px]">
        {{ store.selectedShowtime.screenFormat?.replace('STANDARD_', '') || '2D' }}
      </span>
    </div>
  </div>
</template>

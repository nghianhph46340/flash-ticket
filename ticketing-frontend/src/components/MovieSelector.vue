<script setup lang="ts">
import { useTicketStore } from '../stores/ticketStore'
import { Film, Clock, ChevronRight, Loader2 } from 'lucide-vue-next'

const store = useTicketStore()

const ageRatingColor: Record<string, string> = {
  P:   'bg-emerald-500/20 text-emerald-400 border-emerald-500/30',
  K:   'bg-cyan-500/20    text-cyan-400    border-cyan-500/30',
  T13: 'bg-amber-500/20   text-amber-400   border-amber-500/30',
  T16: 'bg-orange-500/20  text-orange-400  border-orange-500/30',
  T18: 'bg-rose-500/20    text-rose-400    border-rose-500/30',
}

const formatDuration = (mins: number) => {
  const h = Math.floor(mins / 60)
  const m = mins % 60
  return h > 0 ? `${h}g ${m}p` : `${m} phút`
}
</script>

<template>
  <div class="w-full">
    <!-- Header -->
    <div class="flex items-center gap-3 mb-6">
      <div class="w-9 h-9 rounded-xl bg-rose-500/20 border border-rose-500/30 flex items-center justify-center text-rose-400">
        <Film class="w-5 h-5" />
      </div>
      <div>
        <h2 class="text-base font-black text-white uppercase tracking-wide">Phim đang chiếu</h2>
        <p class="text-xs text-slate-400">Chọn phim để xem lịch chiếu và đặt vé</p>
      </div>
    </div>

    <!-- Loading -->
    <div v-if="store.loading" class="flex items-center justify-center py-20 gap-3 text-slate-400">
      <Loader2 class="w-5 h-5 animate-spin" />
      <span class="text-sm">Đang tải danh sách phim...</span>
    </div>

    <!-- Movie Grid -->
    <div v-else class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-5">
      <button
        v-for="movie in store.movies"
        :key="movie.id"
        type="button"
        @click="store.selectMovie(movie)"
        class="group text-left bg-slate-900/80 border border-slate-800 rounded-2xl overflow-hidden hover:border-slate-600 hover:shadow-xl hover:shadow-slate-900/50 transition-all duration-200 cursor-pointer"
      >
        <!-- Poster -->
        <div class="relative aspect-[2/3] overflow-hidden bg-slate-800">
          <img
            v-if="movie.posterUrl"
            :src="movie.posterUrl"
            :alt="movie.title"
            class="w-full h-full object-cover group-hover:scale-105 transition-transform duration-300"
            loading="lazy"
          />
          <div v-else class="w-full h-full flex items-center justify-center text-slate-600">
            <Film class="w-16 h-16" />
          </div>

          <!-- Age Rating Badge -->
          <span
            class="absolute top-2.5 left-2.5 px-2 py-0.5 rounded-md text-[11px] font-black border"
            :class="ageRatingColor[movie.ageRating] || ageRatingColor['P']"
          >
            {{ movie.ageRating }}
          </span>
        </div>

        <!-- Info -->
        <div class="p-4">
          <h3 class="font-black text-sm text-white line-clamp-2 leading-snug mb-2 group-hover:text-amber-300 transition-colors">
            {{ movie.title }}
          </h3>
          <div class="flex items-center justify-between text-xs text-slate-400">
            <div class="flex items-center gap-1.5">
              <Clock class="w-3.5 h-3.5" />
              <span>{{ formatDuration(movie.durationMinutes) }}</span>
            </div>
            <div class="flex items-center gap-1 text-amber-400 font-semibold">
              <span>Chọn suất</span>
              <ChevronRight class="w-3.5 h-3.5" />
            </div>
          </div>
          <p v-if="movie.director" class="text-[11px] text-slate-500 mt-1.5 truncate">
            Đạo diễn: {{ movie.director }}
          </p>
        </div>
      </button>
    </div>

    <!-- Empty state -->
    <div v-if="!store.loading && store.movies.length === 0" class="text-center py-20 text-slate-500">
      <Film class="w-12 h-12 mx-auto mb-3 opacity-30" />
      <p class="text-sm">Không có phim nào đang chiếu</p>
    </div>
  </div>
</template>

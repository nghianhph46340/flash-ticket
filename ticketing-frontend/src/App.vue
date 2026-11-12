<script setup lang="ts">
import { onMounted } from 'vue'
import { useTicketStore } from './stores/ticketStore'
import Header from './components/Header.vue'
import MovieSelector from './components/MovieSelector.vue'
import ShowtimeSelector from './components/ShowtimeSelector.vue'
import SeatMap from './components/SeatMap.vue'
import BookingSummary from './components/BookingSummary.vue'
import CheckoutModal from './components/CheckoutModal.vue'
import TicketPassModal from './components/TicketPassModal.vue'
import { Zap, ShieldAlert } from 'lucide-vue-next'

const store = useTicketStore()

onMounted(async () => {
  await store.loadMovies()
})
</script>

<template>
  <div class="min-h-screen bg-slate-950 text-slate-100 flex flex-col selection:bg-rose-500 selection:text-white">
    <Header />

    <main class="flex-1 max-w-7xl w-full mx-auto px-4 sm:px-6 lg:px-8 py-8 flex flex-col gap-6">

      <!-- Architecture Banner -->
      <div class="p-4 rounded-2xl bg-gradient-to-r from-slate-900 via-slate-850 to-slate-900 border border-slate-800 flex flex-wrap items-center justify-between gap-4 shadow-xl">
        <div class="flex items-center gap-3">
          <div class="w-8 h-8 rounded-lg bg-amber-500/20 border border-amber-500/30 flex items-center justify-center text-amber-400">
            <Zap class="w-4 h-4" />
          </div>
          <div>
            <h1 class="text-sm font-bold text-white flex items-center gap-2">
              FlashTicket Cinema
              <span class="text-[10px] px-2 py-0.5 rounded-full bg-emerald-500/20 text-emerald-400 font-mono">SUB-20MS P99</span>
            </h1>
            <p class="text-xs text-slate-400">Zero Overselling · Redis Lua Atomic Lock · NATS JetStream · Mock Payment Gateway</p>
          </div>
        </div>
        <div class="flex items-center gap-2 text-[11px] text-slate-400">
          <span class="px-2.5 py-1 rounded-lg bg-slate-800 border border-slate-700/60 font-mono">Redis 7.2</span>
          <span class="px-2.5 py-1 rounded-lg bg-slate-800 border border-slate-700/60 font-mono">NATS JetStream</span>
          <span class="px-2.5 py-1 rounded-lg bg-slate-800 border border-slate-700/60 font-mono">Spring Boot 3</span>
        </div>
      </div>

      <!-- Error Alert -->
      <div
        v-if="store.errorMessage"
        class="p-4 rounded-2xl bg-rose-500/10 border border-rose-500/30 text-rose-300 text-xs flex items-center justify-between"
      >
        <div class="flex items-center gap-2">
          <ShieldAlert class="w-4 h-4 text-rose-400" />
          <span>{{ store.errorMessage }}</span>
        </div>
        <button
          type="button"
          @click="store.errorMessage = null"
          class="px-2.5 py-1 rounded bg-rose-500/20 hover:bg-rose-500/30 text-rose-300 text-[11px] font-bold cursor-pointer"
        >Đóng</button>
      </div>

      <!-- ── Màn hình chọn phim ── -->
      <div v-if="store.appView === 'MOVIE_SELECT'">
        <MovieSelector />
      </div>

      <!-- ── Màn hình chọn suất chiếu ── -->
      <div v-else-if="store.appView === 'SHOWTIME_SELECT'">
        <ShowtimeSelector />
      </div>

      <!-- ── Màn hình chọn ghế ── -->
      <div v-else-if="store.appView === 'SEAT_MAP'" class="flex flex-col gap-6">
        <!-- Realtime stats bar -->
        <div class="flex flex-wrap items-center gap-3 text-xs font-mono">
          <div class="px-3 py-1.5 rounded-xl bg-slate-800/80 border border-slate-700/60 flex items-center gap-2">
            <span class="w-2 h-2 rounded-full bg-emerald-400 animate-pulse"></span>
            <span class="text-slate-400">Còn trống:</span>
            <span class="text-emerald-400 font-bold">{{ store.availableCount }}</span>
          </div>
          <div class="px-3 py-1.5 rounded-xl bg-slate-800/80 border border-slate-700/60 flex items-center gap-2">
            <span class="w-2 h-2 rounded-full bg-rose-400 animate-ping"></span>
            <span class="text-slate-400">Đang giữ:</span>
            <span class="text-rose-400 font-bold">{{ store.heldCount }}</span>
          </div>
          <div class="px-3 py-1.5 rounded-xl bg-slate-800/80 border border-slate-700/60 flex items-center gap-2">
            <span class="w-2 h-2 rounded-full bg-slate-500"></span>
            <span class="text-slate-400">Đã bán:</span>
            <span class="text-slate-300 font-bold">{{ store.soldCount }}</span>
          </div>
          <div class="ml-auto flex items-center gap-2">
            <span
              class="w-2 h-2 rounded-full"
              :class="store.wsConnected ? 'bg-emerald-400 animate-pulse' : 'bg-slate-600'"
            ></span>
            <span class="text-slate-400">{{ store.wsConnected ? 'WebSocket Live' : 'Đang kết nối...' }}</span>
          </div>
        </div>

        <!-- Two-column layout -->
        <div class="grid grid-cols-1 lg:grid-cols-12 gap-8 items-start">
          <div class="lg:col-span-8">
            <SeatMap />
          </div>
          <div class="lg:col-span-4 sticky top-24">
            <BookingSummary />
          </div>
        </div>
      </div>

    </main>

    <!-- Modals -->
    <CheckoutModal v-if="store.appView === 'CHECKOUT'" />
    <TicketPassModal v-if="store.appView === 'TICKETS'" />

    <!-- Footer -->
    <footer class="mt-auto border-t border-slate-800/80 py-6 text-center text-xs text-slate-500">
      <div class="max-w-7xl mx-auto px-4 flex flex-col sm:flex-row items-center justify-between gap-3">
        <p>© 2026 FlashTicket Cinema · High-Load Architecture · VPS 3-4GB RAM optimized</p>
        <p class="font-mono text-slate-400">GraalVM Native · Vue 3 + Tailwind · WebSocket STOMP</p>
      </div>
    </footer>
  </div>
</template>

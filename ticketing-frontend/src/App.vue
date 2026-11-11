<script setup lang="ts">
import { onMounted } from 'vue'
import { useTicketStore } from './stores/ticketStore'
import Header from './components/Header.vue'
import EventSelector from './components/EventSelector.vue'
import SeatMap from './components/SeatMap.vue'
import BookingSummary from './components/BookingSummary.vue'
import CheckoutModal from './components/CheckoutModal.vue'
import TicketPassModal from './components/TicketPassModal.vue'
import { Zap, ShieldAlert } from 'lucide-vue-next'

const store = useTicketStore()

onMounted(async () => {
  await store.initSeats()
  store.initWebSocket()
})
</script>

<template>
  <div class="min-h-screen bg-slate-950 text-slate-100 flex flex-col selection:bg-rose-500 selection:text-white">
    <!-- Header -->
    <Header />

    <!-- Main Container -->
    <main class="flex-1 max-w-7xl w-full mx-auto px-4 sm:px-6 lg:px-8 py-8 flex flex-col gap-8">
      <!-- Architecture Highlights Banner -->
      <div class="p-4 rounded-2xl bg-gradient-to-r from-slate-900 via-slate-850 to-slate-900 border border-slate-800 flex flex-wrap items-center justify-between gap-4 shadow-xl">
        <div class="flex items-center gap-3">
          <div class="w-8 h-8 rounded-lg bg-amber-500/20 border border-amber-500/30 flex items-center justify-center text-amber-400">
            <Zap class="w-4 h-4" />
          </div>
          <div>
            <h1 class="text-sm font-bold text-white flex items-center gap-2">
              FlashTicket Concurrency Engine
              <span class="text-[10px] px-2 py-0.5 rounded-full bg-emerald-500/20 text-emerald-400 font-mono">
                SUB-20MS P99
              </span>
            </h1>
            <p class="text-xs text-slate-400">
              Kiến trúc đặt vé chịu tải cao: Zero Overselling • Khóa ghế nguyên tử Redis Lua • Stream NATS JetStream • PostgreSQL Write-Behind
            </p>
          </div>
        </div>

        <div class="flex items-center gap-2 text-[11px] text-slate-400">
          <span class="px-2.5 py-1 rounded-lg bg-slate-800 border border-slate-700/60 font-mono">
            Redis 7.2 Cluster Tag
          </span>
          <span class="px-2.5 py-1 rounded-lg bg-slate-800 border border-slate-700/60 font-mono">
            NATS JetStream Deduplication
          </span>
          <span class="px-2.5 py-1 rounded-lg bg-slate-800 border border-slate-700/60 font-mono">
            VietQR NAPAS 24/7
          </span>
        </div>
      </div>

      <!-- Error Alert nếu có -->
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
          @click="store.initSeats()"
          class="px-2.5 py-1 rounded bg-rose-500/20 hover:bg-rose-500/30 text-rose-300 text-[11px] font-bold cursor-pointer"
        >
          Thử lại
        </button>
      </div>

      <!-- Event Selector -->
      <EventSelector />

      <!-- Two-column Layout: Seat Map & Booking Summary -->
      <div class="grid grid-cols-1 lg:grid-cols-12 gap-8 items-start">
        <!-- Cột trái: Sơ đồ ghế tương tác (8 cols) -->
        <div class="lg:col-span-8 flex flex-col gap-4">
          <SeatMap />
        </div>

        <!-- Cột phải: Giỏ vé & Nút đặt vé (4 cols) -->
        <div class="lg:col-span-4 sticky top-24">
          <BookingSummary />
        </div>
      </div>
    </main>

    <!-- Modals -->
    <CheckoutModal v-if="store.currentView === 'CHECKOUT'" />
    <TicketPassModal v-if="store.currentView === 'TICKETS'" />

    <!-- Footer -->
    <footer class="mt-auto border-t border-slate-800/80 py-6 text-center text-xs text-slate-500">
      <div class="max-w-7xl mx-auto px-4 flex flex-col sm:flex-row items-center justify-between gap-3">
        <p>© 2026 FlashTicket High-Load Architecture • Được thiết kế tối ưu FinOps cho VPS 3-4GB RAM.</p>
        <p class="font-mono text-slate-400">Spring Boot 3 GraalVM AOT • Vue 3 + Tailwind CSS • WebSocket STOMP</p>
      </div>
    </footer>
  </div>
</template>

<script setup lang="ts">
import { useTicketStore } from '../stores/ticketStore'
import { ShieldCheck, Ticket } from 'lucide-vue-next'
import CheckInModal from './CheckInModal.vue'

const store = useTicketStore()
</script>

<template>
  <header class="border-b border-slate-800 bg-slate-900/80 backdrop-blur-md sticky top-0 z-40">
    <div class="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 h-16 flex items-center justify-between">
      <!-- Logo & Event Name -->
      <div class="flex items-center gap-3">
        <div class="w-10 h-10 rounded-xl bg-gradient-to-tr from-amber-500 to-rose-600 flex items-center justify-center shadow-lg shadow-rose-500/20">
          <Ticket class="w-5 h-5 text-white" />
        </div>
        <div>
          <div class="flex items-center gap-2">
            <span class="text-xl font-black tracking-wider bg-clip-text text-transparent bg-gradient-to-r from-amber-400 via-rose-400 to-purple-400">
              FLASHTICKET
            </span>
            <span class="px-2 py-0.5 text-[10px] font-bold uppercase rounded bg-rose-500/20 text-rose-400 border border-rose-500/30">
              Zero Overselling
            </span>
          </div>
          <p class="text-xs text-slate-400 hidden sm:block truncate max-w-md">
            {{ store.selectedMovie?.title || 'Cinema Booking System' }}
            <span v-if="store.selectedShowtime">
              · {{ new Date(store.selectedShowtime.startTime).toLocaleTimeString('vi-VN', { hour: '2-digit', minute: '2-digit' }) }}
            </span>
          </p>
        </div>
      </div>

      <!-- Live Indicators -->
      <div class="flex items-center gap-3">
        <!-- Check-in Button -->
        <CheckInModal />

        <!-- WebSocket STOMP Badge -->
        <div class="flex items-center gap-2 px-3 py-1.5 rounded-full bg-slate-800/80 border border-slate-700/60 text-xs">
          <span class="relative flex h-2 w-2">
            <span
              v-if="store.wsConnected"
              class="animate-ping absolute inline-flex h-full w-full rounded-full bg-emerald-400 opacity-75"
            ></span>
            <span
              class="relative inline-flex rounded-full h-2 w-2"
              :class="store.wsConnected ? 'bg-emerald-500' : 'bg-rose-500'"
            ></span>
          </span>
          <span :class="store.wsConnected ? 'text-emerald-400 font-medium' : 'text-slate-400'">
            {{ store.wsConnected ? 'STOMP JetStream Live' : 'Đang kết nối WebSocket...' }}
          </span>
        </div>

        <!-- User Chip -->
        <div class="hidden md:flex items-center gap-1.5 px-3 py-1.5 rounded-full bg-slate-800/40 border border-slate-700/40 text-xs text-slate-300">
          <ShieldCheck class="w-3.5 h-3.5 text-amber-400" />
          <span class="font-mono text-slate-400">{{ store.userId }}</span>
        </div>
      </div>
    </div>
  </header>
</template>

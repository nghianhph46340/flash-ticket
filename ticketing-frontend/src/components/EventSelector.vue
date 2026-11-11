<script setup lang="ts">
import { useTicketStore } from '../stores/ticketStore'
import { Calendar, MapPin, Sparkles, Radio } from 'lucide-vue-next'

const store = useTicketStore()
</script>

<template>
  <div class="w-full bg-slate-900/70 border border-slate-800 rounded-3xl p-5 shadow-2xl backdrop-blur-md">
    <!-- Header của Event Selector -->
    <div class="flex flex-wrap items-center justify-between gap-4 mb-4 pb-4 border-b border-slate-800/80">
      <div class="flex items-center gap-2.5">
        <div class="w-8 h-8 rounded-xl bg-amber-500/20 border border-amber-500/30 flex items-center justify-center text-amber-400">
          <Sparkles class="w-4 h-4" />
        </div>
        <div>
          <h2 class="text-sm font-bold text-white uppercase tracking-wider flex items-center gap-2">
            Chọn chủ đề & sự kiện đặt vé
            <span class="text-[10px] font-mono px-2 py-0.5 rounded-full bg-rose-500/20 text-rose-300 border border-rose-500/30">
              Live Flash Sale
            </span>
          </h2>
          <p class="text-xs text-slate-400">
            Chuyển đổi sự kiện tức thì — Dữ liệu sơ đồ ghế và kênh WebSocket cập nhật độc lập theo từng Show
          </p>
        </div>
      </div>

      <!-- Quick Metrics Counter -->
      <div class="flex items-center gap-2 text-xs font-mono">
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
      </div>
    </div>

    <!-- Event Cards Grid -->
    <div class="grid grid-cols-1 md:grid-cols-3 gap-3.5">
      <button
        v-for="event in store.availableEvents"
        :key="event.id"
        type="button"
        @click="store.switchEvent(event.id)"
        class="text-left p-4 rounded-2xl border transition-all duration-200 cursor-pointer relative overflow-hidden group flex flex-col justify-between"
        :class="[
          store.eventId === event.id
            ? 'bg-slate-800/90 border-amber-500/80 shadow-lg shadow-amber-500/10 ring-1 ring-amber-400/50'
            : 'bg-slate-850/50 border-slate-800 hover:bg-slate-800/60 hover:border-slate-700'
        ]"
      >
        <!-- Background Gradient Glow khi Active -->
        <div
          v-if="store.eventId === event.id"
          class="absolute top-0 right-0 w-32 h-32 bg-gradient-to-bl from-amber-500/15 via-rose-500/10 to-transparent pointer-events-none rounded-bl-full"
        ></div>

        <!-- Top Row: Badge & Radio Indicator -->
        <div class="flex items-center justify-between gap-2 mb-2">
          <span
            class="text-[10px] font-bold px-2 py-0.5 rounded-md uppercase tracking-wider"
            :class="
              event.badge === 'HOT FLASH SALE'
                ? 'bg-rose-500/20 text-rose-300 border border-rose-500/30'
                : event.badge === 'OPENING'
                ? 'bg-amber-500/20 text-amber-300 border border-amber-500/30'
                : 'bg-cyan-500/20 text-cyan-300 border border-cyan-500/30'
            "
          >
            {{ event.badge }}
          </span>

          <div class="flex items-center gap-1.5 text-xs">
            <Radio
              class="w-3.5 h-3.5 transition-colors"
              :class="store.eventId === event.id ? 'text-amber-400' : 'text-slate-600 group-hover:text-slate-400'"
            />
            <span
              class="text-[11px] font-medium"
              :class="store.eventId === event.id ? 'text-amber-400 font-bold' : 'text-slate-500'"
            >
              {{ store.eventId === event.id ? 'Đang chọn' : 'Xem sơ đồ' }}
            </span>
          </div>
        </div>

        <!-- Middle: Title & Description -->
        <div class="mb-3">
          <h3
            class="text-sm font-bold transition-colors line-clamp-1"
            :class="store.eventId === event.id ? 'text-white' : 'text-slate-200 group-hover:text-white'"
          >
            {{ event.title }}
          </h3>
          <p class="text-xs text-slate-400 line-clamp-1 mt-0.5">
            {{ event.subTitle }}
          </p>
        </div>

        <!-- Bottom: Meta Info -->
        <div class="space-y-1 pt-2 border-t border-slate-800/80 text-[11px] text-slate-400">
          <div class="flex items-center gap-1.5">
            <Calendar class="w-3 h-3 text-slate-500 flex-shrink-0" />
            <span class="font-mono text-slate-300 truncate">{{ event.date }}</span>
          </div>
          <div class="flex items-center gap-1.5">
            <MapPin class="w-3 h-3 text-slate-500 flex-shrink-0" />
            <span class="truncate">{{ event.venue }}</span>
          </div>
        </div>
      </button>
    </div>
  </div>
</template>

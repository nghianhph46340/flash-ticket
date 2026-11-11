<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useTicketStore } from '../stores/ticketStore'
import { CheckCircle2, Calendar, MapPin, ArrowRight } from 'lucide-vue-next'
import QRCode from 'qrcode'

const store = useTicketStore()
const qrDataUrls = ref<Record<string, string>>({})

onMounted(async () => {
  for (const code of store.issuedTickets) {
    try {
      const url = await QRCode.toDataURL(code, {
        width: 160,
        margin: 1,
        color: {
          dark: '#0f172a',
          light: '#ffffff',
        },
      })
      qrDataUrls.value[code] = url
    } catch (err) {
      console.error('Failed to generate QR for ticket:', code, err)
    }
  }
})
</script>

<template>
  <div class="fixed inset-0 z-50 bg-slate-950/85 backdrop-blur-md flex items-center justify-center p-4 overflow-y-auto">
    <div class="bg-slate-900 border border-slate-800 rounded-3xl max-w-2xl w-full p-6 sm:p-8 shadow-2xl relative my-8">
      <!-- Success Badge -->
      <div class="flex items-center gap-3 pb-6 border-b border-slate-800">
        <div class="w-12 h-12 rounded-2xl bg-emerald-500/20 border border-emerald-500/40 flex items-center justify-center text-emerald-400">
          <CheckCircle2 class="w-7 h-7" />
        </div>
        <div>
          <div class="flex items-center gap-2">
            <h2 class="text-xl font-black text-white">Thanh toán thành công!</h2>
            <span class="px-2 py-0.5 rounded text-[10px] font-bold bg-emerald-500/20 text-emerald-400 border border-emerald-500/30">
              ĐÃ XÁC THỰC
            </span>
          </div>
          <p class="text-xs text-slate-400 mt-0.5">
            Vé điện tử chính thức đã được phát hành và ghi nhận vào cơ sở dữ liệu PostgreSQL.
          </p>
        </div>
      </div>

      <!-- Tickets List -->
      <div class="py-6 space-y-6">
        <div
          v-for="(ticketCode, idx) in store.issuedTickets"
          :key="ticketCode"
          class="relative bg-gradient-to-br from-slate-800/90 via-slate-850 to-slate-900 rounded-2xl border border-slate-700/60 overflow-hidden shadow-xl"
        >
          <!-- Neon Accent Line -->
          <div class="h-1.5 w-full bg-gradient-to-r from-amber-400 via-rose-500 to-purple-500"></div>

          <div class="p-5 flex flex-col sm:flex-row items-center justify-between gap-6">
            <!-- Event & Seat Info -->
            <div class="space-y-3 w-full">
              <div class="flex items-center justify-between">
                <span class="px-2.5 py-0.5 rounded-full text-[10px] font-black uppercase tracking-wider bg-rose-500/20 text-rose-400 border border-rose-500/30">
                  ✦ SUPER CONCERT 2026 ✦
                </span>
                <span class="text-xs font-mono font-bold text-slate-400">
                  Vé #{{ idx + 1 }}
                </span>
              </div>

              <div>
                <h4 class="text-base font-black text-white tracking-wide">
                  FLASHTICKET MUSIC FESTIVAL
                </h4>
                <div class="flex flex-col gap-1 mt-2 text-xs text-slate-300">
                  <div class="flex items-center gap-1.5">
                    <Calendar class="w-3.5 h-3.5 text-amber-400" />
                    <span>20:00 - 15/12/2026</span>
                  </div>
                  <div class="flex items-center gap-1.5">
                    <MapPin class="w-3.5 h-3.5 text-rose-400" />
                    <span>Sân Vận Động Quốc Gia Mỹ Đình, Hà Nội</span>
                  </div>
                </div>
              </div>

              <!-- Ticket Code & Seat Badge -->
              <div class="flex items-center gap-3 pt-2">
                <div class="p-2 rounded-xl bg-slate-900/80 border border-slate-700/80">
                  <span class="text-[10px] text-slate-500 block uppercase">Vị trí ghế</span>
                  <span class="text-sm font-black font-mono text-emerald-400">
                    {{ store.currentOrder?.reservedSeats[idx] || 'A1' }}
                  </span>
                </div>
                <div class="p-2 rounded-xl bg-slate-900/80 border border-slate-700/80">
                  <span class="text-[10px] text-slate-500 block uppercase">Mã vé Check-in</span>
                  <span class="text-xs font-mono font-bold text-amber-400">
                    {{ ticketCode }}
                  </span>
                </div>
              </div>
            </div>

            <!-- E-Ticket QR Code -->
            <div class="shrink-0 flex flex-col items-center bg-white p-2.5 rounded-2xl shadow-xl">
              <img
                v-if="qrDataUrls[ticketCode]"
                :src="qrDataUrls[ticketCode]"
                :alt="`QR ${ticketCode}`"
                class="w-32 h-32"
              />
              <span class="text-[9px] text-slate-600 font-bold uppercase mt-1">
                Scan tại cổng soát vé
              </span>
            </div>
          </div>
        </div>
      </div>

      <!-- Footer Buttons -->
      <div class="pt-4 border-t border-slate-800 flex items-center justify-end gap-3">
        <button
          type="button"
          @click="store.cancelOrder()"
          class="py-3 px-6 rounded-2xl bg-gradient-to-r from-amber-500 to-rose-600 hover:from-amber-400 hover:to-rose-500 text-white font-bold text-xs flex items-center gap-2 cursor-pointer shadow-lg shadow-rose-500/20"
        >
          <span>QUAY LẠI SƠ ĐỒ ĐẶT TIẾP</span>
          <ArrowRight class="w-4 h-4" />
        </button>
      </div>
    </div>
  </div>
</template>

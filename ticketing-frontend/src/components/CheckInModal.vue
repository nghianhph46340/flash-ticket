<script setup lang="ts">
import { ref } from 'vue'
import { useTicketStore } from '../stores/ticketStore'
import { checkInApi, type CheckInResponse } from '../services/api'
import { ScanLine, CheckCircle2, XCircle, Search, Ticket as TicketIcon } from 'lucide-vue-next'

const store = useTicketStore()

// Check-in state
const checkinTicketCode = ref('')
const checkinQrHash = ref('')
const checkinResult = ref<CheckInResponse | null>(null)

const showCheckIn = ref(false)
const loading = ref(false)

// Auto-fill từ vé vừa đặt
const fillLastTicket = () => {
  if (store.issuedTickets.length > 0) {
    checkinTicketCode.value = store.issuedTickets[0]
    // QR hash không có sẵn ở frontend — cần nhập thủ công cho demo
    checkinResult.value = null
  }
}

const performCheckIn = async () => {
  if (!checkinTicketCode.value || !checkinQrHash.value) return
  loading.value = true
  checkinResult.value = null
  try {
    checkinResult.value = await checkInApi.checkIn(
      checkinTicketCode.value,
      checkinQrHash.value,
    )
  } catch (err) {
    checkinResult.value = {
      success: false,
      ticketCode: checkinTicketCode.value,
      message: 'Lỗi kết nối. Vui lòng thử lại.',
    }
  } finally {
    loading.value = false
  }
}

const resetCheckIn = () => {
  checkinTicketCode.value = ''
  checkinQrHash.value = ''
  checkinResult.value = null
}

const formatDateTime = (iso?: string) => {
  if (!iso) return 'N/A'
  return new Date(iso).toLocaleString('vi-VN', {
    hour: '2-digit', minute: '2-digit',
    day: '2-digit', month: '2-digit', year: 'numeric',
  })
}
</script>

<template>
  <!-- Toggle Button -->
  <button
    type="button"
    @click="showCheckIn = !showCheckIn"
    class="px-3 py-1.5 rounded-lg bg-emerald-500/20 border border-emerald-500/40 text-emerald-400 text-xs font-bold flex items-center gap-1.5 cursor-pointer hover:bg-emerald-500/30 transition-colors"
  >
    <ScanLine class="w-4 h-4" />
    <span>Soát vé</span>
  </button>

  <!-- Check-in Modal -->
  <div
    v-if="showCheckIn"
    class="fixed inset-0 z-50 bg-slate-950/85 backdrop-blur-md flex items-center justify-center p-4"
    @click.self="showCheckIn = false"
  >
    <div class="bg-slate-900 border border-slate-800 rounded-3xl max-w-lg w-full p-6 shadow-2xl">
      <!-- Header -->
      <div class="flex items-center gap-3 pb-5 border-b border-slate-800">
        <div class="w-10 h-10 rounded-xl bg-emerald-500/20 border border-emerald-500/40 flex items-center justify-center text-emerald-400">
          <ScanLine class="w-5 h-5" />
        </div>
        <div>
          <h2 class="text-sm font-black text-white uppercase tracking-wide">Soát vé tại cửa rạp</h2>
          <p class="text-xs text-slate-400">Mô phỏng máy quét QR tại cổng vào</p>
        </div>
      </div>

      <!-- Input form -->
      <div v-if="!checkinResult" class="py-5 space-y-4">
        <!-- Auto-fill hint -->
        <div v-if="store.issuedTickets.length > 0" class="p-3 rounded-xl bg-amber-500/10 border border-amber-500/20 text-[11px] text-amber-300/80">
          Vé vừa đặt: <span class="font-mono font-bold text-amber-400">{{ store.issuedTickets[0] }}</span>
          <button type="button" @click="fillLastTicket" class="ml-2 text-amber-400 underline cursor-pointer hover:text-amber-300">Dùng vé này</button>
        </div>

        <div>
          <label class="text-xs text-slate-400 block mb-1.5 font-semibold">Mã vé</label>
          <input
            v-model="checkinTicketCode"
            type="text"
            placeholder="VD: TKT-7ACC27B33C"
            class="w-full px-4 py-2.5 rounded-xl bg-slate-800/60 border border-slate-700 text-white text-sm font-mono placeholder-slate-600 focus:border-emerald-500/50 focus:outline-none transition-colors"
          />
        </div>

        <div>
          <label class="text-xs text-slate-400 block mb-1.5 font-semibold">Mã QR (HMAC Hash)</label>
          <input
            v-model="checkinQrHash"
            type="text"
            placeholder="64 ký tự hex từ mã QR"
            class="w-full px-4 py-2.5 rounded-xl bg-slate-800/60 border border-slate-700 text-white text-xs font-mono placeholder-slate-600 focus:border-emerald-500/50 focus:outline-none transition-colors"
          />
          <p class="text-[10px] text-slate-500 mt-1">Mã hash xác thực vé thật, chống vé giả. Trong demo, nhập bất kỳ để test (hệ thống sẽ check với DB).</p>
        </div>

        <button
          type="button"
          :disabled="!checkinTicketCode || !checkinQrHash || loading"
          @click="performCheckIn"
          class="w-full py-3 rounded-2xl font-bold text-sm flex items-center justify-center gap-2 transition-all cursor-pointer"
          :class="[
            checkinTicketCode && checkinQrHash && !loading
              ? 'bg-gradient-to-r from-emerald-500 to-teal-600 hover:from-emerald-400 hover:to-teal-500 text-white shadow-lg shadow-emerald-500/25 active:scale-[0.99]'
              : 'bg-slate-800 text-slate-500 cursor-not-allowed opacity-60'
          ]"
        >
          <Search v-if="!loading" class="w-4 h-4" />
          <span v-if="loading" class="w-4 h-4 mr-1 border-2 border-white/30 border-t-white rounded-full animate-spin"></span>
          <span>{{ loading ? 'Đang quét...' : 'QUÉT VÉ' }}</span>
        </button>
      </div>

      <!-- Result -->
      <div v-else class="py-5">
        <!-- Success -->
        <div v-if="checkinResult.success" class="text-center space-y-4">
          <div class="w-16 h-16 rounded-full bg-emerald-500/20 border border-emerald-500/40 flex items-center justify-center mx-auto">
            <CheckCircle2 class="w-9 h-9 text-emerald-400" />
          </div>
          <div>
            <h3 class="text-lg font-black text-white">Check-in thành công!</h3>
            <p class="text-xs text-emerald-400 mt-0.5">{{ checkinResult.message }}</p>
          </div>

          <!-- Ticket details -->
          <div class="text-left space-y-2 p-4 rounded-xl bg-slate-800/60 border border-slate-700/50 text-sm">
            <div class="flex justify-between">
              <span class="text-slate-400">Mã vé:</span>
              <span class="font-mono font-bold text-amber-400">{{ checkinResult.ticketCode }}</span>
            </div>
            <div class="flex justify-between">
              <span class="text-slate-400">Ghế:</span>
              <span class="font-mono font-bold text-emerald-400">{{ checkinResult.seatNum }}</span>
            </div>
            <div v-if="checkinResult.movieTitle" class="flex justify-between">
              <span class="text-slate-400">Phim:</span>
              <span class="text-white font-semibold">{{ checkinResult.movieTitle }}</span>
            </div>
            <div v-if="checkinResult.auditoriumName" class="flex justify-between">
              <span class="text-slate-400">Phòng:</span>
              <span class="text-white">{{ checkinResult.auditoriumName }}</span>
            </div>
            <div v-if="checkinResult.showtimeStart" class="flex justify-between">
              <span class="text-slate-400">Suất chiếu:</span>
              <span class="text-white">{{ formatDateTime(checkinResult.showtimeStart) }}</span>
            </div>
            <div class="flex justify-between pt-2 border-t border-slate-700/50">
              <span class="text-slate-400">Trạng thái:</span>
              <span class="px-2 py-0.5 rounded text-[10px] font-bold bg-emerald-500/20 text-emerald-400 border border-emerald-500/30">{{ checkinResult.status }}</span>
            </div>
          </div>
        </div>

        <!-- Failure -->
        <div v-else class="text-center space-y-4">
          <div class="w-16 h-16 rounded-full bg-rose-500/20 border border-rose-500/40 flex items-center justify-center mx-auto">
            <XCircle class="w-9 h-9 text-rose-400" />
          </div>
          <div>
            <h3 class="text-lg font-black text-white">Check-in thất bại</h3>
            <p class="text-xs text-rose-400 mt-0.5">{{ checkinResult.message }}</p>
          </div>
          <div v-if="checkinResult.status" class="text-center">
            <span class="px-2.5 py-1 rounded-lg text-[11px] font-bold bg-rose-500/20 text-rose-400 border border-rose-500/30">
              {{ checkinResult.status }}
            </span>
          </div>
        </div>

        <button
          type="button"
          @click="resetCheckIn"
          class="w-full mt-4 py-2.5 rounded-xl bg-slate-800 hover:bg-slate-700 text-slate-300 text-xs font-bold cursor-pointer transition-colors flex items-center justify-center gap-2"
        >
          <TicketIcon class="w-3.5 h-3.5" />
          <span>Quét vé khác</span>
        </button>
      </div>
    </div>
  </div>
</template>

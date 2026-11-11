<script setup lang="ts">
import { ref } from 'vue'
import { useTicketStore } from '../stores/ticketStore'
import { Clock, Copy, Check, QrCode, ShieldCheck, XCircle } from 'lucide-vue-next'

const store = useTicketStore()
const copiedField = ref<string | null>(null)

const copyToClipboard = async (text: string, field: string) => {
  try {
    await navigator.clipboard.writeText(text)
    copiedField.value = field
    setTimeout(() => {
      copiedField.value = null
    }, 2000)
  } catch (err) {
    console.error('Failed to copy:', err)
  }
}

const formatVND = (amount: number) => {
  return new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND' }).format(amount)
}
</script>

<template>
  <div class="fixed inset-0 z-50 bg-slate-950/80 backdrop-blur-md flex items-center justify-center p-4">
    <div class="bg-slate-900 border border-slate-800 rounded-3xl max-w-xl w-full p-6 sm:p-8 shadow-2xl relative overflow-hidden">
      <!-- Header & Countdown -->
      <div class="flex items-center justify-between pb-6 border-b border-slate-800">
        <div>
          <h2 class="text-xl font-black text-white flex items-center gap-2">
            <QrCode class="w-5 h-5 text-amber-400" />
            Thanh toán VietQR NAPAS 24/7
          </h2>
          <p class="text-xs text-slate-400 mt-1">
            Đơn hàng: <span class="font-mono text-amber-400 font-bold">{{ store.currentOrder?.orderCode }}</span>
          </p>
        </div>

        <!-- Timer Badge -->
        <div class="flex items-center gap-2 px-3.5 py-1.5 rounded-full border text-xs font-mono font-bold"
          :class="store.remainingSeconds < 120 ? 'bg-rose-500/20 text-rose-400 border-rose-500/40 animate-pulse' : 'bg-amber-500/10 text-amber-400 border-amber-500/30'">
          <Clock class="w-3.5 h-3.5" />
          <span>{{ store.formattedTime }}</span>
        </div>
      </div>

      <!-- Main Payment Body -->
      <div class="py-6 flex flex-col sm:flex-row items-center gap-6">
        <!-- VietQR Image -->
        <div class="shrink-0 p-3 bg-white rounded-2xl shadow-xl flex flex-col items-center">
          <img
            v-if="store.paymentInfo?.qrImageUrl"
            :src="store.paymentInfo.qrImageUrl"
            alt="Mã VietQR Chuyển khoản"
            class="w-48 h-48 object-contain"
          />
          <span class="text-[10px] text-slate-600 font-bold mt-1 tracking-wider uppercase">
            Quét bằng app ngân hàng
          </span>
        </div>

        <!-- Bank Details -->
        <div class="w-full space-y-3 text-xs">
          <div class="p-2.5 rounded-xl bg-slate-800/60 border border-slate-700/60 flex items-center justify-between">
            <span class="text-slate-400">Số tiền:</span>
            <span class="font-mono font-black text-sm text-emerald-400">
              {{ formatVND(store.paymentInfo?.amount || store.totalAmount) }}
            </span>
          </div>

          <div class="p-2.5 rounded-xl bg-slate-800/60 border border-slate-700/60 flex items-center justify-between">
            <div>
              <span class="text-slate-400 block text-[10px]">Ngân hàng:</span>
              <span class="font-bold text-white">{{ store.paymentInfo?.bankName || 'MB BANK' }}</span>
            </div>
            <span class="text-slate-400 text-[11px]">{{ store.paymentInfo?.accountName || 'FLASHTICKET VIETNAM' }}</span>
          </div>

          <div class="p-2.5 rounded-xl bg-slate-800/60 border border-slate-700/60 flex items-center justify-between">
            <div>
              <span class="text-slate-400 block text-[10px]">Số tài khoản:</span>
              <span class="font-mono font-bold text-white">{{ store.paymentInfo?.accountNumber || '8888999966' }}</span>
            </div>
            <button
              type="button"
              @click="copyToClipboard(store.paymentInfo?.accountNumber || '8888999966', 'account')"
              class="px-2 py-1 rounded bg-slate-700 hover:bg-slate-600 text-slate-200 text-[10px] flex items-center gap-1 cursor-pointer"
            >
              <Check v-if="copiedField === 'account'" class="w-3 h-3 text-emerald-400" />
              <Copy v-else class="w-3 h-3" />
              <span>{{ copiedField === 'account' ? 'Đã chép' : 'Sao chép' }}</span>
            </button>
          </div>

          <div class="p-2.5 rounded-xl bg-slate-800/60 border border-slate-700/60 flex items-center justify-between">
            <div>
              <span class="text-slate-400 block text-[10px]">Nội dung CK:</span>
              <span class="font-mono font-bold text-amber-400">{{ store.currentOrder?.orderCode }}</span>
            </div>
            <button
              type="button"
              @click="copyToClipboard(store.currentOrder?.orderCode || '', 'code')"
              class="px-2 py-1 rounded bg-slate-700 hover:bg-slate-600 text-slate-200 text-[10px] flex items-center gap-1 cursor-pointer"
            >
              <Check v-if="copiedField === 'code'" class="w-3 h-3 text-emerald-400" />
              <Copy v-else class="w-3 h-3" />
              <span>{{ copiedField === 'code' ? 'Đã chép' : 'Sao chép' }}</span>
            </button>
          </div>
        </div>
      </div>

      <!-- Action Buttons -->
      <div class="pt-4 border-t border-slate-800 flex flex-col gap-3">
        <!-- Nút Giả lập Webhook Thanh toán Thành công cho Demo/Portfolio -->
        <button
          type="button"
          :disabled="store.loading"
          @click="store.simulatePaymentSuccess()"
          class="w-full py-3.5 px-4 rounded-2xl bg-gradient-to-r from-emerald-500 to-teal-600 hover:from-emerald-400 hover:to-teal-500 text-white font-bold text-sm tracking-wide shadow-lg shadow-emerald-500/25 flex items-center justify-center gap-2 cursor-pointer transition-all duration-200 active:scale-[0.99]"
        >
          <ShieldCheck class="w-4 h-4" />
          <span>{{ store.loading ? 'Đang xác thực Webhook...' : 'XÁC NHẬN CHUYỂN KHOẢN (GIẢ LẬP WEBHOOK)' }}</span>
        </button>

        <button
          type="button"
          @click="store.cancelOrder()"
          class="w-full py-2.5 text-xs text-slate-400 hover:text-slate-200 flex items-center justify-center gap-1 cursor-pointer"
        >
          <XCircle class="w-3.5 h-3.5" />
          <span>Hủy đơn và hoàn trả ghế lại khán phòng</span>
        </button>
      </div>
    </div>
  </div>
</template>

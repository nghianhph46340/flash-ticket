<script setup lang="ts">
import { useTicketStore } from '../stores/ticketStore'
import { ShoppingCart, Plus, Minus, UtensilsCrossed } from 'lucide-vue-next'

const store = useTicketStore()

const formatVND = (amount: number) =>
  new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND' }).format(amount)

const categoryLabel: Record<string, string> = {
  COMBO: '🎬 Combo',
  POPCORN: '🍿 Bắp rang',
  DRINK: '🥤 Nước uống',
}

const getQtyInCart = (concessionId: string) =>
  store.cart.find((c) => c.concession.id === concessionId)?.quantity ?? 0
</script>

<template>
  <div class="mt-6 pt-6 border-t border-slate-800">
    <div class="flex items-center gap-2 mb-4">
      <UtensilsCrossed class="w-4 h-4 text-amber-400" />
      <h3 class="text-xs font-bold text-slate-300 uppercase tracking-wider">Thêm bắp nước</h3>
      <span class="text-[10px] text-slate-500">(Không bắt buộc)</span>
    </div>

    <div class="space-y-2.5">
      <div
        v-for="item in store.concessions"
        :key="item.id"
        class="flex items-center justify-between p-3 rounded-xl bg-slate-800/50 border border-slate-700/50 hover:border-slate-600 transition-colors"
      >
        <!-- Info -->
        <div class="flex-1 min-w-0 mr-3">
          <div class="flex items-center gap-1.5 mb-0.5">
            <span class="text-[10px] text-slate-500">
              {{ categoryLabel[item.category] || item.category }}
            </span>
          </div>
          <p class="text-xs font-semibold text-white truncate">{{ item.name }}</p>
          <p class="text-xs text-emerald-400 font-bold mt-0.5">{{ formatVND(item.price) }}</p>
        </div>

        <!-- Quantity Controls -->
        <div class="flex items-center gap-2 shrink-0">
          <button
            v-if="getQtyInCart(item.id) > 0"
            type="button"
            @click="store.removeConcession(item.id)"
            class="w-7 h-7 rounded-lg bg-slate-700 hover:bg-slate-600 flex items-center justify-center text-slate-300 cursor-pointer transition-colors"
          >
            <Minus class="w-3.5 h-3.5" />
          </button>

          <span
            v-if="getQtyInCart(item.id) > 0"
            class="text-sm font-black text-white w-5 text-center"
          >
            {{ getQtyInCart(item.id) }}
          </span>

          <button
            type="button"
            @click="store.addConcession(item)"
            class="w-7 h-7 rounded-lg bg-amber-500/20 hover:bg-amber-500/30 border border-amber-500/40 flex items-center justify-center text-amber-400 cursor-pointer transition-colors"
          >
            <Plus class="w-3.5 h-3.5" />
          </button>
        </div>
      </div>
    </div>

    <!-- Cart summary nếu có chọn -->
    <div
      v-if="store.cart.length > 0"
      class="mt-3 p-3 rounded-xl bg-amber-500/10 border border-amber-500/20"
    >
      <div class="flex items-center justify-between text-xs">
        <div class="flex items-center gap-1.5 text-amber-400">
          <ShoppingCart class="w-3.5 h-3.5" />
          <span class="font-semibold">{{ store.cart.reduce((s,c) => s + c.quantity, 0) }} món</span>
        </div>
        <span class="font-black text-amber-400">{{ formatVND(store.concessionAmount) }}</span>
      </div>
    </div>
  </div>
</template>

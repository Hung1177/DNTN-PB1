<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue';
import { Clock, Truck, PackageCheck, ShieldCheck } from 'lucide-vue-next';

const vnTimeStr = ref('');
const countdownStr = ref('');
let timer: number | null = null;

function updateClock() {
  const now = new Date();
  const fmt = new Intl.DateTimeFormat('vi-VN', {
    timeZone: 'Asia/Ho_Chi_Minh',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
  });
  vnTimeStr.value = fmt.format(now);

  const parts = new Intl.DateTimeFormat('en-GB', {
    timeZone: 'Asia/Ho_Chi_Minh',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    hour12: false,
  }).formatToParts(now);

  const h = Number(parts.find((p) => p.type === 'hour')?.value || 0);
  const m = Number(parts.find((p) => p.type === 'minute')?.value || 0);
  const s = Number(parts.find((p) => p.type === 'second')?.value || 0);
  const totalSecLeft = Math.max(0, 23 * 3600 + 59 * 60 + 59 - (h * 3600 + m * 60 + s));
  const rh = String(Math.floor(totalSecLeft / 3600)).padStart(2, '0');
  const rm = String(Math.floor((totalSecLeft % 3600) / 60)).padStart(2, '0');
  const rs = String(totalSecLeft % 60).padStart(2, '0');
  countdownStr.value = `${rh}:${rm}:${rs}`;
}

onMounted(() => {
  updateClock();
  timer = window.setInterval(updateClock, 1000);
});

onBeforeUnmount(() => {
  if (timer) window.clearInterval(timer);
});
</script>

<template>
  <div class="border-b hairline bg-surface">
    <div
      class="mx-auto flex max-w-[1440px] flex-wrap items-center justify-between gap-x-6 gap-y-2 px-4 py-2 text-xs text-ink-secondary sm:px-6"
    >
      <div class="flex flex-wrap items-center gap-x-4 gap-y-1">
        <span class="inline-flex items-center gap-1.5 font-medium text-ink">
          <span class="h-2 w-2 rounded-full bg-forest-700" aria-hidden="true" />
          <span>Giờ máy chủ (UTC+07:00):</span>
          <span class="font-mono tabular-nums text-forest-800">{{ vnTimeStr }}</span>
        </span>
        <span class="hidden text-ink-muted sm:inline" aria-hidden="true">·</span>
        <span class="inline-flex items-center gap-1.5 text-amberish-800 font-medium">
          <Clock :size="14" :stroke-width="1.5" class="text-amberish-700" />
          <span>Còn</span>
          <span class="font-mono tabular-nums">{{ countdownStr }}</span>
          <span>tới chốt đơn 23:59:59</span>
        </span>
      </div>

      <div class="hidden items-center gap-4 text-ink-muted lg:flex">
        <span class="inline-flex items-center gap-1.5">
          <PackageCheck :size="14" :stroke-width="1.5" />
          <span>Kho FEFO 08:00 D+1</span>
        </span>
        <span class="inline-flex items-center gap-1.5">
          <Truck :size="14" :stroke-width="1.5" />
          <span>Nhận tại đại lý từ 16:00 D+1</span>
        </span>
        <span class="inline-flex items-center gap-1.5">
          <ShieldCheck :size="14" :stroke-width="1.5" />
          <span>Thanh toán VNPAY 100%</span>
        </span>
      </div>
    </div>
  </div>
</template>

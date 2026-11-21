<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { useRoute } from 'vue-router';
import { AlertTriangle, ArrowRight, CheckCircle2, Clock, RefreshCw, ShieldCheck } from 'lucide-vue-next';
import httpClient, { ApiEnvelope } from '@/api/httpClient';
import { useDemoStore } from '@/stores/demoStore';
import StatusBadge from '@/components/StatusBadge.vue';
import { formatVnd } from '@/utils/formatters';

interface ReturnStatusDto {
  orderId: number;
  orderCode: string;
  txnRef: string;
  orderStatus: string;
  paymentStatus: string;
  totalAmount?: number;
  message: string;
}

const route = useRoute();
const demoStore = useDemoStore();

const statusData = ref<ReturnStatusDto | null>(null);
const errorMsg = ref('');
const resolutionResult = ref('');
const ipnLog = ref<string[]>([]);

const targetOrderId = computed(() => {
  const qId = Number(route.query.orderId);
  if (!Number.isNaN(qId) && qId > 0) return qId;
  const pending = demoStore.orders.find((o) => o.orderStatus === 'PENDING_PAYMENT');
  return pending?.orderId || demoStore.orders[0]?.orderId || 103;
});

onMounted(async () => {
  if (demoStore.isDemoMode) {
    syncFromDemoOrder();
    return;
  }
  try {
    const res = await httpClient.get<ApiEnvelope<ReturnStatusDto>>('/payments/vnpay-return', {
      params: route.query,
    });
    statusData.value = res.data.data;
  } catch (err: any) {
    errorMsg.value =
      err?.response?.data?.message || 'Không thể xác thực chữ ký Return URL từ VNPAY.';
  }
});

function syncFromDemoOrder(customMessage?: string) {
  const ord = demoStore.orders.find((o) => o.orderId === targetOrderId.value);
  if (!ord) {
    errorMsg.value = 'Không tìm thấy giao dịch đơn hàng tương ứng.';
    return;
  }
  statusData.value = {
    orderId: ord.orderId,
    orderCode: ord.orderCode,
    txnRef: (route.query.vnp_TxnRef as string) || ord.txnRef || `VNP${ord.orderId}`,
    orderStatus: ord.orderStatus,
    paymentStatus: ord.paymentStatus,
    totalAmount: ord.totalAmount,
    message:
      customMessage ||
      (ord.paymentStatus === 'PAID'
        ? 'Đã nhận IPN xác nhận thanh toán thành công từ máy chủ VNPAY.'
        : 'Đang chờ Webhook IPN xác thực từ máy chủ VNPAY (Return URL chỉ để hiển thị trạng thái chờ).'),
  };
}

function triggerSandboxIpn(scenario: 'SUCCESS' | 'DUPLICATE' | 'LATE_PAYMENT') {
  errorMsg.value = '';
  resolutionResult.value = '';
  try {
    const res = demoStore.simulateVnpayIpn(targetOrderId.value, scenario);
    syncFromDemoOrder(res.message);
    ipnLog.value.unshift(`[${new Date().toLocaleTimeString('vi-VN')}] ${scenario}: ${res.message}`);
  } catch (err: any) {
    errorMsg.value = err?.message || 'Lỗi giả lập Webhook IPN.';
  }
}

async function handleLatePaymentChoice(action: 'ROLLOVER' | 'REFUND') {
  if (!statusData.value) return;
  errorMsg.value = '';
  try {
    if (demoStore.isDemoMode) {
      resolutionResult.value = demoStore.resolveLatePayment(statusData.value.orderId, action);
      syncFromDemoOrder(resolutionResult.value);
      return;
    }
    const res = await httpClient.post(
      `/orders/${statusData.value.orderId}/late-payment-resolution`,
      { action }
    );
    resolutionResult.value = res.data.message;
  } catch (err: any) {
    errorMsg.value = err?.response?.data?.message || 'Không thể xử lý lựa chọn đối soát muộn.';
  }
}
</script>

<template>
  <div class="mx-auto max-w-3xl space-y-6 py-2">
    <section class="rounded-2xl border hairline bg-surface p-6 shadow-card space-y-5">
      <div class="flex flex-wrap items-start justify-between gap-3">
        <div>
          <div class="inline-flex items-center gap-1.5 text-xs font-medium text-forest-800">
            <ShieldCheck :size="15" :stroke-width="1.5" />
            <span>Cổng thanh toán trực tuyến VNPAY (100% trả trước)</span>
          </div>
          <h1 class="mt-1.5 text-xl font-medium text-ink">Kết quả xác thực thanh toán</h1>
        </div>
        <StatusBadge v-if="statusData" :status="statusData.paymentStatus" />
      </div>

      <p class="rounded-xl border hairline bg-canvas p-3.5 text-xs text-ink-secondary leading-relaxed">
        Return URL chỉ hiển thị trạng thái chờ. Đơn hàng chỉ chuyển sang
        <strong class="font-medium text-ink">Đã thanh toán chờ chốt 23:59</strong>
        khi máy chủ nhận và kiểm tra hợp lệ chữ ký <span class="font-mono">HMAC-SHA512</span> qua Webhook IPN từ VNPAY.
      </p>

      <div v-if="statusData" class="rounded-xl border hairline bg-canvas/60 p-4 space-y-2.5 text-xs">
        <div class="flex justify-between">
          <span class="text-ink-muted">Mã đơn hàng</span>
          <span class="font-mono font-medium text-ink">{{ statusData.orderCode }}</span>
        </div>
        <div class="flex justify-between">
          <span class="text-ink-muted">Mã giao dịch VNPAY (TxnRef)</span>
          <span class="font-mono text-ink">{{ statusData.txnRef }}</span>
        </div>
        <div v-if="statusData.totalAmount" class="flex justify-between">
          <span class="text-ink-muted">Số tiền giao dịch</span>
          <span class="font-mono font-medium text-forest-800 tabular-nums">
            {{ formatVnd(statusData.totalAmount) }}
          </span>
        </div>
        <div class="flex justify-between items-center">
          <span class="text-ink-muted">Trạng thái thương mại</span>
          <StatusBadge :status="statusData.orderStatus" />
        </div>
        <div class="border-t hairline pt-2.5 font-medium text-forest-800">
          {{ statusData.message }}
        </div>
      </div>

      <!-- Interactive Demo Sandbox IPN Controls -->
      <div v-if="demoStore.isDemoMode" class="rounded-xl border hairline bg-canvas p-4 space-y-3">
        <div class="text-xs font-medium text-ink">
          Mô phỏng Webhook IPN VNPAY (trải nghiệm đầy đủ các tình huống giao dịch)
        </div>
        <div class="flex flex-wrap gap-2">
          <button
            type="button"
            @click="triggerSandboxIpn('SUCCESS')"
            class="inline-flex h-9 items-center gap-1.5 rounded-lg bg-forest-700 px-3.5 text-xs font-medium text-white hover:bg-forest-800"
          >
            <CheckCircle2 :size="14" :stroke-width="1.5" />
            <span>Giả lập IPN thành công (00)</span>
          </button>
          <button
            type="button"
            @click="triggerSandboxIpn('DUPLICATE')"
            class="inline-flex h-9 items-center gap-1.5 rounded-lg border hairline bg-surface px-3.5 text-xs font-medium text-ink hover:bg-raised"
          >
            <RefreshCw :size="14" :stroke-width="1.5" />
            <span>Thử gửi lại IPN trùng lặp</span>
          </button>
          <button
            type="button"
            @click="triggerSandboxIpn('LATE_PAYMENT')"
            class="inline-flex h-9 items-center gap-1.5 rounded-lg border border-amberish-200 bg-amberish-50 px-3.5 text-xs font-medium text-amberish-800 hover:bg-amberish-100"
          >
            <Clock :size="14" :stroke-width="1.5" />
            <span>Giả lập IPN đến muộn (&gt;30 phút / sau 00:00)</span>
          </button>
        </div>
      </div>

      <!-- Late Payment Reconciliation Choice -->
      <div
        v-if="statusData?.paymentStatus === 'LATE_RECONCILIATION'"
        class="rounded-xl border border-amberish-200 bg-amberish-50 p-4 space-y-3 text-xs"
      >
        <div class="flex items-start gap-2 font-medium text-amberish-800">
          <AlertTriangle :size="16" :stroke-width="1.5" class="mt-0.5 shrink-0" />
          <span>Giao dịch thanh toán đến muộn sau hạn giữ tồn 30 phút hoặc sau giờ chốt 00:00</span>
        </div>
        <p class="text-amberish-800/90 leading-relaxed">
          Khoản tiền VNPAY của bạn được bảo vệ an toàn trong trạng thái đối soát. Vui lòng chọn một trong hai phương án xử lý:
        </p>
        <div class="flex flex-wrap gap-2">
          <button
            type="button"
            @click="handleLatePaymentChoice('ROLLOVER')"
            class="h-9 rounded-lg bg-forest-700 px-4 font-medium text-white hover:bg-forest-800"
          >
            Chuyển sang đợt giao kế tiếp (D+2)
          </button>
          <button
            type="button"
            @click="handleLatePaymentChoice('REFUND')"
            class="h-9 rounded-lg bg-amberish-700 px-4 font-medium text-white hover:bg-amberish-800"
          >
            Nhận hoàn tiền 100%
          </button>
        </div>
      </div>

      <div
        v-if="resolutionResult"
        role="status"
        class="rounded-xl border border-forest-200 bg-forest-50 p-3.5 text-xs font-medium text-forest-800"
      >
        {{ resolutionResult }}
      </div>
      <div
        v-if="errorMsg"
        role="alert"
        class="rounded-xl border border-danger-100 bg-danger-50 p-3.5 text-xs font-medium text-danger-700"
      >
        {{ errorMsg }}
      </div>

      <div v-if="ipnLog.length > 0" class="border-t hairline pt-4 space-y-1.5">
        <div class="text-xs font-medium text-ink-secondary">Nhật ký kiểm thử IPN</div>
        <div
          v-for="(entry, i) in ipnLog"
          :key="i"
          class="font-mono text-[11px] text-ink-muted"
        >
          {{ entry }}
        </div>
      </div>

      <div class="flex flex-wrap items-center gap-3 pt-2">
        <router-link
          to="/orders"
          class="inline-flex h-10 items-center gap-1.5 rounded-lg bg-ink px-4 text-xs font-medium text-white hover:opacity-90"
        >
          <span>Xem đơn hàng & Mã QR nhận hàng</span>
          <ArrowRight :size="14" :stroke-width="1.5" />
        </router-link>
        <router-link
          to="/"
          class="inline-flex h-10 items-center rounded-lg border hairline bg-surface px-4 text-xs font-medium text-ink hover:bg-raised"
        >
          Quay lại trang mua sắm
        </router-link>
      </div>
    </section>
  </div>
</template>

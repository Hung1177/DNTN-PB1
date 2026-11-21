<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import QrcodeVue from 'qrcode.vue';
import {
  AlertCircle,
  CheckCircle2,
  Copy,
  CreditCard,
  MapPin,
  QrCode,
  RefreshCw,
  ShieldAlert,
  XCircle,
} from 'lucide-vue-next';
import httpClient, { ApiEnvelope } from '@/api/httpClient';
import { useDemoStore } from '@/stores/demoStore';
import { useAuthStore } from '@/stores/authStore';
import { useNotificationStore } from '@/stores/notificationStore';
import StatusBadge from '@/components/StatusBadge.vue';
import AppModal from '@/components/AppModal.vue';
import type { IssueRecord, OrderRecord } from '@/types/domain';
import { formatVnDateTime, formatVnd } from '@/utils/formatters';

const demoStore = useDemoStore();
const authStore = useAuthStore();
const notificationStore = useNotificationStore();
const router = useRouter();

const liveOrders = ref<OrderRecord[]>([]);
const statusFilter = ref<'ALL' | 'ACTIVE' | 'READY' | 'COMPLETED' | 'ISSUE'>('ALL');
const selectedOrderId = ref<number | null>(null);
const actionMessage = ref('');
const errorMessage = ref('');

// Modals
const cancelModalOpen = ref(false);
const cancelReason = ref('Thay đổi nhu cầu bữa ăn trước giờ chốt 23:59:59');
const issueModalOpen = ref(false);
const issueType = ref<IssueRecord['issueType']>('DAMAGED');
const issueDescription = ref('');
const issueEvidenceUrl = ref('https://img.o2ofood.vn/evidence-customer.jpg');

const allOrders = computed<OrderRecord[]>(() => {
  if (!demoStore.isDemoMode) return liveOrders.value;
  const uid = authStore.user?.userId;
  const isAdmin = authStore.hasRole('ROLE_ADMIN');
  return demoStore.orders.filter((o) => isAdmin || !uid || o.customerId === uid);
});

const filteredOrders = computed(() => {
  return allOrders.value.filter((ord) => {
    if (statusFilter.value === 'ACTIVE') {
      return ['PENDING_PAYMENT', 'PAID_WAITING_CUTOFF', 'CONFIRMED', 'PROCESSING'].includes(ord.orderStatus);
    }
    if (statusFilter.value === 'READY') {
      return ord.fulfillmentStatus === 'READY_FOR_PICKUP';
    }
    if (statusFilter.value === 'COMPLETED') {
      return ord.orderStatus === 'COMPLETED';
    }
    if (statusFilter.value === 'ISSUE') {
      return ord.hasOpenIssue || ord.fulfillmentStatus === 'ISSUE_HOLD';
    }
    return true;
  });
});

const selectedOrder = computed<OrderRecord | null>(() => {
  if (filteredOrders.value.length === 0) return null;
  return (
    filteredOrders.value.find((o) => o.orderId === selectedOrderId.value) ||
    filteredOrders.value[0]
  );
});

onMounted(() => {
  loadOrders();
});

async function loadOrders() {
  if (demoStore.isDemoMode) {
    if (!selectedOrderId.value && allOrders.value.length > 0) {
      selectedOrderId.value = allOrders.value[0].orderId;
    }
    return;
  }
  try {
    const res = await httpClient.get<ApiEnvelope<OrderRecord[]>>('/orders');
    liveOrders.value = res.data.data;
    if (liveOrders.value.length > 0 && !selectedOrderId.value) {
      selectedOrderId.value = liveOrders.value[0].orderId;
    }
  } catch (err: any) {
    errorMessage.value = err?.response?.data?.message || 'Không thể tải danh sách đơn hàng từ máy chủ.';
  }
}

function stepProgressIndex(ord: OrderRecord): number {
  if (ord.orderStatus === 'CANCELLED') return 0;
  if (ord.orderStatus === 'PENDING_PAYMENT') return 1;
  if (ord.orderStatus === 'PAID_WAITING_CUTOFF' || ord.fulfillmentStatus === 'WAITING_PACKING') return 2;
  if (ord.fulfillmentStatus === 'PACKED' || ord.fulfillmentStatus === 'IN_TRANSIT' || ord.fulfillmentStatus === 'AGENT_CHECKING') return 3;
  if (ord.fulfillmentStatus === 'READY_FOR_PICKUP') return 4;
  return 5;
}

function openCancelDialog(ord: OrderRecord) {
  selectedOrderId.value = ord.orderId;
  cancelModalOpen.value = true;
}

async function confirmCancelBeforeCutoff() {
  if (!selectedOrder.value) return;
  actionMessage.value = '';
  errorMessage.value = '';
  try {
    if (demoStore.isDemoMode) {
      actionMessage.value = demoStore.cancelOrderBeforeCutoff(
        selectedOrder.value.orderId,
        cancelReason.value
      );
    } else {
      const res = await httpClient.post(`/orders/${selectedOrder.value.orderId}/cancel`, {
        reason: cancelReason.value,
      });
      actionMessage.value = res.data.message;
      await loadOrders();
    }
    cancelModalOpen.value = false;
  } catch (err: any) {
    errorMessage.value = err?.response?.data?.message || err?.message || 'Không thể hủy đơn hàng.';
    cancelModalOpen.value = false;
  }
}

async function confirmCustomerReceived(orderId: number) {
  actionMessage.value = '';
  errorMessage.value = '';
  try {
    if (demoStore.isDemoMode) {
      actionMessage.value = demoStore.customerConfirmReceived(orderId);
    } else {
      const res = await httpClient.post(`/fulfillment/orders/${orderId}/customer-confirm-received`);
      actionMessage.value = res.data.message;
      await loadOrders();
    }
  } catch (err: any) {
    errorMessage.value = err?.response?.data?.message || err?.message || 'Không thể xác nhận đã nhận hàng.';
  }
}

function openIssueDialog(ord: OrderRecord) {
  selectedOrderId.value = ord.orderId;
  issueDescription.value = '';
  issueModalOpen.value = true;
}

async function submitIssueReport() {
  if (!selectedOrder.value) return;
  actionMessage.value = '';
  errorMessage.value = '';
  try {
    if (demoStore.isDemoMode) {
      actionMessage.value = demoStore.reportIssue(
        selectedOrder.value.orderId,
        authStore.user?.fullName || 'Khách hàng',
        'ROLE_CUSTOMER',
        issueType.value,
        issueDescription.value || 'Sản phẩm bị dập hỏng khi kiểm tra tại điểm nhận',
        issueEvidenceUrl.value
      );
    } else {
      const res = await httpClient.post('/exceptions/issues', {
        orderId: selectedOrder.value.orderId,
        issueType: issueType.value,
        description: issueDescription.value || 'Sản phẩm bị dập hỏng khi kiểm tra tại điểm nhận',
        evidenceUrls: issueEvidenceUrl.value,
      });
      actionMessage.value = res.data.message;
      await loadOrders();
    }
    issueModalOpen.value = false;
  } catch (err: any) {
    errorMessage.value = err?.response?.data?.message || err?.message || 'Không thể gửi khiếu nại.';
  }
}

async function copyQrToken(token: string) {
  try {
    await navigator.clipboard.writeText(token);
    notificationStore.pushToast('Đã sao chép mã nhận hàng', `Mã ${token} đã sẵn sàng dán tại cổng Đại lý.`);
  } catch {
    notificationStore.pushToast('Mã nhận hàng', token);
  }
}

function goToVnpaySimulator(ord: OrderRecord) {
  router.push(
    `/payment/vnpay-return?vnp_TxnRef=${ord.txnRef || 'VNP-DEMO'}&vnp_Amount=${
      ord.totalAmount * 100
    }&vnp_ResponseCode=00&orderId=${ord.orderId}`
  );
}
</script>

<template>
  <div class="space-y-6">
    <!-- Page Header -->
    <div class="flex flex-wrap items-end justify-between gap-4">
      <div>
        <h1 class="text-2xl font-medium tracking-tight text-ink">Đơn hàng & Mã QR nhận hàng</h1>
        <p class="mt-1 text-sm text-ink-secondary">
          Theo dõi tách bạch trạng thái thương mại và trạng thái giao nhận vật lý tại điểm nhận hàng
        </p>
      </div>
      <button
        type="button"
        @click="loadOrders"
        class="inline-flex h-9 items-center gap-1.5 rounded-lg border hairline bg-surface px-3.5 text-xs font-medium text-ink hover:bg-raised"
      >
        <RefreshCw :size="14" :stroke-width="1.5" />
        <span>Làm mới</span>
      </button>
    </div>

    <!-- Filter Tabs -->
    <div class="o2o-tablist" role="tablist" aria-label="Bộ lọc trạng thái đơn hàng">
      <button
        v-for="tab in [
          { key: 'ALL', label: `Tất cả (${allOrders.length})` },
          { key: 'ACTIVE', label: 'Đang xử lý' },
          { key: 'READY', label: 'Sẵn sàng nhận QR' },
          { key: 'COMPLETED', label: 'Hoàn tất' },
          { key: 'ISSUE', label: 'Có sự cố' },
        ]"
        :key="tab.key"
        type="button"
        role="tab"
        :aria-selected="statusFilter === tab.key"
        @click="statusFilter = tab.key as any"
      >
        {{ tab.label }}
      </button>
    </div>

    <div
      v-if="actionMessage"
      role="status"
      class="rounded-xl border border-forest-200 bg-forest-50 px-4 py-3 text-xs font-medium text-forest-800"
    >
      {{ actionMessage }}
    </div>
    <div
      v-if="errorMessage"
      role="alert"
      class="rounded-xl border border-danger-100 bg-danger-50 px-4 py-3 text-xs font-medium text-danger-700"
    >
      {{ errorMessage }}
    </div>

    <!-- Main Two-Column Order Workspace -->
    <div class="grid grid-cols-1 gap-6 lg:grid-cols-12">
      <!-- Order Cards -->
      <div class="lg:col-span-7 space-y-4">
        <div
          v-if="filteredOrders.length === 0"
          class="rounded-2xl border hairline bg-surface p-10 text-center text-sm text-ink-secondary"
        >
          Chưa có đơn hàng nào trong nhóm trạng thái này.
        </div>

        <article
          v-for="ord in filteredOrders"
          :key="ord.orderId"
          @click="selectedOrderId = ord.orderId"
          :class="[
            'cursor-pointer rounded-2xl border bg-surface p-5 shadow-card space-y-4',
            selectedOrder?.orderId === ord.orderId
              ? 'border-forest-700 ring-1 ring-forest-700/15'
              : 'hairline hover:border-black/20',
          ]"
        >
          <div class="flex flex-wrap items-start justify-between gap-3">
            <div>
              <div class="flex items-center gap-2">
                <span class="font-mono text-sm font-medium text-ink">{{ ord.orderCode }}</span>
                <span class="font-mono text-xs text-ink-muted">{{ ord.cycleCode }}</span>
              </div>
              <div class="mt-1 flex items-center gap-1.5 text-xs text-ink-secondary">
                <MapPin :size="13" :stroke-width="1.5" class="text-ink-muted shrink-0" />
                <span>{{ ord.pickupPointName }}</span>
              </div>
            </div>

            <div class="text-right">
              <div class="font-mono text-base font-medium text-forest-800 tabular-nums">
                {{ formatVnd(ord.totalAmount) }}
              </div>
              <div class="text-[11px] text-ink-muted">{{ ord.items.length }} dòng sản phẩm</div>
            </div>
          </div>

          <!-- Separated Status Dimensions -->
          <div class="flex flex-wrap items-center gap-2">
            <StatusBadge :status="ord.orderStatus" />
            <StatusBadge :status="ord.fulfillmentStatus" />
            <StatusBadge :status="ord.paymentStatus" />
            <StatusBadge v-if="ord.completionType" :status="ord.completionType" />
          </div>

          <!-- Stepper -->
          <div class="grid grid-cols-5 gap-1.5 pt-1" aria-label="Tiến độ đơn hàng">
            <div
              v-for="(label, idx) in ['Đặt đơn', 'VNPAY', 'Kho FEFO', 'Tới đại lý', 'Hoàn tất']"
              :key="label"
              class="space-y-1"
            >
              <div
                :class="[
                  'h-1.5 rounded-full',
                  idx + 1 <= stepProgressIndex(ord) ? 'bg-forest-700' : 'bg-raised',
                ]"
              />
              <div class="text-[11px] text-ink-muted truncate">{{ label }}</div>
            </div>
          </div>

          <!-- Items Summary -->
          <div class="rounded-xl bg-canvas/70 border hairline px-3.5 py-2.5 text-xs space-y-1">
            <div
              v-for="item in ord.items"
              :key="item.id"
              class="flex items-center justify-between gap-2 text-ink-secondary"
            >
              <span class="truncate">
                {{ item.productName }} ({{ item.packName }}) × {{ item.quantity }}
              </span>
              <span class="font-mono tabular-nums text-ink">{{ formatVnd(item.lineTotal) }}</span>
            </div>
          </div>

          <!-- Audit / Unclaimed alerts -->
          <div
            v-if="ord.auditAlert"
            class="flex items-start gap-2 rounded-lg border border-amberish-200 bg-amberish-50 px-3 py-2 text-xs text-amberish-800"
          >
            <AlertCircle :size="14" :stroke-width="1.5" class="mt-0.5 shrink-0" />
            <span>{{ ord.auditAlert }}</span>
          </div>

          <!-- Context Actions -->
          <div class="flex flex-wrap items-center gap-2 pt-2 border-t hairline" @click.stop>
            <button
              v-if="ord.orderStatus === 'PENDING_PAYMENT'"
              type="button"
              @click="goToVnpaySimulator(ord)"
              class="inline-flex h-8 items-center gap-1.5 rounded-lg bg-forest-700 px-3 text-xs font-medium text-white hover:bg-forest-800"
            >
              <CreditCard :size="14" :stroke-width="1.5" />
              <span>Thanh toán VNPAY ngay</span>
            </button>

            <button
              v-if="ord.orderStatus === 'PENDING_PAYMENT' || ord.orderStatus === 'PAID_WAITING_CUTOFF'"
              type="button"
              @click="openCancelDialog(ord)"
              class="inline-flex h-8 items-center gap-1.5 rounded-lg border border-danger-100 bg-danger-50 px-3 text-xs font-medium text-danger-700 hover:bg-danger-100"
            >
              <XCircle :size="14" :stroke-width="1.5" />
              <span>Tự hủy trước 23:59</span>
            </button>

            <button
              v-if="
                (ord.fulfillmentStatus === 'READY_FOR_PICKUP' || ord.fulfillmentStatus === 'HANDED_OVER') &&
                ord.orderStatus !== 'COMPLETED'
              "
              type="button"
              @click="confirmCustomerReceived(ord.orderId)"
              class="inline-flex h-8 items-center gap-1.5 rounded-lg bg-forest-700 px-3 text-xs font-medium text-white hover:bg-forest-800"
            >
              <CheckCircle2 :size="14" :stroke-width="1.5" />
              <span>Xác nhận đã nhận hàng</span>
            </button>

            <button
              v-if="ord.orderStatus !== 'CANCELLED' && !ord.hasOpenIssue"
              type="button"
              @click="openIssueDialog(ord)"
              class="inline-flex h-8 items-center gap-1.5 rounded-lg border hairline bg-surface px-3 text-xs font-medium text-ink-secondary hover:bg-raised hover:text-ink"
            >
              <ShieldAlert :size="14" :stroke-width="1.5" />
              <span>Báo sự cố / Hoàn tiền</span>
            </button>
          </div>
        </article>
      </div>

      <!-- Right Panel: One-Time Pickup QR & Order Milestones -->
      <aside class="lg:col-span-5">
        <div
          v-if="selectedOrder"
          class="sticky top-32 rounded-2xl border hairline bg-surface p-6 shadow-card space-y-5"
        >
          <div class="flex items-start justify-between gap-2">
            <div>
              <div class="flex items-center gap-1.5 text-xs font-medium text-forest-800">
                <QrCode :size="15" :stroke-width="1.5" />
                <span>Mã QR nhận hàng dùng 1 lần</span>
              </div>
              <h2 class="mt-1 font-mono text-base font-medium text-ink">
                {{ selectedOrder.orderCode }}
              </h2>
            </div>
            <StatusBadge :status="selectedOrder.fulfillmentStatus" />
          </div>

          <!-- QR Visual -->
          <div class="flex flex-col items-center rounded-xl border hairline bg-canvas p-5">
            <div class="rounded-xl bg-white p-3 shadow-card">
              <QrcodeVue :value="selectedOrder.pickupQrToken" :size="168" level="M" />
            </div>
            <div class="mt-3 flex items-center gap-2">
              <code class="rounded-md bg-raised px-2.5 py-1 font-mono text-xs font-medium text-ink">
                {{ selectedOrder.pickupQrToken }}
              </code>
              <button
                type="button"
                @click="copyQrToken(selectedOrder.pickupQrToken)"
                class="inline-flex h-7 items-center gap-1 rounded-md border hairline bg-surface px-2 text-xs text-ink-secondary hover:text-ink"
              >
                <Copy :size="13" :stroke-width="1.5" />
                <span>Sao chép</span>
              </button>
            </div>
            <p class="mt-2 text-center text-xs text-ink-muted">
              Xuất trình mã này cho Đại lý quét khi đơn ở trạng thái Sẵn sàng nhận tại đại lý (từ 16:00).
            </p>
          </div>

          <!-- Exact Timestamps Breakdown -->
          <div class="space-y-2 border-t hairline pt-4 text-xs">
            <div class="flex justify-between gap-2">
              <span class="text-ink-muted">Hạn chốt tự hủy</span>
              <span class="font-mono tabular-nums text-ink">{{ formatVnDateTime(selectedOrder.cutoffAt) }}</span>
            </div>
            <div class="flex justify-between gap-2">
              <span class="text-ink-muted">Mở nhận hàng từ</span>
              <span class="font-mono tabular-nums text-ink">{{ formatVnDateTime(selectedOrder.pickupStartAt) }}</span>
            </div>
            <div class="flex justify-between gap-2">
              <span class="text-ink-muted">Mốc tự hoàn tất (00:00 D+2)</span>
              <span class="font-mono tabular-nums text-ink">{{ formatVnDateTime(selectedOrder.autoCompleteAt) }}</span>
            </div>
            <div class="flex justify-between gap-2">
              <span class="text-ink-muted">Đại lý quét bàn giao vật lý</span>
              <span class="font-mono tabular-nums text-ink">
                {{ selectedOrder.physicalHandedOverAt ? formatVnDateTime(selectedOrder.physicalHandedOverAt) : 'Chưa bàn giao (NULL)' }}
              </span>
            </div>
            <div class="flex justify-between gap-2">
              <span class="text-ink-muted">Khách xác nhận đã nhận</span>
              <span class="font-mono tabular-nums text-ink">
                {{ selectedOrder.customerConfirmedAt ? formatVnDateTime(selectedOrder.customerConfirmedAt) : 'Chưa xác nhận' }}
              </span>
            </div>
          </div>
        </div>
      </aside>
    </div>

    <!-- Cancel Order Confirmation Modal (Native <dialog>) -->
    <AppModal
      :open="cancelModalOpen"
      title="Xác nhận tự hủy đơn trước giờ chốt 23:59"
      subtitle="Tồn kho sẽ được giải phóng ngay; đơn đã thanh toán VNPAY sẽ tự tạo yêu cầu hoàn tiền 100%"
      @close="cancelModalOpen = false"
    >
      <div v-if="selectedOrder" class="space-y-4 text-xs">
        <div class="rounded-xl border hairline bg-canvas p-3.5 space-y-1">
          <div class="font-medium text-ink">Đơn hàng: {{ selectedOrder.orderCode }}</div>
          <div class="text-ink-secondary">
            Giá trị hoàn trả: <strong class="font-mono text-ink">{{ formatVnd(selectedOrder.totalAmount) }}</strong>
          </div>
        </div>
        <div>
          <label for="cancel-reason" class="mb-1.5 block font-medium text-ink-secondary">Lý do hủy đơn</label>
          <textarea
            id="cancel-reason"
            v-model="cancelReason"
            rows="2"
            class="w-full rounded-lg border hairline bg-canvas/50 p-2.5 text-xs text-ink"
          />
        </div>
      </div>
      <template #footer>
        <button
          type="button"
          autofocus
          @click="cancelModalOpen = false"
          class="h-9 rounded-lg border hairline bg-surface px-4 text-xs font-medium text-ink hover:bg-raised"
        >
          Giữ lại đơn
        </button>
        <button
          type="button"
          @click="confirmCancelBeforeCutoff"
          class="h-9 rounded-lg bg-danger-700 px-4 text-xs font-medium text-white hover:opacity-95"
        >
          Xác nhận hủy đơn
        </button>
      </template>
    </AppModal>

    <!-- Report Issue Modal (Native <dialog>) -->
    <AppModal
      :open="issueModalOpen"
      title="Báo sự cố & Yêu cầu hoàn tiền"
      subtitle="Gửi báo cáo sẽ lập tức chặn tác vụ tự hoàn tất lúc 00:00 D+2 và chuyển Điều phối thu hồi hàng lỗi"
      @close="issueModalOpen = false"
    >
      <div class="space-y-4 text-xs">
        <div>
          <label for="issue-type" class="mb-1.5 block font-medium text-ink-secondary">Loại sự cố</label>
          <select
            id="issue-type"
            v-model="issueType"
            class="h-9 w-full rounded-lg border hairline bg-canvas/50 px-3 text-xs text-ink"
          >
            <option value="DAMAGED">Hàng dập hỏng / rách bao bì</option>
            <option value="MISSING_ITEM">Giao thiếu gói / thiếu kiện</option>
            <option value="WRONG_ITEM">Giao sai sản phẩm hoặc quy cách</option>
            <option value="LATE_DELIVERY">Giao trễ khung giờ cam kết</option>
          </select>
        </div>
        <div>
          <label for="issue-desc" class="mb-1.5 block font-medium text-ink-secondary">Mô tả chi tiết</label>
          <textarea
            id="issue-desc"
            v-model="issueDescription"
            rows="3"
            placeholder="Mô tả tình trạng gói hàng khi kiểm tra tại đại lý..."
            class="w-full rounded-lg border hairline bg-canvas/50 p-2.5 text-xs text-ink"
          />
        </div>
        <div>
          <label for="issue-img" class="mb-1.5 block font-medium text-ink-secondary">Liên kết ảnh minh chứng</label>
          <input
            id="issue-img"
            v-model="issueEvidenceUrl"
            type="url"
            class="h-9 w-full rounded-lg border hairline bg-canvas/50 px-3 text-xs text-ink"
          />
        </div>
      </div>
      <template #footer>
        <button
          type="button"
          @click="issueModalOpen = false"
          class="h-9 rounded-lg border hairline bg-surface px-4 text-xs font-medium text-ink hover:bg-raised"
        >
          Đóng
        </button>
        <button
          type="button"
          @click="submitIssueReport"
          class="h-9 rounded-lg bg-amberish-700 px-4 text-xs font-medium text-white hover:bg-amberish-800"
        >
          Gửi báo cáo sự cố
        </button>
      </template>
    </AppModal>
  </div>
</template>

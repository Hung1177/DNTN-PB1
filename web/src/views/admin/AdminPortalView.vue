<script setup lang="ts">
import { computed, ref } from 'vue';
import {
  CalendarClock,
  CheckCircle2,
  Package,
  Plus,
  ReceiptText,
  ShieldCheck,
  Users,
} from 'lucide-vue-next';
import httpClient from '@/api/httpClient';
import { useDemoStore } from '@/stores/demoStore';
import { useAuthStore } from '@/stores/authStore';
import StatusBadge from '@/components/StatusBadge.vue';
import AppModal from '@/components/AppModal.vue';
import type { StorageType } from '@/types/domain';
import {
  formatVnDate,
  formatVnDateTime,
  formatVnd,
  roleDisplayLabel,
  storageTypeShort,
} from '@/utils/formatters';

const demoStore = useDemoStore();
const authStore = useAuthStore();

const activeTab = ref<'overview' | 'catalog' | 'cycles' | 'refunds' | 'policies'>('overview');
const statusMsg = ref('');
const errorMsg = ref('');

// Add Product modal
const addProdModalOpen = ref(false);
const newProdName = ref('');
const newProdCategory = ref('Rau củ quả');
const newProdStorage = ref<StorageType>('CHILLED');
const newProdPackName = ref('Túi 500g');
const newProdWeight = ref(500);
const newProdPrice = ref(35000);
const newProdStock = ref(60);
const newProdShelfLife = ref(2);

// Create Cycle form
const newCycleDate = ref('2026-11-24');
const newCycleTimeout = ref(30);

// Dashboard Metrics
const totalRevenueVnd = computed(() =>
  demoStore.orders
    .filter((o) => o.paymentStatus === 'PAID' || o.orderStatus === 'COMPLETED')
    .reduce((acc, o) => acc + o.totalAmount, 0)
);

const pendingRefundsCount = computed(
  () => demoStore.refunds.filter((r) => r.status !== 'COMPLETED').length
);

const completedOrdersCount = computed(
  () => demoStore.orders.filter((o) => o.orderStatus === 'COMPLETED').length
);

const totalCommissionNet = computed(() =>
  demoStore.commissionLedger.reduce((acc, e) => acc + e.amount, 0)
);

// 7-day deterministic trend series for the main SVG chart (1 primary accent bar for current cycle)
const chartSeries = [
  { label: '15/11', revenueM: 14.2, orders: 72, current: false },
  { label: '16/11', revenueM: 16.8, orders: 84, current: false },
  { label: '17/11', revenueM: 15.4, orders: 79, current: false },
  { label: '18/11', revenueM: 19.6, orders: 98, current: false },
  { label: '19/11', revenueM: 21.1, orders: 106, current: false },
  { label: '20/11', revenueM: 23.4, orders: 118, current: false },
  { label: '21/11', revenueM: 26.8, orders: 134, current: true },
];
const maxChartVal = 30;

const pickupPointStats = computed(() =>
  demoStore.pickupPoints.map((pp) => {
    const ppOrders = demoStore.orders.filter((o) => o.pickupPointId === pp.id);
    const revenue = ppOrders.reduce((s, o) => s + o.totalAmount, 0);
    const ledgerNet = demoStore.commissionLedger
      .filter((e) => e.pickupPointId === pp.id)
      .reduce((s, e) => s + e.amount, 0);
    return {
      ...pp,
      orderCount: ppOrders.length,
      revenue,
      ledgerNet,
    };
  })
);

function linkedReturnForRefund(returnRequestId: number | null) {
  if (!returnRequestId) return null;
  return demoStore.returnRequests.find((r) => r.id === returnRequestId) || null;
}

async function handleApproveRefund(refundId: number) {
  statusMsg.value = '';
  errorMsg.value = '';
  try {
    if (demoStore.isDemoMode) {
      statusMsg.value = demoStore.approveRefund(
        refundId,
        authStore.user?.fullName || 'Nguyễn Quản Trị'
      );
      return;
    }
    const res = await httpClient.post(`/exceptions/refunds/${refundId}/approve`);
    statusMsg.value = `${res.data.message} (${res.data.data.providerRef})`;
  } catch (err: any) {
    errorMsg.value =
      err?.response?.data?.message ||
      err?.message ||
      'Không thể phê duyệt hoàn tiền (kiểm tra kho đã nhận hàng hoàn vào khu cách ly chưa).';
  }
}

function handleWaiveReturn(returnId: number) {
  statusMsg.value = '';
  errorMsg.value = '';
  try {
    statusMsg.value = demoStore.waiveUnrecoverableReturn(returnId);
  } catch (err: any) {
    errorMsg.value = err?.message || 'Không thể miễn thu hồi đặc cách.';
  }
}

function submitNewProduct() {
  if (!newProdName.value.trim()) return;
  statusMsg.value = demoStore.addCatalogProduct({
    productName: newProdName.value,
    category: newProdCategory.value,
    storageType: newProdStorage.value,
    packName: newProdPackName.value,
    packWeightGrams: newProdWeight.value,
    unitPrice: newProdPrice.value,
    availableQty: newProdStock.value,
    minShelfLifeDaysAtPickup: newProdShelfLife.value,
  });
  newProdName.value = '';
  addProdModalOpen.value = false;
}

function handleCreateCycle() {
  statusMsg.value = '';
  errorMsg.value = '';
  try {
    statusMsg.value = demoStore.createOrderCycle(newCycleDate.value, newCycleTimeout.value);
  } catch (err: any) {
    errorMsg.value = err?.message || 'Không thể tạo đợt giao hàng mới.';
  }
}

function handleActivatePolicy(policyId: number) {
  statusMsg.value = demoStore.activateCommissionPolicy(policyId);
}
</script>

<template>
  <div class="space-y-6">
    <div>
      <h1 class="text-2xl font-medium tracking-tight text-ink">Quản trị hệ thống & Đối soát tài chính</h1>
      <p class="mt-1 text-sm text-ink-secondary">
        Quản lý danh mục đóng gói cố định, cấu hình đợt giao chuẩn UTC+07:00, phê duyệt hoàn tiền và chính sách hoa hồng đại lý
      </p>
    </div>

    <!-- 4 Compact Stat Cards (Numbers Only — Unslop Spec) -->
    <div class="grid grid-cols-2 gap-4 lg:grid-cols-4">
      <div class="rounded-2xl border hairline bg-surface p-5 shadow-card">
        <div class="text-xs text-ink-muted">Doanh thu ghi nhận (VNPAY)</div>
        <div class="mt-2 font-mono text-2xl font-medium text-ink tabular-nums">
          {{ formatVnd(totalRevenueVnd) }}
        </div>
        <div class="mt-1 text-xs text-forest-800">+14.5% so với đợt trước</div>
      </div>
      <div class="rounded-2xl border hairline bg-surface p-5 shadow-card">
        <div class="text-xs text-ink-muted">Tổng đơn trong hệ thống</div>
        <div class="mt-2 font-mono text-2xl font-medium text-ink tabular-nums">
          {{ demoStore.orders.length }}
        </div>
        <div class="mt-1 text-xs text-ink-secondary">{{ completedOrdersCount }} đơn đã hoàn tất</div>
      </div>
      <div class="rounded-2xl border hairline bg-surface p-5 shadow-card">
        <div class="text-xs text-ink-muted">Yêu cầu hoàn tiền chờ duyệt</div>
        <div class="mt-2 font-mono text-2xl font-medium text-amberish-800 tabular-nums">
          {{ pendingRefundsCount }}
        </div>
        <div class="mt-1 text-xs text-ink-secondary">Tự đảo hoa hồng khi duyệt</div>
      </div>
      <div class="rounded-2xl border hairline bg-surface p-5 shadow-card">
        <div class="text-xs text-ink-muted">Tổng hoa hồng ròng 3 đại lý</div>
        <div class="mt-2 font-mono text-2xl font-medium text-forest-800 tabular-nums">
          {{ formatVnd(totalCommissionNet) }}
        </div>
        <div class="mt-1 text-xs text-ink-secondary">{{ demoStore.commissionLedger.length }} bút toán sổ cái</div>
      </div>
    </div>

    <!-- Accessible 5-Tab Bar -->
    <div class="o2o-tablist" role="tablist" aria-label="Nghiệp vụ quản trị">
      <button
        id="tab-adm-overview"
        type="button"
        role="tab"
        :aria-selected="activeTab === 'overview'"
        @click="activeTab = 'overview'"
      >
        Tổng quan & Biểu đồ
      </button>
      <button
        id="tab-adm-catalog"
        type="button"
        role="tab"
        :aria-selected="activeTab === 'catalog'"
        @click="activeTab = 'catalog'"
      >
        Danh mục cố định ({{ demoStore.products.length }})
      </button>
      <button
        id="tab-adm-cycles"
        type="button"
        role="tab"
        :aria-selected="activeTab === 'cycles'"
        @click="activeTab = 'cycles'"
      >
        Cấu hình đợt giao ({{ demoStore.cycles.length }})
      </button>
      <button
        id="tab-adm-refunds"
        type="button"
        role="tab"
        :aria-selected="activeTab === 'refunds'"
        @click="activeTab = 'refunds'"
      >
        Duyệt hoàn tiền & Đối soát ({{ pendingRefundsCount }})
      </button>
      <button
        id="tab-adm-policies"
        type="button"
        role="tab"
        :aria-selected="activeTab === 'policies'"
        @click="activeTab = 'policies'"
      >
        Hoa hồng & Nhân sự
      </button>
    </div>

    <div
      v-if="statusMsg"
      role="status"
      class="rounded-xl border border-forest-200 bg-forest-50 px-4 py-3 text-xs font-medium text-forest-800"
    >
      {{ statusMsg }}
    </div>
    <div
      v-if="errorMsg"
      role="alert"
      class="rounded-xl border border-danger-100 bg-danger-50 px-4 py-3 text-xs font-medium text-danger-700"
    >
      {{ errorMsg }}
    </div>

    <!-- TAB 1: Overview Chart + Pickup Point Ranking -->
    <div v-show="activeTab === 'overview'" role="tabpanel" aria-labelledby="tab-adm-overview" class="grid grid-cols-1 gap-6 lg:grid-cols-12">
      <!-- Main Chart (2/3 width, single accent for current day) -->
      <section class="rounded-2xl border hairline bg-surface p-6 shadow-card lg:col-span-8 space-y-4">
        <div class="flex flex-wrap items-baseline justify-between gap-2">
          <div>
            <h2 class="text-base font-medium text-ink">Doanh thu theo đợt giao 7 ngày gần nhất</h2>
            <p class="text-xs text-ink-muted">Đơn vị: triệu VNĐ (thanh toán trước 100% qua VNPAY)</p>
          </div>
          <span class="font-mono text-xs text-forest-800">Đợt hiện tại: 26,8 triệu ₫ · 134 đơn</span>
        </div>

        <div class="w-full overflow-x-auto">
          <svg
            viewBox="0 0 640 260"
            class="h-64 w-full min-w-[460px]"
            role="img"
            aria-label="Biểu đồ doanh thu 7 ngày gần nhất"
          >
            <!-- Muted horizontal gridlines -->
            <g v-for="tick in [0, 10, 20, 30]" :key="tick">
              <line
                x1="44"
                :y1="220 - (tick / maxChartVal) * 185"
                x2="620"
                :y2="220 - (tick / maxChartVal) * 185"
                stroke="rgba(31, 27, 22, 0.08)"
                stroke-dasharray="3 3"
              />
              <text
                x="36"
                :y="224 - (tick / maxChartVal) * 185"
                text-anchor="end"
                fill="#7B7266"
                font-size="11"
                font-family="JetBrains Mono, monospace"
              >
                {{ tick }}tr
              </text>
            </g>

            <!-- Bars -->
            <g v-for="(pt, idx) in chartSeries" :key="pt.label">
              <rect
                :x="66 + idx * 78"
                :y="220 - (pt.revenueM / maxChartVal) * 185"
                width="44"
                :height="(pt.revenueM / maxChartVal) * 185"
                rx="6"
                :fill="pt.current ? '#166534' : '#DDD6C9'"
              />
              <text
                :x="88 + idx * 78"
                :y="212 - (pt.revenueM / maxChartVal) * 185"
                text-anchor="middle"
                :fill="pt.current ? '#14532D' : '#5E554A'"
                font-size="11"
                font-family="JetBrains Mono, monospace"
              >
                {{ pt.revenueM }}
              </text>
              <text
                :x="88 + idx * 78"
                y="242"
                text-anchor="middle"
                fill="#7B7266"
                font-size="11"
                font-family="JetBrains Mono, monospace"
              >
                {{ pt.label }}
              </text>
            </g>
          </svg>
        </div>
      </section>

      <!-- Ranked List of Pickup Points -->
      <section class="rounded-2xl border hairline bg-surface p-6 shadow-card lg:col-span-4 space-y-4">
        <div>
          <h2 class="text-base font-medium text-ink">Hiệu suất theo đại lý nhận hàng</h2>
          <p class="text-xs text-ink-muted">Công suất kho lạnh & số dư hoa hồng kỳ này</p>
        </div>
        <div class="space-y-4">
          <div
            v-for="pp in pickupPointStats"
            :key="pp.id"
            class="space-y-1.5 border-b hairline pb-3.5 last:border-b-0 last:pb-0 text-xs"
          >
            <div class="flex items-center justify-between">
              <span class="font-medium text-ink">{{ pp.shortName }}</span>
              <span class="font-mono text-forest-800 tabular-nums">{{ formatVnd(pp.ledgerNet) }}</span>
            </div>
            <div class="flex items-center justify-between text-[11px] text-ink-muted">
              <span>Phụ trách: {{ pp.agentName }}</span>
              <span class="font-mono tabular-nums">
                {{ pp.activeCycle?.currentOrders || 0 }} / {{ pp.coldStorageCapacity }} đơn
              </span>
            </div>
            <progress
              :value="pp.activeCycle?.currentOrders || 0"
              :max="pp.coldStorageCapacity"
              class="h-1.5 w-full"
            />
          </div>
        </div>
      </section>
    </div>

    <!-- TAB 2: Fixed-Pack Product Catalog -->
    <div v-show="activeTab === 'catalog'" role="tabpanel" aria-labelledby="tab-adm-catalog" class="space-y-4">
      <div class="flex flex-wrap items-center justify-between gap-3">
        <div class="flex items-center gap-2 text-sm font-medium text-ink">
          <Package :size="16" :stroke-width="1.5" class="text-forest-700" />
          <span>Danh mục 1 đơn vị bán — quy cách đóng gói cố định</span>
        </div>
        <button
          type="button"
          @click="addProdModalOpen = true"
          class="inline-flex h-9 items-center gap-1.5 rounded-lg bg-forest-700 px-3.5 text-xs font-medium text-white hover:bg-forest-800"
        >
          <Plus :size="14" :stroke-width="1.5" />
          <span>Thêm sản phẩm đóng gói cố định</span>
        </button>
      </div>

      <div class="overflow-x-auto rounded-2xl border hairline bg-surface shadow-card">
        <table class="w-full text-left text-xs">
          <thead class="border-b hairline bg-canvas text-ink-muted">
            <tr>
              <th class="px-4 py-3 font-medium">SKU / Tên sản phẩm</th>
              <th class="px-3 py-3 font-medium">Quy cách</th>
              <th class="px-3 py-3 font-medium">Bảo quản</th>
              <th class="px-3 py-3 font-medium text-right">Đơn giá</th>
              <th class="px-3 py-3 font-medium text-right">Tồn khả dụng</th>
              <th class="px-3 py-3 font-medium text-right">Giữ / Chốt</th>
              <th class="px-4 py-3 font-medium text-right">Trạng thái</th>
            </tr>
          </thead>
          <tbody class="divide-y hairline">
            <tr v-for="p in demoStore.products" :key="p.variantId" class="hover:bg-canvas/40">
              <td class="px-4 py-3">
                <div class="font-medium text-ink">{{ p.productName }}</div>
                <div class="font-mono text-[11px] text-ink-muted">{{ p.sku }} · {{ p.category }}</div>
              </td>
              <td class="px-3 py-3 text-ink-secondary">
                {{ p.packName }}
                <span class="text-ink-muted">({{ p.packWeightGrams }}g)</span>
              </td>
              <td class="px-3 py-3 text-ink-secondary">{{ storageTypeShort(p.storageType) }}</td>
              <td class="px-3 py-3 text-right font-mono font-medium text-ink tabular-nums">
                {{ formatVnd(p.unitPrice) }}
              </td>
              <td class="px-3 py-3 text-right font-mono tabular-nums text-forest-800">
                {{ p.availableQty }}
              </td>
              <td class="px-3 py-3 text-right font-mono tabular-nums text-ink-muted">
                {{ p.reservedQty }} / {{ p.committedQty }}
              </td>
              <td class="px-4 py-3 text-right">
                <button
                  type="button"
                  @click="p.isActive = !p.isActive"
                  :class="[
                    'rounded-md border px-2.5 py-1 text-[11px] font-medium',
                    p.isActive
                      ? 'border-forest-200 bg-forest-50 text-forest-800'
                      : 'hairline bg-raised text-ink-muted',
                  ]"
                >
                  {{ p.isActive ? 'Đang bán' : 'Tạm ngưng' }}
                </button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>

    <!-- TAB 3: Order Cycles Configuration -->
    <div v-show="activeTab === 'cycles'" role="tabpanel" aria-labelledby="tab-adm-cycles" class="grid grid-cols-1 gap-6 lg:grid-cols-12">
      <section class="rounded-2xl border hairline bg-surface p-5 shadow-card lg:col-span-4 space-y-4 h-fit text-xs">
        <div class="flex items-center gap-2">
          <CalendarClock :size="18" :stroke-width="1.5" class="text-forest-700" />
          <h2 class="text-base font-medium text-ink">Mở đợt giao hàng mới</h2>
        </div>
        <p class="text-ink-secondary leading-relaxed">
          Hệ thống tự sinh 7 mốc thời gian chuẩn theo múi giờ <span class="font-mono">Asia/Ho_Chi_Minh</span>: chốt 23:59 D, khóa đơn 00:00 D+1, đóng gói 08:00, nhận hàng 16:00 và tự hoàn tất 00:00 D+2.
        </p>
        <form @submit.prevent="handleCreateCycle" class="space-y-3">
          <div>
            <label for="adm-cycle-date" class="mb-1 block font-medium text-ink-secondary">Ngày giao hàng (D+1)</label>
            <input
              id="adm-cycle-date"
              v-model="newCycleDate"
              type="date"
              required
              class="h-9 w-full rounded-lg border hairline bg-canvas/50 px-3 text-xs text-ink"
            />
          </div>
          <div>
            <label for="adm-timeout" class="mb-1 block font-medium text-ink-secondary">Thời gian giữ tồn chờ thanh toán (phút)</label>
            <input
              id="adm-timeout"
              v-model.number="newCycleTimeout"
              type="number"
              min="10"
              max="60"
              required
              class="h-9 w-full rounded-lg border hairline bg-canvas/50 px-3 font-mono text-xs text-ink"
            />
          </div>
          <button
            type="submit"
            class="h-10 w-full rounded-lg bg-forest-700 text-xs font-medium text-white hover:bg-forest-800"
          >
            Tạo đợt giao chuẩn hóa
          </button>
        </form>
      </section>

      <section class="rounded-2xl border hairline bg-surface p-5 shadow-card lg:col-span-8 space-y-3">
        <h2 class="text-base font-medium text-ink">Danh sách đợt giao hàng (OrderCycles)</h2>
        <div class="space-y-3">
          <div
            v-for="c in demoStore.cycles"
            :key="c.id"
            class="rounded-xl border hairline bg-canvas/50 p-4 text-xs space-y-2.5"
          >
            <div class="flex flex-wrap items-center justify-between gap-2">
              <div class="flex items-center gap-2">
                <span class="font-mono text-sm font-medium text-ink">{{ c.cycleCode }}</span>
                <span class="text-ink-muted">Ngày giao: {{ formatVnDate(c.deliveryDate) }}</span>
              </div>
              <StatusBadge :status="c.status" />
            </div>
            <div class="grid grid-cols-1 gap-2 sm:grid-cols-3 text-[11px] text-ink-secondary">
              <div>Hạn chốt: <span class="font-mono text-ink">{{ formatVnDateTime(c.cutoffAt) }}</span></div>
              <div>Kho xong / Xuất: <span class="font-mono text-ink">{{ formatVnDateTime(c.warehousePackTargetAt) }}</span></div>
              <div>Mở nhận hàng: <span class="font-mono text-ink">{{ formatVnDateTime(c.pickupStartAt) }}</span></div>
              <div>Tự hoàn tất: <span class="font-mono text-ink">{{ formatVnDateTime(c.autoCompleteAt) }}</span></div>
              <div>Giữ tồn: <span class="font-mono text-ink">{{ c.paymentTimeoutMinutes }} phút</span></div>
            </div>
          </div>
        </div>
      </section>
    </div>

    <!-- TAB 4: Refund Approval & Commission Reversal -->
    <div v-show="activeTab === 'refunds'" role="tabpanel" aria-labelledby="tab-adm-refunds" class="space-y-4">
      <div class="rounded-xl border hairline bg-surface p-4 text-xs text-ink-secondary leading-relaxed">
        <strong class="font-medium text-ink">Ràng buộc đối soát tài chính:</strong>
        Với đơn hoàn tiền do sự cố hàng lỗi, Quản trị chỉ duyệt được sau khi Kho đã kiểm nhận hàng vào khu cách ly (<span class="font-mono">WAREHOUSE_RECEIVED</span>) hoặc được duyệt đặc cách miễn thu hồi. Nếu đơn đã từng ghi nhận <span class="font-mono">CREDIT_COMMISSION</span>, hệ thống tự động thêm 1 dòng âm <span class="font-mono">DEBIT_REFUND_REVERSAL</span> vào sổ cái hoa hồng.
      </div>

      <div v-if="demoStore.refunds.length === 0" class="rounded-2xl border hairline bg-surface p-10 text-center text-sm text-ink-secondary">
        Không có yêu cầu hoàn tiền nào.
      </div>

      <div v-else class="space-y-3">
        <article
          v-for="refItem in demoStore.refunds"
          :key="refItem.id"
          class="rounded-2xl border hairline bg-surface p-5 shadow-card space-y-3 text-xs"
        >
          <div class="flex flex-wrap items-start justify-between gap-3">
            <div>
              <div class="flex items-center gap-2">
                <span class="font-mono text-sm font-medium text-ink">
                  Hoàn tiền #{{ refItem.id }} · {{ refItem.orderCode }}
                </span>
                <StatusBadge :status="refItem.status" />
              </div>
              <div class="mt-1 text-ink-secondary">
                Khách: {{ refItem.customerName }} · Lý do: <span class="font-mono">{{ refItem.refundReason }}</span>
              </div>
            </div>
            <div class="text-right">
              <div class="font-mono text-lg font-medium text-ink tabular-nums">
                {{ formatVnd(refItem.amount) }}
              </div>
              <div v-if="refItem.providerRef" class="font-mono text-[11px] text-forest-800">
                {{ refItem.providerRef }}
              </div>
            </div>
          </div>

          <div
            v-if="linkedReturnForRefund(refItem.returnRequestId)"
            class="flex flex-wrap items-center justify-between gap-2 rounded-xl border hairline bg-canvas px-3.5 py-2.5"
          >
            <div>
              <span class="text-ink-muted">Trạng thái thu hồi kho cách ly:</span>
              <span class="ml-2">
                <StatusBadge :status="linkedReturnForRefund(refItem.returnRequestId)!.status" />
              </span>
            </div>
            <button
              v-if="
                linkedReturnForRefund(refItem.returnRequestId)!.status !== 'WAREHOUSE_RECEIVED' &&
                linkedReturnForRefund(refItem.returnRequestId)!.status !== 'UNRECOVERABLE_WAIVED' &&
                linkedReturnForRefund(refItem.returnRequestId)!.status !== 'CLOSED'
              "
              type="button"
              @click="handleWaiveReturn(refItem.returnRequestId!)"
              class="h-8 rounded-lg border border-amberish-200 bg-amberish-50 px-3 text-[11px] font-medium text-amberish-800 hover:bg-amberish-100"
            >
              Duyệt đặc cách miễn thu hồi (hỏng không thể hoàn)
            </button>
          </div>

          <div class="flex flex-wrap items-center justify-between gap-2 pt-1">
            <span class="text-ink-muted">
              Tạo lúc: {{ formatVnDateTime(refItem.createdAt) }}
              <span v-if="refItem.approvedByName"> · Duyệt bởi: {{ refItem.approvedByName }}</span>
            </span>

            <button
              v-if="refItem.status !== 'COMPLETED'"
              type="button"
              @click="handleApproveRefund(refItem.id)"
              class="inline-flex h-9 items-center gap-1.5 rounded-lg bg-forest-700 px-4 text-xs font-medium text-white hover:bg-forest-800"
            >
              <CheckCircle2 :size="14" :stroke-width="1.5" />
              <span>Phê duyệt hoàn tiền & Tự đảo hoa hồng</span>
            </button>
          </div>
        </article>
      </div>
    </div>

    <!-- TAB 5: Commission Policies & Users -->
    <div v-show="activeTab === 'policies'" role="tabpanel" aria-labelledby="tab-adm-policies" class="grid grid-cols-1 gap-6 lg:grid-cols-12">
      <section class="rounded-2xl border hairline bg-surface p-5 shadow-card lg:col-span-5 space-y-4">
        <div class="flex items-center gap-2">
          <ReceiptText :size="18" :stroke-width="1.5" class="text-forest-700" />
          <h2 class="text-base font-medium text-ink">Chính sách hoa hồng đại lý</h2>
        </div>

        <div class="space-y-3 text-xs">
          <div
            v-for="pol in demoStore.commissionPolicies"
            :key="pol.id"
            :class="[
              'rounded-xl border p-4 space-y-2',
              pol.isActive ? 'border-forest-700 bg-forest-50/40' : 'hairline bg-canvas/50',
            ]"
          >
            <div class="flex items-center justify-between">
              <span class="font-mono font-medium text-ink">{{ pol.policyCode }}</span>
              <span
                :class="[
                  'rounded-md px-2 py-0.5 text-[11px] font-medium',
                  pol.isActive ? 'bg-forest-700 text-white' : 'bg-raised text-ink-muted',
                ]"
              >
                {{ pol.isActive ? 'Đang áp dụng' : 'Dự phòng' }}
              </span>
            </div>
            <div class="text-ink-secondary">
              {{
                pol.calcType === 'PERCENTAGE_NET_MERCHANDISE'
                  ? `Tỷ lệ ${pol.percentageRate}% giá trị thực thu hàng hóa mỗi đơn hoàn tất`
                  : `Mức cố định ${formatVnd(pol.fixedAmount)} cho mỗi đơn hoàn tất`
              }}
            </div>
            <button
              v-if="!pol.isActive"
              type="button"
              @click="handleActivatePolicy(pol.id)"
              class="h-8 rounded-lg border hairline bg-surface px-3 text-xs font-medium text-ink hover:bg-raised"
            >
              Áp dụng chính sách này
            </button>
          </div>
        </div>
      </section>

      <section class="rounded-2xl border hairline bg-surface p-5 shadow-card lg:col-span-7 space-y-4">
        <div class="flex items-center gap-2">
          <Users :size="18" :stroke-width="1.5" class="text-forest-700" />
          <h2 class="text-base font-medium text-ink">Danh bạ tài khoản & Phân quyền (6 vai trò)</h2>
        </div>
        <div class="overflow-x-auto rounded-xl border hairline">
          <table class="w-full text-left text-xs">
            <thead class="border-b hairline bg-canvas text-ink-muted">
              <tr>
                <th class="px-3.5 py-2.5 font-medium">Họ tên</th>
                <th class="px-3 py-2.5 font-medium">Email / SĐT</th>
                <th class="px-3.5 py-2.5 font-medium">Vai trò</th>
              </tr>
            </thead>
            <tbody class="divide-y hairline bg-surface">
              <tr v-for="u in demoStore.users" :key="u.userId">
                <td class="px-3.5 py-2.5 font-medium text-ink">{{ u.fullName }}</td>
                <td class="px-3 py-2.5">
                  <div class="font-mono text-[11px] text-ink-secondary">{{ u.email }}</div>
                  <div class="text-[11px] text-ink-muted">{{ u.phone }}</div>
                </td>
                <td class="px-3.5 py-2.5">
                  <span class="inline-flex items-center gap-1 rounded-md bg-raised px-2 py-0.5 text-[11px] font-medium text-ink">
                    <ShieldCheck :size="12" :stroke-width="1.5" class="text-forest-700" />
                    <span>{{ roleDisplayLabel(u.roles[0]) }}</span>
                  </span>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>
    </div>

    <!-- Add Product Modal -->
    <AppModal
      :open="addProdModalOpen"
      title="Thêm sản phẩm đóng gói cố định"
      subtitle="Giá niêm yết thống nhất trên toàn hệ thống điểm nhận hàng"
      @close="addProdModalOpen = false"
    >
      <form id="new-prod-form" @submit.prevent="submitNewProduct" class="space-y-3 text-xs">
        <div>
          <label for="np-name" class="mb-1 block font-medium text-ink-secondary">Tên sản phẩm</label>
          <input
            id="np-name"
            v-model="newProdName"
            type="text"
            required
            placeholder="Ví dụ: Cải bó xôi hữu cơ Đà Lạt"
            class="h-9 w-full rounded-lg border hairline bg-canvas/50 px-3 text-xs text-ink"
          />
        </div>
        <div class="grid grid-cols-2 gap-3">
          <div>
            <label for="np-cat" class="mb-1 block font-medium text-ink-secondary">Danh mục</label>
            <select id="np-cat" v-model="newProdCategory" class="h-9 w-full rounded-lg border hairline bg-canvas/50 px-2.5 text-xs text-ink">
              <option>Thịt tươi sống</option>
              <option>Thủy hải sản</option>
              <option>Rau ăn lá</option>
              <option>Rau củ quả</option>
              <option>Trứng & Đậu</option>
              <option>Lương thực khô</option>
            </select>
          </div>
          <div>
            <label for="np-store" class="mb-1 block font-medium text-ink-secondary">Bảo quản</label>
            <select id="np-store" v-model="newProdStorage" class="h-9 w-full rounded-lg border hairline bg-canvas/50 px-2.5 text-xs text-ink">
              <option value="CHILLED">Mát (0–4°C)</option>
              <option value="FROZEN">Đông lạnh (-18°C)</option>
              <option value="AMBIENT">Nhiệt độ thường</option>
            </select>
          </div>
        </div>
        <div class="grid grid-cols-2 gap-3">
          <div>
            <label for="np-pack" class="mb-1 block font-medium text-ink-secondary">Quy cách đóng gói</label>
            <input id="np-pack" v-model="newProdPackName" type="text" required class="h-9 w-full rounded-lg border hairline bg-canvas/50 px-3 text-xs text-ink" />
          </div>
          <div>
            <label for="np-weight" class="mb-1 block font-medium text-ink-secondary">Khối lượng (gram)</label>
            <input id="np-weight" v-model.number="newProdWeight" type="number" min="50" required class="h-9 w-full rounded-lg border hairline bg-canvas/50 px-3 font-mono text-xs text-ink" />
          </div>
        </div>
        <div class="grid grid-cols-3 gap-3">
          <div>
            <label for="np-price" class="mb-1 block font-medium text-ink-secondary">Giá bán (VNĐ)</label>
            <input id="np-price" v-model.number="newProdPrice" type="number" step="1000" required class="h-9 w-full rounded-lg border hairline bg-canvas/50 px-3 font-mono text-xs text-ink" />
          </div>
          <div>
            <label for="np-stock" class="mb-1 block font-medium text-ink-secondary">Tồn đầu đợt</label>
            <input id="np-stock" v-model.number="newProdStock" type="number" min="0" required class="h-9 w-full rounded-lg border hairline bg-canvas/50 px-3 font-mono text-xs text-ink" />
          </div>
          <div>
            <label for="np-shelf" class="mb-1 block font-medium text-ink-secondary">Hạn tối thiểu (ngày)</label>
            <input id="np-shelf" v-model.number="newProdShelfLife" type="number" min="1" required class="h-9 w-full rounded-lg border hairline bg-canvas/50 px-3 font-mono text-xs text-ink" />
          </div>
        </div>
      </form>
      <template #footer>
        <button
          type="button"
          @click="addProdModalOpen = false"
          class="h-9 rounded-lg border hairline bg-surface px-4 text-xs font-medium text-ink hover:bg-raised"
        >
          Hủy
        </button>
        <button
          type="submit"
          form="new-prod-form"
          class="h-9 rounded-lg bg-forest-700 px-4 text-xs font-medium text-white hover:bg-forest-800"
        >
          Lưu sản phẩm
        </button>
      </template>
    </AppModal>
  </div>
</template>

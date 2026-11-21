<script setup lang="ts">
import { computed, reactive, ref } from 'vue';
import {
  AlertTriangle,
  CheckCircle2,
  ClipboardCheck,
  PhoneCall,
  QrCode,
  ReceiptText,
  Store,
  Wallet,
} from 'lucide-vue-next';
import httpClient from '@/api/httpClient';
import { useDemoStore } from '@/stores/demoStore';
import { useAuthStore } from '@/stores/authStore';
import StatusBadge from '@/components/StatusBadge.vue';
import type { PackageCondition } from '@/types/domain';
import { formatVnDateTime, formatVnd, storageTypeShort } from '@/utils/formatters';

const demoStore = useDemoStore();
const authStore = useAuthStore();

const activeTab = ref<'checkin' | 'pickup' | 'unclaimed' | 'ledger'>('checkin');
const selectedPickupPointId = ref<number>(
  authStore.user?.assignedPickupPointId || 1
);

const statusMsg = ref('');
const errorMsg = ref('');

// Driver check-in package conditions map: packageId -> ACCEPTED | DAMAGED | MISSING
const packageConditions = reactive<Record<number, PackageCondition>>({});
const handoverNote = ref('Đã đối chiếu thực tế cùng tài xế tại điểm nhận');

// Customer QR pickup verification
const scanOrderId = ref<number>(101);
const scanQrToken = ref('');

const currentPoint = computed(
  () =>
    demoStore.pickupPoints.find((p) => p.id === selectedPickupPointId.value) ||
    demoStore.pickupPoints[0]
);

const incomingStops = computed(() =>
  demoStore.trips.flatMap((trip) =>
    trip.stops
      .filter((s) => s.pickupPointId === selectedPickupPointId.value && s.status !== 'HANDED_OVER')
      .map((stop) => ({
        trip,
        stop,
        packages: demoStore.packages.filter((p) => p.tripStopId === stop.id),
      }))
  )
);

const readyForPickupOrders = computed(() =>
  demoStore.orders.filter(
    (o) =>
      o.pickupPointId === selectedPickupPointId.value &&
      o.fulfillmentStatus === 'READY_FOR_PICKUP' &&
      !o.isUnclaimedAtAgent
  )
);

const unclaimedOrders = computed(() =>
  demoStore.orders.filter(
    (o) => o.pickupPointId === selectedPickupPointId.value && o.isUnclaimedAtAgent
  )
);

const ledgerEntries = computed(() =>
  demoStore.commissionLedger.filter((e) => e.pickupPointId === selectedPickupPointId.value)
);

const totalCredit = computed(() =>
  ledgerEntries.value
    .filter((e) => e.amount > 0)
    .reduce((acc, e) => acc + e.amount, 0)
);

const totalDebit = computed(() =>
  ledgerEntries.value
    .filter((e) => e.amount < 0)
    .reduce((acc, e) => acc + e.amount, 0)
);

const netCommissionBalance = computed(() => totalCredit.value + totalDebit.value);

function getCondition(pkgId: number): PackageCondition {
  return packageConditions[pkgId] || 'ACCEPTED';
}

function setCondition(pkgId: number, cond: PackageCondition) {
  packageConditions[pkgId] = cond;
}

async function handleSignDriverHandover(stopId: number, pkgIds: number[]) {
  statusMsg.value = '';
  errorMsg.value = '';
  const inspections = pkgIds.map((packageId) => ({
    packageId,
    condition: getCondition(packageId),
  }));

  try {
    if (demoStore.isDemoMode) {
      const res = demoStore.completeAgentHandover(stopId, inspections, handoverNote.value);
      const parts: string[] = [];
      if (res.readyOrderCodes.length > 0) {
        parts.push(`Mở nhận hàng cho đơn: ${res.readyOrderCodes.join(', ')}`);
      }
      if (res.issueOrderCodes.length > 0) {
        parts.push(`Tạm giữ sự cố (ISSUE_HOLD) cho đơn: ${res.issueOrderCodes.join(', ')}`);
      }
      statusMsg.value = `Đã ký biên bản Tài xế → Đại lý. ${parts.join(' · ') || 'Hoàn tất cập nhật điểm dừng.'}`;
      return;
    }

    const res = await httpClient.post(`/fulfillment/trip-stops/${stopId}/agent-handover`, {
      driverId: 4,
      agentUserId: authStore.user?.userId || 6,
      inspections: inspections.map((i) => ({
        packageId: i.packageId,
        conditionStatus: i.condition,
        evidenceImageUrl: i.condition === 'DAMAGED' ? 'https://img.o2ofood.vn/damaged-pkg.jpg' : null,
      })),
      note: handoverNote.value,
    });
    statusMsg.value = res.data.message;
  } catch (err: any) {
    errorMsg.value = err?.response?.data?.message || err?.message || 'Lỗi xác nhận biên bản bàn giao.';
  }
}

function quickFillCustomerOrder(orderId: number, token: string) {
  scanOrderId.value = orderId;
  scanQrToken.value = token;
}

async function handleVerifyCustomerQr() {
  statusMsg.value = '';
  errorMsg.value = '';
  try {
    if (demoStore.isDemoMode) {
      statusMsg.value = demoStore.agentScanCustomerQr(scanOrderId.value, scanQrToken.value);
      scanQrToken.value = '';
      return;
    }
    const res = await httpClient.post(`/fulfillment/orders/${scanOrderId.value}/agent-scan-qr`, {
      qrToken: scanQrToken.value,
    });
    statusMsg.value = res.data.message;
  } catch (err: any) {
    errorMsg.value = err?.response?.data?.message || err?.message || 'Mã QR nhận hàng không hợp lệ hoặc đã dùng.';
  }
}

function handleUnclaimedAction(orderId: number, actionLabel: string) {
  statusMsg.value = demoStore.updateUnclaimedNote(orderId, actionLabel);
}
</script>

<template>
  <div class="space-y-6">
    <!-- Agent Point Header -->
    <div class="flex flex-wrap items-end justify-between gap-4">
      <div>
        <h1 class="text-2xl font-medium tracking-tight text-ink">Cổng Đại lý điểm nhận hàng</h1>
        <p class="mt-1 text-sm text-ink-secondary">
          Kiểm nhận kiện hàng từ tài xế trước 16:00, quét mã QR bàn giao khách, theo dõi hàng tồn chưa lấy và sổ cái hoa hồng
        </p>
      </div>

      <div class="flex items-center gap-2 rounded-xl border hairline bg-surface px-3 py-2 text-xs">
        <Store :size="16" :stroke-width="1.5" class="text-forest-700 shrink-0" />
        <label for="agent-point-select" class="text-ink-muted">Điểm nhận:</label>
        <select
          id="agent-point-select"
          v-model.number="selectedPickupPointId"
          class="bg-transparent font-medium text-ink focus:outline-none"
        >
          <option v-for="pp in demoStore.pickupPoints" :key="pp.id" :value="pp.id">
            {{ pp.shortName }}
          </option>
        </select>
      </div>
    </div>

    <!-- Compact Agent KPI Row -->
    <div class="grid grid-cols-2 gap-4 lg:grid-cols-4">
      <div class="rounded-2xl border hairline bg-surface p-5 shadow-card">
        <div class="text-xs text-ink-muted">Chuyến sắp đến kiểm nhận</div>
        <div class="mt-2 font-mono text-3xl font-medium text-ink tabular-nums">
          {{ incomingStops.length }}
        </div>
        <div class="mt-1 text-xs text-ink-secondary">{{ currentPoint?.code }}</div>
      </div>
      <div class="rounded-2xl border hairline bg-surface p-5 shadow-card">
        <div class="text-xs text-ink-muted">Đơn chờ khách quét QR</div>
        <div class="mt-2 font-mono text-3xl font-medium text-forest-800 tabular-nums">
          {{ readyForPickupOrders.length }}
        </div>
        <div class="mt-1 text-xs text-ink-secondary">Mở trả hàng từ 16:00</div>
      </div>
      <div class="rounded-2xl border hairline bg-surface p-5 shadow-card">
        <div class="text-xs text-ink-muted">Hàng tồn chưa lấy (&gt;00:00 D+2)</div>
        <div class="mt-2 font-mono text-3xl font-medium text-amberish-800 tabular-nums">
          {{ unclaimedOrders.length }}
        </div>
        <div class="mt-1 text-xs text-ink-secondary">Lưu kho lạnh đại lý</div>
      </div>
      <div class="rounded-2xl border hairline bg-surface p-5 shadow-card">
        <div class="text-xs text-ink-muted">Số dư hoa hồng ròng</div>
        <div class="mt-2 font-mono text-2xl font-medium text-forest-800 tabular-nums">
          {{ formatVnd(netCommissionBalance) }}
        </div>
        <div class="mt-1 text-xs text-ink-secondary">{{ ledgerEntries.length }} bút toán bất biến</div>
      </div>
    </div>

    <!-- Tabs -->
    <div class="o2o-tablist" role="tablist" aria-label="Nghiệp vụ đại lý">
      <button
        id="tab-ag-checkin"
        type="button"
        role="tab"
        :aria-selected="activeTab === 'checkin'"
        @click="activeTab = 'checkin'"
      >
        Kiểm nhận từ tài xế ({{ incomingStops.length }})
      </button>
      <button
        id="tab-ag-pickup"
        type="button"
        role="tab"
        :aria-selected="activeTab === 'pickup'"
        @click="activeTab = 'pickup'"
      >
        Quét QR trả hàng khách ({{ readyForPickupOrders.length }})
      </button>
      <button
        id="tab-ag-unclaimed"
        type="button"
        role="tab"
        :aria-selected="activeTab === 'unclaimed'"
        @click="activeTab = 'unclaimed'"
      >
        Hàng tồn chưa lấy ({{ unclaimedOrders.length }})
      </button>
      <button
        id="tab-ag-ledger"
        type="button"
        role="tab"
        :aria-selected="activeTab === 'ledger'"
        @click="activeTab = 'ledger'"
      >
        Sổ cái hoa hồng bất biến ({{ ledgerEntries.length }})
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

    <!-- TAB 1: Driver-to-Agent Handover Check-in -->
    <div v-show="activeTab === 'checkin'" role="tabpanel" aria-labelledby="tab-ag-checkin" class="space-y-4">
      <div
        v-if="incomingStops.length === 0"
        class="rounded-2xl border hairline bg-surface p-10 text-center text-sm text-ink-secondary"
      >
        Tất cả các chuyến giao hàng đến {{ currentPoint?.shortName }} đều đã hoàn tất biên bản kiểm nhận.
      </div>

      <article
        v-for="{ trip, stop, packages: stopPackages } in incomingStops"
        :key="stop.id"
        class="rounded-2xl border hairline bg-surface p-5 shadow-card space-y-4 text-xs"
      >
        <div class="flex flex-wrap items-start justify-between gap-3 border-b hairline pb-3.5">
          <div>
            <div class="flex items-center gap-2">
              <ClipboardCheck :size="16" :stroke-width="1.5" class="text-forest-700" />
              <span class="font-mono text-sm font-medium text-ink">{{ trip.tripCode }}</span>
              <span class="text-ink-muted">· Tài xế {{ trip.driverName }}</span>
            </div>
            <div class="mt-1 text-ink-secondary">
              Điểm dừng #{{ stop.stopSequence }} · Dự kiến {{ formatVnDateTime(stop.etaAt) }}
            </div>
          </div>
          <StatusBadge :status="stop.status" />
        </div>

        <div class="rounded-xl border border-amberish-200 bg-amberish-50 px-3.5 py-2.5 text-amberish-800">
          Quy tắc kiểm nhận đơn nhiều kiện: Nếu 1 đơn gồm 2 kiện mà 1 kiện bị đánh dấu
          <strong class="font-medium">Dập hỏng</strong> hoặc <strong class="font-medium">Thiếu kiện</strong>, toàn bộ đơn đó sẽ giữ ở trạng thái
          <span class="font-mono">ISSUE_HOLD</span> và không gửi thông báo mời khách ra lấy.
        </div>

        <div v-if="stopPackages.length === 0" class="rounded-xl bg-canvas p-4 text-ink-muted">
          Chuyến này chưa có kiện hàng gắn trực tiếp vào điểm dừng này (hãy đóng gói đơn và lập chuyến mới).
        </div>

        <div v-else class="space-y-2.5">
          <div
            v-for="pkg in stopPackages"
            :key="pkg.id"
            class="flex flex-wrap items-center justify-between gap-3 rounded-xl border hairline bg-canvas/60 p-3.5"
          >
            <div>
              <div class="font-mono font-medium text-ink">
                {{ pkg.packageCode }}
                <span class="ml-1 font-sans font-normal text-ink-secondary">
                  (Đơn {{ pkg.orderCode }} · Kiện {{ pkg.packageSeq }}/{{ pkg.totalPackagesInOrder }} · {{ storageTypeShort(pkg.storageType) }})
                </span>
              </div>
              <div class="font-mono text-[11px] text-ink-muted mt-0.5">{{ pkg.qrPayload }}</div>
            </div>

            <div class="flex items-center gap-1.5" role="group" :aria-label="`Tình trạng kiện ${pkg.packageCode}`">
              <button
                v-for="opt in [
                  { val: 'ACCEPTED', label: 'Đủ & nguyên vẹn' },
                  { val: 'DAMAGED', label: 'Dập hỏng' },
                  { val: 'MISSING', label: 'Thiếu kiện' },
                ]"
                :key="opt.val"
                type="button"
                @click="setCondition(pkg.id, opt.val as PackageCondition)"
                :class="[
                  'h-8 rounded-lg border px-2.5 text-xs font-medium',
                  getCondition(pkg.id) === opt.val
                    ? opt.val === 'ACCEPTED'
                      ? 'border-forest-700 bg-forest-700 text-white'
                      : 'border-danger-700 bg-danger-700 text-white'
                    : 'hairline bg-surface text-ink-secondary hover:text-ink',
                ]"
              >
                {{ opt.label }}
              </button>
            </div>
          </div>

          <input
            v-model="handoverNote"
            type="text"
            placeholder="Ghi chú biên bản kiểm nhận..."
            class="h-9 w-full rounded-lg border hairline bg-canvas/50 px-3 text-xs text-ink"
          />

          <button
            type="button"
            @click="handleSignDriverHandover(stop.id, stopPackages.map((p) => p.id))"
            class="inline-flex h-10 w-full items-center justify-center gap-2 rounded-lg bg-forest-700 text-xs font-medium text-white hover:bg-forest-800"
          >
            <CheckCircle2 :size="15" :stroke-width="1.5" />
            <span>Ký biên bản kiểm nhận Tài xế → Đại lý ({{ stopPackages.length }} kiện)</span>
          </button>
        </div>
      </article>
    </div>

    <!-- TAB 2: Customer One-Time QR Handover -->
    <div v-show="activeTab === 'pickup'" role="tabpanel" aria-labelledby="tab-ag-pickup" class="grid grid-cols-1 gap-6 lg:grid-cols-12">
      <!-- Ready Queue -->
      <section class="rounded-2xl border hairline bg-surface p-5 shadow-card lg:col-span-7 space-y-4">
        <h2 class="text-base font-medium text-ink">Đơn hàng sẵn sàng bàn giao cho khách</h2>
        <div v-if="readyForPickupOrders.length === 0" class="rounded-xl bg-canvas p-8 text-center text-xs text-ink-secondary">
          Hiện không có đơn chờ khách đến lấy tại điểm này.
        </div>
        <div v-else class="space-y-3">
          <div
            v-for="ord in readyForPickupOrders"
            :key="ord.orderId"
            class="rounded-xl border hairline bg-canvas/50 p-4 text-xs space-y-2"
          >
            <div class="flex flex-wrap items-start justify-between gap-2">
              <div>
                <div class="font-mono text-sm font-medium text-ink">{{ ord.orderCode }}</div>
                <div class="mt-0.5 text-ink-secondary">
                  Khách: <strong class="font-medium text-ink">{{ ord.customerName }}</strong> · {{ ord.customerPhone }}
                </div>
              </div>
              <button
                type="button"
                @click="quickFillCustomerOrder(ord.orderId, ord.pickupQrToken)"
                class="inline-flex h-8 items-center gap-1.5 rounded-lg border hairline bg-surface px-3 text-xs font-medium text-forest-800 hover:bg-forest-50"
              >
                <QrCode :size="13" :stroke-width="1.5" />
                <span>Chọn quét QR</span>
              </button>
            </div>
            <div class="flex flex-wrap items-center justify-between gap-2 text-[11px] text-ink-muted">
              <span>Giá trị đơn: {{ formatVnd(ord.totalAmount) }}</span>
              <span>Mã nhận mẫu: <code class="font-mono text-ink">{{ ord.pickupQrToken }}</code></span>
            </div>
          </div>
        </div>
      </section>

      <!-- QR Verification Form -->
      <section class="rounded-2xl border hairline bg-surface p-5 shadow-card lg:col-span-5 space-y-4 h-fit text-xs">
        <div class="flex items-center gap-2">
          <QrCode :size="18" :stroke-width="1.5" class="text-forest-700" />
          <h2 class="text-base font-medium text-ink">Xác thực mã QR nhận hàng (1 lần)</h2>
        </div>
        <p class="text-ink-secondary leading-relaxed">
          Quét hoặc nhập mã QR từ ứng dụng khách hàng để ghi nhận chính xác dấu thời gian bàn giao vật lý (<span class="font-mono">PhysicalHandedOverAt</span>).
        </p>

        <form @submit.prevent="handleVerifyCustomerQr" class="space-y-3">
          <div>
            <label for="ag-ord-id" class="mb-1 block font-medium text-ink-secondary">Mã số đơn hàng (Order ID)</label>
            <input
              id="ag-ord-id"
              v-model.number="scanOrderId"
              type="number"
              required
              class="h-10 w-full rounded-lg border hairline bg-canvas/50 px-3 font-mono text-xs text-ink"
            />
          </div>
          <div>
            <label for="ag-qr-token" class="mb-1 block font-medium text-ink-secondary">Mã QR / Pickup Token</label>
            <input
              id="ag-qr-token"
              v-model="scanQrToken"
              type="text"
              required
              placeholder="Ví dụ: PKP-BTL01-8492A1"
              class="h-10 w-full rounded-lg border hairline bg-canvas/50 px-3 font-mono text-xs text-ink"
            />
          </div>
          <button
            type="submit"
            class="h-10 w-full rounded-lg bg-forest-700 text-xs font-medium text-white hover:bg-forest-800"
          >
            Xác nhận bàn giao vật lý cho khách
          </button>
        </form>
      </section>
    </div>

    <!-- TAB 3: Unclaimed Inventory (> 00:00 D+2) -->
    <div v-show="activeTab === 'unclaimed'" role="tabpanel" aria-labelledby="tab-ag-unclaimed" class="space-y-4">
      <div class="flex items-start gap-2.5 rounded-xl border border-amberish-200 bg-amberish-50 p-4 text-xs text-amberish-800">
        <AlertTriangle :size="16" :stroke-width="1.5" class="mt-0.5 shrink-0" />
        <span>
          Các đơn tự hoàn tất lúc <strong class="font-medium">00:00 D+2</strong> (<span class="font-mono">SYSTEM_TIMEOUT</span>) khi khách chưa đến nhận vẫn giữ <span class="font-mono">PhysicalHandedOverAt = NULL</span> và hiện tại đây để đại lý chủ động liên hệ, không làm thất lạc hàng trong kho lạnh.
        </span>
      </div>

      <div v-if="unclaimedOrders.length === 0" class="rounded-2xl border hairline bg-surface p-10 text-center text-sm text-ink-secondary">
        Không có đơn hàng tồn quá hạn chưa lấy tại đại lý này.
      </div>

      <div v-else class="space-y-3">
        <article
          v-for="u in unclaimedOrders"
          :key="u.orderId"
          class="rounded-2xl border border-amberish-200 bg-surface p-5 shadow-card space-y-3 text-xs"
        >
          <div class="flex flex-wrap items-start justify-between gap-2">
            <div>
              <div class="flex items-center gap-2">
                <span class="font-mono text-sm font-medium text-ink">{{ u.orderCode }}</span>
                <StatusBadge status="SYSTEM_TIMEOUT" />
              </div>
              <div class="mt-1 text-ink-secondary">
                Khách: <strong class="font-medium text-ink">{{ u.customerName }}</strong> · {{ u.customerPhone }} · Giá trị: {{ formatVnd(u.totalAmount) }}
              </div>
            </div>
            <span class="rounded-md bg-amberish-100 px-2.5 py-1 font-mono text-[11px] font-medium text-amberish-800">
              UNCLAIMED_AT_AGENT
            </span>
          </div>

          <div v-if="u.unclaimedContactNote" class="rounded-lg bg-canvas px-3 py-2 text-ink-secondary">
            Nhật ký mới nhất: <strong class="font-medium text-ink">{{ u.unclaimedContactNote }}</strong>
          </div>

          <div class="flex flex-wrap gap-2 pt-1">
            <button
              type="button"
              @click="handleUnclaimedAction(u.orderId, 'Đã gọi điện nhắc khách đến nhận trong ngày')"
              class="inline-flex h-8 items-center gap-1.5 rounded-lg border hairline bg-canvas px-3 font-medium text-ink hover:bg-raised"
            >
              <PhoneCall :size="13" :stroke-width="1.5" />
              <span>Ghi nhận đã gọi nhắc khách</span>
            </button>
            <button
              type="button"
              @click="handleUnclaimedAction(u.orderId, 'Đề xuất điều phối thu hồi/tiêu hủy theo hạn bảo quản')"
              class="inline-flex h-8 items-center gap-1.5 rounded-lg border border-amberish-200 bg-amberish-50 px-3 font-medium text-amberish-800 hover:bg-amberish-100"
            >
              <span>Đề xuất thu hồi theo hạn bảo quản</span>
            </button>
          </div>
        </article>
      </div>
    </div>

    <!-- TAB 4: Immutable Commission Ledger -->
    <div v-show="activeTab === 'ledger'" role="tabpanel" aria-labelledby="tab-ag-ledger" class="space-y-4">
      <div class="grid grid-cols-1 gap-4 sm:grid-cols-3">
        <div class="rounded-2xl border hairline bg-surface p-4 shadow-card">
          <div class="text-xs text-ink-muted">Tổng hoa hồng ghi có</div>
          <div class="mt-1.5 font-mono text-2xl font-medium text-forest-800 tabular-nums">
            {{ formatVnd(totalCredit) }}
          </div>
        </div>
        <div class="rounded-2xl border hairline bg-surface p-4 shadow-card">
          <div class="text-xs text-ink-muted">Tổng điều chỉnh thu hồi (hoàn tiền)</div>
          <div class="mt-1.5 font-mono text-2xl font-medium text-danger-700 tabular-nums">
            {{ formatVnd(totalDebit) }}
          </div>
        </div>
        <div class="rounded-2xl border hairline bg-surface p-4 shadow-card">
          <div class="flex items-center gap-1.5 text-xs text-ink-muted">
            <Wallet :size="14" :stroke-width="1.5" />
            <span>Số dư ròng kỳ đối soát</span>
          </div>
          <div class="mt-1.5 font-mono text-2xl font-medium text-ink tabular-nums">
            {{ formatVnd(netCommissionBalance) }}
          </div>
        </div>
      </div>

      <div class="rounded-2xl border hairline bg-surface shadow-card overflow-hidden">
        <div class="flex items-center justify-between border-b hairline px-5 py-3.5 text-xs">
          <div class="flex items-center gap-2 font-medium text-ink">
            <ReceiptText :size="15" :stroke-width="1.5" class="text-forest-700" />
            <span>Sổ cái hoa hồng bất biến (chỉ thêm bút toán, không sửa/xóa)</span>
          </div>
          <span class="font-mono text-[11px] text-ink-muted">Append-only Ledger</span>
        </div>

        <div class="overflow-x-auto">
          <table class="w-full text-left text-xs">
            <thead class="border-b hairline bg-canvas text-ink-muted">
              <tr>
                <th class="px-4 py-2.5 font-medium">Thời gian</th>
                <th class="px-3 py-2.5 font-medium">Đơn hàng</th>
                <th class="px-3 py-2.5 font-medium">Loại bút toán</th>
                <th class="px-3 py-2.5 font-medium">Diễn giải</th>
                <th class="px-4 py-2.5 font-medium text-right">Số tiền</th>
              </tr>
            </thead>
            <tbody class="divide-y hairline">
              <tr v-for="entry in ledgerEntries" :key="entry.id">
                <td class="px-4 py-3 font-mono tabular-nums text-ink-muted">
                  {{ formatVnDateTime(entry.createdAt) }}
                </td>
                <td class="px-3 py-3 font-mono font-medium text-ink">{{ entry.orderCode }}</td>
                <td class="px-3 py-3">
                  <StatusBadge :status="entry.entryType" />
                </td>
                <td class="px-3 py-3 text-ink-secondary">{{ entry.note }}</td>
                <td
                  :class="[
                    'px-4 py-3 text-right font-mono font-medium tabular-nums',
                    entry.amount >= 0 ? 'text-forest-800' : 'text-danger-700',
                  ]"
                >
                  {{ entry.amount >= 0 ? '+' : '' }}{{ formatVnd(entry.amount) }}
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </div>
  </div>
</template>

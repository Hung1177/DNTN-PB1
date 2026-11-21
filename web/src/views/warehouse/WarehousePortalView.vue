<script setup lang="ts">
import { computed, ref } from 'vue';
import QrcodeVue from 'qrcode.vue';
import {
  AlertTriangle,
  Boxes,
  CheckCircle2,
  Layers,
  Lock,
  PackagePlus,
  Printer,
  ShieldAlert,
  Truck,
  Unlock,
} from 'lucide-vue-next';
import httpClient from '@/api/httpClient';
import { useDemoStore } from '@/stores/demoStore';
import StatusBadge from '@/components/StatusBadge.vue';
import AppModal from '@/components/AppModal.vue';
import type { PackageRecord } from '@/types/domain';
import { formatVnDate, storageTypeShort } from '@/utils/formatters';

const demoStore = useDemoStore();

const activeTab = ref<'wave' | 'lots' | 'dispatch' | 'returns'>('wave');
const statusMsg = ref('');
const errorMsg = ref('');

const inboundVariantId = ref(1);
const inboundLotCode = ref('LOT-PORK-20261121-C');
const inboundSupplier = ref('Nông trại CP Hà Nội');
const inboundMfgDate = ref('2026-11-21');
const inboundExpDate = ref('2026-11-28');
const inboundQty = ref(40);
const lotSearch = ref('');

const packModalOpen = ref(false);
const lastPackedResult = ref<{
  orderCode: string;
  pickupPointName: string;
  pickupQrToken: string;
  createdPackages: PackageRecord[];
  allocations: Array<{ productName: string; lotCode: string; expiryDate: string; qty: number }>;
} | null>(null);

const returnNotes = ref<Record<number, string>>({});

const ordersWaitingPack = computed(() =>
  demoStore.orders.filter(
    (o) =>
      (o.orderStatus === 'CONFIRMED' || o.orderStatus === 'PAID_WAITING_CUTOFF') &&
      o.fulfillmentStatus === 'WAITING_PACKING'
  )
);

const waveSummaryByPoint = computed(() => {
  const map = new Map<
    string,
    {
      pickupPointName: string;
      orderCount: number;
      items: Map<string, { name: string; pack: string; qty: number; storage: string }>;
    }
  >();
  for (const ord of ordersWaitingPack.value) {
    if (!map.has(ord.pickupPointName)) {
      map.set(ord.pickupPointName, {
        pickupPointName: ord.pickupPointName,
        orderCount: 0,
        items: new Map(),
      });
    }
    const group = map.get(ord.pickupPointName)!;
    group.orderCount += 1;
    for (const item of ord.items) {
      const key = `${item.variantId}`;
      const existing = group.items.get(key);
      if (existing) existing.qty += item.quantity;
      else {
        group.items.set(key, {
          name: item.productName,
          pack: item.packName,
          qty: item.quantity,
          storage: storageTypeShort(item.storageType),
        });
      }
    }
  }
  return Array.from(map.values()).map((g) => ({
    pickupPointName: g.pickupPointName,
    orderCount: g.orderCount,
    lines: Array.from(g.items.values()),
  }));
});

const filteredLots = computed(() => {
  const q = lotSearch.value.trim().toLowerCase();
  if (!q) return demoStore.lots;
  return demoStore.lots.filter((l) =>
    `${l.lotCode} ${l.productName} ${l.supplierName}`.toLowerCase().includes(q)
  );
});

const assignedTripsForDispatch = computed(() =>
  demoStore.trips.filter((t) => !t.warehouseHandoverSigned && t.status === 'ASSIGNED')
);

const pendingReturns = computed(() =>
  demoStore.returnRequests.filter(
    (r) =>
      r.status === 'DRIVER_PICKED_UP' ||
      r.status === 'APPROVED_FOR_PICKUP' ||
      r.status === 'WAREHOUSE_RECEIVED'
  )
);

const quarantinedLotCount = computed(
  () => demoStore.lots.filter((l) => l.status === 'QUARANTINED').length
);

async function handlePackOrder(orderId: number) {
  statusMsg.value = '';
  errorMsg.value = '';
  try {
    if (demoStore.isDemoMode) {
      const res = demoStore.packOrderWithFefo(orderId);
      lastPackedResult.value = {
        orderCode: res.order.orderCode,
        pickupPointName: res.order.pickupPointName,
        pickupQrToken: res.order.pickupQrToken,
        createdPackages: res.createdPackages,
        allocations: res.allocations,
      };
      packModalOpen.value = true;
      statusMsg.value = `Đã phân bổ FEFO và sinh ${res.createdPackages.length} kiện QR cho đơn ${res.order.orderCode}.`;
      return;
    }
    const res = await httpClient.post(`/warehouse/orders/${orderId}/pack`, ['CHILLED']);
    statusMsg.value = res.data.message;
  } catch (err: any) {
    errorMsg.value = err?.response?.data?.message || err?.message || 'Không thể đóng gói FEFO.';
  }
}

async function handleInboundLot() {
  statusMsg.value = '';
  errorMsg.value = '';
  try {
    if (demoStore.isDemoMode) {
      statusMsg.value = demoStore.inboundLot({
        variantId: inboundVariantId.value,
        lotCode: inboundLotCode.value,
        supplierName: inboundSupplier.value,
        manufacturedDate: inboundMfgDate.value,
        expiryDate: inboundExpDate.value,
        receivedQty: inboundQty.value,
      });
      inboundLotCode.value = `LOT-${Date.now().toString().slice(-6)}`;
      return;
    }
    const res = await httpClient.post('/warehouse/lots', {
      variantId: inboundVariantId.value,
      cycleId: 1,
      lotCode: inboundLotCode.value,
      supplierName: inboundSupplier.value,
      manufacturedDate: inboundMfgDate.value,
      expiryDate: inboundExpDate.value,
      receivedQty: inboundQty.value,
    });
    statusMsg.value = res.data.message;
  } catch (err: any) {
    errorMsg.value = err?.response?.data?.message || err?.message || 'Không thể nhập lô mới.';
  }
}

function handleToggleQuarantine(lotId: number) {
  statusMsg.value = '';
  errorMsg.value = '';
  try {
    statusMsg.value = demoStore.toggleLotQuarantine(lotId);
  } catch (err: any) {
    errorMsg.value = err?.message || 'Không thể cập nhật trạng thái lô.';
  }
}

function handleDispatchTrip(tripId: number) {
  statusMsg.value = '';
  errorMsg.value = '';
  try {
    statusMsg.value = demoStore.confirmWarehousePickup(
      tripId,
      'Thủ kho trung tâm đã kiểm đếm và bàn giao đủ kiện lên xe tải lạnh'
    );
  } catch (err: any) {
    errorMsg.value = err?.message || 'Không thể xác nhận xuất kho.';
  }
}

async function handleReceiveReturn(returnId: number) {
  statusMsg.value = '';
  errorMsg.value = '';
  const note =
    returnNotes.value[returnId] ||
    'Đã kiểm nhận thực tế và nhập kho cách ly hàng lỗi (QUARANTINE_DAMAGED_STOCK).';
  try {
    if (demoStore.isDemoMode) {
      statusMsg.value = demoStore.receiveReturnToQuarantine(returnId, note);
      return;
    }
    const res = await httpClient.post(`/exceptions/returns/${returnId}/warehouse-receive`, {
      driverUserId: 4,
      inspectionNote: note,
    });
    statusMsg.value = res.data.message;
  } catch (err: any) {
    errorMsg.value = err?.response?.data?.message || err?.message || 'Không thể kiểm nhận hàng hoàn.';
  }
}

function packagesCountForTrip(tripId: number): number {
  const trip = demoStore.trips.find((t) => t.id === tripId);
  if (!trip) return 0;
  const stopIds = new Set(trip.stops.map((s) => s.id));
  return demoStore.packages.filter((p) => p.tripStopId && stopIds.has(p.tripStopId)).length;
}
</script>

<template>
  <div class="space-y-6">
    <div>
      <h1 class="text-2xl font-medium tracking-tight text-ink">Kho trung tâm & Phân bổ FEFO</h1>
      <p class="mt-1 text-sm text-ink-secondary">
        Tổng hợp nhu cầu Wave Picking theo đại lý, xuất lô ưu tiên hết hạn sớm đạt chuẩn an toàn và cách ly hàng hoàn lỗi
      </p>
    </div>

    <div class="grid grid-cols-2 gap-4 lg:grid-cols-4">
      <div class="rounded-2xl border hairline bg-surface p-5 shadow-card">
        <div class="text-xs text-ink-muted">Đơn chờ đóng gói FEFO</div>
        <div class="mt-2 font-mono text-3xl font-medium text-ink tabular-nums">
          {{ ordersWaitingPack.length }}
        </div>
        <div class="mt-1 text-xs text-ink-secondary">Mục tiêu SLA trước 08:00 D+1</div>
      </div>
      <div class="rounded-2xl border hairline bg-surface p-5 shadow-card">
        <div class="text-xs text-ink-muted">Lô khả dụng FEFO</div>
        <div class="mt-2 font-mono text-3xl font-medium text-forest-800 tabular-nums">
          {{ demoStore.lots.filter((l) => l.status === 'AVAILABLE').length }}
        </div>
        <div class="mt-1 text-xs text-ink-secondary">Trên tổng {{ demoStore.lots.length }} lô trong kho</div>
      </div>
      <div class="rounded-2xl border hairline bg-surface p-5 shadow-card">
        <div class="text-xs text-ink-muted">Lô đang cách ly lỗi</div>
        <div class="mt-2 font-mono text-3xl font-medium text-danger-700 tabular-nums">
          {{ quarantinedLotCount }}
        </div>
        <div class="mt-1 text-xs text-ink-secondary">Tự động bỏ qua khi phân bổ</div>
      </div>
      <div class="rounded-2xl border hairline bg-surface p-5 shadow-card">
        <div class="text-xs text-ink-muted">Hàng hoàn chờ kiểm nhận</div>
        <div class="mt-2 font-mono text-3xl font-medium text-amberish-800 tabular-nums">
          {{ demoStore.returnRequests.filter((r) => r.status === 'DRIVER_PICKED_UP').length }}
        </div>
        <div class="mt-1 text-xs text-ink-secondary">Nhập thẳng khu cách ly hàng lỗi</div>
      </div>
    </div>

    <div class="o2o-tablist" role="tablist" aria-label="Nghiệp vụ kho trung tâm">
      <button
        id="tab-wh-wave"
        type="button"
        role="tab"
        :aria-selected="activeTab === 'wave'"
        @click="activeTab = 'wave'"
      >
        Wave Picking & Đóng gói FEFO ({{ ordersWaitingPack.length }})
      </button>
      <button
        id="tab-wh-lots"
        type="button"
        role="tab"
        :aria-selected="activeTab === 'lots'"
        @click="activeTab = 'lots'"
      >
        Quản lý Lô & Nhập kho ({{ demoStore.lots.length }})
      </button>
      <button
        id="tab-wh-dispatch"
        type="button"
        role="tab"
        :aria-selected="activeTab === 'dispatch'"
        @click="activeTab = 'dispatch'"
      >
        Xuất kho bàn giao tài xế ({{ assignedTripsForDispatch.length }})
      </button>
      <button
        id="tab-wh-returns"
        type="button"
        role="tab"
        :aria-selected="activeTab === 'returns'"
        @click="activeTab = 'returns'"
      >
        Kiểm nhận hàng hoàn cách ly ({{ pendingReturns.length }})
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

    <!-- TAB 1: Wave Picking & FEFO Packing -->
    <div v-show="activeTab === 'wave'" role="tabpanel" aria-labelledby="tab-wh-wave" class="grid grid-cols-1 gap-6 lg:grid-cols-12">
      <section class="rounded-2xl border hairline bg-surface p-5 shadow-card lg:col-span-5 space-y-4">
        <div class="flex items-center gap-2">
          <Layers :size="18" :stroke-width="1.5" class="text-forest-700" />
          <div>
            <h2 class="text-base font-medium text-ink">Bảng gom Wave Picking theo đại lý</h2>
            <p class="text-xs text-ink-muted">Nhân viên đi lấy hàng một lượt trước khi chia về từng đơn</p>
          </div>
        </div>

        <div v-if="waveSummaryByPoint.length === 0" class="rounded-xl bg-canvas p-6 text-center text-xs text-ink-secondary">
          Không còn đơn nào đang chờ lấy hàng trong đợt này.
        </div>

        <div v-else class="space-y-4">
          <div
            v-for="group in waveSummaryByPoint"
            :key="group.pickupPointName"
            class="rounded-xl border hairline bg-canvas/60 p-4 space-y-2.5"
          >
            <div class="flex items-center justify-between gap-2">
              <span class="text-sm font-medium text-ink">{{ group.pickupPointName }}</span>
              <span class="font-mono text-xs text-forest-800">{{ group.orderCount }} đơn</span>
            </div>
            <div class="divide-y hairline text-xs">
              <div
                v-for="line in group.lines"
                :key="line.name"
                class="flex items-center justify-between py-1.5"
              >
                <div>
                  <span class="text-ink">{{ line.name }}</span>
                  <span class="ml-1 text-ink-muted">({{ line.pack }} · {{ line.storage }})</span>
                </div>
                <span class="font-mono font-medium text-ink tabular-nums">{{ line.qty }} gói</span>
              </div>
            </div>
          </div>
        </div>
      </section>

      <section class="rounded-2xl border hairline bg-surface p-5 shadow-card lg:col-span-7 space-y-4">
        <div class="flex items-center gap-2">
          <Boxes :size="18" :stroke-width="1.5" class="text-forest-700" />
          <div>
            <h2 class="text-base font-medium text-ink">Hàng đợi đóng gói & In tem QR kiện hàng</h2>
            <p class="text-xs text-ink-muted">
              Tự động tách kiện theo điều kiện nhiệt độ (Mát / Đông / Thường) và phân bổ lô FEFO hợp lệ
            </p>
          </div>
        </div>

        <div v-if="ordersWaitingPack.length === 0" class="rounded-xl bg-canvas p-8 text-center text-xs text-ink-secondary">
          Tất cả đơn đã chốt đều đã hoàn tất đóng gói FEFO.
        </div>

        <div v-else class="space-y-3">
          <div
            v-for="ord in ordersWaitingPack"
            :key="ord.orderId"
            class="rounded-xl border hairline bg-canvas/50 p-4 space-y-3"
          >
            <div class="flex flex-wrap items-start justify-between gap-2">
              <div>
                <div class="flex items-center gap-2">
                  <span class="font-mono text-sm font-medium text-ink">{{ ord.orderCode }}</span>
                  <StatusBadge :status="ord.orderStatus" />
                </div>
                <div class="mt-1 text-xs text-ink-secondary">
                  Khách: {{ ord.customerName }} · {{ ord.pickupPointName }}
                </div>
              </div>
              <button
                type="button"
                @click="handlePackOrder(ord.orderId)"
                class="inline-flex h-9 items-center gap-1.5 rounded-lg bg-forest-700 px-3.5 text-xs font-medium text-white hover:bg-forest-800"
              >
                <Printer :size="14" :stroke-width="1.5" />
                <span>Phân bổ FEFO & In tem QR</span>
              </button>
            </div>

            <div class="rounded-lg border hairline bg-surface px-3 py-2 text-xs space-y-1">
              <div
                v-for="it in ord.items"
                :key="it.id"
                class="flex items-center justify-between text-ink-secondary"
              >
                <span>
                  {{ it.productName }} ({{ it.packName }}) · {{ storageTypeShort(it.storageType) }}
                </span>
                <span class="font-mono font-medium text-ink tabular-nums">× {{ it.quantity }}</span>
              </div>
            </div>
          </div>
        </div>
      </section>
    </div>

    <!-- TAB 2: Lot Management & Inbound -->
    <div v-show="activeTab === 'lots'" role="tabpanel" aria-labelledby="tab-wh-lots" class="grid grid-cols-1 gap-6 lg:grid-cols-12">
      <section class="rounded-2xl border hairline bg-surface p-5 shadow-card lg:col-span-4 space-y-4 h-fit">
        <div class="flex items-center gap-2">
          <PackagePlus :size="18" :stroke-width="1.5" class="text-forest-700" />
          <h2 class="text-base font-medium text-ink">Phiếu nhập lô hàng mới</h2>
        </div>
        <form @submit.prevent="handleInboundLot" class="space-y-3 text-xs">
          <div>
            <label for="wh-variant" class="mb-1 block font-medium text-ink-secondary">Sản phẩm quy cách cố định</label>
            <select
              id="wh-variant"
              v-model.number="inboundVariantId"
              class="h-9 w-full rounded-lg border hairline bg-canvas/50 px-2.5 text-xs text-ink"
            >
              <option v-for="p in demoStore.products" :key="p.variantId" :value="p.variantId">
                {{ p.productName }} ({{ p.packName }})
              </option>
            </select>
          </div>
          <div>
            <label for="wh-lotcode" class="mb-1 block font-medium text-ink-secondary">Mã lô (Lot Code)</label>
            <input
              id="wh-lotcode"
              v-model="inboundLotCode"
              type="text"
              required
              class="h-9 w-full rounded-lg border hairline bg-canvas/50 px-3 font-mono text-xs text-ink"
            />
          </div>
          <div>
            <label for="wh-supplier" class="mb-1 block font-medium text-ink-secondary">Nhà cung cấp nội bộ</label>
            <input
              id="wh-supplier"
              v-model="inboundSupplier"
              type="text"
              required
              class="h-9 w-full rounded-lg border hairline bg-canvas/50 px-3 text-xs text-ink"
            />
          </div>
          <div class="grid grid-cols-2 gap-2.5">
            <div>
              <label for="wh-mfg" class="mb-1 block font-medium text-ink-secondary">Ngày sản xuất</label>
              <input
                id="wh-mfg"
                v-model="inboundMfgDate"
                type="date"
                required
                class="h-9 w-full rounded-lg border hairline bg-canvas/50 px-2.5 text-xs text-ink"
              />
            </div>
            <div>
              <label for="wh-exp" class="mb-1 block font-medium text-ink-secondary">Hạn sử dụng</label>
              <input
                id="wh-exp"
                v-model="inboundExpDate"
                type="date"
                required
                class="h-9 w-full rounded-lg border hairline bg-canvas/50 px-2.5 text-xs text-ink"
              />
            </div>
          </div>
          <div>
            <label for="wh-qty" class="mb-1 block font-medium text-ink-secondary">Số lượng gói nhập</label>
            <input
              id="wh-qty"
              v-model.number="inboundQty"
              type="number"
              min="1"
              required
              class="h-9 w-full rounded-lg border hairline bg-canvas/50 px-3 font-mono text-xs text-ink"
            />
          </div>
          <button
            type="submit"
            class="h-10 w-full rounded-lg bg-forest-700 text-xs font-medium text-white hover:bg-forest-800"
          >
            Nhập lô & Cập nhật tồn khả dụng
          </button>
        </form>
      </section>

      <section class="rounded-2xl border hairline bg-surface p-5 shadow-card lg:col-span-8 space-y-4">
        <div class="flex flex-wrap items-center justify-between gap-3">
          <div>
            <h2 class="text-base font-medium text-ink">Danh sách lô hàng & Trạng thái FEFO</h2>
            <p class="text-xs text-ink-muted">Khóa cách ly ngay nếu phát hiện lô hỏng thực tế để FEFO tự chuyển lô kế tiếp</p>
          </div>
          <input
            v-model="lotSearch"
            type="search"
            placeholder="Lọc mã lô, sản phẩm..."
            class="h-9 w-56 rounded-lg border hairline bg-canvas/60 px-3 text-xs text-ink"
          />
        </div>

        <div class="overflow-x-auto rounded-xl border hairline">
          <table class="w-full text-left text-xs">
            <thead class="border-b hairline bg-canvas text-ink-muted">
              <tr>
                <th class="px-3.5 py-2.5 font-medium">Mã lô</th>
                <th class="px-3 py-2.5 font-medium">Sản phẩm</th>
                <th class="px-3 py-2.5 font-medium">Hạn dùng</th>
                <th class="px-3 py-2.5 font-medium text-right">Còn / Nhập</th>
                <th class="px-3 py-2.5 font-medium">Trạng thái</th>
                <th class="px-3.5 py-2.5 font-medium text-right">Thao tác</th>
              </tr>
            </thead>
            <tbody class="divide-y hairline bg-surface">
              <tr v-for="lot in filteredLots" :key="lot.id" class="hover:bg-canvas/40">
                <td class="px-3.5 py-3 font-mono font-medium text-ink">{{ lot.lotCode }}</td>
                <td class="px-3 py-3">
                  <div class="font-medium text-ink">{{ lot.productName }}</div>
                  <div class="text-[11px] text-ink-muted">{{ lot.packName }} · {{ lot.supplierName }}</div>
                </td>
                <td class="px-3 py-3 font-mono tabular-nums text-ink-secondary">
                  {{ formatVnDate(lot.expiryDate) }}
                </td>
                <td class="px-3 py-3 text-right font-mono tabular-nums text-ink">
                  {{ lot.remainingQty }} / {{ lot.initialQty }}
                </td>
                <td class="px-3 py-3">
                  <StatusBadge :status="lot.status" />
                </td>
                <td class="px-3.5 py-3 text-right">
                  <button
                    type="button"
                    @click="handleToggleQuarantine(lot.id)"
                    :class="[
                      'inline-flex h-7 items-center gap-1 rounded-md border px-2.5 text-[11px] font-medium',
                      lot.status === 'QUARANTINED'
                        ? 'border-forest-200 bg-forest-50 text-forest-800 hover:bg-forest-100'
                        : 'border-danger-100 bg-danger-50 text-danger-700 hover:bg-danger-100',
                    ]"
                  >
                    <Unlock v-if="lot.status === 'QUARANTINED'" :size="12" :stroke-width="1.5" />
                    <Lock v-else :size="12" :stroke-width="1.5" />
                    <span>{{ lot.status === 'QUARANTINED' ? 'Mở khóa' : 'Cách ly lô' }}</span>
                  </button>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>
    </div>

    <!-- TAB 3: Warehouse to Driver Handover -->
    <div v-show="activeTab === 'dispatch'" role="tabpanel" aria-labelledby="tab-wh-dispatch" class="space-y-4">
      <div
        v-if="assignedTripsForDispatch.length === 0"
        class="rounded-2xl border hairline bg-surface p-10 text-center text-sm text-ink-secondary"
      >
        Hiện không có chuyến xe nào đang chờ ký xuất kho. Bạn có thể tạo chuyến mới tại cổng Điều phối.
      </div>
      <div v-else class="grid grid-cols-1 gap-4 md:grid-cols-2">
        <div
          v-for="trip in assignedTripsForDispatch"
          :key="trip.id"
          class="rounded-2xl border hairline bg-surface p-5 shadow-card space-y-4"
        >
          <div class="flex items-start justify-between gap-2">
            <div>
              <div class="font-mono text-sm font-medium text-ink">{{ trip.tripCode }}</div>
              <div class="mt-0.5 text-xs text-ink-secondary">
                Tài xế: <strong class="font-medium text-ink">{{ trip.driverName }}</strong> · {{ trip.driverVehicle }}
              </div>
            </div>
            <StatusBadge :status="trip.status" />
          </div>
          <div class="rounded-xl border hairline bg-canvas p-3 text-xs space-y-1">
            <div class="font-medium text-ink">
              Số kiện đã gán: <span class="font-mono">{{ packagesCountForTrip(trip.id) }} kiện</span> · {{ trip.stops.length }} điểm dừng
            </div>
            <div v-for="st in trip.stops" :key="st.id" class="text-ink-secondary">
              {{ st.stopSequence }}. {{ st.pickupPointName }}
            </div>
          </div>
          <button
            type="button"
            @click="handleDispatchTrip(trip.id)"
            class="inline-flex h-10 w-full items-center justify-center gap-2 rounded-lg bg-forest-700 text-xs font-medium text-white hover:bg-forest-800"
          >
            <Truck :size="15" :stroke-width="1.5" />
            <span>Ký biên bản xuất kho & Bàn giao tài xế (08:00)</span>
          </button>
        </div>
      </div>
    </div>

    <!-- TAB 4: Return Quarantine Inspection -->
    <div v-show="activeTab === 'returns'" role="tabpanel" aria-labelledby="tab-wh-returns" class="space-y-4">
      <div class="flex items-start gap-2.5 rounded-xl border border-amberish-200 bg-amberish-50 p-4 text-xs text-amberish-800">
        <AlertTriangle :size="16" :stroke-width="1.5" class="mt-0.5 shrink-0" />
        <span>
          Quy định vệ sinh an toàn thực phẩm: Toàn bộ thực phẩm hoàn trả từ sự cố phải nhập thẳng vào
          <strong class="font-medium">Kho cách ly hàng lỗi (QUARANTINE_DAMAGED_STOCK)</strong>, tuyệt đối không cộng ngược vào tồn kho bán thương mại.
        </span>
      </div>

      <div v-if="pendingReturns.length === 0" class="rounded-2xl border hairline bg-surface p-10 text-center text-sm text-ink-secondary">
        Không có phiếu hàng hoàn nào đang chờ kiểm nhận.
      </div>

      <div v-else class="grid grid-cols-1 gap-4 lg:grid-cols-2">
        <article
          v-for="ret in pendingReturns"
          :key="ret.id"
          class="rounded-2xl border hairline bg-surface p-5 shadow-card space-y-3 text-xs"
        >
          <div class="flex items-start justify-between gap-2">
            <div>
              <div class="font-mono text-sm font-medium text-ink">
                Phiếu hoàn #{{ ret.id }} · {{ ret.orderCode }}
              </div>
              <div class="mt-0.5 text-ink-secondary">
                {{ ret.pickupPointName }} · Chuyến thu hồi: {{ ret.returnTripCode || 'Chưa gán' }}
              </div>
            </div>
            <StatusBadge :status="ret.status" />
          </div>

          <div v-if="ret.status === 'WAREHOUSE_RECEIVED'" class="rounded-lg bg-forest-50 border border-forest-200 p-3 text-forest-800 flex items-start gap-2">
            <CheckCircle2 :size="15" :stroke-width="1.5" class="mt-0.5 shrink-0" />
            <span>{{ ret.warehouseInspectionNote }}</span>
          </div>

          <template v-else>
            <textarea
              v-model="returnNotes[ret.id]"
              rows="2"
              placeholder="Ghi chú biên bản kiểm nhận hàng lỗi nhập khu cách ly..."
              class="w-full rounded-lg border hairline bg-canvas/50 p-2.5 text-xs text-ink"
            />
            <button
              type="button"
              @click="handleReceiveReturn(ret.id)"
              class="inline-flex h-9 w-full items-center justify-center gap-1.5 rounded-lg bg-amberish-700 text-xs font-medium text-white hover:bg-amberish-800"
            >
              <ShieldAlert :size="15" :stroke-width="1.5" />
              <span>Xác nhận nhập khu cách ly hàng lỗi (Mở khóa duyệt hoàn tiền)</span>
            </button>
          </template>
        </article>
      </div>
    </div>

    <AppModal
      :open="packModalOpen"
      title="Tem QR kiện hàng & Chi tiết phân bổ lô FEFO"
      :subtitle="lastPackedResult ? `Đơn ${lastPackedResult.orderCode} · ${lastPackedResult.pickupPointName}` : ''"
      @close="packModalOpen = false"
    >
      <div v-if="lastPackedResult" class="space-y-4 text-xs">
        <div>
          <div class="mb-2 font-medium text-ink">Lô hàng FEFO đã phân bổ (bỏ qua lô cách ly)</div>
          <div class="rounded-xl border hairline bg-canvas divide-y hairline">
            <div
              v-for="(al, i) in lastPackedResult.allocations"
              :key="i"
              class="flex items-center justify-between px-3.5 py-2"
            >
              <div>
                <div class="font-medium text-ink">{{ al.productName }}</div>
                <div class="font-mono text-[11px] text-forest-800">
                  {{ al.lotCode }} · HSD {{ formatVnDate(al.expiryDate) }}
                </div>
              </div>
              <span class="font-mono font-medium text-ink tabular-nums">× {{ al.qty }}</span>
            </div>
          </div>
        </div>

        <div>
          <div class="mb-2 font-medium text-ink">
            Tem QR dán từng kiện hàng ({{ lastPackedResult.createdPackages.length }} kiện)
          </div>
          <div class="grid grid-cols-1 gap-3 sm:grid-cols-2">
            <div
              v-for="pkg in lastPackedResult.createdPackages"
              :key="pkg.id"
              class="flex flex-col items-center rounded-xl border hairline bg-canvas p-3 text-center"
            >
              <div class="rounded-lg bg-white p-2 shadow-card">
                <QrcodeVue :value="pkg.qrPayload" :size="108" level="M" />
              </div>
              <div class="mt-2 font-mono font-medium text-ink">{{ pkg.packageCode }}</div>
              <div class="text-[11px] text-ink-muted">
                Kiện {{ pkg.packageSeq }}/{{ pkg.totalPackagesInOrder }} · {{ storageTypeShort(pkg.storageType) }}
              </div>
            </div>
          </div>
        </div>
      </div>
      <template #footer>
        <button
          type="button"
          autofocus
          @click="packModalOpen = false"
          class="h-9 rounded-lg bg-forest-700 px-4 text-xs font-medium text-white hover:bg-forest-800"
        >
          Hoàn tất đóng gói
        </button>
      </template>
    </AppModal>
  </div>
</template>

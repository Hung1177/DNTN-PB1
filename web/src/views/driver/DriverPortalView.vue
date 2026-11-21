<script setup lang="ts">
import { computed, ref } from 'vue';
import {
  CheckCircle2,
  MapPin,
  Navigation,
  Package,
  QrCode,
  RotateCcw,
  Truck,
} from 'lucide-vue-next';
import httpClient from '@/api/httpClient';
import { useDemoStore } from '@/stores/demoStore';
import { useAuthStore } from '@/stores/authStore';
import StatusBadge from '@/components/StatusBadge.vue';
import { formatVnDateTime, formatVnTime, storageTypeShort } from '@/utils/formatters';

const demoStore = useDemoStore();
const authStore = useAuthStore();

const activeTab = ref<'delivery' | 'returns' | 'history'>('delivery');
const drivers = computed(() => demoStore.users.filter((u) => u.roles.includes('ROLE_DRIVER')));

const selectedDriverId = ref<number>(
  authStore.hasRole('ROLE_DRIVER') && authStore.user ? authStore.user.userId : 4
);

const scannedPkgIds = ref<number[]>([]);
const warehouseNote = ref('Đã kiểm đếm đủ kiện lạnh và kiện thường tại kho trung tâm');
const statusMsg = ref('');
const errorMsg = ref('');

const activeDriver = computed(
  () => drivers.value.find((d) => d.userId === selectedDriverId.value) || drivers.value[0]
);

const activeTrips = computed(() =>
  demoStore.trips.filter(
    (t) => t.driverId === selectedDriverId.value && t.status !== 'COMPLETED'
  )
);

const completedTrips = computed(() =>
  demoStore.trips.filter(
    (t) => t.driverId === selectedDriverId.value && t.status === 'COMPLETED'
  )
);

const assignedReturns = computed(() =>
  demoStore.returnRequests.filter(
    (r) =>
      (r.assignedDriverId === selectedDriverId.value || !r.assignedDriverId) &&
      r.status !== 'CLOSED'
  )
);

function packagesForStop(stopId: number) {
  return demoStore.packages.filter((p) => p.tripStopId === stopId);
}

function packagesForTrip(tripId: number) {
  const trip = demoStore.trips.find((t) => t.id === tripId);
  if (!trip) return [];
  const stopIds = new Set(trip.stops.map((s) => s.id));
  return demoStore.packages.filter((p) => p.tripStopId && stopIds.has(p.tripStopId));
}

function toggleScanPackage(pkgId: number) {
  const idx = scannedPkgIds.value.indexOf(pkgId);
  if (idx >= 0) scannedPkgIds.value.splice(idx, 1);
  else scannedPkgIds.value.push(pkgId);
}

function scanAllTripPackages(tripId: number) {
  const pkgs = packagesForTrip(tripId);
  for (const p of pkgs) {
    if (!scannedPkgIds.value.includes(p.id)) {
      scannedPkgIds.value.push(p.id);
    }
  }
}

async function handleWarehousePickup(tripId: number) {
  statusMsg.value = '';
  errorMsg.value = '';
  try {
    if (demoStore.isDemoMode) {
      statusMsg.value = demoStore.confirmWarehousePickup(tripId, warehouseNote.value);
      return;
    }
    const res = await httpClient.post(`/fulfillment/trips/${tripId}/warehouse-handover`, {
      warehouseUserId: 2,
      driverId: selectedDriverId.value,
      note: warehouseNote.value,
    });
    statusMsg.value = res.data.message;
  } catch (err: any) {
    errorMsg.value = err?.response?.data?.message || err?.message || 'Không thể ký nhận tại kho.';
  }
}

function handleArrivedAtStop(tripId: number, stopId: number) {
  statusMsg.value = '';
  errorMsg.value = '';
  try {
    statusMsg.value = demoStore.markDriverArrivedAtStop(tripId, stopId);
  } catch (err: any) {
    errorMsg.value = err?.message || 'Không thể cập nhật trạng thái đến điểm.';
  }
}

function handlePickupReturn(returnId: number) {
  statusMsg.value = '';
  errorMsg.value = '';
  try {
    statusMsg.value = demoStore.driverPickupReturnFromAgent(returnId);
  } catch (err: any) {
    errorMsg.value = err?.message || 'Không thể xác nhận lấy hàng hoàn.';
  }
}
</script>

<template>
  <div class="space-y-6">
    <!-- Responsive Driver Header -->
    <div class="flex flex-wrap items-end justify-between gap-4">
      <div>
        <h1 class="text-2xl font-medium tracking-tight text-ink">Cổng Tài xế giao nhận</h1>
        <p class="mt-1 text-sm text-ink-secondary">
          Nhận kiện tại kho trung tâm (08:00 D+1), giao theo tuyến điểm nhận trước 16:00 và chở hàng hoàn về kho cách ly
        </p>
      </div>

      <div class="flex items-center gap-2 rounded-xl border hairline bg-surface px-3 py-2 text-xs">
        <Truck :size="16" :stroke-width="1.5" class="text-forest-700 shrink-0" />
        <label for="driver-select" class="text-ink-muted">Tài xế:</label>
        <select
          id="driver-select"
          v-model.number="selectedDriverId"
          class="bg-transparent font-medium text-ink focus:outline-none"
        >
          <option v-for="d in drivers" :key="d.userId" :value="d.userId">
            {{ d.fullName }}
          </option>
        </select>
      </div>
    </div>

    <!-- Active Driver Vehicle Card -->
    <section
      v-if="activeDriver"
      class="rounded-2xl border hairline bg-surface p-5 shadow-card flex flex-wrap items-center justify-between gap-4"
    >
      <div class="space-y-1">
        <div class="text-xs text-ink-muted">Phương tiện đang trực</div>
        <div class="text-base font-medium text-ink">{{ activeDriver.fullName }}</div>
        <div class="text-xs text-ink-secondary">
          {{ activeDriver.vehicleInfo }} · SĐT: {{ activeDriver.phone }}
        </div>
      </div>
      <div class="grid grid-cols-3 gap-4 text-center">
        <div class="rounded-xl border hairline bg-canvas px-4 py-2.5">
          <div class="font-mono text-xl font-medium text-ink tabular-nums">{{ activeTrips.length }}</div>
          <div class="text-[11px] text-ink-muted">Chuyến đang chạy</div>
        </div>
        <div class="rounded-xl border hairline bg-canvas px-4 py-2.5">
          <div class="font-mono text-xl font-medium text-amberish-800 tabular-nums">
            {{ assignedReturns.filter((r) => r.status === 'APPROVED_FOR_PICKUP').length }}
          </div>
          <div class="text-[11px] text-ink-muted">Chờ lấy hoàn</div>
        </div>
        <div class="rounded-xl border hairline bg-canvas px-4 py-2.5">
          <div class="font-mono text-xl font-medium text-forest-800 tabular-nums">
            {{ completedTrips.length }}
          </div>
          <div class="text-[11px] text-ink-muted">Đã hoàn tất</div>
        </div>
      </div>
    </section>

    <!-- Tabs -->
    <div class="o2o-tablist" role="tablist" aria-label="Công việc tài xế">
      <button
        id="tab-drv-delivery"
        type="button"
        role="tab"
        :aria-selected="activeTab === 'delivery'"
        @click="activeTab = 'delivery'"
      >
        Chuyến giao hàng ({{ activeTrips.length }})
      </button>
      <button
        id="tab-drv-returns"
        type="button"
        role="tab"
        :aria-selected="activeTab === 'returns'"
        @click="activeTab = 'returns'"
      >
        Chuyến thu hồi hàng lỗi ({{ assignedReturns.length }})
      </button>
      <button
        id="tab-drv-history"
        type="button"
        role="tab"
        :aria-selected="activeTab === 'history'"
        @click="activeTab = 'history'"
      >
        Lịch sử chuyến ({{ completedTrips.length }})
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

    <!-- TAB 1: Active Delivery Trips -->
    <div v-show="activeTab === 'delivery'" role="tabpanel" aria-labelledby="tab-drv-delivery" class="space-y-6">
      <div
        v-if="activeTrips.length === 0"
        class="rounded-2xl border hairline bg-surface p-10 text-center text-sm text-ink-secondary"
      >
        Hiện chưa có chuyến giao hàng nào được phân công cho tài xế này.
      </div>

      <article
        v-for="trip in activeTrips"
        :key="trip.id"
        class="rounded-2xl border hairline bg-surface p-5 shadow-card space-y-5"
      >
        <div class="flex flex-wrap items-start justify-between gap-3 border-b hairline pb-4">
          <div>
            <div class="flex items-center gap-2">
              <span class="font-mono text-base font-medium text-ink">{{ trip.tripCode }}</span>
              <span class="font-mono text-xs text-ink-muted">{{ trip.cycleCode }}</span>
            </div>
            <div class="mt-1 text-xs text-ink-secondary">
              {{ trip.stops.length }} điểm nhận hàng · Tổng {{ packagesForTrip(trip.id).length }} kiện cần giao
            </div>
          </div>
          <StatusBadge :status="trip.status" />
        </div>

        <!-- Step 1: Warehouse Handover (If Not Yet Departed) -->
        <div
          v-if="!trip.warehouseHandoverSigned"
          class="rounded-xl border border-amberish-200 bg-amberish-50/70 p-4 space-y-3 text-xs"
        >
          <div class="flex flex-wrap items-center justify-between gap-2">
            <div class="font-medium text-amberish-800">
              Bước 1 · Nhận kiện & Ký biên bản xuất kho (08:00 D+1)
            </div>
            <button
              type="button"
              @click="scanAllTripPackages(trip.id)"
              class="inline-flex h-8 items-center gap-1.5 rounded-lg border border-amberish-200 bg-surface px-3 text-xs font-medium text-ink hover:bg-raised"
            >
              <QrCode :size="14" :stroke-width="1.5" />
              <span>Quét QR nhanh tất cả kiện</span>
            </button>
          </div>

          <div v-if="packagesForTrip(trip.id).length > 0" class="grid grid-cols-1 gap-2 sm:grid-cols-2">
            <label
              v-for="pkg in packagesForTrip(trip.id)"
              :key="pkg.id"
              class="flex cursor-pointer items-center justify-between rounded-lg border hairline bg-surface px-3 py-2"
            >
              <span class="flex items-center gap-2">
                <input
                  type="checkbox"
                  class="brand-check h-4 w-4"
                  :checked="scannedPkgIds.includes(pkg.id)"
                  @change="toggleScanPackage(pkg.id)"
                />
                <span class="font-mono font-medium text-ink">{{ pkg.packageCode }}</span>
              </span>
              <span class="text-[11px] text-ink-muted">{{ storageTypeShort(pkg.storageType) }}</span>
            </label>
          </div>

          <input
            v-model="warehouseNote"
            type="text"
            class="h-9 w-full rounded-lg border hairline bg-surface px-3 text-xs text-ink"
          />

          <button
            type="button"
            @click="handleWarehousePickup(trip.id)"
            class="inline-flex h-11 w-full items-center justify-center gap-2 rounded-lg bg-forest-700 text-sm font-medium text-white hover:bg-forest-800"
          >
            <CheckCircle2 :size="16" :stroke-width="1.5" />
            <span>Ký xuất kho & Bắt đầu vận chuyển</span>
          </button>
        </div>

        <!-- Step 2: Route Stops Sequence -->
        <div class="space-y-3">
          <div class="text-xs font-medium text-ink-secondary">
            Lộ trình bàn giao các đại lý (hoàn thành trước 16:00 D+1)
          </div>

          <div
            v-for="stop in trip.stops"
            :key="stop.id"
            class="rounded-xl border hairline bg-canvas/60 p-4 space-y-3"
          >
            <div class="flex flex-wrap items-start justify-between gap-3">
              <div class="flex items-start gap-3">
                <span
                  :class="[
                    'inline-flex h-7 w-7 shrink-0 items-center justify-center rounded-full font-mono text-xs font-medium',
                    stop.status === 'HANDED_OVER'
                      ? 'bg-forest-700 text-white'
                      : stop.status === 'ARRIVED_AT_POINT'
                        ? 'bg-amberish-700 text-white'
                        : 'bg-raised text-ink',
                  ]"
                >
                  {{ stop.stopSequence }}
                </span>
                <div>
                  <div class="text-sm font-medium text-ink">{{ stop.pickupPointName }}</div>
                  <div class="mt-0.5 flex items-center gap-1 text-xs text-ink-secondary">
                    <MapPin :size="13" :stroke-width="1.5" class="text-ink-muted shrink-0" />
                    <span>{{ stop.pickupPointAddress }}</span>
                  </div>
                  <div class="mt-1 font-mono text-[11px] text-ink-muted">
                    Dự kiến: {{ formatVnTime(stop.etaAt) }}
                    <span v-if="stop.arrivedAt"> · Đã đến lúc {{ formatVnTime(stop.arrivedAt) }}</span>
                  </div>
                </div>
              </div>
              <StatusBadge :status="stop.status" />
            </div>

            <!-- Packages at this Stop -->
            <div v-if="packagesForStop(stop.id).length > 0" class="rounded-lg border hairline bg-surface p-3 text-xs space-y-1.5">
              <div class="font-medium text-ink-secondary flex items-center gap-1.5">
                <Package :size="13" :stroke-width="1.5" />
                <span>Kiện hàng tại điểm dừng ({{ packagesForStop(stop.id).length }} kiện)</span>
              </div>
              <div
                v-for="pkg in packagesForStop(stop.id)"
                :key="pkg.id"
                class="flex items-center justify-between border-t hairline pt-1.5"
              >
                <span class="font-mono text-ink">
                  {{ pkg.packageCode }}
                  <span class="text-ink-muted font-sans">({{ pkg.orderCode }} · {{ storageTypeShort(pkg.storageType) }})</span>
                </span>
                <StatusBadge :status="pkg.status" />
              </div>
            </div>

            <!-- Action Buttons -->
            <div class="flex flex-wrap items-center gap-2 pt-1">
              <button
                v-if="trip.warehouseHandoverSigned && stop.status === 'PENDING'"
                type="button"
                @click="handleArrivedAtStop(trip.id, stop.id)"
                class="inline-flex h-10 items-center gap-1.5 rounded-lg bg-forest-700 px-4 text-xs font-medium text-white hover:bg-forest-800"
              >
                <Navigation :size="14" :stroke-width="1.5" />
                <span>Xác nhận đã đến điểm dừng</span>
              </button>

              <div
                v-if="stop.status === 'ARRIVED_AT_POINT'"
                class="flex flex-wrap items-center gap-2 text-xs text-amberish-800"
              >
                <span>Đang chờ Đại lý đối chiếu từng kiện hàng và ký biên bản bàn giao.</span>
                <router-link
                  to="/agent"
                  class="font-medium text-forest-800 underline"
                >
                  Mở cổng Đại lý để ký nhận
                </router-link>
              </div>
            </div>
          </div>
        </div>
      </article>
    </div>

    <!-- TAB 2: Return Pickup Trips -->
    <div v-show="activeTab === 'returns'" role="tabpanel" aria-labelledby="tab-drv-returns" class="space-y-4">
      <div
        v-if="assignedReturns.length === 0"
        class="rounded-2xl border hairline bg-surface p-10 text-center text-sm text-ink-secondary"
      >
        Không có yêu cầu thu hồi hàng lỗi nào cần tài xế xử lý.
      </div>
      <div v-else class="grid grid-cols-1 gap-4 md:grid-cols-2">
        <article
          v-for="ret in assignedReturns"
          :key="ret.id"
          class="rounded-2xl border hairline bg-surface p-5 shadow-card space-y-3 text-xs"
        >
          <div class="flex items-start justify-between gap-2">
            <div>
              <div class="font-mono text-sm font-medium text-ink">
                {{ ret.returnTripCode || `Phiếu thu hồi #${ret.id}` }} · {{ ret.orderCode }}
              </div>
              <div class="mt-0.5 text-ink-secondary">{{ ret.pickupPointName }}</div>
            </div>
            <StatusBadge :status="ret.status" />
          </div>
          <div class="text-ink-muted">
            Khởi tạo: {{ formatVnDateTime(ret.createdAt) }}
          </div>
          <button
            v-if="ret.status === 'APPROVED_FOR_PICKUP'"
            type="button"
            @click="handlePickupReturn(ret.id)"
            class="inline-flex h-10 w-full items-center justify-center gap-1.5 rounded-lg bg-amberish-700 text-xs font-medium text-white hover:bg-amberish-800"
          >
            <RotateCcw :size="14" :stroke-width="1.5" />
            <span>Xác nhận đã lấy hàng lỗi từ Đại lý</span>
          </button>
          <div v-else-if="ret.status === 'DRIVER_PICKED_UP'" class="text-forest-800 font-medium">
            Đang vận chuyển về khu cách ly kho trung tâm ·
            <router-link to="/warehouse" class="underline">Mở cổng Kho để kiểm nhận</router-link>
          </div>
        </article>
      </div>
    </div>

    <!-- TAB 3: History -->
    <div v-show="activeTab === 'history'" role="tabpanel" aria-labelledby="tab-drv-history" class="space-y-3">
      <div
        v-if="completedTrips.length === 0"
        class="rounded-2xl border hairline bg-surface p-10 text-center text-sm text-ink-secondary"
      >
        Chưa có chuyến giao hàng hoàn tất trong hôm nay.
      </div>
      <div
        v-for="trip in completedTrips"
        :key="trip.id"
        class="rounded-2xl border hairline bg-surface p-5 shadow-card flex flex-wrap items-center justify-between gap-3 text-xs"
      >
        <div>
          <div class="font-mono text-sm font-medium text-ink">{{ trip.tripCode }}</div>
          <div class="text-ink-secondary">
            Hoàn tất lúc {{ formatVnDateTime(trip.completedAt) }} · {{ trip.stops.length }} điểm nhận
          </div>
        </div>
        <StatusBadge :status="trip.status" />
      </div>
    </div>
  </div>
</template>

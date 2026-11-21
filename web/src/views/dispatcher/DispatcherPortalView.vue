<script setup lang="ts">
import { computed, ref } from 'vue';
import {
  AlertTriangle,
  ArrowDown,
  ArrowUp,
  MapPin,
  Plus,
  RotateCcw,
  Truck,
} from 'lucide-vue-next';
import httpClient from '@/api/httpClient';
import { useDemoStore } from '@/stores/demoStore';
import StatusBadge from '@/components/StatusBadge.vue';
import { formatVnDateTime, formatVnTime } from '@/utils/formatters';

const demoStore = useDemoStore();

const activeTab = ref<'monitor' | 'plan' | 'returns'>('monitor');
const statusMsg = ref('');
const errorMsg = ref('');

const selectedCycleId = ref(demoStore.cycles[0]?.id || 1);
const selectedDriverId = ref(4);
const orderedStopIds = ref<number[]>([1, 2, 3]);
const returnDriverSelection = ref<Record<number, number>>({});

const drivers = computed(() =>
  demoStore.users.filter((u) => u.roles.includes('ROLE_DRIVER'))
);

const packedUnassignedCount = computed(
  () => demoStore.packages.filter((p) => p.status === 'PACKED' && !p.tripStopId).length
);

const issueHoldOrders = computed(() =>
  demoStore.orders.filter((o) => o.hasOpenIssue || o.fulfillmentStatus === 'ISSUE_HOLD')
);

const openReturnRequests = computed(() =>
  demoStore.returnRequests.filter((r) => r.status !== 'CLOSED')
);

function toggleStop(ppId: number) {
  const idx = orderedStopIds.value.indexOf(ppId);
  if (idx >= 0) orderedStopIds.value.splice(idx, 1);
  else orderedStopIds.value.push(ppId);
}

function moveStop(index: number, delta: -1 | 1) {
  const target = index + delta;
  if (target < 0 || target >= orderedStopIds.value.length) return;
  const copy = [...orderedStopIds.value];
  const [removed] = copy.splice(index, 1);
  copy.splice(target, 0, removed);
  orderedStopIds.value = copy;
}

function pointById(id: number) {
  return demoStore.pickupPoints.find((p) => p.id === id);
}

function packedCountAtPoint(ppId: number): number {
  return demoStore.packages.filter((p) => p.pickupPointId === ppId && p.status === 'PACKED' && !p.tripStopId).length;
}

async function handleCreateTrip() {
  statusMsg.value = '';
  errorMsg.value = '';
  try {
    if (demoStore.isDemoMode) {
      const trip = demoStore.createDeliveryTrip({
        cycleId: selectedCycleId.value,
        driverId: selectedDriverId.value,
        pickupPointIds: orderedStopIds.value,
      });
      statusMsg.value = `Đã lập chuyến ${trip.tripCode} giao ${trip.stops.length} đại lý cho tài xế ${trip.driverName}.`;
      activeTab.value = 'monitor';
      return;
    }
    const res = await httpClient.post('/fulfillment/trips', {
      cycleId: selectedCycleId.value,
      driverId: selectedDriverId.value,
      pickupPointIds: orderedStopIds.value,
    });
    statusMsg.value = res.data.message;
  } catch (err: any) {
    errorMsg.value = err?.response?.data?.message || err?.message || 'Không thể lập chuyến giao.';
  }
}

function handleAssignReturnDriver(returnId: number) {
  statusMsg.value = '';
  errorMsg.value = '';
  const driverId = returnDriverSelection.value[returnId] || drivers.value[0]?.userId || 4;
  try {
    statusMsg.value = demoStore.dispatchReturnTrip(returnId, driverId);
  } catch (err: any) {
    errorMsg.value = err?.message || 'Không thể điều phối chuyến thu hồi.';
  }
}
</script>

<template>
  <div class="space-y-6">
    <div>
      <h1 class="text-2xl font-medium tracking-tight text-ink">Điều phối vận chuyển & Giám sát SLA</h1>
      <p class="mt-1 text-sm text-ink-secondary">
        Phân công chuyến xe tải lạnh theo thứ tự điểm nhận, giám sát mốc xuất kho 08:00 và bàn giao đại lý trước 16:00
      </p>
    </div>

    <div class="grid grid-cols-2 gap-4 lg:grid-cols-4">
      <div class="rounded-2xl border hairline bg-surface p-5 shadow-card">
        <div class="text-xs text-ink-muted">Kiện đã đóng gói chờ ghép chuyến</div>
        <div class="mt-2 font-mono text-3xl font-medium text-ink tabular-nums">
          {{ packedUnassignedCount }}
        </div>
        <div class="mt-1 text-xs text-ink-secondary">Tự động gán khi lập chuyến</div>
      </div>
      <div class="rounded-2xl border hairline bg-surface p-5 shadow-card">
        <div class="text-xs text-ink-muted">Chuyến đang giao hàng</div>
        <div class="mt-2 font-mono text-3xl font-medium text-forest-800 tabular-nums">
          {{ demoStore.trips.filter((t) => t.status === 'IN_TRANSIT').length }}
        </div>
        <div class="mt-1 text-xs text-ink-secondary">Trên tổng {{ demoStore.trips.length }} chuyến</div>
      </div>
      <div class="rounded-2xl border hairline bg-surface p-5 shadow-card">
        <div class="text-xs text-ink-muted">Đơn tạm giữ do sự cố</div>
        <div class="mt-2 font-mono text-3xl font-medium text-danger-700 tabular-nums">
          {{ issueHoldOrders.length }}
        </div>
        <div class="mt-1 text-xs text-ink-secondary">Cần theo dõi & thu hồi</div>
      </div>
      <div class="rounded-2xl border hairline bg-surface p-5 shadow-card">
        <div class="text-xs text-ink-muted">Phiếu thu hồi hàng lỗi</div>
        <div class="mt-2 font-mono text-3xl font-medium text-amberish-800 tabular-nums">
          {{ openReturnRequests.length }}
        </div>
        <div class="mt-1 text-xs text-ink-secondary">Thu hồi từ đại lý về kho cách ly</div>
      </div>
    </div>

    <div class="o2o-tablist" role="tablist" aria-label="Nghiệp vụ điều phối">
      <button
        id="tab-disp-monitor"
        type="button"
        role="tab"
        :aria-selected="activeTab === 'monitor'"
        @click="activeTab = 'monitor'"
      >
        Giám sát chuyến giao & SLA ({{ demoStore.trips.length }})
      </button>
      <button
        id="tab-disp-plan"
        type="button"
        role="tab"
        :aria-selected="activeTab === 'plan'"
        @click="activeTab = 'plan'"
      >
        Lập chuyến giao & Tuyến dừng
      </button>
      <button
        id="tab-disp-returns"
        type="button"
        role="tab"
        :aria-selected="activeTab === 'returns'"
        @click="activeTab = 'returns'"
      >
        Điều phối chuyến thu hồi ({{ openReturnRequests.length }})
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

    <!-- TAB 1: Live Monitor -->
    <div v-show="activeTab === 'monitor'" role="tabpanel" aria-labelledby="tab-disp-monitor" class="grid grid-cols-1 gap-6 lg:grid-cols-12">
      <div class="lg:col-span-8 space-y-4">
        <article
          v-for="trip in demoStore.trips"
          :key="trip.id"
          class="rounded-2xl border hairline bg-surface p-5 shadow-card space-y-4"
        >
          <div class="flex flex-wrap items-start justify-between gap-2">
            <div>
              <div class="flex items-center gap-2">
                <span class="font-mono text-sm font-medium text-ink">{{ trip.tripCode }}</span>
                <span class="font-mono text-xs text-ink-muted">{{ trip.cycleCode }}</span>
              </div>
              <div class="mt-1 text-xs text-ink-secondary">
                Tài xế: <strong class="font-medium text-ink">{{ trip.driverName }}</strong> · {{ trip.driverVehicle }}
              </div>
            </div>
            <StatusBadge :status="trip.status" />
          </div>

          <div class="space-y-2 border-t hairline pt-3">
            <div
              v-for="stop in trip.stops"
              :key="stop.id"
              class="flex flex-wrap items-center justify-between gap-2 rounded-xl border hairline bg-canvas/60 px-3.5 py-2.5 text-xs"
            >
              <div class="flex items-center gap-2.5">
                <span class="inline-flex h-6 w-6 items-center justify-center rounded-full bg-raised font-mono text-[11px] font-medium text-ink">
                  {{ stop.stopSequence }}
                </span>
                <div>
                  <div class="font-medium text-ink">{{ stop.pickupPointName }}</div>
                  <div class="text-[11px] text-ink-muted">
                    Dự kiến: {{ formatVnTime(stop.etaAt) }}
                    <span v-if="stop.arrivedAt"> · Đến lúc {{ formatVnTime(stop.arrivedAt) }}</span>
                    <span v-if="stop.handoverCompletedAt"> · Xong lúc {{ formatVnTime(stop.handoverCompletedAt) }}</span>
                  </div>
                </div>
              </div>
              <StatusBadge :status="stop.status" />
            </div>
          </div>
        </article>
      </div>

      <aside class="lg:col-span-4 space-y-4">
        <div class="rounded-2xl border hairline bg-surface p-5 shadow-card space-y-3">
          <div class="flex items-center gap-2 text-sm font-medium text-ink">
            <AlertTriangle :size="16" :stroke-width="1.5" class="text-amberish-700" />
            <span>Cảnh báo vận hành & Sự cố</span>
          </div>
          <div v-if="issueHoldOrders.length === 0" class="text-xs text-ink-muted py-4">
            Không có đơn hàng nào đang bị treo sự cố.
          </div>
          <div v-else class="space-y-2.5">
            <div
              v-for="ord in issueHoldOrders"
              :key="ord.orderId"
              class="rounded-xl border border-danger-100 bg-danger-50 p-3 text-xs space-y-1"
            >
              <div class="flex items-center justify-between">
                <span class="font-mono font-medium text-danger-700">{{ ord.orderCode }}</span>
                <StatusBadge :status="ord.fulfillmentStatus" />
              </div>
              <div class="text-ink-secondary">{{ ord.pickupPointName }}</div>
              <div class="text-[11px] text-ink-muted">
                Cờ <span class="font-mono">has_open_issue</span> đang khóa tự hoàn tất 00:00 D+2
              </div>
            </div>
          </div>
        </div>
      </aside>
    </div>

    <!-- TAB 2: Trip Planner -->
    <div v-show="activeTab === 'plan'" role="tabpanel" aria-labelledby="tab-disp-plan" class="grid grid-cols-1 gap-6 lg:grid-cols-12">
      <section class="rounded-2xl border hairline bg-surface p-6 shadow-card lg:col-span-7 space-y-5 text-xs">
        <div class="flex items-center gap-2">
          <Truck :size="18" :stroke-width="1.5" class="text-forest-700" />
          <h2 class="text-base font-medium text-ink">Tạo chuyến giao hàng mới</h2>
        </div>

        <div class="grid grid-cols-1 gap-4 sm:grid-cols-2">
          <div>
            <label for="disp-cycle" class="mb-1.5 block font-medium text-ink-secondary">Đợt giao hàng</label>
            <select
              id="disp-cycle"
              v-model.number="selectedCycleId"
              class="h-10 w-full rounded-lg border hairline bg-canvas/50 px-3 text-xs text-ink"
            >
              <option v-for="c in demoStore.cycles" :key="c.id" :value="c.id">
                {{ c.cycleCode }} (Giao {{ c.deliveryDate }})
              </option>
            </select>
          </div>
          <div>
            <label for="disp-driver" class="mb-1.5 block font-medium text-ink-secondary">Tài xế xe tải lạnh</label>
            <select
              id="disp-driver"
              v-model.number="selectedDriverId"
              class="h-10 w-full rounded-lg border hairline bg-canvas/50 px-3 text-xs text-ink"
            >
              <option v-for="d in drivers" :key="d.userId" :value="d.userId">
                {{ d.fullName }} — {{ d.vehicleInfo }}
              </option>
            </select>
          </div>
        </div>

        <div class="space-y-2">
          <div class="font-medium text-ink-secondary">Chọn các điểm nhận đưa vào tuyến</div>
          <div class="grid grid-cols-1 gap-2 sm:grid-cols-3">
            <label
              v-for="pp in demoStore.pickupPoints"
              :key="pp.id"
              class="flex cursor-pointer items-start gap-2 rounded-xl border hairline bg-canvas/50 p-3"
            >
              <input
                type="checkbox"
                class="brand-check mt-0.5 h-4 w-4"
                :checked="orderedStopIds.includes(pp.id)"
                @change="toggleStop(pp.id)"
              />
              <span>
                <span class="block font-medium text-ink">{{ pp.shortName }}</span>
                <span class="text-[11px] text-ink-muted">
                  {{ packedCountAtPoint(pp.id) }} kiện sẵn sàng gán
                </span>
              </span>
            </label>
          </div>
        </div>

        <div class="space-y-2">
          <div class="font-medium text-ink-secondary">Thứ tự giao các điểm dừng (Stop Sequence)</div>
          <div class="space-y-2">
            <div
              v-for="(ppId, idx) in orderedStopIds"
              :key="ppId"
              class="flex items-center justify-between rounded-xl border hairline bg-canvas px-3.5 py-2.5"
            >
              <div class="flex items-center gap-2.5">
                <span class="inline-flex h-6 w-6 items-center justify-center rounded-full bg-forest-700 font-mono text-[11px] font-medium text-white">
                  {{ idx + 1 }}
                </span>
                <div>
                  <div class="font-medium text-ink">{{ pointById(ppId)?.name }}</div>
                  <div class="text-[11px] text-ink-muted">{{ pointById(ppId)?.address }}</div>
                </div>
              </div>
              <div class="flex items-center gap-1">
                <button
                  type="button"
                  aria-label="Di chuyển lên"
                  :disabled="idx === 0"
                  @click="moveStop(idx, -1)"
                  class="inline-flex h-7 w-7 items-center justify-center rounded-md border hairline bg-surface disabled:opacity-40"
                >
                  <ArrowUp :size="13" :stroke-width="1.5" />
                </button>
                <button
                  type="button"
                  aria-label="Di chuyển xuống"
                  :disabled="idx === orderedStopIds.length - 1"
                  @click="moveStop(idx, 1)"
                  class="inline-flex h-7 w-7 items-center justify-center rounded-md border hairline bg-surface disabled:opacity-40"
                >
                  <ArrowDown :size="13" :stroke-width="1.5" />
                </button>
              </div>
            </div>
          </div>
        </div>

        <button
          type="button"
          @click="handleCreateTrip"
          class="inline-flex h-10 w-full items-center justify-center gap-1.5 rounded-lg bg-forest-700 px-4 text-xs font-medium text-white hover:bg-forest-800"
        >
          <Plus :size="15" :stroke-width="1.5" />
          <span>Phân công chuyến xe & Tự động gán kiện hàng đã đóng gói</span>
        </button>
      </section>

      <aside class="rounded-2xl border hairline bg-surface p-6 shadow-card lg:col-span-5 space-y-3 text-xs h-fit">
        <div class="flex items-center gap-2 text-sm font-medium text-ink">
          <MapPin :size="16" :stroke-width="1.5" class="text-forest-700" />
          <span>Nguyên tắc điều phối chuẩn hóa</span>
        </div>
        <ul class="space-y-2 text-ink-secondary leading-relaxed">
          <li>• Kho trung tâm hoàn tất đóng gói FEFO trước <strong class="font-medium text-ink">08:00 D+1</strong>.</li>
          <li>• Tài xế ký nhận điện tử với thủ kho (<span class="font-mono">WAREHOUSE_TO_DRIVER</span>) trước khi rời kho.</li>
          <li>• Bàn giao từng đại lý hoàn thành trước <strong class="font-medium text-ink">16:00 D+1</strong> để hệ thống mở trả hàng cho khách.</li>
          <li>• Mọi đơn hàng bị thiếu hoặc hỏng dù chỉ 1 kiện trong đơn nhiều kiện đều bị giữ lại ở <span class="font-mono">ISSUE_HOLD</span>.</li>
        </ul>
      </aside>
    </div>

    <!-- TAB 3: Return Pickup Dispatch -->
    <div v-show="activeTab === 'returns'" role="tabpanel" aria-labelledby="tab-disp-returns" class="space-y-4">
      <div
        v-if="openReturnRequests.length === 0"
        class="rounded-2xl border hairline bg-surface p-10 text-center text-sm text-ink-secondary"
      >
        Không có yêu cầu thu hồi hàng lỗi nào đang mở.
      </div>
      <div v-else class="overflow-x-auto rounded-2xl border hairline bg-surface shadow-card">
        <table class="w-full text-left text-xs">
          <thead class="border-b hairline bg-canvas text-ink-muted">
            <tr>
              <th class="px-4 py-3 font-medium">Phiếu / Đơn hàng</th>
              <th class="px-3 py-3 font-medium">Điểm nhận hàng</th>
              <th class="px-3 py-3 font-medium">Thời gian tạo</th>
              <th class="px-3 py-3 font-medium">Trạng thái thu hồi</th>
              <th class="px-4 py-3 font-medium text-right">Phân công tài xế thu hồi</th>
            </tr>
          </thead>
          <tbody class="divide-y hairline">
            <tr v-for="ret in openReturnRequests" :key="ret.id">
              <td class="px-4 py-3">
                <div class="font-mono font-medium text-ink">#{{ ret.id }} · {{ ret.orderCode }}</div>
                <div v-if="ret.returnTripCode" class="font-mono text-[11px] text-ink-muted">
                  {{ ret.returnTripCode }}
                </div>
              </td>
              <td class="px-3 py-3 text-ink-secondary">{{ ret.pickupPointName }}</td>
              <td class="px-3 py-3 font-mono tabular-nums text-ink-muted">
                {{ formatVnDateTime(ret.createdAt) }}
              </td>
              <td class="px-3 py-3">
                <StatusBadge :status="ret.status" />
              </td>
              <td class="px-4 py-3 text-right">
                <div v-if="ret.status === 'REQUESTED'" class="inline-flex items-center gap-2">
                  <select
                    v-model.number="returnDriverSelection[ret.id]"
                    class="h-8 rounded-lg border hairline bg-canvas px-2 text-xs text-ink"
                  >
                    <option v-for="d in drivers" :key="d.userId" :value="d.userId">
                      {{ d.fullName }}
                    </option>
                  </select>
                  <button
                    type="button"
                    @click="handleAssignReturnDriver(ret.id)"
                    class="inline-flex h-8 items-center gap-1 rounded-lg bg-forest-700 px-3 text-xs font-medium text-white hover:bg-forest-800"
                  >
                    <RotateCcw :size="13" :stroke-width="1.5" />
                    <span>Điều phối</span>
                  </button>
                </div>
                <span v-else class="text-ink-secondary">
                  {{ ret.assignedDriverName || 'Đã gán' }}
                </span>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>
  </div>
</template>

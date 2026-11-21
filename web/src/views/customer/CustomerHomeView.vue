<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import L from 'leaflet';
import { useRouter } from 'vue-router';
import {
  AlertTriangle,
  ArrowRight,
  Clock,
  CreditCard,
  MapPin,
  Minus,
  Package,
  Plus,
  Search,
  ShoppingBasket,
  Snowflake,
  Thermometer,
  Trash2,
} from 'lucide-vue-next';
import { useCycleStore, PickupPointWithCycle } from '@/stores/cycleStore';
import { useCartStore, CheckoutResponseDto } from '@/stores/cartStore';
import { useAuthStore } from '@/stores/authStore';
import { useDemoStore } from '@/stores/demoStore';
import AppModal from '@/components/AppModal.vue';
import type { StorageType } from '@/types/domain';
import { formatVnDate, formatVnDateTime, formatVnd, storageTypeShort } from '@/utils/formatters';

const cycleStore = useCycleStore();
const cartStore = useCartStore();
const authStore = useAuthStore();
const demoStore = useDemoStore();
const router = useRouter();

const mapContainer = ref<HTMLElement | null>(null);
let leafletMap: L.Map | null = null;
const markers = new Map<number, L.CircleMarker>();

const searchQuery = ref('');
const selectedCategory = ref('Tất cả');
const selectedStorage = ref<'ALL' | StorageType>('ALL');

const checkingOut = ref(false);
const checkoutError = ref('');
const checkoutModalOpen = ref(false);
const lastCheckout = ref<CheckoutResponseDto | null>(null);

const categories = computed(() => {
  const set = new Set(cycleStore.catalogItems.map((i) => i.category));
  return ['Tất cả', ...Array.from(set)];
});

const filteredItems = computed(() => {
  const q = searchQuery.value.trim().toLowerCase();
  return cycleStore.catalogItems.filter((item) => {
    if (selectedCategory.value !== 'Tất cả' && item.category !== selectedCategory.value) {
      return false;
    }
    if (selectedStorage.value !== 'ALL' && item.storageType !== selectedStorage.value) {
      return false;
    }
    if (q) {
      const hay = `${item.productName} ${item.packName} ${item.category} ${item.sku}`.toLowerCase();
      if (!hay.includes(q)) return false;
    }
    return true;
  });
});

function cartQtyFor(variantId: number): number {
  return cartStore.lines.find((l) => l.variantId === variantId)?.quantity || 0;
}

onMounted(async () => {
  await cycleStore.fetchPickupPoints();
  initLeafletMap();
  syncCartBinding();
});

onBeforeUnmount(() => {
  if (leafletMap) {
    leafletMap.remove();
    leafletMap = null;
  }
});

watch(
  () => cycleStore.selectedPoint,
  () => {
    syncCartBinding();
    highlightSelectedMarker();
  }
);

function syncCartBinding() {
  const pt = cycleStore.selectedPoint;
  if (pt && pt.activeCycle) {
    cartStore.bindPointAndCycle(pt.id, pt.activeCycle.cycleId);
  }
}

function initLeafletMap() {
  if (!mapContainer.value) return;
  leafletMap = L.map(mapContainer.value, { scrollWheelZoom: false }).setView([21.0368, 105.7824], 12);
  L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
    attribution: '&copy; OpenStreetMap',
  }).addTo(leafletMap);

  for (const pt of cycleStore.pickupPoints) {
    const isSelected = cycleStore.selectedPoint?.id === pt.id;
    const marker = L.circleMarker([pt.latitude, pt.longitude], {
      radius: isSelected ? 11 : 8,
      color: '#14532D',
      weight: 2,
      fillColor: isSelected ? '#166534' : '#B45309',
      fillOpacity: 0.9,
    }).addTo(leafletMap);

    marker.bindPopup(
      `<div style="font-size:12px;line-height:1.4"><strong>${pt.name}</strong><br/>${pt.address}<br/><em>Đợt: ${
        pt.activeCycle?.cycleCode || 'Đang cập nhật'
      }</em></div>`
    );
    marker.on('click', () => handleSelectPoint(pt));
    markers.set(pt.id, marker);
  }
}

function highlightSelectedMarker() {
  for (const [id, marker] of markers.entries()) {
    const active = cycleStore.selectedPoint?.id === id;
    marker.setStyle({
      radius: active ? 11 : 8,
      fillColor: active ? '#166534' : '#B45309',
    });
  }
}

async function handleSelectPoint(pt: PickupPointWithCycle) {
  await cycleStore.selectPickupPoint(pt);
  if (leafletMap) {
    leafletMap.setView([pt.latitude, pt.longitude], 13);
  }
}

async function handleCheckout() {
  checkoutError.value = '';
  if (!authStore.isAuthenticated) {
    router.push('/login?redirect=/');
    return;
  }
  const pt = cycleStore.selectedPoint;
  if (!pt || !pt.activeCycle) {
    checkoutError.value = 'Vui lòng chọn điểm nhận hàng có đợt giao đang mở.';
    return;
  }
  if (cartStore.lines.length === 0) return;

  checkingOut.value = true;
  try {
    const result = await cartStore.checkoutWithVnpay(pt.id, pt.activeCycle.cycleId);
    lastCheckout.value = result;
    checkoutModalOpen.value = true;
  } catch (err: any) {
    checkoutError.value =
      err?.response?.data?.message ||
      err?.message ||
      'Không thể giữ tồn kho hoặc sản phẩm vừa hết hàng trong đợt.';
  } finally {
    checkingOut.value = false;
  }
}

function proceedToVnpay() {
  if (!lastCheckout.value) return;
  checkoutModalOpen.value = false;
  if (demoStore.isDemoMode) {
    router.push(lastCheckout.value.vnpayPaymentUrl);
  } else {
    window.open(lastCheckout.value.vnpayPaymentUrl, '_blank');
  }
}
</script>

<template>
  <div class="space-y-8">
    <!-- Active Pickup Point & Cycle Hero Bar -->
    <section
      v-if="cycleStore.selectedPoint"
      class="rounded-2xl border hairline bg-surface p-6 shadow-card"
    >
      <div class="grid grid-cols-1 gap-6 lg:grid-cols-12 lg:items-center">
        <div class="lg:col-span-7 space-y-2">
          <div class="inline-flex items-center gap-2 text-xs font-medium text-forest-800">
            <MapPin :size="15" :stroke-width="1.5" />
            <span>Điểm nhận hàng đang chọn · {{ cycleStore.selectedPoint.code }}</span>
          </div>
          <h1 class="text-2xl font-medium tracking-tight text-ink sm:text-[28px] sm:leading-[1.18]">
            {{ cycleStore.selectedPoint.name }}
          </h1>
          <p class="text-sm text-ink-secondary">
            {{ cycleStore.selectedPoint.address }} · Phụ trách: {{ cycleStore.selectedPoint.agentName }}
          </p>
        </div>

        <div
          v-if="cycleStore.selectedPoint.activeCycle"
          class="lg:col-span-5 rounded-xl border hairline bg-canvas p-4 space-y-2.5"
        >
          <div class="flex items-center justify-between gap-2">
            <span class="font-mono text-xs font-medium text-forest-800">
              {{ cycleStore.selectedPoint.activeCycle.cycleCode }}
            </span>
            <span class="text-xs text-ink-secondary">
              Nhận ngày
              <strong class="font-medium text-ink">
                {{ formatVnDate(cycleStore.selectedPoint.activeCycle.deliveryDate) }}
              </strong>
            </span>
          </div>

          <div class="grid grid-cols-2 gap-3 text-xs">
            <div class="rounded-lg bg-surface border hairline p-2.5">
              <div class="text-ink-muted">Hạn chốt & VNPAY</div>
              <div class="mt-0.5 font-mono font-medium text-amberish-800 tabular-nums">
                {{ formatVnDateTime(cycleStore.selectedPoint.activeCycle.cutoffAt) }}
              </div>
            </div>
            <div class="rounded-lg bg-surface border hairline p-2.5">
              <div class="text-ink-muted">Mở trả hàng tại đại lý</div>
              <div class="mt-0.5 font-mono font-medium text-forest-800 tabular-nums">
                Từ {{ formatVnDateTime(cycleStore.selectedPoint.activeCycle.pickupStartAt) }}
              </div>
            </div>
          </div>

          <div
            v-if="cycleStore.selectedPoint.activeCycle.rolledOverToNextCycle"
            class="flex items-start gap-2 rounded-lg border border-amberish-200 bg-amberish-50 px-3 py-2 text-xs text-amberish-800"
          >
            <AlertTriangle :size="15" :stroke-width="1.5" class="mt-0.5 shrink-0" />
            <span>
              Đợt giao ngày mai đã đạt công suất tối đa — điểm này đang mở nhận đặt trước cho đợt kế tiếp (D+2).
            </span>
          </div>
        </div>
      </div>
    </section>

    <!-- Hanoi Network Map & Pickup Point Cards -->
    <section class="grid grid-cols-1 gap-6 lg:grid-cols-12">
      <div class="rounded-2xl border hairline bg-surface p-5 shadow-card lg:col-span-7 flex flex-col">
        <div class="mb-3 flex flex-wrap items-center justify-between gap-2">
          <div>
            <h2 class="text-base font-medium text-ink">Mạng lưới điểm nhận hàng Hà Nội</h2>
            <p class="text-xs text-ink-muted">
              Chọn đại lý gần nhà để xem đúng đợt giao và công suất kho lạnh
            </p>
          </div>
          <span class="text-xs text-ink-muted">{{ cycleStore.pickupPoints.length }} điểm đang hoạt động</span>
        </div>
        <div ref="mapContainer" class="h-72 w-full flex-1 min-h-[280px] border hairline rounded-xl" />
      </div>

      <div class="rounded-2xl border hairline bg-surface p-5 shadow-card lg:col-span-5 flex flex-col">
        <div class="mb-3 flex items-center justify-between">
          <h2 class="text-base font-medium text-ink">Danh sách đại lý nhận hàng</h2>
          <span class="text-xs text-ink-muted">Không phụ phí nhận</span>
        </div>
        <div class="space-y-3 flex-1">
          <button
            v-for="pt in cycleStore.pickupPoints"
            :key="pt.id"
            type="button"
            @click="handleSelectPoint(pt)"
            :class="[
              'w-full rounded-xl border p-4 text-left',
              cycleStore.selectedPoint?.id === pt.id
                ? 'border-forest-700 bg-forest-50/50'
                : 'hairline bg-canvas/50 hover:bg-raised/60',
            ]"
          >
            <div class="flex items-start justify-between gap-2">
              <div>
                <div class="text-sm font-medium text-ink">{{ pt.shortName }}</div>
                <div class="mt-0.5 text-xs text-ink-secondary">{{ pt.address }}</div>
              </div>
              <span
                v-if="pt.activeCycle?.rolledOverToNextCycle"
                class="shrink-0 rounded-md bg-amberish-100 px-2 py-0.5 text-[11px] font-medium text-amberish-800"
              >
                Đợt D+2
              </span>
              <span
                v-else
                class="shrink-0 rounded-md bg-forest-100 px-2 py-0.5 text-[11px] font-medium text-forest-800"
              >
                Đợt D+1
              </span>
            </div>

            <div v-if="pt.activeCycle" class="mt-3 space-y-1">
              <div class="flex items-center justify-between text-[11px] text-ink-muted">
                <span>Công suất kho lạnh đợt {{ pt.activeCycle.cycleCode }}</span>
                <span class="font-mono tabular-nums text-ink-secondary">
                  {{ pt.activeCycle.currentOrders }} / {{ pt.activeCycle.maxOrders }} đơn
                </span>
              </div>
              <progress
                :value="pt.activeCycle.currentOrders"
                :max="pt.activeCycle.maxOrders"
                class="h-1.5 w-full overflow-hidden rounded-full"
              />
            </div>
          </button>
        </div>
      </div>
    </section>

    <!-- Fixed-Pack Catalog + Sticky Cart -->
    <section class="grid grid-cols-1 gap-6 lg:grid-cols-12">
      <!-- Catalog Column -->
      <div class="lg:col-span-8 space-y-4">
        <div class="rounded-2xl border hairline bg-surface p-5 shadow-card space-y-4">
          <div class="flex flex-wrap items-end justify-between gap-3">
            <div>
              <h2 class="text-lg font-medium text-ink">Thực phẩm đóng gói cố định</h2>
              <p class="text-xs text-ink-secondary">
                Giá niêm yết thống nhất từ kho trung tâm · Định lượng cố định giúp tính chính xác 100% khi thanh toán
              </p>
            </div>
            <div class="relative w-full sm:w-64">
              <Search
                :size="15"
                :stroke-width="1.5"
                class="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-ink-muted"
              />
              <input
                v-model="searchQuery"
                type="search"
                placeholder="Tìm thịt, cá, rau, gạo"
                class="h-9 w-full rounded-lg border hairline bg-canvas/60 pl-9 pr-3 text-xs text-ink focus:bg-surface"
              />
            </div>
          </div>

          <!-- Category & Storage Filters -->
          <div class="flex flex-wrap items-center justify-between gap-3 pt-1">
            <div class="flex flex-wrap items-center gap-1.5">
              <button
                v-for="cat in categories"
                :key="cat"
                type="button"
                @click="selectedCategory = cat"
                :class="[
                  'h-8 rounded-lg px-3 text-xs font-medium',
                  selectedCategory === cat
                    ? 'bg-ink text-white'
                    : 'bg-raised text-ink-secondary hover:text-ink',
                ]"
              >
                {{ cat }}
              </button>
            </div>

            <div class="flex items-center gap-1 text-xs">
              <button
                v-for="opt in [
                  { key: 'ALL', label: 'Mọi bảo quản' },
                  { key: 'CHILLED', label: 'Mát 0–4°C' },
                  { key: 'FROZEN', label: 'Đông -18°C' },
                  { key: 'AMBIENT', label: 'Thường' },
                ]"
                :key="opt.key"
                type="button"
                @click="selectedStorage = opt.key as any"
                :class="[
                  'h-8 rounded-lg border px-2.5 text-xs font-medium',
                  selectedStorage === opt.key
                    ? 'border-forest-700 bg-forest-50 text-forest-800'
                    : 'hairline bg-surface text-ink-secondary hover:bg-raised',
                ]"
              >
                {{ opt.label }}
              </button>
            </div>
          </div>
        </div>

        <!-- Product Grid -->
        <div
          v-if="filteredItems.length === 0"
          class="rounded-2xl border hairline bg-surface p-10 text-center text-sm text-ink-secondary"
        >
          Không tìm thấy gói sản phẩm phù hợp với bộ lọc hiện tại.
        </div>

        <div v-else class="grid grid-cols-1 gap-4 sm:grid-cols-2">
          <article
            v-for="item in filteredItems"
            :key="item.variantId"
            class="flex flex-col justify-between rounded-2xl border hairline bg-surface p-5 shadow-card"
          >
            <div>
              <div class="flex items-center justify-between gap-2 text-xs">
                <span class="text-ink-muted">{{ item.category }}</span>
                <span
                  :class="[
                    'inline-flex items-center gap-1 rounded-md px-2 py-0.5 text-[11px] font-medium',
                    item.storageType === 'FROZEN'
                      ? 'bg-sky-50 text-sky-900'
                      : item.storageType === 'CHILLED'
                        ? 'bg-forest-50 text-forest-800'
                        : 'bg-raised text-ink-secondary',
                  ]"
                >
                  <Snowflake v-if="item.storageType === 'FROZEN'" :size="12" :stroke-width="1.5" />
                  <Thermometer v-else-if="item.storageType === 'CHILLED'" :size="12" :stroke-width="1.5" />
                  <Package v-else :size="12" :stroke-width="1.5" />
                  <span>{{ storageTypeShort(item.storageType) }}</span>
                </span>
              </div>

              <h3 class="mt-2 text-base font-medium text-ink">{{ item.productName }}</h3>
              <p class="mt-1 text-xs text-ink-secondary">
                Quy cách: <strong class="font-medium text-ink">{{ item.packName }}</strong> · Hạn an toàn tại ngày nhận ≥ {{ item.minShelfLifeDaysAtPickup }} ngày
              </p>

              <div class="mt-4 flex items-baseline justify-between gap-2">
                <span class="font-mono text-lg font-medium text-forest-800 tabular-nums">
                  {{ formatVnd(item.unitPrice) }}
                </span>
                <span
                  :class="[
                    'font-mono text-xs tabular-nums',
                    item.availableQty > 10
                      ? 'text-ink-muted'
                      : item.availableQty > 0
                        ? 'text-amberish-700'
                        : 'text-danger-700',
                  ]"
                >
                  Còn {{ item.availableQty }} gói
                </span>
              </div>
            </div>

            <div class="mt-4 pt-3 border-t hairline">
              <div v-if="cartQtyFor(item.variantId) > 0" class="flex items-center justify-between gap-2">
                <span class="text-xs text-forest-800 font-medium">Đã chọn trong giỏ</span>
                <div class="flex items-center gap-1.5">
                  <button
                    type="button"
                    aria-label="Giảm số lượng"
                    @click="cartStore.updateQuantity(item.variantId, cartQtyFor(item.variantId) - 1)"
                    class="inline-flex h-8 w-8 items-center justify-center rounded-lg border hairline bg-canvas hover:bg-raised"
                  >
                    <Minus :size="14" :stroke-width="1.5" />
                  </button>
                  <span class="w-8 text-center font-mono text-sm font-medium tabular-nums">
                    {{ cartQtyFor(item.variantId) }}
                  </span>
                  <button
                    type="button"
                    aria-label="Tăng số lượng"
                    @click="cartStore.updateQuantity(item.variantId, cartQtyFor(item.variantId) + 1)"
                    class="inline-flex h-8 w-8 items-center justify-center rounded-lg border hairline bg-canvas hover:bg-raised"
                  >
                    <Plus :size="14" :stroke-width="1.5" />
                  </button>
                </div>
              </div>
              <button
                v-else
                type="button"
                :disabled="!item.inStock"
                @click="cartStore.addFixedPackItem(item, 1)"
                class="inline-flex h-9 w-full items-center justify-center gap-1.5 rounded-lg bg-forest-700 px-3 text-xs font-medium text-white hover:bg-forest-800 disabled:bg-raised disabled:text-ink-muted"
              >
                <Plus :size="15" :stroke-width="1.5" />
                <span>{{ item.inStock ? 'Thêm 1 gói vào giỏ' : 'Hết tồn trong đợt' }}</span>
              </button>
            </div>
          </article>
        </div>
      </div>

      <!-- Sticky Shopping Cart & VNPAY Prepayment -->
      <aside class="lg:col-span-4">
        <div class="sticky top-32 rounded-2xl border hairline bg-surface p-5 shadow-card space-y-4">
          <div class="flex items-center justify-between">
            <div class="flex items-center gap-2">
              <ShoppingBasket :size="18" :stroke-width="1.5" class="text-forest-700" />
              <h2 class="text-base font-medium text-ink">Giỏ hàng đợt này</h2>
            </div>
            <button
              v-if="cartStore.lines.length > 0"
              type="button"
              @click="cartStore.clearCart()"
              class="inline-flex items-center gap-1 text-xs text-ink-muted hover:text-danger-700"
            >
              <Trash2 :size="13" :stroke-width="1.5" />
              <span>Xóa hết</span>
            </button>
          </div>

          <div class="rounded-xl bg-canvas border hairline p-3 text-xs text-ink-secondary space-y-1">
            <div class="flex items-center gap-1.5 font-medium text-ink">
              <Clock :size="14" :stroke-width="1.5" class="text-amberish-700" />
              <span>Giữ tồn kho tối đa 30 phút sau khi bấm thanh toán</span>
            </div>
            <p class="text-ink-muted leading-relaxed">
              Chỉ áp dụng trả trước 100% qua VNPAY (không COD). Hủy đơn tự do trước 23:59:59 để nhận hoàn tiền 100%.
            </p>
          </div>

          <div v-if="cartStore.lines.length === 0" class="py-10 text-center space-y-2">
            <p class="text-sm text-ink-secondary">Giỏ hàng đang trống</p>
            <p class="text-xs text-ink-muted">
              Chọn gói thực phẩm bên trái hoặc dùng
              <router-link to="/ai-assistant" class="font-medium text-forest-700 underline">
                Trợ lý AI gợi ý thực đơn
              </router-link>
            </p>
          </div>

          <div v-else class="space-y-3">
            <div class="max-h-72 space-y-2.5 overflow-y-auto pr-1">
              <div
                v-for="line in cartStore.lines"
                :key="line.variantId"
                class="flex items-center justify-between gap-2 border-b hairline pb-2.5 text-xs"
              >
                <div class="min-w-0">
                  <div class="truncate font-medium text-ink">{{ line.productName }}</div>
                  <div class="mt-0.5 text-ink-muted font-mono tabular-nums">
                    {{ line.packName }} × {{ formatVnd(line.unitPrice) }}
                  </div>
                </div>
                <div class="flex items-center gap-1 shrink-0">
                  <button
                    type="button"
                    @click="cartStore.updateQuantity(line.variantId, line.quantity - 1)"
                    class="inline-flex h-7 w-7 items-center justify-center rounded-md bg-raised text-ink"
                  >
                    <Minus :size="13" :stroke-width="1.5" />
                  </button>
                  <span class="w-6 text-center font-mono font-medium tabular-nums">
                    {{ line.quantity }}
                  </span>
                  <button
                    type="button"
                    @click="cartStore.updateQuantity(line.variantId, line.quantity + 1)"
                    class="inline-flex h-7 w-7 items-center justify-center rounded-md bg-raised text-ink"
                  >
                    <Plus :size="13" :stroke-width="1.5" />
                  </button>
                </div>
              </div>
            </div>

            <div class="space-y-1.5 pt-2 text-xs text-ink-secondary">
              <div class="flex justify-between">
                <span>Tổng số gói cố định</span>
                <span class="font-mono tabular-nums text-ink">{{ cartStore.totalPackages }} gói</span>
              </div>
              <div class="flex justify-between">
                <span>Phí nhận hàng tại đại lý</span>
                <span class="font-mono tabular-nums text-forest-800">0 ₫</span>
              </div>
              <div class="flex items-baseline justify-between border-t hairline pt-2.5 text-sm font-medium text-ink">
                <span>Tổng thanh toán VNPAY</span>
                <span class="font-mono text-lg text-forest-800 tabular-nums">
                  {{ formatVnd(cartStore.totalAmount) }}
                </span>
              </div>
            </div>

            <button
              type="button"
              :disabled="checkingOut"
              @click="handleCheckout"
              class="inline-flex h-11 w-full items-center justify-center gap-2 rounded-lg bg-forest-700 px-4 text-sm font-medium text-white hover:bg-forest-800 disabled:opacity-60"
            >
              <CreditCard :size="16" :stroke-width="1.5" />
              <span>{{ checkingOut ? 'Đang giữ tồn kho...' : 'Giữ tồn & Thanh toán VNPAY' }}</span>
            </button>
          </div>

          <div
            v-if="checkoutError"
            role="alert"
            class="rounded-lg border border-danger-100 bg-danger-50 p-3 text-xs text-danger-700"
          >
            {{ checkoutError }}
          </div>
        </div>
      </aside>
    </section>

    <!-- Native <dialog> Checkout Confirmation Modal -->
    <AppModal
      :open="checkoutModalOpen"
      title="Đã tạo đơn & giữ tồn kho thành công"
      subtitle="Hoàn tất thanh toán trên cổng VNPAY trước khi hết hạn giữ tồn 30 phút"
      @close="checkoutModalOpen = false"
    >
      <div v-if="lastCheckout" class="space-y-4 text-sm">
        <div class="rounded-xl border hairline bg-canvas p-4 space-y-2 text-xs">
          <div class="flex justify-between">
            <span class="text-ink-muted">Mã đơn hàng</span>
            <span class="font-mono font-medium text-ink">{{ lastCheckout.orderCode }}</span>
          </div>
          <div class="flex justify-between">
            <span class="text-ink-muted">Mã tham chiếu VNPAY</span>
            <span class="font-mono text-ink">{{ lastCheckout.txnRef }}</span>
          </div>
          <div class="flex justify-between">
            <span class="text-ink-muted">Giữ tồn đến</span>
            <span class="font-mono text-amberish-800 tabular-nums">
              {{ formatVnDateTime(lastCheckout.reservationExpiresAt) }}
            </span>
          </div>
          <div class="flex justify-between border-t hairline pt-2 text-sm font-medium">
            <span>Số tiền cần thanh toán</span>
            <span class="font-mono text-forest-800 tabular-nums">
              {{ formatVnd(lastCheckout.totalAmount) }}
            </span>
          </div>
        </div>
        <p class="text-xs text-ink-secondary leading-relaxed">
          Đơn hàng chỉ được đưa vào đợt chốt 23:59:59 sau khi máy chủ nhận được xác nhận IPN hợp lệ từ VNPAY.
        </p>
      </div>
      <template #footer>
        <button
          type="button"
          @click="checkoutModalOpen = false"
          class="h-9 rounded-lg border hairline bg-surface px-4 text-xs font-medium text-ink hover:bg-raised"
        >
          Để sau
        </button>
        <button
          type="button"
          autofocus
          @click="proceedToVnpay"
          class="inline-flex h-9 items-center gap-1.5 rounded-lg bg-forest-700 px-4 text-xs font-medium text-white hover:bg-forest-800"
        >
          <span>Mở cổng thanh toán VNPAY</span>
          <ArrowRight :size="14" :stroke-width="1.5" />
        </button>
      </template>
    </AppModal>
  </div>
</template>

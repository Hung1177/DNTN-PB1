<script setup lang="ts">
import { computed, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import {
  Bell,
  Bot,
  Building2,
  ClipboardList,
  LogOut,
  Menu,
  Package,
  RotateCcw,
  Shield,
  ShoppingBag,
  Store,
  Truck,
  UserCircle2,
  Warehouse,
  X,
} from 'lucide-vue-next';
import { useAuthStore, RoleCode } from '@/stores/authStore';
import { useCartStore } from '@/stores/cartStore';
import { useDemoStore } from '@/stores/demoStore';
import { useNotificationStore } from '@/stores/notificationStore';
import ServerClockBar from '@/components/ServerClockBar.vue';
import { roleDisplayLabel } from '@/utils/formatters';

const authStore = useAuthStore();
const cartStore = useCartStore();
const demoStore = useDemoStore();
const notificationStore = useNotificationStore();
const router = useRouter();
const route = useRoute();

const mobileMenuOpen = ref(false);
const notifOpen = ref(false);

const unreadCount = computed(
  () => notificationStore.notifications.filter((n) => !n.read).length
);

const customerLinks = [
  { to: '/', label: 'Mua sắm', icon: ShoppingBag },
  { to: '/ai-assistant', label: 'Trợ lý AI', icon: Bot },
  { to: '/orders', label: 'Đơn hàng & QR', icon: ClipboardList },
];

const roleWorkspaces: Array<{
  role: RoleCode;
  to: string;
  label: string;
  shortLabel: string;
  icon: any;
}> = [
  { role: 'ROLE_CUSTOMER', to: '/', label: 'Khách hàng', shortLabel: 'Khách', icon: ShoppingBag },
  { role: 'ROLE_WAREHOUSE', to: '/warehouse', label: 'Kho FEFO', shortLabel: 'Kho', icon: Warehouse },
  { role: 'ROLE_DISPATCHER', to: '/dispatcher', label: 'Điều phối', shortLabel: 'Điều phối', icon: Building2 },
  { role: 'ROLE_DRIVER', to: '/driver', label: 'Tài xế', shortLabel: 'Tài xế', icon: Truck },
  { role: 'ROLE_AGENT', to: '/agent', label: 'Đại lý nhận', shortLabel: 'Đại lý', icon: Store },
  { role: 'ROLE_ADMIN', to: '/admin', label: 'Quản trị', shortLabel: 'Quản trị', icon: Shield },
];

function navigateAsRole(item: (typeof roleWorkspaces)[number]) {
  if (demoStore.isDemoMode) {
    authStore.quickSwitchRole(item.role);
  }
  mobileMenuOpen.value = false;
  router.push(item.to);
}

function toggleNotifications() {
  notifOpen.value = !notifOpen.value;
  if (notifOpen.value) {
    notificationStore.markAllRead();
  }
}

function handleLogout() {
  authStore.logout();
  router.push('/login');
}

function isLinkActive(path: string): boolean {
  if (path === '/') return route.path === '/';
  return route.path.startsWith(path);
}
</script>

<template>
  <div class="min-h-screen flex flex-col bg-canvas text-ink">
    <!-- Signature Server Clock & Daily Cutoff Ribbon -->
    <ServerClockBar />

    <!-- Quiet Primary Navigation Shell -->
    <header class="sticky top-0 z-40 border-b hairline bg-surface/95 backdrop-blur-sm">
      <div class="mx-auto flex h-16 max-w-[1440px] items-center justify-between gap-4 px-4 sm:px-6">
        <!-- Brand Wordmark -->
        <div class="flex items-center gap-6">
          <router-link to="/" class="flex items-center gap-2.5 text-ink">
            <span
              class="inline-flex h-8 w-8 items-center justify-center rounded-lg bg-forest-700 text-white"
              aria-hidden="true"
            >
              <Package :size="18" :stroke-width="1.5" />
            </span>
            <div class="leading-tight">
              <div class="text-[15px] font-semibold tracking-tight text-ink">Điểm Nhận Tươi</div>
              <div class="text-[11px] text-ink-muted">Thực phẩm đóng gói Hà Nội</div>
            </div>
          </router-link>

          <!-- Primary Customer Links -->
          <nav class="hidden md:flex items-center gap-1" aria-label="Điều hướng chính">
            <router-link
              v-for="link in customerLinks"
              :key="link.to"
              :to="link.to"
              :class="[
                'inline-flex h-9 items-center gap-2 rounded-lg px-3 text-sm font-medium',
                isLinkActive(link.to)
                  ? 'bg-raised text-forest-800'
                  : 'text-ink-secondary hover:bg-raised/70 hover:text-ink',
              ]"
            >
              <component :is="link.icon" :size="16" :stroke-width="1.5" />
              <span>{{ link.label }}</span>
              <span
                v-if="link.to === '/orders' && cartStore.totalPackages > 0"
                class="ml-0.5 rounded bg-forest-700 px-1.5 py-0.5 font-mono text-[11px] leading-none text-white tabular-nums"
              >
                {{ cartStore.totalPackages }}
              </span>
            </router-link>
          </nav>
        </div>

        <!-- Right Controls: Mode indicator, Notifications, User -->
        <div class="flex items-center gap-2">
          <!-- Mode Toggle: Frontend Demo vs Live API -->
          <div
            class="hidden xl:flex items-center rounded-lg border hairline bg-canvas p-0.5 text-xs"
            role="group"
            aria-label="Chế độ kết nối dữ liệu"
          >
            <button
              type="button"
              @click="demoStore.setDemoMode(true)"
              :class="[
                'rounded-md px-2.5 py-1 font-medium',
                demoStore.isDemoMode
                  ? 'bg-surface text-forest-800 shadow-card'
                  : 'text-ink-muted hover:text-ink',
              ]"
            >
              Giao diện mẫu
            </button>
            <button
              type="button"
              @click="demoStore.setDemoMode(false)"
              :class="[
                'rounded-md px-2.5 py-1 font-medium',
                !demoStore.isDemoMode
                  ? 'bg-surface text-forest-800 shadow-card'
                  : 'text-ink-muted hover:text-ink',
              ]"
            >
              API :8080
            </button>
          </div>

          <button
            v-if="demoStore.isDemoMode"
            type="button"
            title="Khôi phục toàn bộ dữ liệu mẫu"
            @click="demoStore.resetSeedData()"
            class="hidden sm:inline-flex h-9 items-center gap-1.5 rounded-lg border hairline bg-surface px-2.5 text-xs font-medium text-ink-secondary hover:bg-raised hover:text-ink"
          >
            <RotateCcw :size="14" :stroke-width="1.5" />
            <span class="hidden lg:inline">Đặt lại mẫu</span>
          </button>

          <!-- Notification Bell -->
          <div class="relative">
            <button
              type="button"
              aria-label="Mở danh sách thông báo"
              @click="toggleNotifications"
              class="relative inline-flex h-9 w-9 items-center justify-center rounded-lg border hairline bg-surface text-ink-secondary hover:bg-raised hover:text-ink"
            >
              <Bell :size="16" :stroke-width="1.5" />
              <span
                v-if="unreadCount > 0"
                class="absolute right-1.5 top-1.5 h-2 w-2 rounded-full bg-amberish-700"
              />
            </button>

            <div
              v-if="notifOpen"
              class="absolute right-0 mt-2 w-80 rounded-xl border hairline bg-surface shadow-float z-50"
            >
              <div class="flex items-center justify-between border-b hairline px-4 py-3">
                <span class="text-sm font-medium text-ink">Thông báo hệ thống</span>
                <button
                  type="button"
                  @click="notifOpen = false"
                  class="text-xs text-ink-muted hover:text-ink"
                >
                  Đóng
                </button>
              </div>
              <div class="max-h-72 divide-y hairline overflow-y-auto">
                <div
                  v-for="item in notificationStore.notifications.slice(0, 8)"
                  :key="item.id"
                  class="px-4 py-3 text-xs"
                >
                  <div class="flex items-start justify-between gap-2">
                    <span class="font-medium text-ink">{{ item.title }}</span>
                    <span class="shrink-0 font-mono text-[11px] text-ink-muted tabular-nums">
                      {{ item.createdAt }}
                    </span>
                  </div>
                  <p class="mt-1 text-ink-secondary leading-relaxed">{{ item.message }}</p>
                </div>
              </div>
            </div>
          </div>

          <!-- Account & Auth -->
          <div v-if="authStore.isAuthenticated" class="hidden sm:flex items-center gap-2 pl-1">
            <router-link
              to="/login"
              class="flex items-center gap-2 rounded-lg border hairline bg-surface px-2.5 py-1.5 text-xs hover:bg-raised"
            >
              <UserCircle2 :size="16" :stroke-width="1.5" class="text-ink-muted" />
              <div class="leading-tight">
                <div class="font-medium text-ink">{{ authStore.user?.fullName }}</div>
                <div class="text-[11px] text-ink-muted">
                  {{ roleDisplayLabel(authStore.user?.roles[0] || '') }}
                </div>
              </div>
            </router-link>
            <button
              type="button"
              aria-label="Đăng xuất"
              @click="handleLogout"
              class="inline-flex h-9 w-9 items-center justify-center rounded-lg border hairline bg-surface text-ink-secondary hover:bg-danger-50 hover:text-danger-700"
            >
              <LogOut :size="16" :stroke-width="1.5" />
            </button>
          </div>
          <router-link
            v-else
            to="/login"
            class="inline-flex h-9 items-center rounded-lg bg-forest-700 px-3.5 text-sm font-medium text-white hover:bg-forest-800"
          >
            Đăng nhập
          </router-link>

          <!-- Mobile Drawer Trigger -->
          <button
            type="button"
            aria-label="Mở menu điều hướng"
            @click="mobileMenuOpen = !mobileMenuOpen"
            class="inline-flex h-9 w-9 items-center justify-center rounded-lg border hairline bg-surface text-ink md:hidden"
          >
            <X v-if="mobileMenuOpen" :size="18" :stroke-width="1.5" />
            <Menu v-else :size="18" :stroke-width="1.5" />
          </button>
        </div>
      </div>

      <!-- Role Portal Switcher Strip (All 6 Portals Accessible in 1 Click) -->
      <div class="border-t hairline bg-canvas/80">
        <div
          class="mx-auto flex max-w-[1440px] items-center justify-between gap-3 overflow-x-auto px-4 py-2 sm:px-6"
        >
          <div class="flex items-center gap-1.5">
            <span class="mr-1 shrink-0 text-xs text-ink-muted">Cổng vai trò:</span>
            <button
              v-for="ws in roleWorkspaces"
              :key="ws.role"
              type="button"
              @click="navigateAsRole(ws)"
              :class="[
                'inline-flex h-8 shrink-0 items-center gap-1.5 rounded-lg px-2.5 text-xs font-medium',
                isLinkActive(ws.to) && (ws.to !== '/' || route.path === '/')
                  ? 'bg-forest-700 text-white'
                  : 'text-ink-secondary hover:bg-raised hover:text-ink',
              ]"
            >
              <component :is="ws.icon" :size="14" :stroke-width="1.5" />
              <span>{{ ws.label }}</span>
            </button>
          </div>
          <span class="hidden shrink-0 font-mono text-[11px] text-ink-muted lg:inline">
            {{ demoStore.isDemoMode ? 'Chế độ Frontend mẫu (sẵn sàng nối Spring Boot)' : 'Đang gọi API /api/v1' }}
          </span>
        </div>
      </div>

      <!-- Mobile Menu -->
      <div v-if="mobileMenuOpen" class="border-t hairline bg-surface px-4 py-3 space-y-2 md:hidden">
        <div class="space-y-1">
          <router-link
            v-for="link in customerLinks"
            :key="link.to"
            :to="link.to"
            @click="mobileMenuOpen = false"
            class="flex items-center gap-2.5 rounded-lg px-3 py-2 text-sm font-medium text-ink hover:bg-raised"
          >
            <component :is="link.icon" :size="16" :stroke-width="1.5" class="text-ink-muted" />
            <span>{{ link.label }}</span>
          </router-link>
        </div>
        <div class="border-t hairline pt-2 flex items-center justify-between text-xs">
          <span class="text-ink-secondary">{{ authStore.user?.fullName || 'Chưa đăng nhập' }}</span>
          <router-link
            to="/login"
            @click="mobileMenuOpen = false"
            class="font-medium text-forest-700"
          >
            Đổi tài khoản
          </router-link>
        </div>
      </div>
    </header>

    <!-- Live Floating Toast Notification -->
    <div
      v-if="notificationStore.activeToast"
      role="status"
      aria-live="polite"
      class="fixed bottom-4 right-4 z-50 max-w-sm rounded-xl border hairline bg-surface p-4 shadow-float"
    >
      <div class="flex items-start justify-between gap-3">
        <div>
          <div class="text-sm font-medium text-ink">
            {{ notificationStore.activeToast.title }}
          </div>
          <p class="mt-1 text-xs text-ink-secondary leading-relaxed">
            {{ notificationStore.activeToast.message }}
          </p>
        </div>
        <button
          type="button"
          aria-label="Đóng thông báo"
          @click="notificationStore.dismissToast()"
          class="text-ink-muted hover:text-ink"
        >
          <X :size="15" :stroke-width="1.5" />
        </button>
      </div>
    </div>

    <!-- Main Fluid Content -->
    <main class="mx-auto w-full max-w-[1440px] flex-1 px-4 py-6 sm:px-6">
      <router-view />
    </main>

    <!-- Quiet Footer -->
    <footer class="mt-12 border-t hairline bg-surface py-5 text-xs text-ink-muted">
      <div
        class="mx-auto flex max-w-[1440px] flex-wrap items-center justify-between gap-3 px-4 sm:px-6"
      >
        <span>Hệ thống đặt mua thực phẩm qua điểm nhận hàng — O2O Food Pickup Hà Nội</span>
        <span>Chuẩn giờ Asia/Ho_Chi_Minh (UTC+07:00) · Đóng gói cố định · Thanh toán VNPAY 100%</span>
      </div>
    </footer>
  </div>
</template>

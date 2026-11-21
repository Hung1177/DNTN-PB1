<script setup lang="ts">
import { ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ArrowRight, KeyRound, Mail, ShieldCheck, UserCheck } from 'lucide-vue-next';
import { useAuthStore, RoleCode } from '@/stores/authStore';
import { useDemoStore } from '@/stores/demoStore';
import { roleDisplayLabel } from '@/utils/formatters';

const authStore = useAuthStore();
const demoStore = useDemoStore();
const router = useRouter();
const route = useRoute();

const email = ref('khachhang1@gmail.com');
const password = ref('Password@123');
const errorMessage = ref('');
const loading = ref(false);

const quickAccounts: Array<{
  role: RoleCode;
  title: string;
  desc: string;
  email: string;
  defaultPath: string;
}> = [
  {
    role: 'ROLE_CUSTOMER',
    title: 'Khách hàng 01 — Nguyễn Minh Anh',
    desc: 'Chọn điểm nhận trên bản đồ, đặt gói cố định, VNPAY, QR & AI thực đơn',
    email: 'khachhang1@gmail.com',
    defaultPath: '/',
  },
  {
    role: 'ROLE_WAREHOUSE',
    title: 'Thủ kho trung tâm — Trần Văn Kho',
    desc: 'Nhập lô, phân bổ FEFO, đóng gói in tem QR kiện & kiểm nhận kho cách ly',
    email: 'warehouse@o2ofood.vn',
    defaultPath: '/warehouse',
  },
  {
    role: 'ROLE_DISPATCHER',
    title: 'Điều phối viên — Lê Điều Phối',
    desc: 'Lập chuyến giao, giám sát SLA 08:00 & 16:00, điều phối chuyến thu hồi',
    email: 'dispatcher@o2ofood.vn',
    defaultPath: '/dispatcher',
  },
  {
    role: 'ROLE_DRIVER',
    title: 'Tài xế xe lạnh — Phạm Văn Nam',
    desc: 'Ký xuất kho 08:00, cập nhật lộ trình từng đại lý và bàn giao kiện hàng',
    email: 'driver1@o2ofood.vn',
    defaultPath: '/driver',
  },
  {
    role: 'ROLE_AGENT',
    title: 'Đại lý Minh Khai — Đỗ Thị Hạnh',
    desc: 'Kiểm nhận kiện tài xế, quét QR trả khách, hàng tồn chưa lấy & sổ hoa hồng',
    email: 'agent.nhon@o2ofood.vn',
    defaultPath: '/agent',
  },
  {
    role: 'ROLE_ADMIN',
    title: 'Quản trị hệ thống — Nguyễn Quản Trị',
    desc: 'Danh mục gói cố định, cấu hình đợt, phê duyệt hoàn tiền & đối soát hoa hồng',
    email: 'admin@o2ofood.vn',
    defaultPath: '/admin',
  },
];

async function loginWithAccount(acc: (typeof quickAccounts)[number]) {
  email.value = acc.email;
  password.value = 'Password@123';
  await submitLogin(acc.defaultPath);
}

async function submitLogin(targetOverride?: string) {
  loading.value = true;
  errorMessage.value = '';
  try {
    const profile = await authStore.login(email.value, password.value);
    const redirect = (route.query.redirect as string) || targetOverride;
    if (redirect) {
      router.push(redirect);
      return;
    }
    const primaryRole = profile.roles[0];
    const matched = quickAccounts.find((a) => a.role === primaryRole);
    router.push(matched?.defaultPath || '/');
  } catch (err: any) {
    errorMessage.value =
      err?.response?.data?.message || err?.message || 'Đăng nhập thất bại, vui lòng kiểm tra lại.';
  } finally {
    loading.value = false;
  }
}
</script>

<template>
  <div class="mx-auto max-w-5xl py-4">
    <div class="grid grid-cols-1 gap-6 lg:grid-cols-12">
      <!-- Left: Sign-in Form -->
      <section class="rounded-2xl border hairline bg-surface p-6 shadow-card lg:col-span-5">
        <div class="mb-6">
          <div class="inline-flex items-center gap-1.5 rounded-md bg-forest-50 px-2.5 py-1 text-xs font-medium text-forest-800">
            <ShieldCheck :size="14" :stroke-width="1.5" />
            <span>{{ demoStore.isDemoMode ? 'Chế độ giao diện mẫu' : 'Xác thực JWT Backend' }}</span>
          </div>
          <h1 class="mt-3 text-2xl font-medium tracking-tight text-ink">Đăng nhập hệ thống</h1>
          <p class="mt-1 text-sm text-ink-secondary">
            Truy cập đúng không gian làm việc theo vai trò được phân quyền.
          </p>
        </div>

        <form @submit.prevent="submitLogin()" class="space-y-4">
          <div>
            <label for="login-email" class="mb-1.5 block text-xs font-medium text-ink-secondary">
              Email tài khoản
            </label>
            <div class="relative">
              <Mail
                :size="16"
                :stroke-width="1.5"
                class="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-ink-muted"
              />
              <input
                id="login-email"
                v-model="email"
                type="email"
                autocomplete="username"
                required
                class="h-10 w-full rounded-lg border hairline bg-canvas/50 pl-9 pr-3 text-sm text-ink focus:bg-surface"
              />
            </div>
          </div>

          <div>
            <label for="login-password" class="mb-1.5 block text-xs font-medium text-ink-secondary">
              Mật khẩu
            </label>
            <div class="relative">
              <KeyRound
                :size="16"
                :stroke-width="1.5"
                class="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-ink-muted"
              />
              <input
                id="login-password"
                v-model="password"
                type="password"
                autocomplete="current-password"
                required
                class="h-10 w-full rounded-lg border hairline bg-canvas/50 pl-9 pr-3 text-sm text-ink focus:bg-surface"
              />
            </div>
          </div>

          <div
            v-if="errorMessage"
            role="alert"
            class="rounded-lg border border-danger-100 bg-danger-50 px-3 py-2.5 text-xs text-danger-700"
          >
            {{ errorMessage }}
          </div>

          <button
            type="submit"
            :disabled="loading"
            class="inline-flex h-10 w-full items-center justify-center gap-2 rounded-lg bg-forest-700 px-4 text-sm font-medium text-white hover:bg-forest-800 disabled:opacity-60"
          >
            <span>{{ loading ? 'Đang xác thực...' : 'Đăng nhập' }}</span>
            <ArrowRight :size="16" :stroke-width="1.5" />
          </button>
        </form>

        <div class="mt-6 rounded-xl border hairline bg-canvas p-4 text-xs text-ink-secondary space-y-1">
          <div class="font-medium text-ink">Tài khoản đang hoạt động</div>
          <div v-if="authStore.user">
            {{ authStore.user.fullName }} · {{ roleDisplayLabel(authStore.user.roles[0]) }}
          </div>
          <div v-else class="text-ink-muted">Chưa đăng nhập</div>
        </div>
      </section>

      <!-- Right: 1-Click Role Switcher Cards -->
      <section class="rounded-2xl border hairline bg-surface p-6 shadow-card lg:col-span-7">
        <div class="flex items-start justify-between gap-3 mb-4">
          <div>
            <h2 class="text-base font-medium text-ink">Chọn nhanh vai trò trải nghiệm</h2>
            <p class="mt-0.5 text-xs text-ink-secondary">
              Mật khẩu mặc định của toàn bộ tài khoản mẫu là <span class="font-mono text-ink">Password@123</span>.
            </p>
          </div>
          <UserCheck :size="18" :stroke-width="1.5" class="text-ink-muted shrink-0" />
        </div>

        <div class="grid grid-cols-1 gap-3 sm:grid-cols-2">
          <button
            v-for="acc in quickAccounts"
            :key="acc.email"
            type="button"
            @click="loginWithAccount(acc)"
            class="group flex flex-col justify-between rounded-xl border hairline bg-canvas/50 p-4 text-left hover:border-forest-600/40 hover:bg-forest-50/40"
          >
            <div>
              <div class="flex items-center justify-between gap-2">
                <span class="text-xs font-medium text-forest-800">
                  {{ roleDisplayLabel(acc.role) }}
                </span>
                <ArrowRight
                  :size="14"
                  :stroke-width="1.5"
                  class="text-ink-muted group-hover:text-forest-700"
                />
              </div>
              <div class="mt-1.5 text-sm font-medium text-ink">{{ acc.title }}</div>
              <p class="mt-1 text-xs text-ink-secondary leading-relaxed">{{ acc.desc }}</p>
            </div>
            <div class="mt-3 font-mono text-[11px] text-ink-muted">{{ acc.email }}</div>
          </button>
        </div>
      </section>
    </div>
  </div>
</template>

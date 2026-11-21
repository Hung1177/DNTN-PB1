import { createRouter, createWebHistory, RouteRecordRaw } from 'vue-router';
import { RoleCode, useAuthStore } from '@/stores/authStore';
import { useDemoStore } from '@/stores/demoStore';

import CustomerHomeView from '@/views/customer/CustomerHomeView.vue';
import VnpayReturnView from '@/views/customer/VnpayReturnView.vue';
import OrderDetailQrView from '@/views/customer/OrderDetailQrView.vue';
import AiAssistantView from '@/views/customer/AiAssistantView.vue';
import WarehousePortalView from '@/views/warehouse/WarehousePortalView.vue';
import DispatcherPortalView from '@/views/dispatcher/DispatcherPortalView.vue';
import AgentPortalView from '@/views/agent/AgentPortalView.vue';
import DriverPortalView from '@/views/driver/DriverPortalView.vue';
import AdminPortalView from '@/views/admin/AdminPortalView.vue';
import LoginView from '@/views/auth/LoginView.vue';

const routes: RouteRecordRaw[] = [
  {
    path: '/',
    name: 'customer-home',
    component: CustomerHomeView,
    meta: { section: 'customer', title: 'Mua sắm theo đợt' },
  },
  {
    path: '/login',
    name: 'login',
    component: LoginView,
    meta: { section: 'auth', title: 'Đăng nhập' },
  },
  {
    path: '/payment/vnpay-return',
    name: 'vnpay-return',
    component: VnpayReturnView,
    meta: { section: 'customer', title: 'Kết quả VNPAY' },
  },
  {
    path: '/orders',
    name: 'customer-orders',
    component: OrderDetailQrView,
    meta: {
      section: 'customer',
      title: 'Đơn hàng & Mã QR',
      requiresAuth: true,
      roles: ['ROLE_CUSTOMER', 'ROLE_ADMIN'] as RoleCode[],
      preferredRole: 'ROLE_CUSTOMER' as RoleCode,
    },
  },
  {
    path: '/ai-assistant',
    name: 'ai-assistant',
    component: AiAssistantView,
    meta: { section: 'customer', title: 'Trợ lý AI & Thực đơn' },
  },
  {
    path: '/warehouse',
    name: 'warehouse-portal',
    component: WarehousePortalView,
    meta: {
      section: 'ops',
      title: 'Kho trung tâm FEFO',
      requiresAuth: true,
      roles: ['ROLE_WAREHOUSE', 'ROLE_ADMIN'] as RoleCode[],
      preferredRole: 'ROLE_WAREHOUSE' as RoleCode,
    },
  },
  {
    path: '/dispatcher',
    name: 'dispatcher-portal',
    component: DispatcherPortalView,
    meta: {
      section: 'ops',
      title: 'Điều phối vận chuyển',
      requiresAuth: true,
      roles: ['ROLE_DISPATCHER', 'ROLE_ADMIN'] as RoleCode[],
      preferredRole: 'ROLE_DISPATCHER' as RoleCode,
    },
  },
  {
    path: '/driver',
    name: 'driver-portal',
    component: DriverPortalView,
    meta: {
      section: 'ops',
      title: 'Tài xế giao nhận',
      requiresAuth: true,
      roles: ['ROLE_DRIVER', 'ROLE_ADMIN'] as RoleCode[],
      preferredRole: 'ROLE_DRIVER' as RoleCode,
    },
  },
  {
    path: '/agent',
    name: 'agent-portal',
    component: AgentPortalView,
    meta: {
      section: 'ops',
      title: 'Đại lý điểm nhận',
      requiresAuth: true,
      roles: ['ROLE_AGENT', 'ROLE_ADMIN'] as RoleCode[],
      preferredRole: 'ROLE_AGENT' as RoleCode,
    },
  },
  {
    path: '/admin',
    name: 'admin-portal',
    component: AdminPortalView,
    meta: {
      section: 'ops',
      title: 'Quản trị & Đối soát',
      requiresAuth: true,
      roles: ['ROLE_ADMIN'] as RoleCode[],
      preferredRole: 'ROLE_ADMIN' as RoleCode,
    },
  },
];

const router = createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior() {
    return { top: 0 };
  },
});

router.beforeEach((to, _from, next) => {
  const authStore = useAuthStore();
  const demoStore = useDemoStore();
  const requiresAuth = Boolean(to.meta.requiresAuth);
  const allowedRoles = (to.meta.roles as RoleCode[] | undefined) || [];
  const preferredRole = to.meta.preferredRole as RoleCode | undefined;

  if (demoStore.isDemoMode && requiresAuth) {
    if (!authStore.isAuthenticated || (allowedRoles.length > 0 && !allowedRoles.some((r) => authStore.hasRole(r)))) {
      if (preferredRole) {
        authStore.quickSwitchRole(preferredRole);
      }
    }
    return next();
  }

  if (requiresAuth && !authStore.isAuthenticated) {
    return next({ name: 'login', query: { redirect: to.fullPath } });
  }
  if (allowedRoles.length > 0 && authStore.user) {
    const allowed = allowedRoles.some((r) => authStore.hasRole(r));
    if (!allowed) {
      return next({ name: 'customer-home' });
    }
  }
  return next();
});

export default router;

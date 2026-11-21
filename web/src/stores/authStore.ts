import { defineStore } from 'pinia';
import { computed, ref } from 'vue';
import httpClient, { ApiEnvelope } from '@/api/httpClient';
import type { RoleCode, UserAccount } from '@/types/domain';
import { useDemoStore } from '@/stores/demoStore';

export type { RoleCode };
export type UserProfile = UserAccount;

export const useAuthStore = defineStore('auth', () => {
  const demoStore = useDemoStore();

  const savedUserRaw = localStorage.getItem('o2ofood_user');
  const initialUser: UserProfile | null = savedUserRaw
    ? JSON.parse(savedUserRaw)
    : demoStore.isDemoMode
      ? demoStore.users[0]
      : null;

  const accessToken = ref<string | null>(
    localStorage.getItem('o2ofood_access_token') || (initialUser ? 'demo-jwt-token' : null)
  );
  const refreshToken = ref<string | null>(
    localStorage.getItem('o2ofood_refresh_token') || (initialUser ? 'demo-refresh-token' : null)
  );
  const user = ref<UserProfile | null>(initialUser);

  const isAuthenticated = computed(() => Boolean(accessToken.value && user.value));

  function hasRole(role: RoleCode): boolean {
    return Boolean(user.value?.roles?.includes(role));
  }

  function setSession(profile: UserProfile, token: string, refresh: string) {
    accessToken.value = token;
    refreshToken.value = refresh;
    user.value = profile;
    localStorage.setItem('o2ofood_access_token', token);
    localStorage.setItem('o2ofood_refresh_token', refresh);
    localStorage.setItem('o2ofood_user', JSON.stringify(profile));
  }

  async function login(email: string, password: string): Promise<UserProfile> {
    if (demoStore.isDemoMode) {
      const matched = demoStore.users.find(
        (u) => u.email.toLowerCase() === email.trim().toLowerCase()
      );
      if (!matched) {
        throw new Error('Không tìm thấy tài khoản trong danh sách dữ liệu mẫu.');
      }
      if (password.length < 4) {
        throw new Error('Mật khẩu không hợp lệ.');
      }
      setSession(matched, `demo-jwt-${matched.userId}`, `demo-refresh-${matched.userId}`);
      return matched;
    }

    const res = await httpClient.post<
      ApiEnvelope<{
        accessToken: string;
        refreshToken: string;
        user: UserProfile;
      }>
    >('/auth/login', { email, password });

    const payload = res.data.data;
    setSession(payload.user, payload.accessToken, payload.refreshToken);
    return payload.user;
  }

  function quickSwitchRole(role: RoleCode): UserProfile | null {
    const matched = demoStore.users.find((u) => u.roles.includes(role));
    if (!matched) return null;
    setSession(matched, `demo-jwt-${matched.userId}`, `demo-refresh-${matched.userId}`);
    return matched;
  }

  function logout() {
    accessToken.value = null;
    refreshToken.value = null;
    user.value = null;
    localStorage.removeItem('o2ofood_access_token');
    localStorage.removeItem('o2ofood_refresh_token');
    localStorage.removeItem('o2ofood_user');
  }

  return {
    accessToken,
    refreshToken,
    user,
    isAuthenticated,
    hasRole,
    login,
    quickSwitchRole,
    logout,
  };
});

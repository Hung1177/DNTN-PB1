import { defineStore } from 'pinia';
import { ref } from 'vue';

export interface AppNotification {
  id: string;
  title: string;
  message: string;
  createdAt: string;
  read: boolean;
}

export const useNotificationStore = defineStore('notification', () => {
  const notifications = ref<AppNotification[]>([
    {
      id: 'init-1',
      title: 'Đơn ORD-20261120-001 sẵn sàng nhận tại đại lý',
      message: 'Điểm nhận Minh Khai — Bắc Từ Liêm đã kiểm nhận đủ 2 kiện. Vui lòng xuất trình mã QR từ 16:00.',
      createdAt: '16:02',
      read: false,
    },
    {
      id: 'init-2',
      title: 'Nhắc hạn chốt đợt CYCLE-2026-11-22',
      message: 'Các đơn thanh toán VNPAY trước 23:59:59 hôm nay sẽ được giao vào ngày mai 22/11/2026.',
      createdAt: '14:30',
      read: true,
    },
  ]);
  const connected = ref(false);
  const activeToast = ref<AppNotification | null>(null);
  let eventSource: EventSource | null = null;
  let toastTimer: number | null = null;

  function pushToast(title: string, message: string) {
    const item: AppNotification = {
      id: `${Date.now()}-${Math.random()}`,
      title,
      message,
      createdAt: new Date().toLocaleTimeString('vi-VN', {
        timeZone: 'Asia/Ho_Chi_Minh',
        hour: '2-digit',
        minute: '2-digit',
      }),
      read: false,
    };
    notifications.value.unshift(item);
    activeToast.value = item;
    if (toastTimer) window.clearTimeout(toastTimer);
    toastTimer = window.setTimeout(() => {
      activeToast.value = null;
    }, 4800);
  }

  function markAllRead() {
    for (const n of notifications.value) {
      n.read = true;
    }
  }

  function dismissToast() {
    activeToast.value = null;
  }

  function connectSse() {
    if (eventSource) return;
    try {
      eventSource = new EventSource('/api/v1/notifications/stream');
      eventSource.addEventListener('CONNECTED', () => {
        connected.value = true;
      });
      eventSource.onmessage = (ev) => {
        pushToast('Thông báo hệ thống', ev.data);
      };
    } catch {
      connected.value = false;
    }
  }

  function disconnectSse() {
    if (eventSource) {
      eventSource.close();
      eventSource = null;
      connected.value = false;
    }
  }

  return {
    notifications,
    connected,
    activeToast,
    pushToast,
    markAllRead,
    dismissToast,
    connectSse,
    disconnectSse,
  };
});

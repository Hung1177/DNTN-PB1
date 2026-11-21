<script setup lang="ts">
import { computed } from 'vue';

const props = defineProps<{
  status: string;
  label?: string;
}>();

const config = computed(() => {
  const s = props.status;
  const map: Record<string, { text: string; tone: 'green' | 'amber' | 'red' | 'neutral' | 'blue' }> = {
    PENDING_PAYMENT: { text: 'Chờ thanh toán', tone: 'amber' },
    PAID_WAITING_CUTOFF: { text: 'Đã thanh toán · Chờ chốt', tone: 'blue' },
    CONFIRMED: { text: 'Đã chốt đơn', tone: 'blue' },
    PROCESSING: { text: 'Đang xử lý giao', tone: 'blue' },
    COMPLETED: { text: 'Hoàn tất', tone: 'green' },
    CANCELLED: { text: 'Đã hủy', tone: 'red' },

    WAITING_PACKING: { text: 'Chờ kho đóng gói', tone: 'neutral' },
    PACKED: { text: 'Đã đóng gói FEFO', tone: 'blue' },
    IN_TRANSIT: { text: 'Đang vận chuyển', tone: 'blue' },
    AGENT_CHECKING: { text: 'Đại lý đang kiểm nhận', tone: 'amber' },
    READY_FOR_PICKUP: { text: 'Sẵn sàng nhận tại đại lý', tone: 'green' },
    HANDED_OVER: { text: 'Đã bàn giao khách', tone: 'green' },
    ISSUE_HOLD: { text: 'Tạm giữ do sự cố', tone: 'red' },

    UNPAID: { text: 'Chưa thanh toán', tone: 'amber' },
    PAID: { text: 'Đã thu 100% VNPAY', tone: 'green' },
    LATE_RECONCILIATION: { text: 'Đối soát thanh toán muộn', tone: 'amber' },
    REFUND_REQUESTED: { text: 'Đang yêu cầu hoàn tiền', tone: 'amber' },
    REFUND_APPROVED: { text: 'Đã duyệt hoàn tiền', tone: 'blue' },
    REFUNDING: { text: 'Đang hoàn tiền', tone: 'blue' },
    REFUNDED: { text: 'Đã hoàn tiền', tone: 'green' },

    AVAILABLE: { text: 'Khả dụng FEFO', tone: 'green' },
    QUARANTINED: { text: 'Đang cách ly', tone: 'red' },
    EXPIRED: { text: 'Hết hạn', tone: 'red' },

    OPEN: { text: 'Đang mở đặt', tone: 'green' },
    LOCKED: { text: 'Đã khóa chốt', tone: 'amber' },
    FULFILLING: { text: 'Đang giao nhận', tone: 'blue' },
    CLOSED: { text: 'Đã đóng', tone: 'neutral' },

    ASSIGNED: { text: 'Chờ xuất kho', tone: 'amber' },
    PENDING: { text: 'Chờ đến điểm', tone: 'neutral' },
    ARRIVED_AT_POINT: { text: 'Đã đến đại lý', tone: 'amber' },

    REQUESTED: { text: 'Mới yêu cầu thu hồi', tone: 'amber' },
    APPROVED_FOR_PICKUP: { text: 'Chờ tài xế lấy hoàn', tone: 'blue' },
    DRIVER_PICKED_UP: { text: 'Tài xế đang chở về kho', tone: 'blue' },
    WAREHOUSE_RECEIVED: { text: 'Kho đã nhập cách ly', tone: 'green' },
    UNRECOVERABLE_WAIVED: { text: 'Miễn thu hồi đặc cách', tone: 'amber' },

    PENDING_APPROVAL: { text: 'Chờ duyệt hoàn', tone: 'amber' },
    APPROVED: { text: 'Đã duyệt', tone: 'blue' },

    CREDIT_COMMISSION: { text: 'Ghi có hoa hồng', tone: 'green' },
    DEBIT_REFUND_REVERSAL: { text: 'Thu hồi do hoàn tiền', tone: 'red' },
    MANUAL_ADJUSTMENT: { text: 'Điều chỉnh thủ công', tone: 'amber' },

    HANDED_TO_DRIVER: { text: 'Đã giao tài xế', tone: 'blue' },
    DELIVERED_TO_AGENT: { text: 'Đại lý đã nhận đủ', tone: 'green' },
    HANDED_TO_CUSTOMER: { text: 'Đã giao cho khách', tone: 'green' },
    DAMAGED: { text: 'Dập hỏng', tone: 'red' },
    MISSING: { text: 'Thiếu kiện', tone: 'red' },
    ACCEPTED: { text: 'Nguyên vẹn', tone: 'green' },

    CUSTOMER_CONFIRMED: { text: 'Khách xác nhận', tone: 'green' },
    SYSTEM_TIMEOUT: { text: 'Tự hoàn tất 00:00 D+2', tone: 'amber' },
  };
  return map[s] || { text: props.label || s, tone: 'neutral' as const };
});

const toneClass = computed(() => {
  switch (config.value.tone) {
    case 'green':
      return 'bg-forest-50 text-forest-800 border-forest-200';
    case 'amber':
      return 'bg-amberish-50 text-amberish-800 border-amberish-200';
    case 'red':
      return 'bg-danger-50 text-danger-700 border-danger-100';
    case 'blue':
      return 'bg-sky-50 text-sky-900 border-sky-200';
    default:
      return 'bg-raised text-ink-secondary border-black/10';
  }
});

const dotClass = computed(() => {
  switch (config.value.tone) {
    case 'green':
      return 'bg-forest-700';
    case 'amber':
      return 'bg-amberish-700';
    case 'red':
      return 'bg-danger-700';
    case 'blue':
      return 'bg-sky-700';
    default:
      return 'bg-ink-muted';
  }
});
</script>

<template>
  <span
    :title="status"
    :class="[
      'inline-flex items-center gap-1.5 rounded-md border px-2 py-0.5 text-xs font-medium leading-4',
      toneClass,
    ]"
  >
    <span :class="['h-1.5 w-1.5 rounded-full shrink-0', dotClass]" aria-hidden="true" />
    <span>{{ label || config.text }}</span>
  </span>
</template>

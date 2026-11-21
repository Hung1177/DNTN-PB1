export function formatVnd(amount: number | null | undefined): string {
  const safe = Number(amount || 0);
  return new Intl.NumberFormat('vi-VN', {
    style: 'currency',
    currency: 'VND',
    maximumFractionDigits: 0,
  }).format(safe);
}

export function formatVnDateTime(iso: string | null | undefined): string {
  if (!iso) return '—';
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) return iso;
  return new Intl.DateTimeFormat('vi-VN', {
    timeZone: 'Asia/Ho_Chi_Minh',
    hour: '2-digit',
    minute: '2-digit',
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
  }).format(date);
}

export function formatVnTime(iso: string | null | undefined): string {
  if (!iso) return '—';
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) return iso;
  return new Intl.DateTimeFormat('vi-VN', {
    timeZone: 'Asia/Ho_Chi_Minh',
    hour: '2-digit',
    minute: '2-digit',
  }).format(date);
}

export function formatVnDate(dateStr: string | null | undefined): string {
  if (!dateStr) return '—';
  const parts = dateStr.split('-');
  if (parts.length === 3) {
    return `${parts[2]}/${parts[1]}/${parts[0]}`;
  }
  return dateStr;
}

export function storageTypeLabel(type: 'AMBIENT' | 'CHILLED' | 'FROZEN'): string {
  switch (type) {
    case 'CHILLED':
      return 'Bảo quản mát (0–4°C)';
    case 'FROZEN':
      return 'Đông lạnh (-18°C)';
    default:
      return 'Nhiệt độ thường';
  }
}

export function storageTypeShort(type: 'AMBIENT' | 'CHILLED' | 'FROZEN'): string {
  switch (type) {
    case 'CHILLED':
      return 'Mát 0–4°C';
    case 'FROZEN':
      return 'Đông -18°C';
    default:
      return 'Khô / Thường';
  }
}

export function roleDisplayLabel(role: string): string {
  const map: Record<string, string> = {
    ROLE_CUSTOMER: 'Khách hàng',
    ROLE_WAREHOUSE: 'Nhân viên kho',
    ROLE_DISPATCHER: 'Điều phối viên',
    ROLE_DRIVER: 'Tài xế giao nhận',
    ROLE_AGENT: 'Đại lý điểm nhận',
    ROLE_ADMIN: 'Quản trị viên',
  };
  return map[role] || role;
}

export function nowIsoVn(): string {
  const d = new Date();
  const offsetMin = 7 * 60;
  const local = new Date(d.getTime() + (offsetMin + d.getTimezoneOffset()) * 60000);
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${local.getFullYear()}-${pad(local.getMonth() + 1)}-${pad(local.getDate())}T${pad(
    local.getHours()
  )}:${pad(local.getMinutes())}:${pad(local.getSeconds())}+07:00`;
}

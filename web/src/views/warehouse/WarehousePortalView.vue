<script setup lang="ts">
import { ref } from 'vue';
import httpClient from '@/api/httpClient';

const variantId = ref(1);
const cycleId = ref(1);
const lotCode = ref('LOT-NEW-20261118');
const supplierName = ref('Nông trại CP Hà Nội');
const manufacturedDate = ref('2026-11-17');
const expiryDate = ref('2026-11-24');
const receivedQty = ref(40);

const orderIdForPack = ref(1);
const returnRequestId = ref(1);
const driverUserId = ref(4);
const inspectionNote = ref('Đã kiểm nhận vào khu cách ly hàng lỗi (QUARANTINE_DAMAGED_STOCK)');
const statusMsg = ref('');
const errorMsg = ref('');

async function handleInboundLot() {
  statusMsg.value = '';
  errorMsg.value = '';
  try {
    const res = await httpClient.post('/warehouse/lots', {
      variantId: variantId.value,
      cycleId: cycleId.value,
      lotCode: lotCode.value,
      supplierName: supplierName.value,
      manufacturedDate: manufacturedDate.value,
      expiryDate: expiryDate.value,
      receivedQty: receivedQty.value,
    });
    statusMsg.value = res.data.message;
  } catch (err: any) {
    errorMsg.value = err?.response?.data?.message || 'Lỗi nhập lô mới.';
  }
}

async function handlePackOrderFefo() {
  statusMsg.value = '';
  errorMsg.value = '';
  try {
    const res = await httpClient.post(`/warehouse/orders/${orderIdForPack.value}/pack`, [
      'CHILLED',
    ]);
    statusMsg.value = `${res.data.message} — Mã QR nhận hàng của khách: ${res.data.data.customerPickupQrToken}`;
  } catch (err: any) {
    errorMsg.value = err?.response?.data?.message || 'Không thể phân bổ FEFO hoặc đóng gói đơn.';
  }
}

async function handleReceiveReturnQuarantine() {
  statusMsg.value = '';
  errorMsg.value = '';
  try {
    const res = await httpClient.post(
      `/exceptions/returns/${returnRequestId.value}/warehouse-receive`,
      {
        driverUserId: driverUserId.value,
        inspectionNote: inspectionNote.value,
      }
    );
    statusMsg.value = res.data.message;
  } catch (err: any) {
    errorMsg.value = err?.response?.data?.message || 'Không thể xác nhận hàng hoàn về kho.';
  }
}
</script>

<template>
  <div class="space-y-6">
    <div>
      <h1 class="text-xl font-bold text-slate-900">
        Phân Hệ Kho Trung Tâm (ROLE_WAREHOUSE — FEFO, Wave Picking, Đóng Gói QR & Kho Cách Ly)
      </h1>
      <p class="text-xs text-slate-500">
        Xuất lô ưu tiên hạn sử dụng gần nhất (FEFO) nhưng đảm bảo còn hạn sau ngày nhận D+1 • Hàng hoàn đưa vào kho cách ly, tuyệt đối không cộng ngược vào tồn thương mại.
      </p>
    </div>

    <div v-if="statusMsg" class="p-3 rounded-lg bg-emerald-50 text-emerald-800 text-xs font-semibold">
      {{ statusMsg }}
    </div>
    <div v-if="errorMsg" class="p-3 rounded-lg bg-red-50 text-red-700 text-xs font-semibold">
      {{ errorMsg }}
    </div>

    <div class="grid grid-cols-1 lg:grid-cols-3 gap-6 text-xs">
      <!-- UC-W01: Lot Inbound -->
      <section class="bg-white rounded-xl border border-slate-200 p-5 shadow space-y-3">
        <h2 class="text-sm font-bold text-slate-900">1. Nhập Kho Theo Lô (UC-W01)</h2>
        <div>
          <label class="block font-semibold mb-1">Variant ID & Cycle ID</label>
          <div class="grid grid-cols-2 gap-2">
            <input v-model.number="variantId" type="number" class="rounded border px-2.5 py-1.5" />
            <input v-model.number="cycleId" type="number" class="rounded border px-2.5 py-1.5" />
          </div>
        </div>
        <div>
          <label class="block font-semibold mb-1">Mã lô (lot_code)</label>
          <input v-model="lotCode" type="text" class="w-full rounded border px-2.5 py-1.5" />
        </div>
        <div>
          <label class="block font-semibold mb-1">Nhà cung cấp nội bộ</label>
          <input v-model="supplierName" type="text" class="w-full rounded border px-2.5 py-1.5" />
        </div>
        <div class="grid grid-cols-2 gap-2">
          <div>
            <label class="block font-semibold mb-1">Ngày SX</label>
            <input v-model="manufacturedDate" type="date" class="w-full rounded border px-2 py-1.5" />
          </div>
          <div>
            <label class="block font-semibold mb-1">Hạn dùng (FEFO)</label>
            <input v-model="expiryDate" type="date" class="w-full rounded border px-2 py-1.5" />
          </div>
        </div>
        <div>
          <label class="block font-semibold mb-1">Số lượng gói nhập</label>
          <input v-model.number="receivedQty" type="number" class="w-full rounded border px-2.5 py-1.5" />
        </div>
        <button
          type="button"
          @click="handleInboundLot"
          class="w-full py-2 rounded-lg bg-emerald-700 text-white font-semibold"
        >
          Nhập Lô & Cộng Tồn Đợt
        </button>
      </section>

      <!-- UC-W02: FEFO Allocation & QR Packing -->
      <section class="bg-white rounded-xl border border-slate-200 p-5 shadow space-y-3">
        <h2 class="text-sm font-bold text-slate-900">
          2. Phân Bổ Lô FEFO & Đóng Gói Kiện QR (UC-W02, AT-07)
        </h2>
        <p class="text-slate-500">
          Tự động chọn lô có <code>expiry_date</code> sớm nhất thỏa mãn hạn an toàn tại ngày nhận, bỏ qua lô <code>QUARANTINED</code>.
        </p>
        <div>
          <label class="block font-semibold mb-1">Order ID cần đóng gói</label>
          <input v-model.number="orderIdForPack" type="number" class="w-full rounded border px-2.5 py-1.5" />
        </div>
        <button
          type="button"
          @click="handlePackOrderFefo"
          class="w-full py-2 rounded-lg bg-blue-700 text-white font-semibold"
        >
          Phân Bổ Lô FEFO & In Tem QR Kiện Hàng
        </button>
      </section>

      <!-- UC-W04: Return Quarantine Inspection -->
      <section class="bg-white rounded-xl border border-slate-200 p-5 shadow space-y-3">
        <h2 class="text-sm font-bold text-slate-900">
          3. Kiểm Nhận Hàng Hoàn Về Khu Cách Ly (UC-W04, AT-11)
        </h2>
        <p class="text-slate-500">
          Ghi nhận <code>RETURN_TO_QUARANTINE</code>, mở khóa bước Admin duyệt hoàn tiền, không cộng ngược vào tồn thương mại.
        </p>
        <div>
          <label class="block font-semibold mb-1">Return Request ID</label>
          <input v-model.number="returnRequestId" type="number" class="w-full rounded border px-2.5 py-1.5" />
        </div>
        <div>
          <label class="block font-semibold mb-1">Driver User ID bàn giao</label>
          <input v-model.number="driverUserId" type="number" class="w-full rounded border px-2.5 py-1.5" />
        </div>
        <div>
          <label class="block font-semibold mb-1">Ghi chú kiểm nhận cách ly</label>
          <textarea v-model="inspectionNote" rows="2" class="w-full rounded border p-2"></textarea>
        </div>
        <button
          type="button"
          @click="handleReceiveReturnQuarantine"
          class="w-full py-2 rounded-lg bg-amber-600 text-white font-semibold"
        >
          Xác Nhận Nhập Khu Cách Ly Hàng Lỗi
        </button>
      </section>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue';
import httpClient from '@/api/httpClient';

const cycleId = ref(1);
const driverId = ref(4);
const pickupPointsCsv = ref('1,2,3');
const statusMsg = ref('');
const errorMsg = ref('');
const createdTrip = ref<any>(null);

async function handleCreateTrip() {
  statusMsg.value = '';
  errorMsg.value = '';
  try {
    const pickupPointIds = pickupPointsCsv.value
      .split(',')
      .map((s) => Number(s.trim()))
      .filter((n) => !Number.isNaN(n) && n > 0);
    const res = await httpClient.post('/fulfillment/trips', {
      cycleId: cycleId.value,
      driverId: driverId.value,
      pickupPointIds,
    });
    createdTrip.value = res.data.data;
    statusMsg.value = res.data.message;
  } catch (err: any) {
    errorMsg.value = err?.response?.data?.message || 'Không thể tạo chuyến giao hàng.';
  }
}
</script>

<template>
  <div class="space-y-6">
    <div>
      <h1 class="text-xl font-bold text-slate-900">
        Phân Hệ Điều Phối Vận Chuyển (ROLE_DISPATCHER — UC-D01)
      </h1>
      <p class="text-xs text-slate-500">
        Phân công chuyến giao hàng (Trips & TripStops) cho đợt D+1, giám sát SLA đóng gói 08:00 và hạn bàn giao đại lý 16:00.
      </p>
    </div>

    <div v-if="statusMsg" class="p-3 rounded-lg bg-emerald-50 text-emerald-800 text-xs font-semibold">
      {{ statusMsg }}
    </div>
    <div v-if="errorMsg" class="p-3 rounded-lg bg-red-50 text-red-700 text-xs font-semibold">
      {{ errorMsg }}
    </div>

    <div class="bg-white rounded-xl border border-slate-200 p-5 shadow max-w-xl space-y-3 text-xs">
      <h2 class="text-sm font-bold text-slate-900">Tạo Chuyến Giao Hàng & Tuyến Điểm Dừng (UC-D01)</h2>
      <div>
        <label class="block font-semibold mb-1">Cycle ID (Đợt giao hàng)</label>
        <input v-model.number="cycleId" type="number" class="w-full rounded border px-2.5 py-1.5" />
      </div>
      <div>
        <label class="block font-semibold mb-1">Driver User ID (Tài xế phụ trách)</label>
        <input v-model.number="driverId" type="number" class="w-full rounded border px-2.5 py-1.5" />
      </div>
      <div>
        <label class="block font-semibold mb-1">Thứ tự Pickup Point IDs (cách dấu phẩy)</label>
        <input v-model="pickupPointsCsv" type="text" class="w-full rounded border px-2.5 py-1.5" />
      </div>
      <button
        type="button"
        @click="handleCreateTrip"
        class="w-full py-2 rounded-lg bg-emerald-700 text-white font-semibold"
      >
        Phân Công Chuyến Xe & Gán Kiện Hàng Tự Động
      </button>

      <div v-if="createdTrip" class="mt-4 p-3 rounded bg-slate-50 border border-slate-200 space-y-1">
        <div class="font-bold">Mã chuyến vừa tạo: {{ createdTrip.trip?.tripCode }} (ID: {{ createdTrip.trip?.id }})</div>
        <div>Số điểm dừng đại lý: {{ createdTrip.stops?.length }}</div>
      </div>
    </div>
  </div>
</template>

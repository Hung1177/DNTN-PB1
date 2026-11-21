import { defineStore } from 'pinia';
import { computed, ref } from 'vue';
import httpClient, { ApiEnvelope } from '@/api/httpClient';
import type { ActivePointCycle, CatalogProductItem, PickupPointWithCycle } from '@/types/domain';
import { useDemoStore } from '@/stores/demoStore';
import { nowIsoVn } from '@/utils/formatters';

export type { ActivePointCycle, CatalogProductItem, PickupPointWithCycle };

export const useCycleStore = defineStore('cycle', () => {
  const demoStore = useDemoStore();

  const livePickupPoints = ref<PickupPointWithCycle[]>([]);
  const liveCatalogItems = ref<CatalogProductItem[]>([]);
  const selectedPointId = ref<number | null>(1);
  const serverTime = ref<string>(nowIsoVn());
  const loading = ref(false);

  const pickupPoints = computed(() =>
    demoStore.isDemoMode ? demoStore.pickupPoints : livePickupPoints.value
  );

  const selectedPoint = computed<PickupPointWithCycle | null>(() => {
    const list = pickupPoints.value;
    if (list.length === 0) return null;
    return list.find((p) => p.id === selectedPointId.value) || list[0];
  });

  const catalogItems = computed(() =>
    demoStore.isDemoMode
      ? demoStore.products.filter((p) => p.isActive)
      : liveCatalogItems.value
  );

  async function fetchPickupPoints() {
    loading.value = true;
    try {
      if (demoStore.isDemoMode) {
        serverTime.value = nowIsoVn();
        if (!selectedPointId.value && demoStore.pickupPoints.length > 0) {
          selectedPointId.value = demoStore.pickupPoints[0].id;
        }
        return;
      }
      const res = await httpClient.get<ApiEnvelope<PickupPointWithCycle[]>>('/pickup-points');
      livePickupPoints.value = res.data.data;
      serverTime.value = res.data.serverTime;
      if (!selectedPointId.value && livePickupPoints.value.length > 0) {
        await selectPickupPoint(livePickupPoints.value[0]);
      }
    } finally {
      loading.value = false;
    }
  }

  async function selectPickupPoint(point: PickupPointWithCycle) {
    selectedPointId.value = point.id;
    if (!demoStore.isDemoMode && point.activeCycle) {
      await fetchCatalogByCycle(point.activeCycle.cycleId);
    }
  }

  async function fetchCatalogByCycle(cycleId: number) {
    if (demoStore.isDemoMode) return;
    const res = await httpClient.get<ApiEnvelope<CatalogProductItem[]>>('/catalog/products', {
      params: { cycleId },
    });
    liveCatalogItems.value = res.data.data;
    serverTime.value = res.data.serverTime;
  }

  return {
    pickupPoints,
    selectedPoint,
    catalogItems,
    serverTime,
    loading,
    fetchPickupPoints,
    selectPickupPoint,
    fetchCatalogByCycle,
  };
});

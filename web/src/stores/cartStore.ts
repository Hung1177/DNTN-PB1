import { defineStore } from 'pinia';
import { computed, ref } from 'vue';
import httpClient, { ApiEnvelope } from '@/api/httpClient';
import type { CatalogProductItem } from '@/types/domain';
import { useDemoStore } from '@/stores/demoStore';
import { useAuthStore } from '@/stores/authStore';

export interface CartLine {
  variantId: number;
  productId: number;
  productName: string;
  packName: string;
  packWeightGrams: number;
  unitPrice: number;
  quantity: number;
  maxAvailable: number;
}

export interface CheckoutResponseDto {
  orderId: number;
  orderCode: string;
  txnRef: string;
  orderStatus: string;
  paymentStatus: string;
  totalAmount: number;
  reservationExpiresAt: string;
  cutoffAt: string;
  pickupStartAt: string;
  vnpayPaymentUrl: string;
}

export const useCartStore = defineStore('cart', () => {
  const demoStore = useDemoStore();
  const authStore = useAuthStore();

  const lines = ref<CartLine[]>([]);
  const pickupPointId = ref<number | null>(1);
  const cycleId = ref<number | null>(1);

  const totalPackages = computed(() =>
    lines.value.reduce((acc, item) => acc + item.quantity, 0)
  );

  const totalAmount = computed(() =>
    lines.value.reduce((acc, item) => acc + item.unitPrice * item.quantity, 0)
  );

  function bindPointAndCycle(targetPointId: number, targetCycleId: number) {
    if (
      (pickupPointId.value && pickupPointId.value !== targetPointId) ||
      (cycleId.value && cycleId.value !== targetCycleId)
    ) {
      lines.value = [];
    }
    pickupPointId.value = targetPointId;
    cycleId.value = targetCycleId;
  }

  function addFixedPackItem(
    item: Pick<
      CatalogProductItem,
      'variantId' | 'productId' | 'productName' | 'packName' | 'packWeightGrams' | 'unitPrice' | 'availableQty'
    >,
    qty = 1
  ) {
    const existing = lines.value.find((l) => l.variantId === item.variantId);
    if (existing) {
      existing.quantity = Math.min(existing.quantity + qty, item.availableQty);
      existing.maxAvailable = item.availableQty;
    } else if (item.availableQty >= qty && qty > 0) {
      lines.value.push({
        variantId: item.variantId,
        productId: item.productId,
        productName: item.productName,
        packName: item.packName,
        packWeightGrams: item.packWeightGrams,
        unitPrice: item.unitPrice,
        quantity: qty,
        maxAvailable: item.availableQty,
      });
    }
  }

  function updateQuantity(variantId: number, quantity: number) {
    const idx = lines.value.findIndex((l) => l.variantId === variantId);
    if (idx === -1) return;
    if (quantity <= 0) {
      lines.value.splice(idx, 1);
    } else {
      lines.value[idx].quantity = Math.min(quantity, lines.value[idx].maxAvailable);
    }
  }

  function clearCart() {
    lines.value = [];
  }

  async function checkoutWithVnpay(
    targetPickupPointId: number,
    targetCycleId: number
  ): Promise<CheckoutResponseDto> {
    const reqItems = lines.value.map((l) => ({
      variantId: l.variantId,
      quantity: l.quantity,
    }));

    if (demoStore.isDemoMode) {
      const customer = authStore.user || demoStore.users[0];
      const result = demoStore.checkoutOrder(customer, targetPickupPointId, targetCycleId, reqItems);
      lines.value = [];
      return result;
    }

    const res = await httpClient.post<ApiEnvelope<CheckoutResponseDto>>('/orders/checkout', {
      pickupPointId: targetPickupPointId,
      cycleId: targetCycleId,
      items: reqItems,
    });
    lines.value = [];
    return res.data.data;
  }

  return {
    lines,
    pickupPointId,
    cycleId,
    totalPackages,
    totalAmount,
    bindPointAndCycle,
    addFixedPackItem,
    updateQuantity,
    clearCart,
    checkoutWithVnpay,
  };
});

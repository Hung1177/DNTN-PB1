import { defineStore } from 'pinia';
import { ref, watch } from 'vue';
import type {
  CatalogProductItem,
  CommissionLedgerEntry,
  CommissionPolicyRecord,
  IssueRecord,
  LotRecord,
  OrderCycleConfig,
  OrderRecord,
  PackageCondition,
  PackageRecord,
  PickupPointWithCycle,
  RecipeSuggestionPlan,
  RefundRecord,
  ReturnRequestRecord,
  RoleCode,
  StorageType,
  TripRecord,
  UserAccount,
} from '@/types/domain';
import {
  SEED_COMMISSION_LEDGER,
  SEED_CYCLES,
  SEED_ISSUES,
  SEED_LOTS,
  SEED_ORDERS,
  SEED_PACKAGES,
  SEED_PICKUP_POINTS,
  SEED_POLICIES,
  SEED_PRODUCTS,
  SEED_RECIPES,
  SEED_REFUNDS,
  SEED_RETURNS,
  SEED_TRIPS,
  SEED_USERS,
} from '@/services/demoSeed';
import { formatVnd, nowIsoVn } from '@/utils/formatters';
import { useNotificationStore } from '@/stores/notificationStore';

const STORAGE_KEY = 'o2ofood_demo_state_v1';

function deepClone<T>(val: T): T {
  return JSON.parse(JSON.stringify(val));
}

export const useDemoStore = defineStore('demo', () => {
  const isDemoMode = ref<boolean>(localStorage.getItem('o2ofood_api_mode') !== 'live');

  const users = ref<UserAccount[]>(deepClone(SEED_USERS));
  const cycles = ref<OrderCycleConfig[]>(deepClone(SEED_CYCLES));
  const pickupPoints = ref<PickupPointWithCycle[]>(deepClone(SEED_PICKUP_POINTS));
  const products = ref<CatalogProductItem[]>(deepClone(SEED_PRODUCTS));
  const lots = ref<LotRecord[]>(deepClone(SEED_LOTS));
  const orders = ref<OrderRecord[]>(deepClone(SEED_ORDERS));
  const packages = ref<PackageRecord[]>(deepClone(SEED_PACKAGES));
  const trips = ref<TripRecord[]>(deepClone(SEED_TRIPS));
  const issues = ref<IssueRecord[]>(deepClone(SEED_ISSUES));
  const returnRequests = ref<ReturnRequestRecord[]>(deepClone(SEED_RETURNS));
  const refunds = ref<RefundRecord[]>(deepClone(SEED_REFUNDS));
  const commissionPolicies = ref<CommissionPolicyRecord[]>(deepClone(SEED_POLICIES));
  const commissionLedger = ref<CommissionLedgerEntry[]>(deepClone(SEED_COMMISSION_LEDGER));

  const saved = localStorage.getItem(STORAGE_KEY);
  if (saved) {
    try {
      const parsed = JSON.parse(saved);
      if (Array.isArray(parsed.cycles)) cycles.value = parsed.cycles;
      if (Array.isArray(parsed.pickupPoints)) pickupPoints.value = parsed.pickupPoints;
      if (Array.isArray(parsed.products)) products.value = parsed.products;
      if (Array.isArray(parsed.lots)) lots.value = parsed.lots;
      if (Array.isArray(parsed.orders)) orders.value = parsed.orders;
      if (Array.isArray(parsed.packages)) packages.value = parsed.packages;
      if (Array.isArray(parsed.trips)) trips.value = parsed.trips;
      if (Array.isArray(parsed.issues)) issues.value = parsed.issues;
      if (Array.isArray(parsed.returnRequests)) returnRequests.value = parsed.returnRequests;
      if (Array.isArray(parsed.refunds)) refunds.value = parsed.refunds;
      if (Array.isArray(parsed.commissionPolicies))
        commissionPolicies.value = parsed.commissionPolicies;
      if (Array.isArray(parsed.commissionLedger)) commissionLedger.value = parsed.commissionLedger;
    } catch {
      localStorage.removeItem(STORAGE_KEY);
    }
  }

  function persist() {
    localStorage.setItem(
      STORAGE_KEY,
      JSON.stringify({
        cycles: cycles.value,
        pickupPoints: pickupPoints.value,
        products: products.value,
        lots: lots.value,
        orders: orders.value,
        packages: packages.value,
        trips: trips.value,
        issues: issues.value,
        returnRequests: returnRequests.value,
        refunds: refunds.value,
        commissionPolicies: commissionPolicies.value,
        commissionLedger: commissionLedger.value,
      })
    );
  }

  watch(
    [
      cycles,
      pickupPoints,
      products,
      lots,
      orders,
      packages,
      trips,
      issues,
      returnRequests,
      refunds,
      commissionPolicies,
      commissionLedger,
    ],
    persist,
    { deep: true }
  );

  function setDemoMode(demo: boolean) {
    isDemoMode.value = demo;
    localStorage.setItem('o2ofood_api_mode', demo ? 'demo' : 'live');
  }

  function resetSeedData() {
    localStorage.removeItem(STORAGE_KEY);
    users.value = deepClone(SEED_USERS);
    cycles.value = deepClone(SEED_CYCLES);
    pickupPoints.value = deepClone(SEED_PICKUP_POINTS);
    products.value = deepClone(SEED_PRODUCTS);
    lots.value = deepClone(SEED_LOTS);
    orders.value = deepClone(SEED_ORDERS);
    packages.value = deepClone(SEED_PACKAGES);
    trips.value = deepClone(SEED_TRIPS);
    issues.value = deepClone(SEED_ISSUES);
    returnRequests.value = deepClone(SEED_RETURNS);
    refunds.value = deepClone(SEED_REFUNDS);
    commissionPolicies.value = deepClone(SEED_POLICIES);
    commissionLedger.value = deepClone(SEED_COMMISSION_LEDGER);
    useNotificationStore().pushToast(
      'Đã khôi phục dữ liệu mẫu',
      'Toàn bộ đơn hàng, lô FEFO, chuyến giao và sổ cái hoa hồng đã trở về trạng thái ban đầu.'
    );
  }

  function recordCommissionCreditOnce(order: OrderRecord, reasonNote: string) {
    const exists = commissionLedger.value.some(
      (e) => e.orderId === order.orderId && e.entryType === 'CREDIT_COMMISSION'
    );
    if (exists) return;

    const activePolicy =
      commissionPolicies.value.find((p) => p.isActive) || commissionPolicies.value[0];
    const amount =
      activePolicy.calcType === 'PERCENTAGE_NET_MERCHANDISE'
        ? Math.round((order.totalAmount * (activePolicy.percentageRate || 5)) / 100)
        : activePolicy.fixedAmount || 10000;

    commissionLedger.value.unshift({
      id: Date.now(),
      pickupPointId: order.pickupPointId,
      orderId: order.orderId,
      orderCode: order.orderCode,
      policyCode: activePolicy.policyCode,
      entryType: 'CREDIT_COMMISSION',
      amount,
      note: reasonNote,
      createdAt: nowIsoVn(),
    });
  }

  function checkoutOrder(
    customer: UserAccount,
    pickupPointId: number,
    cycleId: number,
    cartItems: Array<{ variantId: number; quantity: number }>
  ) {
    const point = pickupPoints.value.find((p) => p.id === pickupPointId);
    const cycle = cycles.value.find((c) => c.id === cycleId) || cycles.value[0];
    if (!point || !cycle) {
      throw new Error('Điểm nhận hàng hoặc đợt giao không hợp lệ.');
    }

    const sorted = [...cartItems].sort((a, b) => a.variantId - b.variantId);
    for (const req of sorted) {
      const prod = products.value.find((p) => p.variantId === req.variantId);
      if (!prod || !prod.isActive || prod.availableQty < req.quantity) {
        throw new Error(
          `Không đủ tồn kho khả dụng cho "${prod?.productName || 'sản phẩm'}" trong đợt này.`
        );
      }
    }

    const orderItems = sorted.map((req, idx) => {
      const prod = products.value.find((p) => p.variantId === req.variantId)!;
      prod.availableQty -= req.quantity;
      prod.reservedQty += req.quantity;
      prod.inStock = prod.availableQty > 0;
      return {
        id: Date.now() + idx,
        variantId: prod.variantId,
        productName: prod.productName,
        packName: prod.packName,
        storageType: prod.storageType,
        unitPrice: prod.unitPrice,
        quantity: req.quantity,
        lineTotal: prod.unitPrice * req.quantity,
      };
    });

    const totalAmount = orderItems.reduce((sum, i) => sum + i.lineTotal, 0);
    const nextOrderId =
      orders.value.reduce((max, o) => Math.max(max, o.orderId), 100) + 1;
    const seq = String(nextOrderId).padStart(3, '0');
    const orderCode = `ORD-20261121-${seq}`;
    const txnRef = `VNP20261121${seq}`;
    const qrToken = `PKP-${point.code.split('-').pop()}-${Math.random()
      .toString(36)
      .slice(2, 8)
      .toUpperCase()}`;

    const expiresDate = new Date(Date.now() + cycle.paymentTimeoutMinutes * 60000);
    const cutoffDate = new Date(cycle.cutoffAt);
    const actualExpiry = expiresDate < cutoffDate ? expiresDate : cutoffDate;

    const newOrder: OrderRecord = {
      orderId: nextOrderId,
      orderCode,
      customerId: customer.userId,
      customerName: customer.fullName,
      customerPhone: customer.phone,
      pickupPointId: point.id,
      pickupPointName: point.name,
      cycleId: cycle.id,
      cycleCode: cycle.cycleCode,
      orderStatus: 'PENDING_PAYMENT',
      fulfillmentStatus: 'WAITING_PACKING',
      paymentStatus: 'UNPAID',
      completionType: null,
      totalAmount,
      cutoffAt: cycle.cutoffAt,
      pickupStartAt: cycle.pickupStartAt,
      autoCompleteAt: cycle.autoCompleteAt,
      reservationExpiresAt: actualExpiry.toISOString(),
      txnRef,
      pickupQrToken: qrToken,
      pickupQrUsedAt: null,
      physicalHandedOverAt: null,
      customerConfirmedAt: null,
      hasOpenIssue: false,
      isUnclaimedAtAgent: false,
      createdAt: nowIsoVn(),
      items: orderItems,
    };

    orders.value.unshift(newOrder);
    if (point.activeCycle) {
      point.activeCycle.currentOrders += 1;
    }

    useNotificationStore().pushToast(
      `Đã tạo đơn ${orderCode}`,
      `Giữ tồn kho tối đa 30 phút. Vui lòng hoàn tất thanh toán VNPAY ${formatVnd(totalAmount)}.`
    );

    return {
      orderId: newOrder.orderId,
      orderCode: newOrder.orderCode,
      txnRef,
      orderStatus: newOrder.orderStatus,
      paymentStatus: newOrder.paymentStatus,
      totalAmount: newOrder.totalAmount,
      reservationExpiresAt: newOrder.reservationExpiresAt || '',
      cutoffAt: newOrder.cutoffAt,
      pickupStartAt: newOrder.pickupStartAt,
      vnpayPaymentUrl: `/payment/vnpay-return?vnp_TxnRef=${txnRef}&vnp_Amount=${
        totalAmount * 100
      }&vnp_ResponseCode=00&orderId=${newOrder.orderId}`,
    };
  }

  function simulateVnpayIpn(
    orderId: number,
    scenario: 'SUCCESS' | 'DUPLICATE' | 'LATE_PAYMENT'
  ): { order: OrderRecord; message: string } {
    const order = orders.value.find((o) => o.orderId === orderId);
    if (!order) throw new Error('Không tìm thấy đơn hàng.');

    if (scenario === 'DUPLICATE' && order.paymentStatus === 'PAID') {
      return {
        order,
        message:
          'IPN trùng lặp đã được phát hiện và bỏ qua an toàn (RspCode 00). Tồn kho không bị trừ hai lần.',
      };
    }

    if (scenario === 'LATE_PAYMENT') {
      order.orderStatus = 'CANCELLED';
      order.paymentStatus = 'LATE_RECONCILIATION';
      for (const item of order.items) {
        const prod = products.value.find((p) => p.variantId === item.variantId);
        if (prod && prod.reservedQty >= item.quantity) {
          prod.reservedQty -= item.quantity;
          prod.availableQty += item.quantity;
          prod.inStock = prod.availableQty > 0;
        }
      }
      return {
        order,
        message:
          'Giao dịch VNPAY đến muộn sau thời hạn giữ tồn 30 phút hoặc sau 23:59:59. Đơn chuyển sang đối soát thanh toán muộn.',
      };
    }

    if (order.orderStatus === 'PENDING_PAYMENT') {
      order.orderStatus = 'PAID_WAITING_CUTOFF';
      order.paymentStatus = 'PAID';
      for (const item of order.items) {
        const prod = products.value.find((p) => p.variantId === item.variantId);
        if (prod) {
          prod.reservedQty = Math.max(0, prod.reservedQty - item.quantity);
          prod.committedQty += item.quantity;
        }
      }
      useNotificationStore().pushToast(
        `Thanh toán VNPAY thành công — ${order.orderCode}`,
        'Đơn hàng đã vào đợt chờ chốt 23:59:59. Bạn có thể tự hủy hoàn tiền 100% trước giờ chốt.'
      );
      return {
        order,
        message:
          'Xác thực chữ ký HMAC-SHA512 thành công qua IPN. Đơn hàng đã chuyển sang trạng thái Đã thanh toán chờ chốt 23:59.',
      };
    }

    return {
      order,
      message: 'Đơn hàng đã ở trạng thái thanh toán hợp lệ.',
    };
  }

  function resolveLatePayment(orderId: number, action: 'ROLLOVER' | 'REFUND'): string {
    const order = orders.value.find((o) => o.orderId === orderId);
    if (!order) throw new Error('Không tìm thấy đơn hàng.');

    if (action === 'ROLLOVER') {
      const nextCycle = cycles.value[1] || cycles.value[0];
      order.cycleId = nextCycle.id;
      order.cycleCode = nextCycle.cycleCode;
      order.cutoffAt = nextCycle.cutoffAt;
      order.pickupStartAt = nextCycle.pickupStartAt;
      order.autoCompleteAt = nextCycle.autoCompleteAt;
      order.orderStatus = 'PAID_WAITING_CUTOFF';
      order.paymentStatus = 'PAID';
      return `Đã chuyển đơn ${order.orderCode} sang đợt giao kế tiếp (${nextCycle.cycleCode} · Nhận từ 16:00 ngày ${nextCycle.deliveryDate}).`;
    }

    order.paymentStatus = 'REFUND_APPROVED';
    refunds.value.unshift({
      id: Date.now(),
      orderId: order.orderId,
      orderCode: order.orderCode,
      customerName: order.customerName,
      pickupPointId: order.pickupPointId,
      returnRequestId: null,
      refundReason: 'LATE_PAYMENT_REFUND',
      amount: order.totalAmount,
      status: 'APPROVED',
      providerRef: `VNP-REF-${order.orderId}`,
      approvedByName: 'Hệ thống tự động',
      completedAt: null,
      createdAt: nowIsoVn(),
    });
    return `Đã khởi tạo lệnh hoàn tiền 100% (${formatVnd(order.totalAmount)}) cho giao dịch đến muộn.`;
  }

  function cancelOrderBeforeCutoff(orderId: number, reason: string): string {
    const order = orders.value.find((o) => o.orderId === orderId);
    if (!order) throw new Error('Không tìm thấy đơn hàng.');

    if (order.orderStatus !== 'PENDING_PAYMENT' && order.orderStatus !== 'PAID_WAITING_CUTOFF') {
      throw new Error('Đơn hàng đã qua giờ chốt 00:00 nên không thể tự hủy.');
    }

    const wasPaid = order.orderStatus === 'PAID_WAITING_CUTOFF';
    order.orderStatus = 'CANCELLED';

    for (const item of order.items) {
      const prod = products.value.find((p) => p.variantId === item.variantId);
      if (prod) {
        if (wasPaid) {
          prod.committedQty = Math.max(0, prod.committedQty - item.quantity);
        } else {
          prod.reservedQty = Math.max(0, prod.reservedQty - item.quantity);
        }
        prod.availableQty += item.quantity;
        prod.inStock = prod.availableQty > 0;
      }
    }

    if (wasPaid) {
      order.paymentStatus = 'REFUND_APPROVED';
      refunds.value.unshift({
        id: Date.now(),
        orderId: order.orderId,
        orderCode: order.orderCode,
        customerName: order.customerName,
        pickupPointId: order.pickupPointId,
        returnRequestId: null,
        refundReason: 'CUSTOMER_CANCEL_BEFORE_CUTOFF',
        amount: order.totalAmount,
        status: 'APPROVED',
        providerRef: null,
        approvedByName: 'Tự duyệt theo chính sách trước 23:59',
        completedAt: null,
        createdAt: nowIsoVn(),
      });
      useNotificationStore().pushToast(
        `Đã hủy đơn ${order.orderCode}`,
        `Hoàn trả tồn kho và tạo lệnh hoàn tiền 100% (${formatVnd(order.totalAmount)}). Lý do: ${reason}`
      );
      return `Đã hủy đơn ${order.orderCode} trước giờ chốt, giải phóng tồn kho và tạo lệnh hoàn tiền 100% (${formatVnd(order.totalAmount)}).`;
    }

    return `Đã hủy đơn chưa thanh toán ${order.orderCode} và hoàn trả tồn kho ngay lập tức.`;
  }

  function customerConfirmReceived(orderId: number): string {
    const order = orders.value.find((o) => o.orderId === orderId);
    if (!order) throw new Error('Không tìm thấy đơn hàng.');

    order.customerConfirmedAt = nowIsoVn();
    order.orderStatus = 'COMPLETED';
    order.completionType = 'CUSTOMER_CONFIRMED';
    order.isUnclaimedAtAgent = false;

    if (!order.physicalHandedOverAt) {
      order.auditAlert =
        'Khách đã xác nhận nhận hàng nhưng đại lý chưa quét mã QR bàn giao vật lý — cần đối chiếu.';
    }

    recordCommissionCreditOnce(
      order,
      `Hoa hồng ghi nhận khi khách xác nhận đã nhận hàng (${order.orderCode})`
    );
    useNotificationStore().pushToast(
      `Đơn ${order.orderCode} đã hoàn tất`,
      'Cảm ơn bạn đã xác nhận. Hoa hồng đại lý đã được ghi nhận vào sổ cái.'
    );
    return order.physicalHandedOverAt
      ? `Đơn ${order.orderCode} hoàn tất (Khách xác nhận + Đại lý đã quét bàn giao).`
      : `Đã ghi nhận xác nhận của khách. Hệ thống đồng thời tạo cảnh báo đối chiếu vì đại lý chưa quét QR bàn giao.`;
  }

  function reportIssue(
    orderId: number,
    reporterName: string,
    reporterRole: RoleCode,
    issueType: IssueRecord['issueType'],
    description: string,
    evidenceUrl: string
  ): string {
    const order = orders.value.find((o) => o.orderId === orderId);
    if (!order) throw new Error('Không tìm thấy đơn hàng.');

    order.hasOpenIssue = true;
    order.fulfillmentStatus = 'ISSUE_HOLD';
    order.paymentStatus = 'REFUND_REQUESTED';

    const issueId = issues.value.reduce((m, i) => Math.max(m, i.id), 0) + 1;
    issues.value.unshift({
      id: issueId,
      orderId: order.orderId,
      orderCode: order.orderCode,
      pickupPointId: order.pickupPointId,
      pickupPointName: order.pickupPointName,
      reportedByName: reporterName,
      reporterRole,
      issueType,
      description,
      evidenceUrl: evidenceUrl || 'https://img.o2ofood.vn/evidence-upload.jpg',
      status: 'OPEN',
      createdAt: nowIsoVn(),
    });

    const returnId = returnRequests.value.reduce((m, r) => Math.max(m, r.id), 0) + 1;
    returnRequests.value.unshift({
      id: returnId,
      issueId,
      orderId: order.orderId,
      orderCode: order.orderCode,
      pickupPointId: order.pickupPointId,
      pickupPointName: order.pickupPointName,
      returnTripCode: null,
      assignedDriverId: null,
      assignedDriverName: null,
      status: 'REQUESTED',
      warehouseInspectionNote: null,
      createdAt: nowIsoVn(),
    });

    refunds.value.unshift({
      id: refunds.value.reduce((m, r) => Math.max(m, r.id), 0) + 1,
      orderId: order.orderId,
      orderCode: order.orderCode,
      customerName: order.customerName,
      pickupPointId: order.pickupPointId,
      returnRequestId: returnId,
      refundReason: 'ORDER_ISSUE_RETURN',
      amount: order.totalAmount,
      status: 'PENDING_APPROVAL',
      providerRef: null,
      approvedByName: null,
      completedAt: null,
      createdAt: nowIsoVn(),
    });

    useNotificationStore().pushToast(
      `Đã ghi nhận sự cố đơn ${order.orderCode}`,
      'Đơn hàng đã khóa cờ sự cố (chặn tự hoàn tất 00:00 D+2) và chuyển Điều phối tạo chuyến thu hồi.'
    );

    return `Đã gửi khiếu nại #${issueId} cho đơn ${order.orderCode}. Tác vụ tự hoàn tất lúc 00:00 D+2 đã bị chặn tự động.`;
  }

  function inboundLot(input: {
    variantId: number;
    lotCode: string;
    supplierName: string;
    manufacturedDate: string;
    expiryDate: string;
    receivedQty: number;
  }): string {
    const prod = products.value.find((p) => p.variantId === input.variantId);
    if (!prod) throw new Error('Không tìm thấy quy cách sản phẩm.');

    const expDate = new Date(input.expiryDate);
    const minSafe = new Date('2026-11-22');
    minSafe.setDate(minSafe.getDate() + prod.minShelfLifeDaysAtPickup);
    if (expDate < minSafe) {
      throw new Error(
        `Hạn dùng ${input.expiryDate} không đạt chuẩn an toàn tối thiểu (+${prod.minShelfLifeDaysAtPickup} ngày kể từ ngày nhận 22/11/2026).`
      );
    }

    const nextId = lots.value.reduce((m, l) => Math.max(m, l.id), 0) + 1;
    lots.value.unshift({
      id: nextId,
      variantId: prod.variantId,
      productName: prod.productName,
      packName: prod.packName,
      storageType: prod.storageType,
      lotCode: input.lotCode.trim().toUpperCase(),
      supplierName: input.supplierName.trim(),
      manufacturedDate: input.manufacturedDate,
      expiryDate: input.expiryDate,
      initialQty: input.receivedQty,
      remainingQty: input.receivedQty,
      status: 'AVAILABLE',
      receivedAt: nowIsoVn(),
    });

    prod.availableQty += input.receivedQty;
    prod.inStock = prod.availableQty > 0;

    return `Đã nhập lô ${input.lotCode} (+${input.receivedQty} ${prod.packName}) cho ${prod.productName} và cập nhật tồn kho khả dụng.`;
  }

  function toggleLotQuarantine(lotId: number): string {
    const lot = lots.value.find((l) => l.id === lotId);
    if (!lot) throw new Error('Không tìm thấy lô hàng.');
    if (lot.status === 'QUARANTINED') {
      lot.status = 'AVAILABLE';
      return `Đã mở khóa lô ${lot.lotCode} trở lại trạng thái khả dụng FEFO.`;
    }
    lot.status = 'QUARANTINED';
    return `Đã cách ly lô ${lot.lotCode} (QUARANTINED). Thuật toán FEFO sẽ tự động bỏ qua lô này khi phân bổ.`;
  }

  function packOrderWithFefo(orderId: number): {
    order: OrderRecord;
    createdPackages: PackageRecord[];
    allocations: Array<{ productName: string; lotCode: string; expiryDate: string; qty: number }>;
  } {
    const order = orders.value.find((o) => o.orderId === orderId);
    if (!order) throw new Error('Không tìm thấy đơn hàng.');
    if (order.fulfillmentStatus !== 'WAITING_PACKING') {
      throw new Error(`Đơn ${order.orderCode} không ở trạng thái chờ đóng gói.`);
    }

    const allocations: Array<{
      productName: string;
      lotCode: string;
      expiryDate: string;
      qty: number;
    }> = [];

    for (const item of order.items) {
      const candidateLots = lots.value
        .filter((l) => l.variantId === item.variantId && l.status === 'AVAILABLE' && l.remainingQty > 0)
        .sort((a, b) => a.expiryDate.localeCompare(b.expiryDate));

      let remaining = item.quantity;
      const chosenCodes: string[] = [];
      for (const lot of candidateLots) {
        if (remaining <= 0) break;
        const take = Math.min(lot.remainingQty, remaining);
        lot.remainingQty -= take;
        remaining -= take;
        chosenCodes.push(lot.lotCode);
        allocations.push({
          productName: item.productName,
          lotCode: lot.lotCode,
          expiryDate: lot.expiryDate,
          qty: take,
        });
      }
      item.allocatedLotCode = chosenCodes.join(', ') || 'FEFO-AUTO';
    }

    const storageGroups = Array.from(new Set(order.items.map((i) => i.storageType)));
    const nextPkgId = packages.value.reduce((m, p) => Math.max(m, p.id), 0) + 1;
    const createdPackages: PackageRecord[] = storageGroups.map((st, idx) => ({
      id: nextPkgId + idx,
      orderId: order.orderId,
      orderCode: order.orderCode,
      pickupPointId: order.pickupPointId,
      tripStopId: null,
      packageCode: `PKG-${order.orderId}-${idx + 1}OF${storageGroups.length}`,
      qrPayload: `O2O-PKG:${order.orderId}:${idx + 1}:${st}`,
      packageSeq: idx + 1,
      totalPackagesInOrder: storageGroups.length,
      storageType: st,
      status: 'PACKED',
    }));

    packages.value.unshift(...createdPackages);
    order.orderStatus = 'PROCESSING';
    order.fulfillmentStatus = 'PACKED';

    useNotificationStore().pushToast(
      `Đã đóng gói FEFO đơn ${order.orderCode}`,
      `Sinh ${createdPackages.length} tem QR kiện hàng sẵn sàng bàn giao tài xế lúc 08:00.`
    );

    return { order, createdPackages, allocations };
  }

  function receiveReturnToQuarantine(returnId: number, inspectionNote: string): string {
    const ret = returnRequests.value.find((r) => r.id === returnId);
    if (!ret) throw new Error('Không tìm thấy phiếu thu hồi.');

    ret.status = 'WAREHOUSE_RECEIVED';
    ret.warehouseInspectionNote =
      inspectionNote || 'Đã nhập khu cách ly hàng lỗi (QUARANTINE_DAMAGED_STOCK). Không cộng ngược vào tồn thương mại.';

    const issue = issues.value.find((i) => i.id === ret.issueId);
    if (issue) issue.status = 'INVESTIGATING';

    useNotificationStore().pushToast(
      `Kho đã nhận hàng hoàn #${ret.id}`,
      `Đơn ${ret.orderCode} đã nhập khu cách ly hàng lỗi — mở khóa bước Quản trị duyệt hoàn tiền.`
    );
    return `Đã xác nhận nhập khu cách ly cho phiếu thu hồi #${ret.id} (${ret.orderCode}). Không cộng ngược tồn bán.`;
  }

  function createDeliveryTrip(input: {
    cycleId: number;
    driverId: number;
    pickupPointIds: number[];
  }): TripRecord {
    const cycle = cycles.value.find((c) => c.id === input.cycleId) || cycles.value[0];
    const driver = users.value.find((u) => u.userId === input.driverId);
    if (!driver) throw new Error('Vui lòng chọn tài xế hợp lệ.');
    if (input.pickupPointIds.length === 0) throw new Error('Vui lòng chọn ít nhất 1 điểm dừng đại lý.');

    const nextTripId = trips.value.reduce((m, t) => Math.max(m, t.id), 0) + 1;
    const baseStopId =
      trips.value.flatMap((t) => t.stops).reduce((m, s) => Math.max(m, s.id), 0) + 1;

    const stops = input.pickupPointIds.map((ppId, index) => {
      const pp = pickupPoints.value.find((p) => p.id === ppId)!;
      const etaHour = 9 + index;
      return {
        id: baseStopId + index,
        tripId: nextTripId,
        pickupPointId: pp.id,
        pickupPointName: pp.name,
        pickupPointAddress: pp.address,
        stopSequence: index + 1,
        etaAt: `${cycle.deliveryDate}T${String(etaHour).padStart(2, '0')}:15:00+07:00`,
        arrivedAt: null,
        handoverCompletedAt: null,
        status: 'PENDING' as const,
      };
    });

    const newTrip: TripRecord = {
      id: nextTripId,
      tripCode: `TRIP-${cycle.deliveryDate.replace(/-/g, '')}-${String(nextTripId).padStart(2, '0')}`,
      cycleId: cycle.id,
      cycleCode: cycle.cycleCode,
      driverId: driver.userId,
      driverName: driver.fullName,
      driverVehicle: driver.vehicleInfo || 'Xe tải lạnh tiêu chuẩn',
      tripType: 'DELIVERY',
      status: 'ASSIGNED',
      departedAt: null,
      completedAt: null,
      warehouseHandoverSigned: false,
      stops,
    };

    for (const stop of stops) {
      for (const pkg of packages.value) {
        if (pkg.pickupPointId === stop.pickupPointId && pkg.status === 'PACKED' && !pkg.tripStopId) {
          pkg.tripStopId = stop.id;
        }
      }
    }

    trips.value.unshift(newTrip);
    useNotificationStore().pushToast(
      `Đã lập chuyến ${newTrip.tripCode}`,
      `Phân công tài xế ${driver.fullName} đi qua ${stops.length} điểm đại lý.`
    );
    return newTrip;
  }

  function dispatchReturnTrip(returnId: number, driverId: number): string {
    const ret = returnRequests.value.find((r) => r.id === returnId);
    const driver = users.value.find((u) => u.userId === driverId);
    if (!ret || !driver) throw new Error('Phiếu thu hồi hoặc tài xế không hợp lệ.');

    ret.assignedDriverId = driver.userId;
    ret.assignedDriverName = driver.fullName;
    ret.returnTripCode = `RET-20261121-${String(ret.id).padStart(2, '0')}`;
    ret.status = 'APPROVED_FOR_PICKUP';

    return `Đã điều phối tài xế ${driver.fullName} thu hồi hàng lỗi ${ret.orderCode} (${ret.returnTripCode}).`;
  }

  function confirmWarehousePickup(tripId: number, note: string): string {
    const trip = trips.value.find((t) => t.id === tripId);
    if (!trip) throw new Error('Không tìm thấy chuyến giao.');

    trip.warehouseHandoverSigned = true;
    trip.warehouseHandoverNote = note || 'Đã kiểm đếm đủ kiện tại kho trung tâm';
    trip.status = 'IN_TRANSIT';
    trip.departedAt = nowIsoVn();

    const stopIds = new Set(trip.stops.map((s) => s.id));
    let count = 0;
    for (const pkg of packages.value) {
      if (pkg.tripStopId && stopIds.has(pkg.tripStopId) && pkg.status === 'PACKED') {
        pkg.status = 'HANDED_TO_DRIVER';
        count += 1;
        const ord = orders.value.find((o) => o.orderId === pkg.orderId);
        if (ord) ord.fulfillmentStatus = 'IN_TRANSIT';
      }
    }

    useNotificationStore().pushToast(
      `Xuất kho chuyến ${trip.tripCode}`,
      `Đã ký biên bản Kho → Tài xế (${count} kiện). Chuyến xe đang trên đường giao.`
    );
    return `Đã ký biên bản nhận hàng từ kho cho chuyến ${trip.tripCode} (${count} kiện chuyển sang Đang vận chuyển).`;
  }

  function markDriverArrivedAtStop(tripId: number, stopId: number): string {
    const trip = trips.value.find((t) => t.id === tripId);
    const stop = trip?.stops.find((s) => s.id === stopId);
    if (!trip || !stop) throw new Error('Không tìm thấy điểm dừng.');

    stop.status = 'ARRIVED_AT_POINT';
    stop.arrivedAt = nowIsoVn();

    for (const pkg of packages.value) {
      if (pkg.tripStopId === stop.id) {
        const ord = orders.value.find((o) => o.orderId === pkg.orderId);
        if (ord && ord.fulfillmentStatus === 'IN_TRANSIT') {
          ord.fulfillmentStatus = 'AGENT_CHECKING';
        }
      }
    }

    return `Đã cập nhật đến điểm dừng ${stop.pickupPointName}. Sẵn sàng kiểm nhận với Đại lý.`;
  }

  function driverPickupReturnFromAgent(returnId: number): string {
    const ret = returnRequests.value.find((r) => r.id === returnId);
    if (!ret) throw new Error('Không tìm thấy phiếu thu hồi.');
    ret.status = 'DRIVER_PICKED_UP';
    return `Tài xế đã nhận kiện hàng lỗi đơn ${ret.orderCode} từ Đại lý để chở về khu cách ly kho trung tâm.`;
  }

  function completeAgentHandover(
    stopId: number,
    inspections: Array<{ packageId: number; condition: PackageCondition }>,
    note: string
  ): { readyOrderCodes: string[]; issueOrderCodes: string[] } {
    const targetTrip = trips.value.find((t) => t.stops.some((s) => s.id === stopId));
    const stop = targetTrip?.stops.find((s) => s.id === stopId);
    if (!stop || !targetTrip) throw new Error('Không tìm thấy điểm dừng cần kiểm nhận.');

    const affectedOrderIds = new Set<number>();

    for (const insp of inspections) {
      const pkg = packages.value.find((p) => p.id === insp.packageId);
      if (!pkg) continue;
      affectedOrderIds.add(pkg.orderId);
      if (insp.condition === 'ACCEPTED') pkg.status = 'DELIVERED_TO_AGENT';
      else if (insp.condition === 'DAMAGED') pkg.status = 'DAMAGED';
      else pkg.status = 'MISSING';
    }

    const readyOrderCodes: string[] = [];
    const issueOrderCodes: string[] = [];

    for (const ordId of affectedOrderIds) {
      const ord = orders.value.find((o) => o.orderId === ordId);
      if (!ord) continue;
      const ordPkgs = packages.value.filter((p) => p.orderId === ordId);
      const hasProblem = ordPkgs.some((p) => p.status === 'DAMAGED' || p.status === 'MISSING');

      if (hasProblem) {
        ord.fulfillmentStatus = 'ISSUE_HOLD';
        ord.hasOpenIssue = true;
        issueOrderCodes.push(ord.orderCode);
        const problemPkg = ordPkgs.find((p) => p.status === 'DAMAGED' || p.status === 'MISSING')!;
        reportIssue(
          ord.orderId,
          'Đại lý điểm nhận',
          'ROLE_AGENT',
          problemPkg.status === 'DAMAGED' ? 'DAMAGED' : 'MISSING_ITEM',
          `Phát hiện kiện ${problemPkg.packageCode} bị ${
            problemPkg.status === 'DAMAGED' ? 'dập hỏng' : 'giao thiếu'
          } khi kiểm nhận với tài xế. Ghi chú: ${note || 'Không có'}.`,
          'https://img.o2ofood.vn/agent-handover-check.jpg'
        );
      } else {
        ord.fulfillmentStatus = 'READY_FOR_PICKUP';
        readyOrderCodes.push(ord.orderCode);
        useNotificationStore().pushToast(
          `Đơn ${ord.orderCode} đã sẵn sàng tại đại lý`,
          `Đại lý ${ord.pickupPointName} đã kiểm nhận đủ ${ordPkgs.length} kiện. Mời khách mang mã QR đến nhận từ 16:00.`
        );
      }
    }

    stop.status = 'HANDED_OVER';
    stop.handoverCompletedAt = nowIsoVn();

    if (targetTrip.stops.every((s) => s.status === 'HANDED_OVER')) {
      targetTrip.status = 'COMPLETED';
      targetTrip.completedAt = nowIsoVn();
    }

    return { readyOrderCodes, issueOrderCodes };
  }

  function agentScanCustomerQr(orderId: number, qrToken: string): string {
    const order = orders.value.find((o) => o.orderId === orderId);
    if (!order) throw new Error('Không tìm thấy đơn hàng.');
    if (order.pickupQrUsedAt) {
      throw new Error('Mã QR nhận hàng dùng 1 lần này đã được sử dụng trước đó.');
    }
    if (order.pickupQrToken.trim().toUpperCase() !== qrToken.trim().toUpperCase()) {
      throw new Error(`Mã QR không khớp (Mã hợp lệ của đơn này là ${order.pickupQrToken}).`);
    }

    const now = nowIsoVn();
    order.pickupQrUsedAt = now;
    order.physicalHandedOverAt = now;
    order.fulfillmentStatus = 'HANDED_OVER';
    order.isUnclaimedAtAgent = false;
    order.auditAlert = undefined;

    for (const pkg of packages.value) {
      if (pkg.orderId === order.orderId) pkg.status = 'HANDED_TO_CUSTOMER';
    }

    useNotificationStore().pushToast(
      `Đã bàn giao vật lý đơn ${order.orderCode}`,
      'Đại lý đã quét QR thành công. Khách hàng có thể bấm "Đã nhận hàng" trên ứng dụng.'
    );

    return `Quét QR hợp lệ. Đã ghi nhận bàn giao vật lý cho đơn ${order.orderCode} lúc ${now}.`;
  }

  function updateUnclaimedNote(orderId: number, actionNote: string): string {
    const order = orders.value.find((o) => o.orderId === orderId);
    if (!order) throw new Error('Không tìm thấy đơn hàng.');
    order.unclaimedContactNote = `${actionNote} (${nowIsoVn().slice(11, 16)})`;
    return `Đã lưu nhật ký xử lý hàng tồn chưa lấy cho đơn ${order.orderCode}.`;
  }

  function approveRefund(refundId: number, adminName: string): string {
    const refund = refunds.value.find((r) => r.id === refundId);
    if (!refund) throw new Error('Không tìm thấy yêu cầu hoàn tiền.');
    if (refund.status === 'COMPLETED') {
      throw new Error('Yêu cầu hoàn tiền này đã được xử lý xong trước đó.');
    }

    if (refund.returnRequestId) {
      const ret = returnRequests.value.find((r) => r.id === refund.returnRequestId);
      if (
        ret &&
        ret.status !== 'WAREHOUSE_RECEIVED' &&
        ret.status !== 'UNRECOVERABLE_WAIVED'
      ) {
        throw new Error(
          'Chưa thể duyệt hoàn tiền: Kho trung tâm chưa kiểm nhận hàng hoàn vào khu cách ly (hoặc chưa được miễn thu hồi đặc cách).'
        );
      }
      if (ret) ret.status = 'CLOSED';
      const issue = issues.value.find((i) => i.id === ret?.issueId);
      if (issue) issue.status = 'RESOLVED';
    }

    refund.status = 'COMPLETED';
    refund.approvedByName = adminName;
    refund.providerRef = `VNP-REF-${Date.now().toString().slice(-6)}`;
    refund.completedAt = nowIsoVn();

    const order = orders.value.find((o) => o.orderId === refund.orderId);
    if (order) {
      order.paymentStatus = 'REFUNDED';
      order.hasOpenIssue = false;
    }

    const priorCredit = commissionLedger.value.find(
      (e) => e.orderId === refund.orderId && e.entryType === 'CREDIT_COMMISSION'
    );
    const alreadyReversed = commissionLedger.value.some(
      (e) => e.orderId === refund.orderId && e.entryType === 'DEBIT_REFUND_REVERSAL'
    );
    let reversalNote = '';
    if (priorCredit && !alreadyReversed) {
      commissionLedger.value.unshift({
        id: Date.now(),
        pickupPointId: priorCredit.pickupPointId,
        orderId: priorCredit.orderId,
        orderCode: priorCredit.orderCode,
        policyCode: priorCredit.policyCode,
        entryType: 'DEBIT_REFUND_REVERSAL',
        amount: -Math.abs(priorCredit.amount),
        note: `Thu hồi tự động hoa hồng do duyệt hoàn tiền #${refund.id} (${refund.providerRef})`,
        createdAt: nowIsoVn(),
      });
      reversalNote = ` Đồng thời ghi bút toán âm ${formatVnd(-Math.abs(priorCredit.amount))} vào sổ cái hoa hồng.`;
    }

    useNotificationStore().pushToast(
      `Đã duyệt hoàn tiền ${refund.orderCode}`,
      `Số tiền ${formatVnd(refund.amount)} (${refund.providerRef}).${reversalNote}`
    );

    return `Phê duyệt hoàn tiền thành công (${refund.providerRef}).${reversalNote}`;
  }

  function waiveUnrecoverableReturn(returnId: number): string {
    const ret = returnRequests.value.find((r) => r.id === returnId);
    if (!ret) throw new Error('Không tìm thấy phiếu thu hồi.');
    ret.status = 'UNRECOVERABLE_WAIVED';
    ret.warehouseInspectionNote =
      'Quản trị duyệt đặc cách: thực phẩm hư hỏng nặng không thể thu hồi về kho.';
    return `Đã duyệt đặc cách miễn thu hồi cho phiếu #${ret.id} (${ret.orderCode}). Bây giờ có thể phê duyệt hoàn tiền.`;
  }

  function addCatalogProduct(input: {
    productName: string;
    category: string;
    storageType: StorageType;
    packName: string;
    packWeightGrams: number;
    unitPrice: number;
    availableQty: number;
    minShelfLifeDaysAtPickup: number;
  }): string {
    const nextId = products.value.reduce((m, p) => Math.max(m, p.productId), 0) + 1;
    const sku = `SKU-${nextId}-${input.packWeightGrams}G`;
    products.value.unshift({
      productId: nextId,
      variantId: nextId,
      sku,
      productName: input.productName.trim(),
      category: input.category,
      storageType: input.storageType,
      packName: input.packName.trim(),
      packWeightGrams: input.packWeightGrams,
      unitPrice: input.unitPrice,
      availableQty: input.availableQty,
      reservedQty: 0,
      committedQty: 0,
      inStock: input.availableQty > 0,
      minShelfLifeDaysAtPickup: input.minShelfLifeDaysAtPickup,
      isActive: true,
    });
    return `Đã thêm sản phẩm đóng gói cố định "${input.productName} (${input.packName})" giá ${formatVnd(input.unitPrice)}.`;
  }

  function createOrderCycle(deliveryDate: string, paymentTimeoutMinutes: number): string {
    const d = new Date(`${deliveryDate}T00:00:00+07:00`);
    const prev = new Date(d.getTime() - 86400000);
    const next = new Date(d.getTime() + 86400000);
    const fmt = (dt: Date) => dt.toISOString().slice(0, 10);

    const prevStr = fmt(prev);
    const nextStr = fmt(next);
    const nextId = cycles.value.reduce((m, c) => Math.max(m, c.id), 0) + 1;
    const cycleCode = `CYCLE-${deliveryDate}`;

    cycles.value.unshift({
      id: nextId,
      cycleCode,
      deliveryDate,
      openAt: `${prevStr}T00:00:00+07:00`,
      cutoffAt: `${prevStr}T23:59:59+07:00`,
      autoConfirmAt: `${deliveryDate}T00:00:00+07:00`,
      warehousePackTargetAt: `${deliveryDate}T08:00:00+07:00`,
      dispatchStartAt: `${deliveryDate}T08:00:00+07:00`,
      pickupStartAt: `${deliveryDate}T16:00:00+07:00`,
      autoCompleteAt: `${nextStr}T00:00:00+07:00`,
      paymentTimeoutMinutes,
      status: 'OPEN',
    });
    return `Đã mở đợt giao hàng mới ${cycleCode} (chốt 23:59 ngày ${prevStr}, nhận từ 16:00 ngày ${deliveryDate}).`;
  }

  function activateCommissionPolicy(policyId: number): string {
    for (const p of commissionPolicies.value) {
      p.isActive = p.id === policyId;
    }
    const active = commissionPolicies.value.find((p) => p.id === policyId);
    return `Đã áp dụng chính sách hoa hồng ${active?.policyCode} cho các đơn hàng mới.`;
  }

  function askAiAssistant(
    message: string,
    authenticatedUser: UserAccount | null
  ): {
    intentType: string;
    executedTool: string | null;
    replyMarkdown: string;
    denied?: boolean;
  } {
    const q = message.toLowerCase();
    const orderCodeMatch = message.match(/ORD-\d{8}-\d{3}/i);

    if (orderCodeMatch || q.includes('đơn hàng') || q.includes('đơn của tôi') || q.includes('tra cứu')) {
      if (!authenticatedUser) {
        return {
          intentType: 'ORDER_LOOKUP',
          executedTool: 'getCustomerOrders',
          denied: true,
          replyMarkdown:
            'Bạn cần đăng nhập để tra cứu trạng thái đơn hàng cá nhân. Hệ thống chỉ trả dữ liệu thuộc đúng tài khoản đang xác thực.',
        };
      }

      if (orderCodeMatch) {
        const code = orderCodeMatch[0].toUpperCase();
        const found = orders.value.find((o) => o.orderCode.toUpperCase() === code);
        if (!found) {
          return {
            intentType: 'ORDER_LOOKUP',
            executedTool: 'getOrderDetail',
            replyMarkdown: `Không tìm thấy đơn hàng có mã ${code} trong hệ thống.`,
          };
        }
        const isAdmin = authenticatedUser.roles.includes('ROLE_ADMIN');
        if (found.customerId !== authenticatedUser.userId && !isAdmin) {
          return {
            intentType: 'ACCESS_DENIED',
            executedTool: 'getOrderDetail (403 Forbidden)',
            denied: true,
            replyMarkdown: `Từ chối truy cập (403 Forbidden): Đơn hàng ${code} không thuộc tài khoản ${authenticatedUser.fullName}. Trợ lý AI không được phép truy vấn đơn hàng của khách hàng khác.`,
          };
        }

        return {
          intentType: 'ORDER_LOOKUP',
          executedTool: `getOrderDetail(userId=${authenticatedUser.userId}, code="${code}")`,
          replyMarkdown: [
            `Thông tin thực tế của đơn ${found.orderCode}:`,
            `• Điểm nhận: ${found.pickupPointName}`,
            `• Thương mại: ${found.orderStatus} | Vật lý: ${found.fulfillmentStatus} | Thanh toán: ${found.paymentStatus}`,
            `• Tổng giá trị: ${formatVnd(found.totalAmount)} (${found.items.length} mặt hàng đóng gói cố định)`,
            `• Hạn chốt tự hủy: 23:59:59 · Giờ nhận dự kiến: từ 16:00 (${found.cycleCode})`,
            found.fulfillmentStatus === 'READY_FOR_PICKUP'
              ? `• Mã QR nhận hàng 1 lần: ${found.pickupQrToken}`
              : '',
          ]
            .filter(Boolean)
            .join('\n'),
        };
      }

      const myOrders = orders.value.filter((o) => o.customerId === authenticatedUser.userId);
      if (myOrders.length === 0) {
        return {
          intentType: 'ORDER_LOOKUP',
          executedTool: `getCustomerOrders(userId=${authenticatedUser.userId})`,
          replyMarkdown: `Tài khoản ${authenticatedUser.fullName} hiện chưa có đơn hàng nào.`,
        };
      }
      const lines = myOrders.slice(0, 5).map(
        (o) =>
          `• ${o.orderCode} — ${formatVnd(o.totalAmount)} | Thương mại: ${o.orderStatus} | Vật lý: ${o.fulfillmentStatus}`
      );
      return {
        intentType: 'ORDER_LOOKUP',
        executedTool: `getCustomerOrders(userId=${authenticatedUser.userId})`,
        replyMarkdown: `Danh sách ${myOrders.length} đơn hàng gần nhất của ${authenticatedUser.fullName}:\n${lines.join('\n')}\n\nNhập mã đơn cụ thể (ví dụ ${myOrders[0].orderCode}) để xem chi tiết.`,
      };
    }

    if (q.includes('chốt') || q.includes('23:59') || q.includes('giờ') || q.includes('nhận hàng') || q.includes('16:00')) {
      return {
        intentType: 'POLICY_RAG',
        executedTool: 'retrievePolicy("POL-CUTOFF-SCHEDULE v1.0")',
        replyMarkdown: [
          'Theo Chính sách Giờ chốt & Nhận hàng (POL-CUTOFF-SCHEDULE v1.0 — UTC+07:00):',
          '• Hạn chốt đặt & thanh toán: 23:59:59 Ngày D cho đợt giao Ngày D+1.',
          '• Giữ tồn kho chờ thanh toán: Tối đa 30 phút (hoặc chặn trần đến 23:59:59 Ngày D).',
          '• Tự động khóa & xác nhận đơn: Đúng 00:00:00 Ngày D+1, đẩy sang Kho trung tâm đóng gói FEFO (mục tiêu 08:00).',
          '• Mở nhận hàng tại Đại lý: Từ 16:00:00 Ngày D+1 sau khi đại lý kiểm nhận đủ kiện.',
          '• Tự hoàn tất đơn: Đúng 00:00:00 Ngày D+2 nếu không có sự cố mở; đơn khách chưa lấy giữ trong Hàng tồn chưa lấy tại đại lý.',
        ].join('\n'),
      };
    }

    if (q.includes('hủy') || q.includes('hoàn tiền') || q.includes('vnpay') || q.includes('cod') || q.includes('muộn')) {
      return {
        intentType: 'POLICY_RAG',
        executedTool: 'retrievePolicy("POL-PAYMENT-CANCEL-REFUND v1.0")',
        replyMarkdown: [
          'Theo Chính sách Thanh toán, Hủy đơn & Hoàn tiền (POL-PAYMENT-CANCEL-REFUND v1.0):',
          '• Thanh toán 100% qua VNPAY (Không COD): Đơn chỉ vào đợt chốt sau khi nhận IPN hợp lệ từ VNPAY.',
          '• Tự hủy trước 23:59:59 Ngày D: Được hủy trực tiếp trên ứng dụng. Đơn đã thanh toán tự giải phóng tồn kho và tạo lệnh hoàn tiền 100%.',
          '• Sau 00:00:00 Ngày D+1: Không thể tự hủy vì kho đã tiến hành phân bổ lô và đóng gói.',
          '• Thanh toán đến muộn: Khách được chọn chuyển sang đợt kế tiếp (D+2) hoặc nhận hoàn tiền 100%.',
        ].join('\n'),
      };
    }

    if (q.includes('fefo') || q.includes('đóng gói') || q.includes('cố định') || q.includes('lô') || q.includes('hạn')) {
      return {
        intentType: 'POLICY_RAG',
        executedTool: 'retrievePolicy("POL-PACKAGING-FEFO v1.0")',
        replyMarkdown: [
          'Theo Chính sách Đóng gói cố định & FEFO (POL-PACKAGING-FEFO v1.0):',
          '• Mọi mặt hàng bán theo quy cách đóng gói cố định (Khay 500g, Khay 300g, Túi 500g, Hộp 10 quả) để tính chính xác 100% tiền khi thanh toán VNPAY.',
          '• Kho trung tâm phân bổ theo FEFO — ưu tiên lô hết hạn sớm nhất nhưng luôn còn đủ số ngày an toàn tối thiểu tại ngày nhận D+1.',
          '• Thực phẩm hoàn trả do sự cố đưa vào Kho cách ly hàng lỗi, tuyệt đối không cộng ngược vào tồn kho bán lại.',
        ].join('\n'),
      };
    }

    return {
      intentType: 'OUT_OF_SCOPE',
      executedTool: 'retrievePolicy(no_match)',
      replyMarkdown:
        'Tài liệu chính sách hiện chưa có thông tin về câu hỏi này. Bạn có thể hỏi về giờ chốt 23:59, giữ tồn 30 phút, hủy đơn/hoàn tiền VNPAY, quy tắc đóng gói FEFO, hoặc nhập mã đơn (ví dụ ORD-20261120-001) để tra cứu đơn chính chủ.',
    };
  }

  function suggestRecipes(input: {
    servings: number;
    budgetVnd: number;
    pantryProductIds: number[];
    allergies: string[];
  }): RecipeSuggestionPlan[] {
    const normalizedAllergies = input.allergies.map((a) => a.toLowerCase()).filter(Boolean);
    const pantrySet = new Set(input.pantryProductIds);
    const plans: RecipeSuggestionPlan[] = [];

    for (const recipe of SEED_RECIPES) {
      const scale = Math.max(0.5, input.servings / recipe.servings);
      let excludedByAllergy = false;

      const cartItems: RecipeSuggestionPlan['cartItems'] = [];
      let totalCartAmount = 0;
      let allInStock = true;

      for (const ing of recipe.ingredients) {
        const prod = products.value.find((p) => p.productId === ing.productId);
        if (!prod || !prod.isActive) continue;

        const allergyHaystack = `${prod.productName} ${prod.allergenTags || ''} ${ing.ingredientName}`.toLowerCase();
        if (normalizedAllergies.some((a) => allergyHaystack.includes(a))) {
          excludedByAllergy = true;
          break;
        }

        const neededGrams = Math.round(ing.requiredWeightGrams * scale);
        const coveredByPantry = pantrySet.has(prod.productId);
        const packagesToBuy = coveredByPantry ? 0 : Math.max(1, Math.ceil(neededGrams / prod.packWeightGrams));
        const boughtGrams = packagesToBuy * prod.packWeightGrams;
        const leftoverWeightGrams = coveredByPantry ? 0 : Math.max(0, boughtGrams - neededGrams);
        const lineTotal = packagesToBuy * prod.unitPrice;
        if (!coveredByPantry && prod.availableQty < packagesToBuy) {
          allInStock = false;
        }

        totalCartAmount += lineTotal;
        cartItems.push({
          productId: prod.productId,
          variantId: prod.variantId,
          sku: prod.sku,
          productName: prod.productName,
          packName: prod.packName,
          packWeightGrams: prod.packWeightGrams,
          storageType: prod.storageType,
          requiredWeightGrams: neededGrams,
          packagesToBuy,
          leftoverWeightGrams,
          officialUnitPrice: prod.unitPrice,
          lineTotal,
          availableStockInCycle: prod.availableQty,
          coveredByPantry,
        });
      }

      if (excludedByAllergy || cartItems.length === 0) continue;

      const budgetDiffVnd = totalCartAmount - input.budgetVnd;
      const withinBudget = totalCartAmount <= input.budgetVnd;
      const pantrySkipped = cartItems.filter((c) => c.coveredByPantry).length;
      const explanation = withinBudget
        ? `Phù hợp cho ${input.servings} người ăn (thời gian nấu khoảng ${recipe.cookingTimeMinutes} phút), tổng tiền sau quy đổi gói cố định thấp hơn ngân sách ${formatVnd(
            Math.abs(budgetDiffVnd)
          )}${pantrySkipped > 0 ? `, đã trừ ${pantrySkipped} nguyên liệu sẵn có tại nhà` : ''}.`
        : `Món cho ${input.servings} người ăn nhưng vượt ngân sách ${formatVnd(
            budgetDiffVnd
          )} do quy cách đóng gói cố định phải làm tròn lên nguyên gói.`;

      plans.push({
        recipeId: recipe.id,
        recipeName: recipe.name,
        baseServings: recipe.servings,
        requestedServings: input.servings,
        cookingTimeMinutes: recipe.cookingTimeMinutes,
        instructions: recipe.instructions,
        dietaryTags: recipe.dietaryTags,
        totalCartAmount,
        budgetVnd: input.budgetVnd,
        withinBudget,
        budgetDiffVnd,
        allInStock,
        aiExplanation: explanation,
        cartItems,
      });
    }

    return plans.sort((a, b) => {
      if (a.withinBudget !== b.withinBudget) return a.withinBudget ? -1 : 1;
      return a.totalCartAmount - b.totalCartAmount;
    });
  }

  return {
    isDemoMode,
    users,
    cycles,
    pickupPoints,
    products,
    lots,
    orders,
    packages,
    trips,
    issues,
    returnRequests,
    refunds,
    commissionPolicies,
    commissionLedger,
    setDemoMode,
    resetSeedData,
    checkoutOrder,
    simulateVnpayIpn,
    resolveLatePayment,
    cancelOrderBeforeCutoff,
    customerConfirmReceived,
    reportIssue,
    inboundLot,
    toggleLotQuarantine,
    packOrderWithFefo,
    receiveReturnToQuarantine,
    createDeliveryTrip,
    dispatchReturnTrip,
    confirmWarehousePickup,
    markDriverArrivedAtStop,
    driverPickupReturnFromAgent,
    completeAgentHandover,
    agentScanCustomerQr,
    updateUnclaimedNote,
    approveRefund,
    waiveUnrecoverableReturn,
    addCatalogProduct,
    createOrderCycle,
    activateCommissionPolicy,
    askAiAssistant,
    suggestRecipes,
  };
});

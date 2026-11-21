export type RoleCode =
  | 'ROLE_CUSTOMER'
  | 'ROLE_WAREHOUSE'
  | 'ROLE_DISPATCHER'
  | 'ROLE_DRIVER'
  | 'ROLE_AGENT'
  | 'ROLE_ADMIN';

export type StorageType = 'AMBIENT' | 'CHILLED' | 'FROZEN';

export type OrderStatus =
  | 'PENDING_PAYMENT'
  | 'PAID_WAITING_CUTOFF'
  | 'CONFIRMED'
  | 'PROCESSING'
  | 'COMPLETED'
  | 'CANCELLED';

export type FulfillmentStatus =
  | 'WAITING_PACKING'
  | 'PACKED'
  | 'IN_TRANSIT'
  | 'AGENT_CHECKING'
  | 'READY_FOR_PICKUP'
  | 'HANDED_OVER'
  | 'ISSUE_HOLD';

export type PaymentStatus =
  | 'UNPAID'
  | 'PAID'
  | 'LATE_RECONCILIATION'
  | 'REFUND_REQUESTED'
  | 'REFUND_APPROVED'
  | 'REFUNDING'
  | 'REFUNDED'
  | 'REFUND_FAILED';

export type CompletionType = 'CUSTOMER_CONFIRMED' | 'SYSTEM_TIMEOUT' | null;

export type LotStatus = 'AVAILABLE' | 'QUARANTINED' | 'EXPIRED';

export type PackageStatus =
  | 'PACKED'
  | 'HANDED_TO_DRIVER'
  | 'DELIVERED_TO_AGENT'
  | 'MISSING'
  | 'DAMAGED'
  | 'HANDED_TO_CUSTOMER';

export type PackageCondition = 'ACCEPTED' | 'MISSING' | 'DAMAGED';

export type ReturnStatus =
  | 'REQUESTED'
  | 'APPROVED_FOR_PICKUP'
  | 'DRIVER_PICKED_UP'
  | 'IN_RETURN_TRANSIT'
  | 'WAREHOUSE_RECEIVED'
  | 'UNRECOVERABLE_WAIVED'
  | 'CLOSED';

export type RefundStatus =
  | 'PENDING_APPROVAL'
  | 'APPROVED'
  | 'PROCESSING'
  | 'COMPLETED'
  | 'FAILED_RECONCILIATION';

export type CommissionEntryType =
  | 'CREDIT_COMMISSION'
  | 'DEBIT_REFUND_REVERSAL'
  | 'MANUAL_ADJUSTMENT';

export interface UserAccount {
  userId: number;
  email: string;
  phone: string;
  fullName: string;
  roles: RoleCode[];
  status: 'ACTIVE' | 'INACTIVE';
  assignedPickupPointId?: number;
  vehicleInfo?: string;
}

export interface ActivePointCycle {
  cycleId: number;
  cycleCode: string;
  deliveryDate: string;
  cutoffAt: string;
  pickupStartAt: string;
  autoCompleteAt: string;
  maxOrders: number;
  currentOrders: number;
  rolledOverToNextCycle: boolean;
}

export interface PickupPointWithCycle {
  id: number;
  code: string;
  name: string;
  shortName: string;
  address: string;
  district: string;
  latitude: number;
  longitude: number;
  coldStorageCapacity: number;
  status: 'ACTIVE' | 'PAUSED';
  agentName: string;
  agentUserId: number;
  agentPhone: string;
  activeCycle: ActivePointCycle | null;
}

export interface CatalogProductItem {
  productId: number;
  variantId: number;
  sku: string;
  productName: string;
  category: string;
  storageType: StorageType;
  packName: string;
  packWeightGrams: number;
  unitPrice: number;
  availableQty: number;
  reservedQty: number;
  committedQty: number;
  inStock: boolean;
  minShelfLifeDaysAtPickup: number;
  dietaryTags?: string;
  allergenTags?: string;
  isActive: boolean;
}

export interface LotRecord {
  id: number;
  variantId: number;
  productName: string;
  packName: string;
  storageType: StorageType;
  lotCode: string;
  supplierName: string;
  manufacturedDate: string;
  expiryDate: string;
  initialQty: number;
  remainingQty: number;
  status: LotStatus;
  receivedAt: string;
}

export interface OrderItemRecord {
  id: number;
  variantId: number;
  productName: string;
  packName: string;
  storageType: StorageType;
  unitPrice: number;
  quantity: number;
  lineTotal: number;
  allocatedLotCode?: string;
}

export interface PackageRecord {
  id: number;
  orderId: number;
  orderCode: string;
  pickupPointId: number;
  tripStopId: number | null;
  packageCode: string;
  qrPayload: string;
  packageSeq: number;
  totalPackagesInOrder: number;
  storageType: StorageType;
  status: PackageStatus;
}

export interface OrderRecord {
  orderId: number;
  orderCode: string;
  customerId: number;
  customerName: string;
  customerPhone: string;
  pickupPointId: number;
  pickupPointName: string;
  cycleId: number;
  cycleCode: string;
  orderStatus: OrderStatus;
  fulfillmentStatus: FulfillmentStatus;
  paymentStatus: PaymentStatus;
  completionType: CompletionType;
  totalAmount: number;
  cutoffAt: string;
  pickupStartAt: string;
  autoCompleteAt: string;
  reservationExpiresAt?: string;
  txnRef?: string;
  pickupQrToken: string;
  pickupQrUsedAt: string | null;
  physicalHandedOverAt: string | null;
  customerConfirmedAt: string | null;
  hasOpenIssue: boolean;
  isUnclaimedAtAgent: boolean;
  unclaimedContactNote?: string;
  auditAlert?: string;
  createdAt: string;
  items: OrderItemRecord[];
}

export interface TripStopRecord {
  id: number;
  tripId: number;
  pickupPointId: number;
  pickupPointName: string;
  pickupPointAddress: string;
  stopSequence: number;
  etaAt: string;
  arrivedAt: string | null;
  handoverCompletedAt: string | null;
  status: 'PENDING' | 'ARRIVED_AT_POINT' | 'HANDED_OVER';
}

export interface TripRecord {
  id: number;
  tripCode: string;
  cycleId: number;
  cycleCode: string;
  driverId: number;
  driverName: string;
  driverVehicle: string;
  tripType: 'DELIVERY' | 'RETURN_PICKUP';
  status: 'ASSIGNED' | 'IN_TRANSIT' | 'COMPLETED';
  departedAt: string | null;
  completedAt: string | null;
  warehouseHandoverSigned: boolean;
  warehouseHandoverNote?: string;
  stops: TripStopRecord[];
}

export interface IssueRecord {
  id: number;
  orderId: number;
  orderCode: string;
  pickupPointId: number;
  pickupPointName: string;
  reportedByName: string;
  reporterRole: RoleCode;
  issueType: 'DAMAGED' | 'MISSING_ITEM' | 'WRONG_ITEM' | 'LATE_DELIVERY';
  description: string;
  evidenceUrl: string;
  status: 'OPEN' | 'INVESTIGATING' | 'RESOLVED' | 'REJECTED';
  createdAt: string;
}

export interface ReturnRequestRecord {
  id: number;
  issueId: number;
  orderId: number;
  orderCode: string;
  pickupPointId: number;
  pickupPointName: string;
  returnTripCode: string | null;
  assignedDriverId: number | null;
  assignedDriverName: string | null;
  status: ReturnStatus;
  warehouseInspectionNote: string | null;
  createdAt: string;
}

export interface RefundRecord {
  id: number;
  orderId: number;
  orderCode: string;
  customerName: string;
  pickupPointId: number;
  returnRequestId: number | null;
  refundReason: 'CUSTOMER_CANCEL_BEFORE_CUTOFF' | 'ORDER_ISSUE_RETURN' | 'LATE_PAYMENT_REFUND';
  amount: number;
  status: RefundStatus;
  providerRef: string | null;
  approvedByName: string | null;
  completedAt: string | null;
  createdAt: string;
}

export interface CommissionPolicyRecord {
  id: number;
  policyCode: string;
  calcType: 'FIXED_PER_ORDER' | 'PERCENTAGE_NET_MERCHANDISE';
  fixedAmount: number | null;
  percentageRate: number | null;
  effectiveFrom: string;
  isActive: boolean;
}

export interface CommissionLedgerEntry {
  id: number;
  pickupPointId: number;
  orderId: number;
  orderCode: string;
  policyCode: string;
  entryType: CommissionEntryType;
  amount: number;
  note: string;
  createdAt: string;
}

export interface OrderCycleConfig {
  id: number;
  cycleCode: string;
  deliveryDate: string;
  openAt: string;
  cutoffAt: string;
  autoConfirmAt: string;
  warehousePackTargetAt: string;
  dispatchStartAt: string;
  pickupStartAt: string;
  autoCompleteAt: string;
  paymentTimeoutMinutes: number;
  status: 'OPEN' | 'LOCKED' | 'FULFILLING' | 'CLOSED';
}

export interface RecipeIngredientSpec {
  productId: number;
  ingredientName: string;
  requiredWeightGrams: number;
  isOptional: boolean;
}

export interface RecipeRecord {
  id: number;
  name: string;
  servings: number;
  cookingTimeMinutes: number;
  instructions: string;
  dietaryTags: string[];
  ingredients: RecipeIngredientSpec[];
}

export interface RecipeCartItemPlan {
  productId: number;
  variantId: number;
  sku: string;
  productName: string;
  packName: string;
  packWeightGrams: number;
  storageType: StorageType;
  requiredWeightGrams: number;
  packagesToBuy: number;
  leftoverWeightGrams: number;
  officialUnitPrice: number;
  lineTotal: number;
  availableStockInCycle: number;
  coveredByPantry: boolean;
}

export interface RecipeSuggestionPlan {
  recipeId: number;
  recipeName: string;
  baseServings: number;
  requestedServings: number;
  cookingTimeMinutes: number;
  instructions: string;
  dietaryTags: string[];
  totalCartAmount: number;
  budgetVnd: number;
  withinBudget: boolean;
  budgetDiffVnd: number;
  allInStock: boolean;
  aiExplanation: string;
  cartItems: RecipeCartItemPlan[];
}

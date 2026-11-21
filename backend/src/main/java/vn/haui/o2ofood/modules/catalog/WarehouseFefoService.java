package vn.haui.o2ofood.modules.catalog;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.haui.o2ofood.common.BusinessException;
import vn.haui.o2ofood.common.ErrorCode;
import vn.haui.o2ofood.common.TimeProvider;
import vn.haui.o2ofood.modules.cycle.InventoryBalanceEntity;
import vn.haui.o2ofood.modules.cycle.InventoryBalanceRepository;
import vn.haui.o2ofood.modules.cycle.OrderCycleEntity;
import vn.haui.o2ofood.modules.cycle.OrderCycleRepository;
import vn.haui.o2ofood.modules.exception.IssueEntity;
import vn.haui.o2ofood.modules.exception.IssueRepository;
import vn.haui.o2ofood.modules.fulfillment.FulfillmentStatus;
import vn.haui.o2ofood.modules.fulfillment.PackageEntity;
import vn.haui.o2ofood.modules.fulfillment.PackageRepository;
import vn.haui.o2ofood.modules.notification.OutboxEventEntity;
import vn.haui.o2ofood.modules.notification.OutboxEventRepository;
import vn.haui.o2ofood.modules.notification.OutboxEventStatus;
import vn.haui.o2ofood.modules.ordering.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.*;

@Service
public class WarehouseFefoService {

    private final LotRepository lotRepository;
    private final ProductRepository productRepository;
    private final ProductVariantRepository productVariantRepository;
    private final OrderCycleRepository orderCycleRepository;
    private final InventoryBalanceRepository inventoryBalanceRepository;
    private final StockMovementRepository stockMovementRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrderLotAllocationRepository orderLotAllocationRepository;
    private final PackageRepository packageRepository;
    private final IssueRepository issueRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final TimeProvider timeProvider;

    public WarehouseFefoService(LotRepository lotRepository,
                                ProductRepository productRepository,
                                ProductVariantRepository productVariantRepository,
                                OrderCycleRepository orderCycleRepository,
                                InventoryBalanceRepository inventoryBalanceRepository,
                                StockMovementRepository stockMovementRepository,
                                OrderRepository orderRepository,
                                OrderItemRepository orderItemRepository,
                                OrderLotAllocationRepository orderLotAllocationRepository,
                                PackageRepository packageRepository,
                                IssueRepository issueRepository,
                                OutboxEventRepository outboxEventRepository,
                                TimeProvider timeProvider) {
        this.lotRepository = lotRepository;
        this.productRepository = productRepository;
        this.productVariantRepository = productVariantRepository;
        this.orderCycleRepository = orderCycleRepository;
        this.inventoryBalanceRepository = inventoryBalanceRepository;
        this.stockMovementRepository = stockMovementRepository;
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.orderLotAllocationRepository = orderLotAllocationRepository;
        this.packageRepository = packageRepository;
        this.issueRepository = issueRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.timeProvider = timeProvider;
    }

    /**
     * UC-W01: Nhập kho theo Lô (Lots) & Kiểm tra hạn sử dụng an toàn.
     */
    @Transactional
    public LotEntity inboundLot(Long warehouseUserId, InboundLotRequest request) {
        OffsetDateTime now = timeProvider.now();
        ProductVariantEntity variant = productVariantRepository.findById(request.variantId())
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy quy cách sản phẩm"));
        ProductEntity product = productRepository.findById(variant.getProductId())
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy sản phẩm"));

        LocalDate minSafeExpiry = now.toLocalDate().plusDays(1 + product.getMinShelfLifeDaysAtPickup());
        if (request.expiryDate().isBefore(request.manufacturedDate())
                || request.expiryDate().isBefore(minSafeExpiry)) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_ERROR,
                    "Lô nhập đã hết hạn hoặc không đủ hạn sử dụng tối thiểu cho đợt giao tiếp theo"
            );
        }

        LotEntity lot = new LotEntity();
        lot.setVariantId(variant.getId());
        lot.setLotCode(request.lotCode());
        lot.setSupplierName(request.supplierName());
        lot.setManufacturedDate(request.manufacturedDate());
        lot.setExpiryDate(request.expiryDate());
        lot.setInitialQty(request.receivedQty());
        lot.setRemainingQty(request.receivedQty());
        lot.setStatus("AVAILABLE");
        lot.setReceivedAt(now);
        lot = lotRepository.save(lot);

        if (request.cycleId() != null) {
            InventoryBalanceEntity balance = inventoryBalanceRepository
                    .findByCycleIdAndVariantId(request.cycleId(), variant.getId())
                    .orElseGet(() -> {
                        InventoryBalanceEntity b = new InventoryBalanceEntity();
                        b.setCycleId(request.cycleId());
                        b.setVariantId(variant.getId());
                        b.setAvailableQty(0);
                        b.setReservedQty(0);
                        b.setCommittedQty(0);
                        b.setUpdatedAt(now);
                        return b;
                    });
            balance.setAvailableQty(balance.getAvailableQty() + request.receivedQty());
            balance.setUpdatedAt(now);
            inventoryBalanceRepository.save(balance);
        }

        StockMovementEntity movement = new StockMovementEntity();
        movement.setVariantId(variant.getId());
        movement.setLotId(lot.getId());
        movement.setMovementType("INBOUND");
        movement.setQuantity(request.receivedQty());
        movement.setCreatedBy(warehouseUserId);
        movement.setCreatedAt(now);
        stockMovementRepository.save(movement);

        return lot;
    }

    /**
     * UC-W02 Ngoại lệ: Khóa cách ly lô hàng hỏng đột xuất trong kho (QUARANTINED).
     */
    @Transactional
    public LotEntity quarantineLot(Long warehouseUserId, Long lotId) {
        LotEntity lot = lotRepository.findById(lotId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy lô hàng"));
        lot.setStatus("QUARANTINED");
        return lotRepository.saveAndFlush(lot);
    }

    /**
     * UC-W02, Mục 4.3 & AT-07: Phân bổ lô xuất kho tự động theo thuật toán FEFO (First-Expired, First-Out).
     * - Chỉ chọn lô có status = 'AVAILABLE', remaining_qty > 0, và
     *   expiry_date >= delivery_date + min_shelf_life_days_at_pickup.
     * - Sắp xếp theo expiry_date ASC, received_at ASC, id ASC.
     * - Bỏ qua các lô đã hết hạn hoặc bị khóa QUARANTINED.
     */
    @Transactional
    public List<OrderLotAllocationEntity> allocateFefoForOrder(Long warehouseUserId, Long orderId) {
        OffsetDateTime now = timeProvider.now();
        OrderEntity order = orderRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy đơn hàng"));
        OrderCycleEntity cycle = orderCycleRepository.findById(order.getCycleId())
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy đợt giao hàng"));

        List<OrderItemEntity> items = orderItemRepository.findByOrderIdOrderByIdAsc(order.getId());
        List<OrderLotAllocationEntity> createdAllocations = new ArrayList<>();

        for (OrderItemEntity item : items) {
            List<OrderLotAllocationEntity> existing = orderLotAllocationRepository.findByOrderItemIdOrderByIdAsc(item.getId());
            if (!existing.isEmpty()) {
                createdAllocations.addAll(existing);
                continue;
            }

            ProductVariantEntity variant = productVariantRepository.findById(item.getVariantId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy quy cách sản phẩm"));
            ProductEntity product = productRepository.findById(variant.getProductId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy sản phẩm"));

            int minShelfLifeDays = product.getMinShelfLifeDaysAtPickup() != null ? product.getMinShelfLifeDaysAtPickup() : 1;
            LocalDate minRequiredExpiryDate = cycle.getDeliveryDate().plusDays(minShelfLifeDays);

            List<LotEntity> eligibleLots = lotRepository.findEligibleLotsForFefo(variant.getId(), minRequiredExpiryDate);

            int remainingToAllocate = item.getQuantity();
            for (LotEntity lot : eligibleLots) {
                if (remainingToAllocate <= 0) {
                    break;
                }
                int takeQty = Math.min(lot.getRemainingQty(), remainingToAllocate);
                if (takeQty <= 0) {
                    continue;
                }

                lot.setRemainingQty(lot.getRemainingQty() - takeQty);
                lotRepository.save(lot);

                OrderLotAllocationEntity allocation = new OrderLotAllocationEntity();
                allocation.setOrderItemId(item.getId());
                allocation.setLotId(lot.getId());
                allocation.setAllocatedQty(takeQty);
                allocation = orderLotAllocationRepository.save(allocation);
                createdAllocations.add(allocation);

                StockMovementEntity movement = new StockMovementEntity();
                movement.setVariantId(variant.getId());
                movement.setLotId(lot.getId());
                movement.setOrderId(order.getId());
                movement.setMovementType("ALLOCATE_FEFO");
                movement.setQuantity(-takeQty);
                movement.setCreatedBy(warehouseUserId);
                movement.setCreatedAt(now);
                stockMovementRepository.save(movement);

                remainingToAllocate -= takeQty;
            }

            if (remainingToAllocate > 0) {
                // UC-W02 Ngoại lệ: Thiếu tồn lô thực tế tại kho -> Tạo Issue và khóa đơn ở ISSUE_HOLD
                order.setFulfillmentStatus(FulfillmentStatus.ISSUE_HOLD);
                order.setHasOpenIssue(true);
                order.setUpdatedAt(now);
                orderRepository.save(order);

                IssueEntity issue = new IssueEntity();
                issue.setOrderId(order.getId());
                issue.setReportedByUserId(warehouseUserId);
                issue.setReporterRole("ROLE_WAREHOUSE");
                issue.setIssueType("MISSING_ITEM");
                issue.setDescription("Thiếu lô đạt chuẩn FEFO trong kho cho sản phẩm: " + item.getProductNameSnapshot());
                issue.setStatus("OPEN");
                issue.setCreatedAt(now);
                issueRepository.save(issue);

                throw new BusinessException(
                        ErrorCode.INSUFFICIENT_STOCK,
                        "Không đủ tồn lô đạt chuẩn hạn sử dụng FEFO cho sản phẩm: " + item.getProductNameSnapshot()
                );
            }
        }

        return createdAllocations;
    }

    /**
     * UC-W02: Đóng gói đơn hàng thành các kiện (1 Order : N Packages) và sinh mã QR nhận hàng dùng 1 lần.
     */
    @Transactional
    public PackedOrderResult packOrderIntoPackages(Long warehouseUserId,
                                                   Long orderId,
                                                   List<String> packageStorageTypes) {
        OffsetDateTime now = timeProvider.now();
        OrderEntity order = orderRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy đơn hàng"));

        // Đảm bảo đã phân bổ lô FEFO trước khi đóng gói
        allocateFefoForOrder(warehouseUserId, orderId);

        List<String> storageTypes = (packageStorageTypes != null && !packageStorageTypes.isEmpty())
                ? packageStorageTypes
                : List.of("CHILLED");

        List<PackageEntity> existingPackages = packageRepository.findByOrderIdOrderByPackageSeqAsc(orderId);
        List<PackageEntity> packages = new ArrayList<>();
        if (!existingPackages.isEmpty()) {
            packages.addAll(existingPackages);
        } else {
            int totalPackages = storageTypes.size();
            for (int i = 0; i < totalPackages; i++) {
                int seq = i + 1;
                String pkgCode = "PKG-" + order.getOrderCode() + "-" + seq + "OF" + totalPackages;
                String qrPayload = "O2O_PKG|" + pkgCode + "|" + order.getId() + "|" + seq + "/" + totalPackages;

                PackageEntity pkg = new PackageEntity();
                pkg.setOrderId(order.getId());
                pkg.setPackageCode(pkgCode);
                pkg.setQrPayload(qrPayload);
                pkg.setPackageSeq(seq);
                pkg.setTotalPackagesInOrder(totalPackages);
                pkg.setStorageType(storageTypes.get(i));
                pkg.setStatus("PACKED_READY_FOR_DISPATCH");
                packages.add(packageRepository.save(pkg));
            }
        }

        String rawCustomerPickupToken = "PICKUP-OTP-" + order.getOrderCode() + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        order.setPickupQrTokenHash(sha256Hex(rawCustomerPickupToken));
        order.setOrderStatus(OrderStatus.PROCESSING);
        order.setFulfillmentStatus(FulfillmentStatus.PACKED);
        order.setUpdatedAt(now);
        orderRepository.save(order);

        return new PackedOrderResult(order.getId(), order.getOrderCode(), rawCustomerPickupToken, packages);
    }

    /**
     * UC-W03 Ngoại lệ: Kiểm tra SLA đóng gói lúc 08:00 D+1.
     * Nếu quá 08:00 mà đơn chưa được kho xác nhận đóng gói xong -> phát cảnh báo SLA_BREACH_PACKING,
     * tuyệt đối không tự động giả lập trạng thái PACKED.
     */
    @Transactional
    public int checkWarehousePackingSla(Long cycleId) {
        OffsetDateTime now = timeProvider.now();
        OrderCycleEntity cycle = orderCycleRepository.findById(cycleId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy đợt giao hàng"));
        if (now.isBefore(cycle.getWarehousePackTargetAt())) {
            return 0;
        }
        List<OrderEntity> confirmedOrders = orderRepository.findConfirmedOrdersForWarehousePacking(cycleId);
        int breachCount = 0;
        for (OrderEntity order : confirmedOrders) {
            if (order.getFulfillmentStatus() == FulfillmentStatus.WAITING_PACKING) {
                breachCount++;
                OutboxEventEntity alert = new OutboxEventEntity();
                alert.setAggregateType("ORDER");
                alert.setAggregateId(String.valueOf(order.getId()));
                alert.setEventType("SLA_BREACH_PACKING");
                alert.setPayload("{\"orderId\":" + order.getId() + ",\"orderCode\":\"" + order.getOrderCode() + "\"}");
                alert.setStatus(OutboxEventStatus.PENDING);
                alert.setCreatedAt(now);
                outboxEventRepository.save(alert);
            }
        }
        return breachCount;
    }

    public static String sha256Hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("Không thể băm SHA-256", e);
        }
    }

    public record InboundLotRequest(
            Long variantId,
            Long cycleId,
            String lotCode,
            String supplierName,
            LocalDate manufacturedDate,
            LocalDate expiryDate,
            int receivedQty
    ) {
    }

    public record PackedOrderResult(
            Long orderId,
            String orderCode,
            String customerPickupQrToken,
            List<PackageEntity> packages
    ) {
    }
}

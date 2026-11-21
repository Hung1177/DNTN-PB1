package vn.haui.o2ofood.modules.cycle;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vn.haui.o2ofood.common.ApiResponse;
import vn.haui.o2ofood.common.BusinessException;
import vn.haui.o2ofood.common.ErrorCode;
import vn.haui.o2ofood.common.TimeProvider;
import vn.haui.o2ofood.modules.catalog.ProductEntity;
import vn.haui.o2ofood.modules.catalog.ProductRepository;
import vn.haui.o2ofood.modules.catalog.ProductVariantEntity;
import vn.haui.o2ofood.modules.catalog.ProductVariantRepository;
import vn.haui.o2ofood.modules.identity.PickupPointEntity;
import vn.haui.o2ofood.modules.identity.PickupPointRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1")
public class CatalogCycleController {

    private final PickupPointRepository pickupPointRepository;
    private final OrderCycleRepository orderCycleRepository;
    private final PointCycleRepository pointCycleRepository;
    private final InventoryBalanceRepository inventoryBalanceRepository;
    private final ProductRepository productRepository;
    private final ProductVariantRepository productVariantRepository;
    private final TimeProvider timeProvider;

    public CatalogCycleController(PickupPointRepository pickupPointRepository,
                                  OrderCycleRepository orderCycleRepository,
                                  PointCycleRepository pointCycleRepository,
                                  InventoryBalanceRepository inventoryBalanceRepository,
                                  ProductRepository productRepository,
                                  ProductVariantRepository productVariantRepository,
                                  TimeProvider timeProvider) {
        this.pickupPointRepository = pickupPointRepository;
        this.orderCycleRepository = orderCycleRepository;
        this.pointCycleRepository = pointCycleRepository;
        this.inventoryBalanceRepository = inventoryBalanceRepository;
        this.productRepository = productRepository;
        this.productVariantRepository = productVariantRepository;
        this.timeProvider = timeProvider;
    }

    /**
     * UC-C01: Xem danh sách Điểm nhận hàng & Đợt giao hàng đang mở cho từng điểm.
     * Nếu đợt ngày mai (D+1) đã đóng hoặc đầy công suất, tự động trả về đợt kế tiếp (D+2).
     */
    @GetMapping("/pickup-points")
    public ResponseEntity<ApiResponse<List<PickupPointCycleResponse>>> listPickupPoints() {
        OffsetDateTime now = timeProvider.now();
        List<PickupPointEntity> points = pickupPointRepository.findByStatusOrderByIdAsc("ACTIVE");
        List<OrderCycleEntity> openCycles = orderCycleRepository.findOpenCyclesAfter(now);

        List<PickupPointCycleResponse> result = new ArrayList<>();
        for (PickupPointEntity point : points) {
            ActivePointCycleDto activeCycle = resolveActiveCycleForPoint(point.getId(), openCycles);
            result.add(new PickupPointCycleResponse(
                    point.getId(),
                    point.getCode(),
                    point.getName(),
                    point.getAddress(),
                    point.getLatitude(),
                    point.getLongitude(),
                    point.getColdStorageCapacity(),
                    point.getStatus(),
                    activeCycle
            ));
        }
        return ResponseEntity.ok(ApiResponse.ok(result, now));
    }

    @GetMapping("/cycles/active")
    public ResponseEntity<ApiResponse<ActivePointCycleDto>> getActiveCycleForPoint(
            @RequestParam("pickupPointId") Long pickupPointId) {
        OffsetDateTime now = timeProvider.now();
        PickupPointEntity point = pickupPointRepository.findById(pickupPointId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy điểm nhận hàng"));
        if (!"ACTIVE".equalsIgnoreCase(point.getStatus())) {
            throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION, "Điểm nhận hàng hiện đang tạm ngưng hoạt động");
        }
        List<OrderCycleEntity> openCycles = orderCycleRepository.findOpenCyclesAfter(now);
        ActivePointCycleDto dto = resolveActiveCycleForPoint(pickupPointId, openCycles);
        if (dto == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Hiện chưa có đợt giao hàng mở cho điểm nhận này");
        }
        return ResponseEntity.ok(ApiResponse.ok(dto, now));
    }

    /**
     * Xem danh mục sản phẩm đóng gói cố định kèm tồn kho khả dụng (available_qty) theo đợt giao hàng.
     */
    @GetMapping("/catalog/products")
    public ResponseEntity<ApiResponse<List<CatalogProductItemDto>>> getCatalogByCycle(
            @RequestParam("cycleId") Long cycleId) {
        OffsetDateTime now = timeProvider.now();
        orderCycleRepository.findById(cycleId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy đợt giao hàng"));

        Map<Long, ProductEntity> productsById = productRepository.findByIsActiveTrueOrderByIdAsc()
                .stream()
                .collect(Collectors.toMap(ProductEntity::getId, Function.identity()));
        List<ProductVariantEntity> variants = productVariantRepository.findByIsActiveTrueOrderByIdAsc();
        Map<Long, InventoryBalanceEntity> balancesByVariant = inventoryBalanceRepository.findByCycleIdOrderByVariantIdAsc(cycleId)
                .stream()
                .collect(Collectors.toMap(InventoryBalanceEntity::getVariantId, Function.identity()));

        List<CatalogProductItemDto> items = new ArrayList<>();
        for (ProductVariantEntity variant : variants) {
            ProductEntity product = productsById.get(variant.getProductId());
            if (product == null) {
                continue;
            }
            InventoryBalanceEntity balance = balancesByVariant.get(variant.getId());
            int availableQty = balance != null ? balance.getAvailableQty() : 0;
            items.add(new CatalogProductItemDto(
                    product.getId(),
                    variant.getId(),
                    variant.getSku(),
                    product.getName(),
                    product.getCategory(),
                    product.getStorageType(),
                    variant.getPackName(),
                    variant.getPackWeightGrams(),
                    variant.getUnitPrice(),
                    availableQty,
                    availableQty > 0,
                    product.getDietaryTags(),
                    product.getAllergenTags()
            ));
        }
        return ResponseEntity.ok(ApiResponse.ok(items, now));
    }

    private ActivePointCycleDto resolveActiveCycleForPoint(Long pickupPointId, List<OrderCycleEntity> openCycles) {
        boolean skippedFirstCycle = false;
        for (OrderCycleEntity cycle : openCycles) {
            Optional<PointCycleEntity> pcOpt = pointCycleRepository.findByCycleIdAndPickupPointId(cycle.getId(), pickupPointId);
            if (pcOpt.isPresent()) {
                PointCycleEntity pc = pcOpt.get();
                if ("OPEN".equalsIgnoreCase(pc.getStatus()) && pc.getCurrentOrders() < pc.getMaxOrders()) {
                    return new ActivePointCycleDto(
                            cycle.getId(),
                            cycle.getCycleCode(),
                            cycle.getDeliveryDate(),
                            cycle.getCutoffAt(),
                            cycle.getPickupStartAt(),
                            cycle.getAutoCompleteAt(),
                            pc.getMaxOrders(),
                            pc.getCurrentOrders(),
                            skippedFirstCycle
                    );
                }
            }
            skippedFirstCycle = true;
        }
        return null;
    }

    public record ActivePointCycleDto(
            Long cycleId,
            String cycleCode,
            LocalDate deliveryDate,
            OffsetDateTime cutoffAt,
            OffsetDateTime pickupStartAt,
            OffsetDateTime autoCompleteAt,
            Integer maxOrders,
            Integer currentOrders,
            boolean rolledOverToNextCycle
    ) {
    }

    public record PickupPointCycleResponse(
            Long id,
            String code,
            String name,
            String address,
            BigDecimal latitude,
            BigDecimal longitude,
            Integer coldStorageCapacity,
            String status,
            ActivePointCycleDto activeCycle
    ) {
    }

    public record CatalogProductItemDto(
            Long productId,
            Long variantId,
            String sku,
            String productName,
            String category,
            String storageType,
            String packName,
            Integer packWeightGrams,
            BigDecimal unitPrice,
            Integer availableQty,
            boolean inStock,
            String dietaryTags,
            String allergenTags
    ) {
    }
}

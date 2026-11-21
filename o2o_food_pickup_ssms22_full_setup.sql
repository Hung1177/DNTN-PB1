/* =========================================================================================
   HỆ THỐNG ĐẶT MUA THỰC PHẨM QUA ĐIỂM NHẬN HÀNG (O2O FOOD PICKUP SYSTEM)
   Môi trường: SQL Server 2022 Developer & SQL Server Management Studio 22 (SSMS 22)
   Múi giờ chuẩn: Asia/Ho_Chi_Minh (+07:00) - DATETIMEOFFSET(7)
   Hướng dẫn chạy: Mở file này trong SSMS 22 và nhấn F5 (Execute) để tạo mới toàn bộ CSDL
   kèm dữ liệu giả lập (Fake Data) đầy đủ cho tất cả 35 bảng thuộc 7 phân hệ nghiệp vụ.
   ========================================================================================= */

USE master;
GO

IF DB_ID(N'O2OFoodPickupDB') IS NOT NULL
BEGIN
    ALTER DATABASE O2OFoodPickupDB SET SINGLE_USER WITH ROLLBACK IMMEDIATE;
    DROP DATABASE O2OFoodPickupDB;
END
GO

CREATE DATABASE O2OFoodPickupDB;
GO

USE O2OFoodPickupDB;
GO

SET NOCOUNT ON;
SET XACT_ABORT ON;
GO

/* =========================================================================================
   PHẦN 1: KHỞI TẠO CẤU TRÚC BẢNG (DDL - 35 BẢNG THEO ĐẶC TẢ HỆ THỐNG)
   ========================================================================================= */

-- -----------------------------------------------------------------------------------------
-- 1. NHÓM NGƯỜI DÙNG, VAI TRÒ & ĐIỂM NHẬN HÀNG (USERS, ROLES, PICKUP POINTS)
-- -----------------------------------------------------------------------------------------
CREATE TABLE dbo.roles (
    id INT IDENTITY(1,1) PRIMARY KEY,
    code VARCHAR(50) NOT NULL UNIQUE,
    name NVARCHAR(100) NOT NULL,
    description NVARCHAR(255) NULL
);

CREATE TABLE dbo.users (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    email VARCHAR(150) NOT NULL UNIQUE,
    phone VARCHAR(20) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    full_name NVARCHAR(120) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE'
        CHECK (status IN ('ACTIVE', 'LOCKED', 'INACTIVE')),
    created_at DATETIMEOFFSET(7) NOT NULL DEFAULT SYSDATETIMEOFFSET(),
    updated_at DATETIMEOFFSET(7) NOT NULL DEFAULT SYSDATETIMEOFFSET(),
    version BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE dbo.user_roles (
    user_id BIGINT NOT NULL,
    role_id INT NOT NULL,
    assigned_at DATETIMEOFFSET(7) NOT NULL DEFAULT SYSDATETIMEOFFSET(),
    PRIMARY KEY (user_id, role_id),
    CONSTRAINT FK_UserRoles_User FOREIGN KEY (user_id) REFERENCES dbo.users(id),
    CONSTRAINT FK_UserRoles_Role FOREIGN KEY (role_id) REFERENCES dbo.roles(id)
);

CREATE TABLE dbo.pickup_points (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    code VARCHAR(30) NOT NULL UNIQUE,
    name NVARCHAR(150) NOT NULL,
    address NVARCHAR(300) NOT NULL,
    ward NVARCHAR(100) NULL,
    district NVARCHAR(100) NULL,
    city NVARCHAR(100) NOT NULL DEFAULT N'Hà Nội',
    latitude DECIMAL(9,6) NOT NULL,
    longitude DECIMAL(9,6) NOT NULL,
    contact_phone VARCHAR(20) NOT NULL,
    cold_storage_capacity INT NOT NULL DEFAULT 100 CHECK (cold_storage_capacity > 0),
    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE'
        CHECK (status IN ('ACTIVE', 'INACTIVE', 'MAINTENANCE')),
    created_at DATETIMEOFFSET(7) NOT NULL DEFAULT SYSDATETIMEOFFSET(),
    version BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE dbo.point_assignments (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    pickup_point_id BIGINT NOT NULL,
    agent_user_id BIGINT NOT NULL,
    is_primary BIT NOT NULL DEFAULT 1,
    active_from DATETIMEOFFSET(7) NOT NULL DEFAULT SYSDATETIMEOFFSET(),
    active_to DATETIMEOFFSET(7) NULL,
    CONSTRAINT FK_PointAssignments_Point FOREIGN KEY (pickup_point_id) REFERENCES dbo.pickup_points(id),
    CONSTRAINT FK_PointAssignments_User FOREIGN KEY (agent_user_id) REFERENCES dbo.users(id)
);

-- -----------------------------------------------------------------------------------------
-- 2. NHÓM SẢN PHẨM, QUY CÁCH CỐ ĐỊNH, ĐỢT ĐẶT HÀNG & LÔ TỒN FEFO
-- -----------------------------------------------------------------------------------------
CREATE TABLE dbo.products (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    sku_prefix VARCHAR(40) NOT NULL UNIQUE,
    name NVARCHAR(200) NOT NULL,
    category NVARCHAR(100) NOT NULL,
    description NVARCHAR(1000) NULL,
    storage_type VARCHAR(30) NOT NULL DEFAULT 'CHILLED'
        CHECK (storage_type IN ('AMBIENT', 'CHILLED', 'FROZEN')),
    min_shelf_life_days_at_pickup INT NOT NULL DEFAULT 1 CHECK (min_shelf_life_days_at_pickup >= 0),
    dietary_tags NVARCHAR(255) NULL,
    allergen_tags NVARCHAR(255) NULL,
    image_url VARCHAR(500) NULL,
    is_active BIT NOT NULL DEFAULT 1,
    created_at DATETIMEOFFSET(7) NOT NULL DEFAULT SYSDATETIMEOFFSET()
);

CREATE TABLE dbo.product_variants (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    product_id BIGINT NOT NULL,
    sku VARCHAR(60) NOT NULL UNIQUE,
    pack_name NVARCHAR(120) NOT NULL, -- Quy cách cố định: Khay 500g, Túi 500g, Hộp 10 quả...
    pack_weight_grams INT NOT NULL CHECK (pack_weight_grams > 0),
    unit_price DECIMAL(18,2) NOT NULL CHECK (unit_price >= 0),
    is_active BIT NOT NULL DEFAULT 1,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT FK_ProductVariants_Product FOREIGN KEY (product_id) REFERENCES dbo.products(id)
);

CREATE TABLE dbo.lots (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    variant_id BIGINT NOT NULL,
    lot_code VARCHAR(60) NOT NULL UNIQUE,
    supplier_name NVARCHAR(150) NOT NULL,
    manufactured_date DATE NOT NULL,
    expiry_date DATE NOT NULL,
    initial_qty INT NOT NULL CHECK (initial_qty > 0),
    remaining_qty INT NOT NULL CHECK (remaining_qty >= 0),
    status VARCHAR(30) NOT NULL DEFAULT 'AVAILABLE'
        CHECK (status IN ('AVAILABLE', 'QUARANTINED', 'EXPIRED', 'DEPLETED')),
    received_at DATETIMEOFFSET(7) NOT NULL DEFAULT SYSDATETIMEOFFSET(),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT FK_Lots_Variant FOREIGN KEY (variant_id) REFERENCES dbo.product_variants(id),
    CONSTRAINT CK_Lots_Expiry CHECK (expiry_date >= manufactured_date)
);

CREATE INDEX IX_Lots_FEFO ON dbo.lots(variant_id, status, expiry_date ASC, received_at ASC);

CREATE TABLE dbo.order_cycles (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    cycle_code VARCHAR(40) NOT NULL UNIQUE,
    delivery_date DATE NOT NULL,
    open_at DATETIMEOFFSET(7) NOT NULL,
    cutoff_at DATETIMEOFFSET(7) NOT NULL,                 -- 23:59:59 Ngày D
    auto_confirm_at DATETIMEOFFSET(7) NOT NULL,           -- 00:00:00 Ngày D+1
    warehouse_pack_target_at DATETIMEOFFSET(7) NOT NULL,  -- 08:00:00 Ngày D+1
    dispatch_start_at DATETIMEOFFSET(7) NOT NULL,         -- 08:00:00 Ngày D+1
    agent_handover_deadline_at DATETIMEOFFSET(7) NOT NULL,-- 16:00:00 Ngày D+1
    pickup_start_at DATETIMEOFFSET(7) NOT NULL,           -- 16:00:00 Ngày D+1
    auto_complete_at DATETIMEOFFSET(7) NOT NULL,          -- 00:00:00 Ngày D+2
    payment_timeout_minutes INT NOT NULL DEFAULT 30 CHECK (payment_timeout_minutes > 0),
    status VARCHAR(30) NOT NULL DEFAULT 'OPEN'
        CHECK (status IN ('OPEN', 'LOCKED', 'FULFILLING', 'CLOSED')),
    version BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE dbo.point_cycles (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    cycle_id BIGINT NOT NULL,
    pickup_point_id BIGINT NOT NULL,
    max_orders INT NOT NULL DEFAULT 150 CHECK (max_orders > 0),
    current_orders INT NOT NULL DEFAULT 0 CHECK (current_orders >= 0),
    status VARCHAR(30) NOT NULL DEFAULT 'OPEN'
        CHECK (status IN ('OPEN', 'FULL', 'CLOSED')),
    CONSTRAINT UQ_PointCycles UNIQUE (cycle_id, pickup_point_id),
    CONSTRAINT FK_PointCycles_Cycle FOREIGN KEY (cycle_id) REFERENCES dbo.order_cycles(id),
    CONSTRAINT FK_PointCycles_Point FOREIGN KEY (pickup_point_id) REFERENCES dbo.pickup_points(id)
);

CREATE TABLE dbo.inventory_balances (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    cycle_id BIGINT NOT NULL,
    variant_id BIGINT NOT NULL,
    available_qty INT NOT NULL DEFAULT 0 CHECK (available_qty >= 0),
    reserved_qty INT NOT NULL DEFAULT 0 CHECK (reserved_qty >= 0),
    committed_qty INT NOT NULL DEFAULT 0 CHECK (committed_qty >= 0),
    updated_at DATETIMEOFFSET(7) NOT NULL DEFAULT SYSDATETIMEOFFSET(),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT UQ_InventoryBalances UNIQUE (cycle_id, variant_id),
    CONSTRAINT FK_InventoryBalances_Cycle FOREIGN KEY (cycle_id) REFERENCES dbo.order_cycles(id),
    CONSTRAINT FK_InventoryBalances_Variant FOREIGN KEY (variant_id) REFERENCES dbo.product_variants(id)
);

-- -----------------------------------------------------------------------------------------
-- 3. NHÓM CHÍNH SÁCH HOA HỒNG, GIỎ HÀNG & ĐƠN HÀNG (COMMISSION POLICIES, CARTS, ORDERS)
-- -----------------------------------------------------------------------------------------
CREATE TABLE dbo.commission_policies (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    policy_code VARCHAR(40) NOT NULL UNIQUE,
    name NVARCHAR(150) NOT NULL,
    calc_type VARCHAR(30) NOT NULL
        CHECK (calc_type IN ('FIXED_PER_ORDER', 'PERCENTAGE_NET_MERCHANDISE')),
    fixed_amount DECIMAL(18,2) NULL CHECK (fixed_amount >= 0),
    percentage_rate DECIMAL(5,2) NULL CHECK (percentage_rate >= 0 AND percentage_rate <= 100),
    effective_from DATETIMEOFFSET(7) NOT NULL,
    effective_to DATETIMEOFFSET(7) NULL,
    is_active BIT NOT NULL DEFAULT 1
);

CREATE TABLE dbo.carts (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    customer_id BIGINT NOT NULL UNIQUE,
    pickup_point_id BIGINT NULL,
    cycle_id BIGINT NULL,
    updated_at DATETIMEOFFSET(7) NOT NULL DEFAULT SYSDATETIMEOFFSET(),
    CONSTRAINT FK_Carts_Customer FOREIGN KEY (customer_id) REFERENCES dbo.users(id),
    CONSTRAINT FK_Carts_Point FOREIGN KEY (pickup_point_id) REFERENCES dbo.pickup_points(id),
    CONSTRAINT FK_Carts_Cycle FOREIGN KEY (cycle_id) REFERENCES dbo.order_cycles(id)
);

CREATE TABLE dbo.cart_items (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    cart_id BIGINT NOT NULL,
    variant_id BIGINT NOT NULL,
    quantity INT NOT NULL CHECK (quantity > 0),
    added_at DATETIMEOFFSET(7) NOT NULL DEFAULT SYSDATETIMEOFFSET(),
    CONSTRAINT UQ_CartItems UNIQUE (cart_id, variant_id),
    CONSTRAINT FK_CartItems_Cart FOREIGN KEY (cart_id) REFERENCES dbo.carts(id) ON DELETE CASCADE,
    CONSTRAINT FK_CartItems_Variant FOREIGN KEY (variant_id) REFERENCES dbo.product_variants(id)
);

CREATE TABLE dbo.orders (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    order_code VARCHAR(40) NOT NULL UNIQUE,
    customer_id BIGINT NOT NULL,
    pickup_point_id BIGINT NOT NULL,
    cycle_id BIGINT NOT NULL,
    commission_policy_id BIGINT NOT NULL,
    subtotal_amount DECIMAL(18,2) NOT NULL CHECK (subtotal_amount >= 0),
    discount_amount DECIMAL(18,2) NOT NULL DEFAULT 0 CHECK (discount_amount >= 0),
    total_amount DECIMAL(18,2) NOT NULL CHECK (total_amount >= 0),
    order_status VARCHAR(30) NOT NULL
        CHECK (order_status IN ('PENDING_PAYMENT', 'PAID_WAITING_CUTOFF', 'CONFIRMED', 'PROCESSING', 'COMPLETED', 'CANCELLED')),
    fulfillment_status VARCHAR(30) NOT NULL
        CHECK (fulfillment_status IN ('WAITING_PACKING', 'PACKED', 'IN_TRANSIT', 'AGENT_CHECKING', 'READY_FOR_PICKUP', 'HANDED_OVER', 'ISSUE_HOLD', 'CANCELLED')),
    payment_status VARCHAR(30) NOT NULL
        CHECK (payment_status IN ('UNPAID', 'PAID', 'LATE_RECONCILIATION', 'REFUND_REQUESTED', 'REFUND_APPROVED', 'REFUNDING', 'REFUNDED', 'REFUND_FAILED')),
    cutoff_at DATETIMEOFFSET(7) NOT NULL,
    pickup_start_at DATETIMEOFFSET(7) NOT NULL,
    auto_complete_at DATETIMEOFFSET(7) NOT NULL,
    confirmed_at DATETIMEOFFSET(7) NULL,
    completed_at DATETIMEOFFSET(7) NULL,
    completion_type VARCHAR(30) NULL
        CHECK (completion_type IN ('CUSTOMER_CONFIRMED', 'SYSTEM_TIMEOUT')),
    customer_confirmed_at DATETIMEOFFSET(7) NULL,
    physical_handed_over_at DATETIMEOFFSET(7) NULL,
    pickup_qr_token_hash VARCHAR(128) NULL,
    pickup_qr_used_at DATETIMEOFFSET(7) NULL,
    has_open_issue BIT NOT NULL DEFAULT 0,
    is_unclaimed_at_agent BIT NOT NULL DEFAULT 0,
    cancel_reason NVARCHAR(255) NULL,
    created_at DATETIMEOFFSET(7) NOT NULL DEFAULT SYSDATETIMEOFFSET(),
    updated_at DATETIMEOFFSET(7) NOT NULL DEFAULT SYSDATETIMEOFFSET(),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT FK_Orders_Customer FOREIGN KEY (customer_id) REFERENCES dbo.users(id),
    CONSTRAINT FK_Orders_Point FOREIGN KEY (pickup_point_id) REFERENCES dbo.pickup_points(id),
    CONSTRAINT FK_Orders_Cycle FOREIGN KEY (cycle_id) REFERENCES dbo.order_cycles(id),
    CONSTRAINT FK_Orders_Policy FOREIGN KEY (commission_policy_id) REFERENCES dbo.commission_policies(id)
);

CREATE INDEX IX_Orders_Cycle_Status ON dbo.orders(cycle_id, order_status, fulfillment_status);
CREATE INDEX IX_Orders_Customer ON dbo.orders(customer_id, created_at DESC);
CREATE INDEX IX_Orders_Point ON dbo.orders(pickup_point_id, cycle_id);

CREATE TABLE dbo.order_items (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    order_id BIGINT NOT NULL,
    variant_id BIGINT NOT NULL,
    product_name_snapshot NVARCHAR(200) NOT NULL,
    pack_name_snapshot NVARCHAR(120) NOT NULL,
    unit_price_snapshot DECIMAL(18,2) NOT NULL,
    quantity INT NOT NULL CHECK (quantity > 0),
    line_total DECIMAL(18,2) NOT NULL CHECK (line_total >= 0),
    CONSTRAINT FK_OrderItems_Order FOREIGN KEY (order_id) REFERENCES dbo.orders(id),
    CONSTRAINT FK_OrderItems_Variant FOREIGN KEY (variant_id) REFERENCES dbo.product_variants(id)
);

CREATE TABLE dbo.order_lot_allocations (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    order_item_id BIGINT NOT NULL,
    lot_id BIGINT NOT NULL,
    allocated_qty INT NOT NULL CHECK (allocated_qty > 0),
    allocated_at DATETIMEOFFSET(7) NOT NULL DEFAULT SYSDATETIMEOFFSET(),
    CONSTRAINT FK_OrderLotAlloc_OrderItem FOREIGN KEY (order_item_id) REFERENCES dbo.order_items(id),
    CONSTRAINT FK_OrderLotAlloc_Lot FOREIGN KEY (lot_id) REFERENCES dbo.lots(id)
);

CREATE TABLE dbo.reservations (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    order_id BIGINT NOT NULL,
    cycle_id BIGINT NOT NULL,
    variant_id BIGINT NOT NULL,
    reserved_qty INT NOT NULL CHECK (reserved_qty > 0),
    status VARCHAR(30) NOT NULL
        CHECK (status IN ('ACTIVE', 'COMMITTED', 'RELEASED', 'EXPIRED')),
    expires_at DATETIMEOFFSET(7) NOT NULL,
    created_at DATETIMEOFFSET(7) NOT NULL DEFAULT SYSDATETIMEOFFSET(),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT FK_Reservations_Order FOREIGN KEY (order_id) REFERENCES dbo.orders(id),
    CONSTRAINT FK_Reservations_Cycle FOREIGN KEY (cycle_id) REFERENCES dbo.order_cycles(id),
    CONSTRAINT FK_Reservations_Variant FOREIGN KEY (variant_id) REFERENCES dbo.product_variants(id)
);

CREATE TABLE dbo.stock_movements (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    variant_id BIGINT NOT NULL,
    lot_id BIGINT NULL,
    order_id BIGINT NULL,
    movement_type VARCHAR(40) NOT NULL
        CHECK (movement_type IN ('INBOUND', 'ALLOCATE_FEFO', 'DISPATCH', 'RETURN_TO_QUARANTINE', 'ADJUSTMENT')),
    quantity INT NOT NULL,
    note NVARCHAR(300) NULL,
    created_by BIGINT NOT NULL,
    created_at DATETIMEOFFSET(7) NOT NULL DEFAULT SYSDATETIMEOFFSET(),
    CONSTRAINT FK_StockMovements_Variant FOREIGN KEY (variant_id) REFERENCES dbo.product_variants(id),
    CONSTRAINT FK_StockMovements_Lot FOREIGN KEY (lot_id) REFERENCES dbo.lots(id),
    CONSTRAINT FK_StockMovements_Order FOREIGN KEY (order_id) REFERENCES dbo.orders(id),
    CONSTRAINT FK_StockMovements_User FOREIGN KEY (created_by) REFERENCES dbo.users(id)
);

-- -----------------------------------------------------------------------------------------
-- 4. NHÓM GIAO NHẬN, CHUYẾN XE, KIỆN HÀNG QR & BIÊN BẢN BÀN GIAO
-- -----------------------------------------------------------------------------------------
CREATE TABLE dbo.trips (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    trip_code VARCHAR(40) NOT NULL UNIQUE,
    cycle_id BIGINT NOT NULL,
    driver_id BIGINT NOT NULL,
    trip_type VARCHAR(30) NOT NULL DEFAULT 'DELIVERY'
        CHECK (trip_type IN ('DELIVERY', 'RETURN_PICKUP')),
    status VARCHAR(30) NOT NULL DEFAULT 'PLANNED'
        CHECK (status IN ('PLANNED', 'LOADING_AT_WAREHOUSE', 'IN_TRANSIT', 'COMPLETED', 'CANCELLED')),
    departed_at DATETIMEOFFSET(7) NULL,
    completed_at DATETIMEOFFSET(7) NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT FK_Trips_Cycle FOREIGN KEY (cycle_id) REFERENCES dbo.order_cycles(id),
    CONSTRAINT FK_Trips_Driver FOREIGN KEY (driver_id) REFERENCES dbo.users(id)
);

CREATE TABLE dbo.trip_stops (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    trip_id BIGINT NOT NULL,
    pickup_point_id BIGINT NOT NULL,
    stop_sequence INT NOT NULL CHECK (stop_sequence > 0),
    eta_at DATETIMEOFFSET(7) NULL,
    arrived_at DATETIMEOFFSET(7) NULL,
    handover_completed_at DATETIMEOFFSET(7) NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING', 'ARRIVED', 'HANDOVER_COMPLETED', 'DELAYED')),
    CONSTRAINT UQ_TripStops UNIQUE (trip_id, pickup_point_id),
    CONSTRAINT FK_TripStops_Trip FOREIGN KEY (trip_id) REFERENCES dbo.trips(id),
    CONSTRAINT FK_TripStops_Point FOREIGN KEY (pickup_point_id) REFERENCES dbo.pickup_points(id)
);

CREATE TABLE dbo.shipments (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    shipment_code VARCHAR(40) NOT NULL UNIQUE,
    order_id BIGINT NOT NULL,
    trip_stop_id BIGINT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'PREPARING'
        CHECK (status IN ('PREPARING', 'PACKED', 'IN_TRANSIT', 'DELIVERED_TO_AGENT', 'PARTIAL_OR_DAMAGED', 'HANDED_TO_CUSTOMER')),
    created_at DATETIMEOFFSET(7) NOT NULL DEFAULT SYSDATETIMEOFFSET(),
    CONSTRAINT FK_Shipments_Order FOREIGN KEY (order_id) REFERENCES dbo.orders(id),
    CONSTRAINT FK_Shipments_TripStop FOREIGN KEY (trip_stop_id) REFERENCES dbo.trip_stops(id)
);

CREATE TABLE dbo.packages (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    order_id BIGINT NOT NULL,
    shipment_id BIGINT NULL,
    trip_stop_id BIGINT NULL,
    package_code VARCHAR(50) NOT NULL UNIQUE,
    qr_payload VARCHAR(200) NOT NULL UNIQUE,
    package_seq INT NOT NULL DEFAULT 1,
    total_packages_in_order INT NOT NULL DEFAULT 1,
    storage_type VARCHAR(30) NOT NULL DEFAULT 'CHILLED',
    status VARCHAR(30) NOT NULL DEFAULT 'PACKED'
        CHECK (status IN ('PACKED', 'HANDED_TO_DRIVER', 'DELIVERED_TO_AGENT', 'MISSING', 'DAMAGED', 'HANDED_TO_CUSTOMER', 'RETURNED_TO_WAREHOUSE')),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT FK_Packages_Order FOREIGN KEY (order_id) REFERENCES dbo.orders(id),
    CONSTRAINT FK_Packages_Shipment FOREIGN KEY (shipment_id) REFERENCES dbo.shipments(id),
    CONSTRAINT FK_Packages_TripStop FOREIGN KEY (trip_stop_id) REFERENCES dbo.trip_stops(id)
);

CREATE TABLE dbo.handover_records (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    handover_type VARCHAR(40) NOT NULL
        CHECK (handover_type IN ('WAREHOUSE_TO_DRIVER', 'DRIVER_TO_AGENT', 'AGENT_TO_DRIVER_RETURN', 'DRIVER_TO_WAREHOUSE_RETURN')),
    trip_stop_id BIGINT NULL,
    from_user_id BIGINT NOT NULL,
    to_user_id BIGINT NOT NULL,
    total_expected_packages INT NOT NULL CHECK (total_expected_packages >= 0),
    accepted_packages INT NOT NULL CHECK (accepted_packages >= 0),
    missing_packages INT NOT NULL DEFAULT 0 CHECK (missing_packages >= 0),
    damaged_packages INT NOT NULL DEFAULT 0 CHECK (damaged_packages >= 0),
    note NVARCHAR(500) NULL,
    signed_at DATETIMEOFFSET(7) NOT NULL DEFAULT SYSDATETIMEOFFSET(),
    CONSTRAINT FK_HandoverRecords_TripStop FOREIGN KEY (trip_stop_id) REFERENCES dbo.trip_stops(id),
    CONSTRAINT FK_HandoverRecords_FromUser FOREIGN KEY (from_user_id) REFERENCES dbo.users(id),
    CONSTRAINT FK_HandoverRecords_ToUser FOREIGN KEY (to_user_id) REFERENCES dbo.users(id)
);

CREATE TABLE dbo.handover_items (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    handover_record_id BIGINT NOT NULL,
    package_id BIGINT NOT NULL,
    condition_status VARCHAR(30) NOT NULL
        CHECK (condition_status IN ('ACCEPTED', 'MISSING', 'DAMAGED')),
    evidence_image_url VARCHAR(500) NULL,
    remark NVARCHAR(300) NULL,
    CONSTRAINT FK_HandoverItems_Record FOREIGN KEY (handover_record_id) REFERENCES dbo.handover_records(id),
    CONSTRAINT FK_HandoverItems_Package FOREIGN KEY (package_id) REFERENCES dbo.packages(id)
);

-- -----------------------------------------------------------------------------------------
-- 5. NHÓM NGOẠI LỆ, HÀNG HOÀN VỀ KHO LỖI, THANH TOÁN, HOÀN TIỀN & SỔ CÁI HOA HỒNG
-- -----------------------------------------------------------------------------------------
CREATE TABLE dbo.issues (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    issue_code VARCHAR(40) NOT NULL UNIQUE,
    order_id BIGINT NOT NULL,
    reported_by_user_id BIGINT NOT NULL,
    reporter_role VARCHAR(30) NOT NULL,
    issue_type VARCHAR(40) NOT NULL
        CHECK (issue_type IN ('DAMAGED', 'MISSING_ITEM', 'WRONG_ITEM', 'LATE_DELIVERY', 'QUALITY_COMPLAINT')),
    description NVARCHAR(1000) NOT NULL,
    evidence_urls NVARCHAR(MAX) NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'OPEN'
        CHECK (status IN ('OPEN', 'INVESTIGATING', 'RESOLVED', 'REJECTED')),
    resolution_note NVARCHAR(500) NULL,
    created_at DATETIMEOFFSET(7) NOT NULL DEFAULT SYSDATETIMEOFFSET(),
    resolved_at DATETIMEOFFSET(7) NULL,
    CONSTRAINT FK_Issues_Order FOREIGN KEY (order_id) REFERENCES dbo.orders(id),
    CONSTRAINT FK_Issues_Reporter FOREIGN KEY (reported_by_user_id) REFERENCES dbo.users(id)
);

CREATE TABLE dbo.return_requests (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    return_code VARCHAR(40) NOT NULL UNIQUE,
    issue_id BIGINT NOT NULL,
    order_id BIGINT NOT NULL,
    pickup_point_id BIGINT NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'REQUESTED'
        CHECK (status IN ('REQUESTED', 'APPROVED_FOR_PICKUP', 'DRIVER_PICKED_UP', 'IN_RETURN_TRANSIT', 'WAREHOUSE_RECEIVED', 'UNRECOVERABLE_WAIVED', 'CLOSED')),
    warehouse_inspection_note NVARCHAR(500) NULL,
    created_at DATETIMEOFFSET(7) NOT NULL DEFAULT SYSDATETIMEOFFSET(),
    updated_at DATETIMEOFFSET(7) NOT NULL DEFAULT SYSDATETIMEOFFSET(),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT FK_ReturnRequests_Issue FOREIGN KEY (issue_id) REFERENCES dbo.issues(id),
    CONSTRAINT FK_ReturnRequests_Order FOREIGN KEY (order_id) REFERENCES dbo.orders(id),
    CONSTRAINT FK_ReturnRequests_Point FOREIGN KEY (pickup_point_id) REFERENCES dbo.pickup_points(id)
);

CREATE TABLE dbo.return_items (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    return_request_id BIGINT NOT NULL,
    order_item_id BIGINT NOT NULL,
    quantity INT NOT NULL CHECK (quantity > 0),
    condition_note NVARCHAR(300) NULL,
    quarantine_bin_code VARCHAR(50) NULL,
    CONSTRAINT FK_ReturnItems_Request FOREIGN KEY (return_request_id) REFERENCES dbo.return_requests(id),
    CONSTRAINT FK_ReturnItems_OrderItem FOREIGN KEY (order_item_id) REFERENCES dbo.order_items(id)
);

CREATE TABLE dbo.return_shipments (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    return_shipment_code VARCHAR(40) NOT NULL UNIQUE,
    return_request_id BIGINT NOT NULL,
    trip_id BIGINT NOT NULL,
    picked_up_from_agent_at DATETIMEOFFSET(7) NULL,
    received_at_warehouse_at DATETIMEOFFSET(7) NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'ASSIGNED'
        CHECK (status IN ('ASSIGNED', 'PICKED_UP_FROM_AGENT', 'DELIVERED_TO_WAREHOUSE')),
    CONSTRAINT FK_ReturnShipments_Request FOREIGN KEY (return_request_id) REFERENCES dbo.return_requests(id),
    CONSTRAINT FK_ReturnShipments_Trip FOREIGN KEY (trip_id) REFERENCES dbo.trips(id)
);

CREATE TABLE dbo.payment_attempts (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    order_id BIGINT NOT NULL,
    txn_ref VARCHAR(64) NOT NULL UNIQUE,
    provider VARCHAR(30) NOT NULL DEFAULT 'VNPAY',
    amount DECIMAL(18,2) NOT NULL CHECK (amount >= 0),
    status VARCHAR(40) NOT NULL DEFAULT 'INITIATED'
        CHECK (status IN ('INITIATED', 'SUCCESS', 'FAILED', 'LATE_PAID_RECONCILIATION')),
    vnp_transaction_no VARCHAR(64) NULL,
    vnp_bank_code VARCHAR(30) NULL,
    vnp_pay_date VARCHAR(20) NULL,
    created_at DATETIMEOFFSET(7) NOT NULL DEFAULT SYSDATETIMEOFFSET(),
    confirmed_at DATETIMEOFFSET(7) NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT FK_PaymentAttempts_Order FOREIGN KEY (order_id) REFERENCES dbo.orders(id)
);

CREATE TABLE dbo.payment_events (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    payment_attempt_id BIGINT NOT NULL,
    event_source VARCHAR(30) NOT NULL
        CHECK (event_source IN ('RETURN_URL', 'IPN_WEBHOOK')),
    vnp_response_code VARCHAR(10) NOT NULL,
    raw_payload NVARCHAR(MAX) NOT NULL,
    signature_valid BIT NOT NULL,
    received_at DATETIMEOFFSET(7) NOT NULL DEFAULT SYSDATETIMEOFFSET(),
    CONSTRAINT FK_PaymentEvents_Attempt FOREIGN KEY (payment_attempt_id) REFERENCES dbo.payment_attempts(id)
);

CREATE TABLE dbo.refunds (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    refund_code VARCHAR(40) NOT NULL UNIQUE,
    order_id BIGINT NOT NULL,
    return_request_id BIGINT NULL,
    refund_reason VARCHAR(40) NOT NULL
        CHECK (refund_reason IN ('CUSTOMER_CANCEL_BEFORE_CUTOFF', 'ORDER_ISSUE_RETURN', 'LATE_PAYMENT_REFUND')),
    amount DECIMAL(18,2) NOT NULL CHECK (amount > 0),
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING_APPROVAL'
        CHECK (status IN ('PENDING_APPROVAL', 'APPROVED', 'PROCESSING', 'COMPLETED', 'FAILED_RECONCILIATION')),
    provider_ref VARCHAR(100) NULL,
    approved_by BIGINT NULL,
    created_at DATETIMEOFFSET(7) NOT NULL DEFAULT SYSDATETIMEOFFSET(),
    completed_at DATETIMEOFFSET(7) NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT FK_Refunds_Order FOREIGN KEY (order_id) REFERENCES dbo.orders(id),
    CONSTRAINT FK_Refunds_ReturnRequest FOREIGN KEY (return_request_id) REFERENCES dbo.return_requests(id),
    CONSTRAINT FK_Refunds_Approver FOREIGN KEY (approved_by) REFERENCES dbo.users(id)
);

CREATE TABLE dbo.settlement_batches (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    batch_code VARCHAR(40) NOT NULL UNIQUE,
    pickup_point_id BIGINT NOT NULL,
    period_start DATE NOT NULL,
    period_end DATE NOT NULL,
    total_credit DECIMAL(18,2) NOT NULL DEFAULT 0,
    total_debit DECIMAL(18,2) NOT NULL DEFAULT 0,
    net_payout_amount DECIMAL(18,2) NOT NULL DEFAULT 0,
    status VARCHAR(30) NOT NULL DEFAULT 'OPEN'
        CHECK (status IN ('OPEN', 'RECONCILED', 'PAID_OUT')),
    paid_out_at DATETIMEOFFSET(7) NULL,
    created_at DATETIMEOFFSET(7) NOT NULL DEFAULT SYSDATETIMEOFFSET(),
    CONSTRAINT FK_SettlementBatches_Point FOREIGN KEY (pickup_point_id) REFERENCES dbo.pickup_points(id)
);

CREATE TABLE dbo.commission_ledger (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    pickup_point_id BIGINT NOT NULL,
    order_id BIGINT NOT NULL,
    policy_id BIGINT NOT NULL,
    entry_type VARCHAR(30) NOT NULL
        CHECK (entry_type IN ('CREDIT_COMMISSION', 'DEBIT_REFUND_REVERSAL', 'MANUAL_ADJUSTMENT')),
    amount DECIMAL(18,2) NOT NULL, -- Dương (+) cho CREDIT_COMMISSION, Âm (-) cho DEBIT_REFUND_REVERSAL
    commission_status VARCHAR(30) NOT NULL DEFAULT 'RECORDED'
        CHECK (commission_status IN ('RECORDED', 'ADJUSTED', 'RECONCILED', 'PAID_OUT')),
    settlement_batch_id BIGINT NULL,
    note NVARCHAR(300) NULL,
    created_at DATETIMEOFFSET(7) NOT NULL DEFAULT SYSDATETIMEOFFSET(),
    CONSTRAINT FK_CommissionLedger_Point FOREIGN KEY (pickup_point_id) REFERENCES dbo.pickup_points(id),
    CONSTRAINT FK_CommissionLedger_Order FOREIGN KEY (order_id) REFERENCES dbo.orders(id),
    CONSTRAINT FK_CommissionLedger_Policy FOREIGN KEY (policy_id) REFERENCES dbo.commission_policies(id),
    CONSTRAINT FK_CommissionLedger_Batch FOREIGN KEY (settlement_batch_id) REFERENCES dbo.settlement_batches(id)
);

-- Ràng buộc cứng chống cộng hoa hồng 2 lần cho cùng 1 đơn hàng
CREATE UNIQUE INDEX UX_CommissionLedger_Order_Credit
ON dbo.commission_ledger(order_id)
WHERE entry_type = 'CREDIT_COMMISSION';

-- -----------------------------------------------------------------------------------------
-- 6. NHÓM TÁC VỤ NỀN, OUTBOX, THÔNG BÁO & NHẬT KÝ KIỂM TOÁN
-- -----------------------------------------------------------------------------------------
CREATE TABLE dbo.shedlock (
    name VARCHAR(64) NOT NULL PRIMARY KEY,
    lock_until DATETIME2 NOT NULL,
    locked_at DATETIME2 NOT NULL,
    locked_by VARCHAR(255) NOT NULL
);

CREATE TABLE dbo.job_runs (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    job_name VARCHAR(80) NOT NULL,
    cycle_id BIGINT NULL,
    started_at DATETIMEOFFSET(7) NOT NULL,
    finished_at DATETIMEOFFSET(7) NULL,
    processed_count INT NOT NULL DEFAULT 0,
    status VARCHAR(30) NOT NULL
        CHECK (status IN ('RUNNING', 'SUCCESS', 'FAILED')),
    error_message NVARCHAR(MAX) NULL,
    CONSTRAINT FK_JobRuns_Cycle FOREIGN KEY (cycle_id) REFERENCES dbo.order_cycles(id)
);

CREATE TABLE dbo.outbox_events (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    aggregate_type VARCHAR(50) NOT NULL,
    aggregate_id VARCHAR(64) NOT NULL,
    event_type VARCHAR(80) NOT NULL,
    payload NVARCHAR(MAX) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING', 'PUBLISHED', 'FAILED')),
    created_at DATETIMEOFFSET(7) NOT NULL DEFAULT SYSDATETIMEOFFSET(),
    published_at DATETIMEOFFSET(7) NULL
);

CREATE TABLE dbo.notifications (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    user_id BIGINT NOT NULL,
    order_id BIGINT NULL,
    channel VARCHAR(20) NOT NULL DEFAULT 'IN_APP'
        CHECK (channel IN ('IN_APP', 'WEB_SSE', 'ANDROID_FCM')),
    title NVARCHAR(200) NOT NULL,
    body NVARCHAR(1000) NOT NULL,
    is_read BIT NOT NULL DEFAULT 0,
    created_at DATETIMEOFFSET(7) NOT NULL DEFAULT SYSDATETIMEOFFSET(),
    CONSTRAINT FK_Notifications_User FOREIGN KEY (user_id) REFERENCES dbo.users(id),
    CONSTRAINT FK_Notifications_Order FOREIGN KEY (order_id) REFERENCES dbo.orders(id)
);

CREATE TABLE dbo.audit_logs (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    actor_user_id BIGINT NULL,
    action_code VARCHAR(80) NOT NULL,
    target_entity VARCHAR(60) NOT NULL,
    target_id VARCHAR(64) NOT NULL,
    detail_json NVARCHAR(MAX) NULL,
    created_at DATETIMEOFFSET(7) NOT NULL DEFAULT SYSDATETIMEOFFSET(),
    CONSTRAINT FK_AuditLogs_User FOREIGN KEY (actor_user_id) REFERENCES dbo.users(id)
);

-- -----------------------------------------------------------------------------------------
-- 7. NHÓM TRÍ TUỆ NHÂN TẠO (RECIPES, RAG KNOWLEDGE DOCUMENTS, CHAT SESSIONS)
-- -----------------------------------------------------------------------------------------
CREATE TABLE dbo.recipes (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    recipe_code VARCHAR(40) NOT NULL UNIQUE,
    name NVARCHAR(150) NOT NULL,
    servings INT NOT NULL CHECK (servings > 0),
    cooking_time_minutes INT NOT NULL,
    instructions NVARCHAR(MAX) NOT NULL,
    dietary_tags NVARCHAR(255) NULL,
    is_active BIT NOT NULL DEFAULT 1
);

CREATE TABLE dbo.recipe_ingredients (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    recipe_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    ingredient_name NVARCHAR(120) NOT NULL,
    required_weight_grams INT NOT NULL CHECK (required_weight_grams > 0),
    is_optional BIT NOT NULL DEFAULT 0,
    CONSTRAINT FK_RecipeIngredients_Recipe FOREIGN KEY (recipe_id) REFERENCES dbo.recipes(id) ON DELETE CASCADE,
    CONSTRAINT FK_RecipeIngredients_Product FOREIGN KEY (product_id) REFERENCES dbo.products(id)
);

CREATE TABLE dbo.knowledge_documents (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    doc_code VARCHAR(50) NOT NULL,
    version_tag VARCHAR(20) NOT NULL,
    title NVARCHAR(200) NOT NULL,
    content_markdown NVARCHAR(MAX) NOT NULL,
    is_active BIT NOT NULL DEFAULT 1,
    updated_at DATETIMEOFFSET(7) NOT NULL DEFAULT SYSDATETIMEOFFSET(),
    CONSTRAINT UQ_KnowledgeDocs UNIQUE (doc_code, version_tag)
);

CREATE TABLE dbo.chat_sessions (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    session_code VARCHAR(64) NOT NULL UNIQUE,
    customer_id BIGINT NOT NULL,
    started_at DATETIMEOFFSET(7) NOT NULL DEFAULT SYSDATETIMEOFFSET(),
    last_message_at DATETIMEOFFSET(7) NOT NULL DEFAULT SYSDATETIMEOFFSET(),
    CONSTRAINT FK_ChatSessions_Customer FOREIGN KEY (customer_id) REFERENCES dbo.users(id)
);

CREATE TABLE dbo.chat_messages (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    session_id BIGINT NOT NULL,
    sender_type VARCHAR(20) NOT NULL
        CHECK (sender_type IN ('USER', 'ASSISTANT', 'TOOL')),
    content NVARCHAR(MAX) NOT NULL,
    tool_name VARCHAR(80) NULL,
    created_at DATETIMEOFFSET(7) NOT NULL DEFAULT SYSDATETIMEOFFSET(),
    CONSTRAINT FK_ChatMessages_Session FOREIGN KEY (session_id) REFERENCES dbo.chat_sessions(id) ON DELETE CASCADE
);

CREATE TABLE dbo.chat_feedback (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    session_id BIGINT NOT NULL,
    message_id BIGINT NOT NULL,
    rating INT NOT NULL CHECK (rating BETWEEN 1 AND 5),
    comment NVARCHAR(500) NULL,
    created_at DATETIMEOFFSET(7) NOT NULL DEFAULT SYSDATETIMEOFFSET(),
    CONSTRAINT FK_ChatFeedback_Session FOREIGN KEY (session_id) REFERENCES dbo.chat_sessions(id),
    CONSTRAINT FK_ChatFeedback_Message FOREIGN KEY (message_id) REFERENCES dbo.chat_messages(id)
);
GO

/* =========================================================================================
   PHẦN 2: CHÈN DỮ LIỆU GIẢ LẬP ĐẦY ĐỦ NGHIỆP VỤ (SEED FAKE DATA)
   Mật khẩu mẫu mặc định (BCrypt của "123456"): $2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy
   ========================================================================================= */

-- 1. Roles (6 vai trò chuẩn)
INSERT INTO dbo.roles (code, name, description) VALUES
('ROLE_ADMIN',      N'Quản trị viên',   N'Quản lý danh mục, cấu hình đợt, chính sách hoa hồng, duyệt hoàn tiền'),
('ROLE_WAREHOUSE',  N'Nhân viên kho',   N'Quản lý lô nhập, lấy hàng FEFO, đóng gói QR, nhận hàng hoàn về kho lỗi'),
('ROLE_DISPATCHER', N'Điều phối viên',  N'Phân công chuyến giao, giám sát tiến độ, lập chuyến thu hồi'),
('ROLE_DRIVER',     N'Tài xế giao nhận',N'Kiểm nhận tại kho từ 08:00, giao đại lý trước 16:00, chở hàng hoàn về kho'),
('ROLE_AGENT',      N'Đại lý nhận hàng',N'Đối chiếu kiện hàng với tài xế, trả hàng qua QR từ 16:00, hưởng hoa hồng'),
('ROLE_CUSTOMER',   N'Khách hàng',      N'Đặt mua thực phẩm trả trước 100%, hủy trước 23:59, nhận hàng tại điểm');

-- 2. Users (12 người dùng tương ứng đủ 6 vai trò)
INSERT INTO dbo.users (email, phone, password_hash, full_name, status) VALUES
('admin@o2ofood.vn',      '0901000001', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', N'Nguyễn Quản Trị',       'ACTIVE'), -- id = 1
('kho1@o2ofood.vn',       '0901000002', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', N'Trần Văn Kho',          'ACTIVE'), -- id = 2
('dieuphoi1@o2ofood.vn',  '0901000003', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', N'Lê Thị Điều Phối',      'ACTIVE'), -- id = 3
('taixe1@o2ofood.vn',     '0901000004', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', N'Phạm Quốc Tài Xế 1',    'ACTIVE'), -- id = 4
('taixe2@o2ofood.vn',     '0901000005', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', N'Hoàng Văn Tài Xế 2',    'ACTIVE'), -- id = 5
('daily.nhon@o2ofood.vn', '0901000006', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', N'Cô Lan - Đại Lý Nhổn',  'ACTIVE'), -- id = 6
('daily.caugiay@o2ofood.vn','0901000007','$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy',N'Chú Minh - Đại Lý Cầu Giấy','ACTIVE'),-- id = 7
('daily.mydinh@o2ofood.vn','0901000008','$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', N'Chị Hoa - Đại Lý Mỹ Đình','ACTIVE'),-- id = 8
('hung.khach@gmail.com',  '0988111720', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', N'Nông Thế Hưng',         'ACTIVE'), -- id = 9
('manh.khach@gmail.com',  '0988111721', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', N'Nguyễn Đức Mạnh',       'ACTIVE'), -- id = 10
('kien.khach@gmail.com',  '0988111722', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', N'Trần Trung Kiên',       'ACTIVE'), -- id = 11
('linh.khach@gmail.com',  '0988111723', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', N'Vũ Phương Linh',        'ACTIVE'); -- id = 12

-- 3. UserRoles
INSERT INTO dbo.user_roles (user_id, role_id) VALUES
(1, 1), -- Admin
(2, 2), -- Nhân viên kho
(3, 3), -- Điều phối
(4, 4), (5, 4), -- 2 Tài xế
(6, 5), (7, 5), (8, 5), -- 3 Đại lý
(9, 6), (10, 6), (11, 6), (12, 6); -- 4 Khách hàng

-- 4. PickupPoints (3 điểm nhận hàng tại Hà Nội)
INSERT INTO dbo.pickup_points (code, name, address, ward, district, city, latitude, longitude, contact_phone, cold_storage_capacity, status) VALUES
('PP-HN-NHON',    N'Điểm nhận hàng Tạp hóa Cô Lan - Nhổn (Gần ĐH Công Nghiệp)', N'Số 28 Ngõ 132 Nguyên Xá', N'Minh Khai',   N'Bắc Từ Liêm', N'Hà Nội', 21.053800, 105.735100, '0901000006', 120, 'ACTIVE'), -- id = 1
('PP-HN-CAUGIAY', N'Điểm nhận hàng MiniMart Chú Minh - Dịch Vọng',              N'Số 15 Ngõ 68 Cầu Giấy',   N'Dịch Vọng',   N'Cầu Giấy',    N'Hà Nội', 21.033500, 105.798200, '0901000007', 150, 'ACTIVE'), -- id = 2
('PP-HN-MYDINH',  N'Điểm nhận hàng Cửa hàng Thực phẩm Chị Hoa - Mỹ Đình',       N'Số 42 Đường Mỹ Đình',     N'Mỹ Đình 2',   N'Nam Từ Liêm', N'Hà Nội', 21.028100, 105.770400, '0901000008', 100, 'ACTIVE'); -- id = 3

-- 5. PointAssignments (Phân công đại lý phụ trách điểm nhận)
INSERT INTO dbo.point_assignments (pickup_point_id, agent_user_id, is_primary, active_from) VALUES
(1, 6, 1, '2026-10-01T00:00:00+07:00'),
(2, 7, 1, '2026-10-01T00:00:00+07:00'),
(3, 8, 1, '2026-10-01T00:00:00+07:00');

-- 6. Products (10 mặt hàng thực phẩm thiết yếu)
INSERT INTO dbo.products (sku_prefix, name, category, description, storage_type, min_shelf_life_days_at_pickup, dietary_tags, allergen_tags, image_url) VALUES
('PRD-CHICKEN-BREAST', N'Ức gà phi lê tươi sạch chuẩn VietGAP', N'Thịt gia cầm', N'Ức gà phi lê bỏ da, giàu đạm, đóng khay hút chân không', 'CHILLED', 2, N'EatClean,HighProtein,LowFat', NULL, '/images/products/uc-ga.jpg'),          -- id = 1
('PRD-PORK-BELLY',     N'Thịt ba chỉ heo mảnh mát MEATDeli',    N'Thịt heo',     N'Ba chỉ heo ủ mát chuẩn công nghệ châu Âu',                 'CHILLED', 2, N'FreshMeat',                   NULL, '/images/products/ba-chi-heo.jpg'),     -- id = 2
('PRD-BEEF-SHANK',     N'Thịt bắp bò Úc thái lát',              N'Thịt bò',      N'Bắp bò tươi đóng khay tiện lợi cho món xào hoặc nhúng',    'CHILLED', 2, N'HighProtein,IronRich',        NULL, '/images/products/bap-bo.jpg'),         -- id = 3
('PRD-EGG-CHICKEN',    N'Trứng gà sạch Dabaco',                 N'Trứng & Đậu',  N'Trứng gà tươi tuyển chọn đóng hộp chống vỡ',               'AMBIENT', 5, N'HighProtein,Vegetarian',      N'Egg', '/images/products/trung-ga.jpg'),       -- id = 4
('PRD-WHITE-TOFU',     N'Đậu phụ trắng non làng Mơ',            N'Trứng & Đậu',  N'Đậu trắng mềm làm mới mỗi ngày, đóng hộp nước tinh khiết', 'CHILLED', 1, N'Vegan,Vegetarian,HighProtein',N'Soy', '/images/products/dau-trang.jpg'),      -- id = 5
('PRD-BROCCOLI',       N'Súp lơ xanh Đà Lạt VietGAP',           N'Rau củ tươi',  N'Súp lơ xanh tươi giòn giàu chất xơ và vitamin',            'CHILLED', 2, N'Vegan,EatClean,LowCalorie',   NULL, '/images/products/sup-lo-xanh.jpg'),    -- id = 6
('PRD-CUCUMBER',       N'Dưa leo baby Sơn La',                  N'Rau củ tươi',  N'Dưa leo baby giòn ngọt ăn liền hoặc làm salad',            'CHILLED', 2, N'Vegan,EatClean,LowCalorie',   NULL, '/images/products/dua-leo.jpg'),        -- id = 7
('PRD-MORNING-GLORY',  N'Rau muống cạn hữu cơ Trâu Quỳ',       N'Rau củ tươi',  N'Rau muống non nhặt sạch đóng túi 500g',                    'CHILLED', 1, N'Vegan,HighFiber',             NULL, '/images/products/rau-muong.jpg'),      -- id = 8
('PRD-TOMATO',         N'Cà chua chín cây Đà Lạt',              N'Rau củ tươi',  N'Cà chua chín mọng dùng nấu canh, sốt đậu phụ',             'CHILLED', 3, N'Vegan',                       NULL, '/images/products/ca-chua.jpg'),        -- id = 9
('PRD-ROCK-APPLE',     N'Táo đá giòn ngọt Hà Giang',            N'Trái cây',     N'Táo đá quả nhỏ giòn tan, ngọt thanh',                      'CHILLED', 5, N'Vegan,LowCalorie',            NULL, '/images/products/tao-da.jpg');         -- id = 10

-- 7. ProductVariants (Quy cách đóng gói cố định để tính tiền chính xác 100% trước khi thanh toán)
INSERT INTO dbo.product_variants (product_id, sku, pack_name, pack_weight_grams, unit_price) VALUES
(1,  'SKU-CHICKEN-500G',  N'Khay 500g',          500,  45000.00), -- id = 1 (Ức gà 500g)
(2,  'SKU-PORK-500G',     N'Khay 500g',          500,  75000.00), -- id = 2 (Ba chỉ heo 500g)
(3,  'SKU-BEEF-300G',     N'Khay 300g',          300,  89000.00), -- id = 3 (Bắp bò 300g)
(4,  'SKU-EGG-10PCS',     N'Hộp 10 quả (600g)',  600,  32000.00), -- id = 4 (Trứng gà 10 quả)
(5,  'SKU-TOFU-400G',     N'Hộp 2 bìa (400g)',   400,  15000.00), -- id = 5 (Đậu trắng 400g)
(6,  'SKU-BROCCOLI-500G', N'Túi 500g',           500,  28000.00), -- id = 6 (Súp lơ 500g)
(7,  'SKU-CUCUMBER-500G', N'Túi 500g',           500,  18000.00), -- id = 7 (Dưa leo 500g)
(8,  'SKU-MGLORY-500G',   N'Túi 500g',           500,  14000.00), -- id = 8 (Rau muống 500g)
(9,  'SKU-TOMATO-500G',   N'Túi 500g',           500,  20000.00), -- id = 9 (Cà chua 500g)
(10, 'SKU-APPLE-1KG',     N'Túi 1kg',           1000,  38000.00); -- id = 10 (Táo đá 1kg)

-- 8. Lots (Nhiều lô có hạn sử dụng khác nhau để kiểm thử thuật toán FEFO)
INSERT INTO dbo.lots (variant_id, lot_code, supplier_name, manufactured_date, expiry_date, initial_qty, remaining_qty, status, received_at) VALUES
(1,  'LOT-CHK-261116-A', N'Công ty CP Thực phẩm CP Việt Nam', '2026-11-16', '2026-11-20', 80,  45, 'AVAILABLE',   '2026-11-16T06:00:00+07:00'), -- id = 1 (Hết hạn sớm hơn -> ưu tiên xuất FEFO)
(1,  'LOT-CHK-261117-B', N'Công ty CP Thực phẩm CP Việt Nam', '2026-11-17', '2026-11-23', 100, 100,'AVAILABLE',   '2026-11-17T06:00:00+07:00'), -- id = 2 (Hết hạn muộn hơn)
(2,  'LOT-PRK-261116-A', N'Nhà máy Thịt mát MEATDeli Hà Nam', '2026-11-16', '2026-11-21', 60,  38, 'AVAILABLE',   '2026-11-16T06:30:00+07:00'), -- id = 3
(3,  'LOT-BEF-261115-A', N'Thực phẩm Nhập khẩu Hải Đăng',     '2026-11-15', '2026-11-22', 50,  30, 'AVAILABLE',   '2026-11-15T07:00:00+07:00'), -- id = 4
(4,  'LOT-EGG-261114-A', N'Tập đoàn Dabaco Bắc Ninh',         '2026-11-14', '2026-11-28', 150, 95, 'AVAILABLE',   '2026-11-14T08:00:00+07:00'), -- id = 5
(5,  'LOT-TOF-261117-A', N'Cơ sở Đậu sạch Làng Mơ',           '2026-11-17', '2026-11-20', 80,  55, 'AVAILABLE',   '2026-11-17T05:30:00+07:00'), -- id = 6
(6,  'LOT-BRC-261115-Q', N'HTX Rau sạch Đà Lạt',              '2026-11-15', '2026-11-18', 30,  10, 'QUARANTINED', '2026-11-15T06:00:00+07:00'), -- id = 7 (Lô bị khóa cách ly -> FEFO phải bỏ qua)
(6,  'LOT-BRC-261117-A', N'HTX Rau sạch Đà Lạt',              '2026-11-17', '2026-11-22', 90,  62, 'AVAILABLE',   '2026-11-17T06:00:00+07:00'), -- id = 8 (Lô hợp lệ cho Súp lơ)
(7,  'LOT-CUC-261117-A', N'Nông trại Xanh Mộc Châu',          '2026-11-17', '2026-11-22', 80,  60, 'AVAILABLE',   '2026-11-17T06:00:00+07:00'), -- id = 9
(8,  'LOT-MGL-261117-A', N'HTX Rau hữu cơ Trâu Quỳ',          '2026-11-17', '2026-11-20', 70,  48, 'AVAILABLE',   '2026-11-17T05:30:00+07:00'), -- id = 10
(9,  'LOT-TOM-261116-A', N'HTX Rau sạch Đà Lạt',              '2026-11-16', '2026-11-23', 80,  65, 'AVAILABLE',   '2026-11-16T06:00:00+07:00'), -- id = 11
(10, 'LOT-APL-261112-A', N'Nông sản sạch Hà Giang',           '2026-11-12', '2026-11-26', 60,  42, 'AVAILABLE',   '2026-11-12T09:00:00+07:00'); -- id = 12

-- 9. OrderCycles (3 đợt đặt hàng: Đợt đã đóng ngày 16/11 nhận 17/11, Đợt đang giao nhận hôm nay 17/11, và Đợt đang mở đặt hôm nay 17/11 nhận 18/11)
INSERT INTO dbo.order_cycles (
    cycle_code, delivery_date, open_at, cutoff_at, auto_confirm_at,
    warehouse_pack_target_at, dispatch_start_at, agent_handover_deadline_at,
    pickup_start_at, auto_complete_at, payment_timeout_minutes, status
) VALUES
-- Cycle 1 (id = 1): Đặt ngày 15/11, giao 16/11, tự hoàn tất 00:00 ngày 17/11 (Đã hoàn tất toàn bộ chu trình)
('CYCLE-2026-11-16', '2026-11-16',
 '2026-11-15T00:00:00+07:00', '2026-11-15T23:59:59+07:00', '2026-11-16T00:00:00+07:00',
 '2026-11-16T08:00:00+07:00', '2026-11-16T08:00:00+07:00', '2026-11-16T16:00:00+07:00',
 '2026-11-16T16:00:00+07:00', '2026-11-17T00:00:00+07:00', 30, 'CLOSED'),

-- Cycle 2 (id = 2): Đặt ngày 16/11, giao hôm nay 17/11 từ 16:00, sẽ tự hoàn tất lúc 00:00 ngày 18/11 (Đang ở giai đoạn khách ra đại lý nhận hàng)
('CYCLE-2026-11-17', '2026-11-17',
 '2026-11-16T00:00:00+07:00', '2026-11-16T23:59:59+07:00', '2026-11-17T00:00:00+07:00',
 '2026-11-17T08:00:00+07:00', '2026-11-17T08:00:00+07:00', '2026-11-17T16:00:00+07:00',
 '2026-11-17T16:00:00+07:00', '2026-11-18T00:00:00+07:00', 30, 'FULFILLING'),

-- Cycle 3 (id = 3): Đặt hôm nay 17/11 trước 23:59:59, nhận từ 16:00 ngày mai 18/11, tự hoàn tất lúc 00:00 ngày 19/11 (Đang mở cho khách đặt)
('CYCLE-2026-11-18', '2026-11-18',
 '2026-11-17T00:00:00+07:00', '2026-11-17T23:59:59+07:00', '2026-11-18T00:00:00+07:00',
 '2026-11-18T08:00:00+07:00', '2026-11-18T08:00:00+07:00', '2026-11-18T16:00:00+07:00',
 '2026-11-18T16:00:00+07:00', '2026-11-19T00:00:00+07:00', 30, 'OPEN');

-- 10. PointCycles
INSERT INTO dbo.point_cycles (cycle_id, pickup_point_id, max_orders, current_orders, status) VALUES
(1, 1, 120, 3, 'CLOSED'), (1, 2, 150, 0, 'CLOSED'), (1, 3, 100, 0, 'CLOSED'),
(2, 1, 120, 1, 'OPEN'),   (2, 2, 150, 1, 'OPEN'),   (2, 3, 100, 0, 'OPEN'),
(3, 1, 120, 2, 'OPEN'),   (3, 2, 150, 1, 'OPEN'),   (3, 3, 100, 0, 'OPEN');

-- 11. InventoryBalances (Tồn kho theo đợt)
INSERT INTO dbo.inventory_balances (cycle_id, variant_id, available_qty, reserved_qty, committed_qty) VALUES
-- Cycle 3 (Đang mở bán hôm nay 17/11 cho ngày nhận 18/11)
(3, 1, 120, 2, 5), -- Ức gà 500g
(3, 2,  35, 0, 2), -- Ba chỉ heo 500g
(3, 3,  28, 0, 1), -- Bắp bò 300g
(3, 4,  85, 1, 4), -- Trứng gà 10 quả
(3, 5,  50, 0, 3), -- Đậu trắng 400g
(3, 6,  55, 1, 3), -- Súp lơ xanh 500g
(3, 7,  58, 0, 2), -- Dưa leo 500g
(3, 8,  45, 0, 2), -- Rau muống 500g
(3, 9,  60, 0, 2), -- Cà chua 500g
(3, 10, 40, 0, 2); -- Táo đá 1kg

-- 12. CommissionPolicies (Chính sách hoa hồng có đánh phiên bản)
INSERT INTO dbo.commission_policies (policy_code, name, calc_type, fixed_amount, percentage_rate, effective_from, is_active) VALUES
('POL-FIXED-5K-2026', N'Hoa hồng cố định 5.000đ trên mỗi đơn hoàn tất', 'FIXED_PER_ORDER', 5000.00, NULL, '2026-01-01T00:00:00+07:00', 1), -- id = 1
('POL-PCT-5PCT-2026', N'Hoa hồng 5% giá trị tiền hàng thực thu',         'PERCENTAGE_NET_MERCHANDISE', NULL, 5.00, '2026-01-01T00:00:00+07:00', 0); -- id = 2

-- 13. Carts & CartItems (Giỏ hàng đang chọn của khách Hưng cho đợt CYCLE-2026-11-18)
INSERT INTO dbo.carts (customer_id, pickup_point_id, cycle_id) VALUES
(9, 1, 3); -- id = 1 (Khách Nông Thế Hưng chọn Điểm nhận Nhổn - đợt giao 18/11)

INSERT INTO dbo.cart_items (cart_id, variant_id, quantity) VALUES
(1, 1, 2), -- 2 khay Ức gà 500g
(1, 5, 1), -- 1 hộp Đậu trắng 400g
(1, 6, 1), -- 1 túi Súp lơ xanh 500g
(1, 10, 1);-- 1 túi Táo đá 1kg

-- 14. Orders (7 đơn hàng mô phỏng đầy đủ mọi tình huống nghiệp vụ & ngoại lệ trong đề tài)
INSERT INTO dbo.orders (
    order_code, customer_id, pickup_point_id, cycle_id, commission_policy_id,
    subtotal_amount, discount_amount, total_amount,
    order_status, fulfillment_status, payment_status,
    cutoff_at, pickup_start_at, auto_complete_at,
    confirmed_at, completed_at, completion_type,
    customer_confirmed_at, physical_handed_over_at,
    pickup_qr_token_hash, pickup_qr_used_at,
    has_open_issue, is_unclaimed_at_agent, cancel_reason, created_at
) VALUES
-- Đơn 1 (id = 1): Chu trình chuẩn hoàn chỉnh (Khách Hưng nhận tại Đại lý Nhổn, quét QR và bấm Đã nhận hàng -> CUSTOMER_CONFIRMED)
('ORD-261116-0001', 9, 1, 1, 1,
 136000.00, 0.00, 136000.00,
 'COMPLETED', 'HANDED_OVER', 'PAID',
 '2026-11-15T23:59:59+07:00', '2026-11-16T16:00:00+07:00', '2026-11-17T00:00:00+07:00',
 '2026-11-16T00:00:02+07:00', '2026-11-16T17:35:10+07:00', 'CUSTOMER_CONFIRMED',
 '2026-11-16T17:35:10+07:00', '2026-11-16T17:34:00+07:00',
 'SHA256_QR_TOKEN_ORD_0001', '2026-11-16T17:34:00+07:00',
 0, 0, NULL, '2026-11-15T19:20:00+07:00'),

-- Đơn 2 (id = 2): Đơn tự hoàn tất lúc 00:00 D+2 (SYSTEM_TIMEOUT) nhưng khách Mạnh chưa đến lấy hàng (is_unclaimed_at_agent = 1, physical_handed_over_at = NULL)
('ORD-261116-0002', 10, 1, 1, 1,
 93000.00, 0.00, 93000.00,
 'COMPLETED', 'READY_FOR_PICKUP', 'PAID',
 '2026-11-15T23:59:59+07:00', '2026-11-16T16:00:00+07:00', '2026-11-17T00:00:00+07:00',
 '2026-11-16T00:00:02+07:00', '2026-11-17T00:00:05+07:00', 'SYSTEM_TIMEOUT',
 NULL, NULL,
 'SHA256_QR_TOKEN_ORD_0002', NULL,
 0, 1, NULL, '2026-11-15T20:10:00+07:00'),

-- Đơn 3 (id = 3): Đơn đã hoàn tất nhưng khách Kiên báo hàng lỗi dập hỏng -> Đã thu hồi về kho cách ly, hoàn tiền 100% và ghi bút toán âm thu hồi hoa hồng
('ORD-261116-0003', 11, 1, 1, 1,
 75000.00, 0.00, 75000.00,
 'COMPLETED', 'HANDED_OVER', 'REFUNDED',
 '2026-11-15T23:59:59+07:00', '2026-11-16T16:00:00+07:00', '2026-11-17T00:00:00+07:00',
 '2026-11-16T00:00:02+07:00', '2026-11-16T18:05:00+07:00', 'CUSTOMER_CONFIRMED',
 '2026-11-16T18:05:00+07:00', '2026-11-16T18:00:00+07:00',
 'SHA256_QR_TOKEN_ORD_0003', '2026-11-16T18:00:00+07:00',
 0, 0, NULL, '2026-11-15T21:00:00+07:00'),

-- Đơn 4 (id = 4): Đơn đợt hôm nay (CYCLE-2026-11-17) của khách Hưng đã về Đại lý Nhổn, kiểm nhận đủ 2 kiện, đang READY_FOR_PICKUP chờ khách ra quét QR
('ORD-261117-0004', 9, 1, 2, 1,
 151000.00, 0.00, 151000.00,
 'PROCESSING', 'READY_FOR_PICKUP', 'PAID',
 '2026-11-16T23:59:59+07:00', '2026-11-17T16:00:00+07:00', '2026-11-18T00:00:00+07:00',
 '2026-11-17T00:00:01+07:00', NULL, NULL,
 NULL, NULL,
 'SHA256_QR_TOKEN_ORD_0004', NULL,
 0, 0, NULL, '2026-11-16T18:45:00+07:00'),

-- Đơn 5 (id = 5): Đơn đợt hôm nay (CYCLE-2026-11-17) tại Đại lý Cầu Giấy bị dập hỏng 1 hộp trứng khi tài xế giao -> Đang giữ ở ISSUE_HOLD (chặn tự hoàn tất lúc 00:00)
('ORD-261117-0005', 12, 2, 2, 1,
 64000.00, 0.00, 64000.00,
 'PROCESSING', 'ISSUE_HOLD', 'PAID',
 '2026-11-16T23:59:59+07:00', '2026-11-17T16:00:00+07:00', '2026-11-18T00:00:00+07:00',
 '2026-11-17T00:00:01+07:00', NULL, NULL,
 NULL, NULL,
 'SHA256_QR_TOKEN_ORD_0005', NULL,
 1, 0, NULL, '2026-11-16T21:15:00+07:00'),

-- Đơn 6 (id = 6): Đơn đợt ngày mai (CYCLE-2026-11-18) khách Hưng vừa đặt và thanh toán VNPAY thành công -> PAID_WAITING_CUTOFF (vẫn được phép tự hủy trước 23:59:59 hôm nay)
('ORD-261118-0006', 9, 1, 3, 1,
 135000.00, 0.00, 135000.00,
 'PAID_WAITING_CUTOFF', 'WAITING_PACKING', 'PAID',
 '2026-11-17T23:59:59+07:00', '2026-11-18T16:00:00+07:00', '2026-11-19T00:00:00+07:00',
 NULL, NULL, NULL,
 NULL, NULL,
 'SHA256_QR_TOKEN_ORD_0006', NULL,
 0, 0, NULL, '2026-11-17T20:05:00+07:00'),

-- Đơn 7 (id = 7): Đơn đợt ngày mai (CYCLE-2026-11-18) khách Mạnh đã thanh toán nhưng tự bấm hủy lúc 21:30 (trước hạn 23:59) -> CANCELLED & hoàn tiền 100%
('ORD-261118-0007', 10, 2, 3, 1,
 89000.00, 0.00, 89000.00,
 'CANCELLED', 'CANCELLED', 'REFUNDED',
 '2026-11-17T23:59:59+07:00', '2026-11-18T16:00:00+07:00', '2026-11-19T00:00:00+07:00',
 NULL, NULL, NULL,
 NULL, NULL,
 NULL, NULL,
 0, 0, N'Khách hàng tự hủy đơn trước giờ chốt 23:59', '2026-11-17T21:00:00+07:00');

-- 15. OrderItems
INSERT INTO dbo.order_items (order_id, variant_id, product_name_snapshot, pack_name_snapshot, unit_price_snapshot, quantity, line_total) VALUES
-- Đơn 1 (136.000đ: 2 Ức gà 500g + 1 Súp lơ 500g + 1 Dưa leo 500g)
(1, 1, N'Ức gà phi lê tươi sạch chuẩn VietGAP', N'Khay 500g', 45000.00, 2, 90000.00),
(1, 6, N'Súp lơ xanh Đà Lạt VietGAP',          N'Túi 500g',  28000.00, 1, 28000.00),
(1, 7, N'Dưa leo baby Sơn La',                  N'Túi 500g',  18000.00, 1, 18000.00),
-- Đơn 2 (93.000đ: 1 Ba chỉ heo 500g + 1 Dưa leo 500g)
(2, 2, N'Thịt ba chỉ heo mảnh mát MEATDeli',    N'Khay 500g', 75000.00, 1, 75000.00),
(2, 7, N'Dưa leo baby Sơn La',                  N'Túi 500g',  18000.00, 1, 18000.00),
-- Đơn 3 (75.000đ: 1 Ba chỉ heo 500g)
(3, 2, N'Thịt ba chỉ heo mảnh mát MEATDeli',    N'Khay 500g', 75000.00, 1, 75000.00),
-- Đơn 4 (151.000đ: 2 Ức gà 500g + 1 Đậu trắng 400g + 1 Súp lơ 500g + 1 Dưa leo 500g)
(4, 1, N'Ức gà phi lê tươi sạch chuẩn VietGAP', N'Khay 500g', 45000.00, 2, 90000.00),
(4, 5, N'Đậu phụ trắng non làng Mơ',            N'Hộp 2 bìa (400g)', 15000.00, 1, 15000.00),
(4, 6, N'Súp lơ xanh Đà Lạt VietGAP',          N'Túi 500g',  28000.00, 1, 28000.00),
(4, 7, N'Dưa leo baby Sơn La',                  N'Túi 500g',  18000.00, 1, 18000.00),
-- Đơn 5 (64.000đ: 2 Hộp trứng gà Dabaco 10 quả)
(5, 4, N'Trứng gà sạch Dabaco',                 N'Hộp 10 quả (600g)', 32000.00, 2, 64000.00),
-- Đơn 6 (135.000đ: 3 Ức gà 500g)
(6, 1, N'Ức gà phi lê tươi sạch chuẩn VietGAP', N'Khay 500g', 45000.00, 3, 135000.00),
-- Đơn 7 (89.000đ: 1 Bắp bò Úc 300g)
(7, 3, N'Thịt bắp bò Úc thái lát',              N'Khay 300g', 89000.00, 1, 89000.00);

-- 16. OrderLotAllocations (Ghi nhận xuất lô theo đúng chuẩn FEFO)
INSERT INTO dbo.order_lot_allocations (order_item_id, lot_id, allocated_qty, allocated_at) VALUES
(1, 1, 2, '2026-11-16T05:10:00+07:00'), -- Xuất từ LOT-CHK-261116-A (hết hạn 20/11 trước lô 23/11)
(2, 8, 1, '2026-11-16T05:10:00+07:00'), -- Xuất từ LOT-BRC-261117-A (bỏ qua lô QUARANTINED id=7)
(3, 9, 1, '2026-11-16T05:10:00+07:00'),
(4, 3, 1, '2026-11-16T05:12:00+07:00'),
(5, 9, 1, '2026-11-16T05:12:00+07:00'),
(6, 3, 1, '2026-11-16T05:15:00+07:00'),
(7, 1, 2, '2026-11-17T05:20:00+07:00'),
(8, 6, 1, '2026-11-17T05:20:00+07:00'),
(9, 8, 1, '2026-11-17T05:20:00+07:00'),
(10, 9, 1, '2026-11-17T05:20:00+07:00'),
(11, 5, 2, '2026-11-17T05:25:00+07:00');

-- 17. Reservations (Giữ tồn kho)
INSERT INTO dbo.reservations (order_id, cycle_id, variant_id, reserved_qty, status, expires_at, created_at) VALUES
(1, 1, 1, 2, 'COMMITTED', '2026-11-15T19:50:00+07:00', '2026-11-15T19:20:00+07:00'),
(2, 1, 2, 1, 'COMMITTED', '2026-11-15T20:40:00+07:00', '2026-11-15T20:10:00+07:00'),
(3, 1, 2, 1, 'COMMITTED', '2026-11-15T21:30:00+07:00', '2026-11-15T21:00:00+07:00'),
(4, 2, 1, 2, 'COMMITTED', '2026-11-16T19:15:00+07:00', '2026-11-16T18:45:00+07:00'),
(5, 2, 4, 2, 'COMMITTED', '2026-11-16T21:45:00+07:00', '2026-11-16T21:15:00+07:00'),
(6, 3, 1, 3, 'COMMITTED', '2026-11-17T20:35:00+07:00', '2026-11-17T20:05:00+07:00'),
(7, 3, 3, 1, 'RELEASED',  '2026-11-17T21:30:00+07:00', '2026-11-17T21:00:00+07:00');

-- 18. Trips & TripStops (Chuyến giao hàng từ kho lúc 08:00 và chuyến thu hồi hàng hoàn)
INSERT INTO dbo.trips (trip_code, cycle_id, driver_id, trip_type, status, departed_at, completed_at) VALUES
('TRIP-DEL-261116-01', 1, 4, 'DELIVERY',      'COMPLETED', '2026-11-16T08:05:00+07:00', '2026-11-16T14:30:00+07:00'), -- id = 1
('TRIP-RET-261116-01', 1, 4, 'RETURN_PICKUP', 'COMPLETED', '2026-11-16T19:00:00+07:00', '2026-11-16T20:30:00+07:00'), -- id = 2 (Chuyến thu hồi hàng lỗi Đơn 3)
('TRIP-DEL-261117-01', 2, 4, 'DELIVERY',      'COMPLETED', '2026-11-17T08:10:00+07:00', '2026-11-17T15:10:00+07:00'); -- id = 3 (Chuyến giao hôm nay 17/11)

INSERT INTO dbo.trip_stops (trip_id, pickup_point_id, stop_sequence, eta_at, arrived_at, handover_completed_at, status) VALUES
(1, 1, 1, '2026-11-16T09:30:00+07:00', '2026-11-16T09:25:00+07:00', '2026-11-16T09:45:00+07:00', 'HANDOVER_COMPLETED'), -- id = 1
(2, 1, 1, '2026-11-16T19:30:00+07:00', '2026-11-16T19:28:00+07:00', '2026-11-16T19:40:00+07:00', 'HANDOVER_COMPLETED'), -- id = 2
(3, 1, 1, '2026-11-17T09:30:00+07:00', '2026-11-17T09:20:00+07:00', '2026-11-17T09:40:00+07:00', 'HANDOVER_COMPLETED'), -- id = 3 (Điểm Nhổn hôm nay)
(3, 2, 2, '2026-11-17T11:00:00+07:00', '2026-11-17T10:50:00+07:00', '2026-11-17T11:15:00+07:00', 'HANDOVER_COMPLETED'); -- id = 4 (Điểm Cầu Giấy hôm nay)

-- 19. Shipments & Packages (Bao gồm đơn nhiều kiện và kiện hàng bị dập hỏng)
INSERT INTO dbo.shipments (shipment_code, order_id, trip_stop_id, status) VALUES
('SHP-261116-0001', 1, 1, 'HANDED_TO_CUSTOMER'), -- id = 1
('SHP-261116-0002', 2, 1, 'DELIVERED_TO_AGENT'), -- id = 2
('SHP-261116-0003', 3, 1, 'HANDED_TO_CUSTOMER'), -- id = 3
('SHP-261117-0004', 4, 3, 'DELIVERED_TO_AGENT'), -- id = 4 (Đơn 4 của khách Hưng gồm 2 kiện: 1 kiện mát, 1 kiện rau)
('SHP-261117-0005', 5, 4, 'PARTIAL_OR_DAMAGED'); -- id = 5 (Đơn 5 bị dập vỡ khi giao đến đại lý Cầu Giấy)

INSERT INTO dbo.packages (order_id, shipment_id, trip_stop_id, package_code, qr_payload, package_seq, total_packages_in_order, storage_type, status) VALUES
(1, 1, 1, 'PKG-261116-0001-1OF1', 'O2O|PKG|PKG-261116-0001-1OF1|ORD-261116-0001', 1, 1, 'CHILLED', 'HANDED_TO_CUSTOMER'),     -- id = 1
(2, 2, 1, 'PKG-261116-0002-1OF1', 'O2O|PKG|PKG-261116-0002-1OF1|ORD-261116-0002', 1, 1, 'CHILLED', 'DELIVERED_TO_AGENT'),     -- id = 2
(3, 3, 1, 'PKG-261116-0003-1OF1', 'O2O|PKG|PKG-261116-0003-1OF1|ORD-261116-0003', 1, 1, 'CHILLED', 'RETURNED_TO_WAREHOUSE'), -- id = 3
(4, 4, 3, 'PKG-261117-0004-1OF2', 'O2O|PKG|PKG-261117-0004-1OF2|ORD-261117-0004', 1, 2, 'CHILLED', 'DELIVERED_TO_AGENT'),     -- id = 4 (Kiện 1/2 của Đơn 4)
(4, 4, 3, 'PKG-261117-0004-2OF2', 'O2O|PKG|PKG-261117-0004-2OF2|ORD-261117-0004', 2, 2, 'CHILLED', 'DELIVERED_TO_AGENT'),     -- id = 5 (Kiện 2/2 của Đơn 4)
(5, 5, 4, 'PKG-261117-0005-1OF1', 'O2O|PKG|PKG-261117-0005-1OF1|ORD-261117-0005', 1, 1, 'AMBIENT', 'DAMAGED');                -- id = 6 (Kiện trứng bị dập vỡ)

-- 20. HandoverRecords & HandoverItems (Biên bản bàn giao Kho -> Tài xế & Tài xế -> Đại lý)
INSERT INTO dbo.handover_records (handover_type, trip_stop_id, from_user_id, to_user_id, total_expected_packages, accepted_packages, missing_packages, damaged_packages, note, signed_at) VALUES
('WAREHOUSE_TO_DRIVER', NULL, 2, 4, 3, 3, 0, 0, N'Bàn giao đủ 3 kiện đợt 16/11 tại Kho trung tâm lúc 08:00', '2026-11-16T08:00:00+07:00'), -- id = 1
('DRIVER_TO_AGENT',     1,    4, 6, 3, 3, 0, 0, N'Đại lý Cô Lan Nhổn kiểm nhận đủ 3 kiện nguyên vẹn',         '2026-11-16T09:45:00+07:00'), -- id = 2
('DRIVER_TO_AGENT',     3,    4, 6, 2, 2, 0, 0, N'Đại lý Cô Lan Nhổn nhận đủ 2 kiện của đơn ORD-261117-0004', '2026-11-17T09:40:00+07:00'), -- id = 3
('DRIVER_TO_AGENT',     4,    4, 7, 1, 0, 0, 1, N'Đại lý Chú Minh Cầu Giấy ghi nhận 1 kiện trứng bị dập vỡ',  '2026-11-17T11:15:00+07:00'); -- id = 4

INSERT INTO dbo.handover_items (handover_record_id, package_id, condition_status, evidence_image_url, remark) VALUES
(2, 1, 'ACCEPTED', NULL, N'Nguyên tem niêm phong'),
(2, 2, 'ACCEPTED', NULL, N'Nguyên tem niêm phong'),
(2, 3, 'ACCEPTED', NULL, N'Nguyên tem niêm phong'),
(3, 4, 'ACCEPTED', NULL, N'Kiện 1/2 nguyên vẹn'),
(3, 5, 'ACCEPTED', NULL, N'Kiện 2/2 nguyên vẹn'),
(4, 6, 'DAMAGED',  '/uploads/evidence/dmg-pkg-0005.jpg', N'Vỡ 4 quả trứng trong hộp khi vận chuyển');

-- 21. Issues, ReturnRequests, ReturnItems, ReturnShipments (Hồ sơ sự cố & thu hồi hàng lỗi về kho cách ly)
INSERT INTO dbo.issues (issue_code, order_id, reported_by_user_id, reporter_role, issue_type, description, evidence_urls, status, resolution_note, created_at, resolved_at) VALUES
('ISS-261116-01', 3, 11, 'ROLE_CUSTOMER', 'QUALITY_COMPLAINT', N'Khay thịt ba chỉ có mùi lạ khi mở hộp tại điểm nhận', '["/uploads/issues/iss-0003.jpg"]', 'RESOLVED', N'Đã thu hồi về kho lỗi và hoàn tiền 100%', '2026-11-16T18:15:00+07:00', '2026-11-16T21:00:00+07:00'), -- id = 1
('ISS-261117-02', 5, 7,  'ROLE_AGENT',    'DAMAGED',           N'Kiện trứng gà bị dập vỡ khi tài xế bàn giao tại đại lý Cầu Giấy', '["/uploads/evidence/dmg-pkg-0005.jpg"]', 'INVESTIGATING', NULL, '2026-11-17T11:16:00+07:00', NULL); -- id = 2

INSERT INTO dbo.return_requests (return_code, issue_id, order_id, pickup_point_id, status, warehouse_inspection_note, created_at) VALUES
('RET-261116-01', 1, 3, 1, 'CLOSED', N'Kho đã nhận hàng hoàn và đưa vào tủ cách ly hàng hủy QUARANTINE-BIN-A1, không cộng lại tồn bán', '2026-11-16T18:20:00+07:00'), -- id = 1
('RET-261117-02', 2, 5, 2, 'APPROVED_FOR_PICKUP', NULL, '2026-11-17T11:20:00+07:00'); -- id = 2

INSERT INTO dbo.return_items (return_request_id, order_item_id, quantity, condition_note, quarantine_bin_code) VALUES
(1, 6,  1, N'Khay thịt ba chỉ lỗi bảo quản', 'QUARANTINE-BIN-A1'),
(2, 11, 2, N'Hộp trứng gà vỡ vỏ',           'QUARANTINE-BIN-B2');

INSERT INTO dbo.return_shipments (return_shipment_code, return_request_id, trip_id, picked_up_from_agent_at, received_at_warehouse_at, status) VALUES
('RSHP-261116-01', 1, 2, '2026-11-16T19:40:00+07:00', '2026-11-16T20:25:00+07:00', 'DELIVERED_TO_WAREHOUSE');

-- 22. PaymentAttempts & PaymentEvents (Giao dịch VNPAY Sandbox & IPN Webhook)
INSERT INTO dbo.payment_attempts (order_id, txn_ref, provider, amount, status, vnp_transaction_no, vnp_bank_code, vnp_pay_date, created_at, confirmed_at) VALUES
(1, 'VNP2611150001', 'VNPAY', 136000.00, 'SUCCESS', '14589001', 'NCB', '20261115192215', '2026-11-15T19:20:00+07:00', '2026-11-15T19:22:16+07:00'), -- id = 1
(2, 'VNP2611150002', 'VNPAY',  93000.00, 'SUCCESS', '14589002', 'NCB', '20261115201210', '2026-11-15T20:10:00+07:00', '2026-11-15T20:12:11+07:00'), -- id = 2
(3, 'VNP2611150003', 'VNPAY',  75000.00, 'SUCCESS', '14589003', 'NCB', '20261115210300', '2026-11-15T21:00:00+07:00', '2026-11-15T21:03:01+07:00'), -- id = 3
(4, 'VNP2611160004', 'VNPAY', 151000.00, 'SUCCESS', '14589004', 'VCB', '20261116184700', '2026-11-16T18:45:00+07:00', '2026-11-16T18:47:02+07:00'), -- id = 4
(5, 'VNP2611160005', 'VNPAY',  64000.00, 'SUCCESS', '14589005', 'TCB', '20261116211700', '2026-11-16T21:15:00+07:00', '2026-11-16T21:17:02+07:00'), -- id = 5
(6, 'VNP2611170006', 'VNPAY', 135000.00, 'SUCCESS', '14589006', 'MBB', '20261117200630', '2026-11-17T20:05:00+07:00', '2026-11-17T20:06:31+07:00'), -- id = 6
(7, 'VNP2611170007', 'VNPAY',  89000.00, 'SUCCESS', '14589007', 'NCB', '20261117210145', '2026-11-17T21:00:00+07:00', '2026-11-17T21:01:46+07:00'); -- id = 7

INSERT INTO dbo.payment_events (payment_attempt_id, event_source, vnp_response_code, raw_payload, signature_valid) VALUES
(1, 'IPN_WEBHOOK', '00', N'{"vnp_TxnRef":"VNP2611150001","vnp_Amount":"13600000","vnp_ResponseCode":"00"}', 1),
(4, 'IPN_WEBHOOK', '00', N'{"vnp_TxnRef":"VNP2611160004","vnp_Amount":"15100000","vnp_ResponseCode":"00"}', 1),
(6, 'IPN_WEBHOOK', '00', N'{"vnp_TxnRef":"VNP2611170006","vnp_Amount":"13500000","vnp_ResponseCode":"00"}', 1);

-- 23. Refunds (Hoàn tiền cho Đơn 3 bị lỗi đã thu hồi về kho và Đơn 7 khách tự hủy trước 23:59)
INSERT INTO dbo.refunds (refund_code, order_id, return_request_id, refund_reason, amount, status, provider_ref, approved_by, created_at, completed_at) VALUES
('RFD-261116-0001', 3, 1,    'ORDER_ISSUE_RETURN',            75000.00, 'COMPLETED', 'VNP_RFD_99001', 1, '2026-11-16T20:30:00+07:00', '2026-11-16T21:00:00+07:00'),
('RFD-261117-0002', 7, NULL, 'CUSTOMER_CANCEL_BEFORE_CUTOFF', 89000.00, 'COMPLETED', 'VNP_RFD_99002', 1, '2026-11-17T21:30:00+07:00', '2026-11-17T21:35:00+07:00');

-- 24. SettlementBatches & CommissionLedger (Sổ cái hoa hồng chỉ thêm dòng - Append-only)
INSERT INTO dbo.settlement_batches (batch_code, pickup_point_id, period_start, period_end, total_credit, total_debit, net_payout_amount, status) VALUES
('STL-2026-11-W3-PP1', 1, '2026-11-15', '2026-11-21', 15000.00, -5000.00, 10000.00, 'OPEN'); -- id = 1

INSERT INTO dbo.commission_ledger (pickup_point_id, order_id, policy_id, entry_type, amount, commission_status, settlement_batch_id, note, created_at) VALUES
(1, 1, 1, 'CREDIT_COMMISSION',      5000.00, 'RECORDED', 1, N'Hoa hồng đơn ORD-261116-0001 (Khách xác nhận CUSTOMER_CONFIRMED)', '2026-11-16T17:35:10+07:00'),
(1, 2, 1, 'CREDIT_COMMISSION',      5000.00, 'RECORDED', 1, N'Hoa hồng đơn ORD-261116-0002 (Tự hoàn tất 00:00 SYSTEM_TIMEOUT)',  '2026-11-17T00:00:05+07:00'),
(1, 3, 1, 'CREDIT_COMMISSION',      5000.00, 'ADJUSTED', 1, N'Hoa hồng đơn ORD-261116-0003 khi khách nhận ban đầu',              '2026-11-16T18:05:00+07:00'),
(1, 3, 1, 'DEBIT_REFUND_REVERSAL', -5000.00, 'ADJUSTED', 1, N'Bút toán điều chỉnh âm do đơn ORD-261116-0003 hoàn hàng và hoàn tiền','2026-11-16T21:00:05+07:00');

-- 25. StockMovements, Shedlock, JobRuns, OutboxEvents, Notifications, AuditLogs
INSERT INTO dbo.stock_movements (variant_id, lot_id, order_id, movement_type, quantity, note, created_by) VALUES
(1, 1, 1, 'ALLOCATE_FEFO',        -2, N'Phân bổ FEFO cho đơn ORD-261116-0001', 2),
(2, 3, 3, 'ALLOCATE_FEFO',        -1, N'Phân bổ FEFO cho đơn ORD-261116-0003', 2),
(2, 3, 3, 'RETURN_TO_QUARANTINE',  1, N'Nhập hàng hoàn lỗi vào khu cách ly QUARANTINE-BIN-A1 (không cộng tồn bán)', 2);

INSERT INTO dbo.shedlock (name, lock_until, locked_at, locked_by) VALUES
('AutoConfirmOrdersJob',  '2026-11-17T00:01:00', '2026-11-17T00:00:00', 'api-node-1'),
('AutoCompleteOrdersJob', '2026-11-17T00:01:00', '2026-11-17T00:00:05', 'api-node-1'),
('ReservationCleanupJob', '2026-11-17T23:25:00', '2026-11-17T23:24:00', 'api-node-1');

INSERT INTO dbo.job_runs (job_name, cycle_id, started_at, finished_at, processed_count, status) VALUES
('AutoConfirmOrdersJob',  2, '2026-11-17T00:00:00+07:00', '2026-11-17T00:00:02+07:00', 2, 'SUCCESS'),
('AutoCompleteOrdersJob', 1, '2026-11-17T00:00:05+07:00', '2026-11-17T00:00:06+07:00', 1, 'SUCCESS');

INSERT INTO dbo.notifications (user_id, order_id, channel, title, body, is_read) VALUES
(9,  4, 'ANDROID_FCM', N'Đơn hàng ORD-261117-0004 đã sẵn sàng tại Đại lý Nhổn!', N'Mời bạn mang mã QR đến Điểm nhận hàng Cô Lan - Nhổn từ 16:00 hôm nay để nhận thực phẩm tươi sống.', 0),
(12, 5, 'ANDROID_FCM', N'Thông báo sự cố đơn hàng ORD-261117-0005',               N'Kiện trứng gà của bạn bị dập vỡ trong quá trình vận chuyển đến đại lý. Hệ thống đang xử lý hoàn tiền 100% cho bạn.', 0);

INSERT INTO dbo.audit_logs (actor_user_id, action_code, target_entity, target_id, detail_json) VALUES
(1, 'APPROVE_REFUND', 'Refunds', '1', N'{"orderCode":"ORD-261116-0003","amount":75000,"commissionReversed":true}');

-- 26. Recipes & RecipeIngredients (Dữ liệu cho tính năng AI gợi ý món và quy đổi sang gói cố định)
INSERT INTO dbo.recipes (recipe_code, name, servings, cooking_time_minutes, instructions, dietary_tags) VALUES
('RCP-EATCLEAN-01', N'Ức gà áp chảo ăn kèm súp lơ xanh và dưa leo', 2, 20, N'1. Ướp ức gà với tiêu và muối hồng.\n2. Áp chảo ức gà 5 phút mỗi mặt.\n3. Luộc súp lơ xanh 3 phút, cắt dưa leo ăn kèm.', N'EatClean,HighProtein,LowCalorie'), -- id = 1
('RCP-FAMILY-02',   N'Đậu phụ trắng sốt cà chua và canh rau muống', 2, 25, N'1. Thái vuông đậu phụ rán vàng nhẹ.\n2. Xào nhuyễn cà chua làm sốt đảo cùng đậu.\n3. Luộc rau muống lấy nước canh.',          N'Vegetarian,Vegan,BudgetFriendly'), -- id = 2
('RCP-PROTEIN-03',  N'Thịt ba chỉ rang cháy cạnh và trứng gà luộc', 3, 30, N'1. Thái miếng thịt ba chỉ đảo săn cháy cạnh.\n2. Nêm nước mắm và hành hoa.\n3. Luộc trứng gà lòng đào ăn kèm dưa leo.',  N'HighProtein,FamilyMeal'),         -- id = 3
('RCP-BEEF-04',     N'Bắp bò Úc xào súp lơ xanh Đà Lạt',            2, 15, N'1. Trần sơ súp lơ xanh.\n2. Phi thơm tỏi xào nhanh bắp bò trên lửa lớn.\n3. Đảo đều cùng súp lơ và dầu hào.',            N'HighProtein,IronRich');           -- id = 4

INSERT INTO dbo.recipe_ingredients (recipe_id, product_id, ingredient_name, required_weight_grams, is_optional) VALUES
-- Món 1 (2 người): Cần 400g ức gà (-> quy đổi 1 khay 500g = 45k, dư 100g), 300g súp lơ (-> 1 túi 500g = 28k), 250g dưa leo (-> 1 túi 500g = 18k) => Tổng giỏ = 91.000đ (< 100.000đ ngân sách!)
(1, 1, N'Ức gà phi lê', 400, 0),
(1, 6, N'Súp lơ xanh',  300, 0),
(1, 7, N'Dưa leo baby', 250, 0),
-- Món 2 (2 người - 59.000đ): Đậu trắng 400g (15k) + Cà chua 300g (1 túi 500g = 20k) + Rau muống 400g (1 túi 500g = 14k)
(2, 5, N'Đậu phụ trắng', 400, 0),
(2, 9, N'Cà chua',       300, 0),
(2, 8, N'Rau muống',     400, 0),
-- Món 3 (3 người)
(3, 2, N'Thịt ba chỉ heo', 450, 0),
(3, 4, N'Trứng gà',        240, 0),
(3, 7, N'Dưa leo baby',    300, 1),
-- Món 4 (2 người)
(4, 3, N'Bắp bò Úc',    280, 0),
(4, 6, N'Súp lơ xanh',  350, 0);

-- 27. KnowledgeDocuments (Tài liệu chính sách có đánh phiên bản phục vụ AI Chatbot RAG)
INSERT INTO dbo.knowledge_documents (doc_code, version_tag, title, content_markdown, is_active) VALUES
('POL-ORDER-CYCLE', 'v1.0', N'Chính sách Giờ chốt đơn, Thanh toán và Lịch nhận hàng tại Đại lý',
 N'### 1. Quy định đặt hàng và thanh toán trước 100%\n- Khách hàng đặt thực phẩm trên Web hoặc ứng dụng Android và thanh toán trực tuyến 100% qua VNPAY (không hỗ trợ COD).\n- Thời gian giữ tồn kho chờ thanh toán tối đa là **30 phút** (và không vượt quá 23:59:59 của ngày đặt).\n### 2. Giờ chốt đơn và quyền tự hủy\n- Hạn chốt đơn hàng ngày là **23:59:59 Ngày D**.\n- Trước 23:59:59 Ngày D, khách hàng được phép tự nhấn hủy đơn trên ứng dụng và được hoàn lại 100% số tiền đã thanh toán.\n- Đúng **00:00:00 Ngày D+1**, hệ thống tự động xác nhận đơn chuyển xuống Kho trung tâm; khách hàng không còn quyền tự hủy.\n### 3. Thời gian nhận hàng và tự động hoàn tất\n- Hàng được giao tới Đại lý trước 16:00 Ngày D+1. Khách hàng nhận thông báo và xuất mã QR nhận hàng từ **16:00 Ngày D+1**.\n- Lúc **00:00:00 Ngày D+2**, các đơn đã sẵn sàng tại đại lý và không có khiếu nại sẽ được hệ thống tự động chuyển sang Hoàn tất.', 1),

('POL-RETURN-REFUND', 'v1.0', N'Chính sách Báo sự cố, Đổi trả hàng lỗi và Hoàn tiền',
 N'### 1. Xử lý thực phẩm dập hỏng hoặc giao sai\n- Khi phát hiện thực phẩm bị dập hỏng hoặc thiếu kiện tại đại lý, khách hàng hoặc đại lý lập Hồ sơ sự cố kèm hình ảnh.\n- Đơn hàng đang có sự cố sẽ bị khóa tự động hoàn tất lúc 00:00.\n### 2. Thu hồi và hoàn tiền\n- Hàng lỗi được tài xế thu hồi từ đại lý về khu cách ly của Kho trung tâm. Sau khi kho xác nhận, hệ thống hoàn tiền 100% giá trị đơn hàng cho khách.', 1);

-- 28. ChatSessions, ChatMessages & ChatFeedback (Dữ liệu mẫu hội thoại AI Chatbot)
INSERT INTO dbo.chat_sessions (session_code, customer_id) VALUES
('CHAT-261117-HUNG-01', 9); -- id = 1

INSERT INTO dbo.chat_messages (session_id, sender_type, content, tool_name) VALUES
(1, 'USER',      N'Đơn hàng hôm nay của mình đã về đại lý Nhổn chưa bạn? Và tối nay mình muốn nấu bữa tối EatClean cho 2 người dưới 100k thì mua gì?', NULL),
(1, 'TOOL',      N'{"orderCode":"ORD-261117-0004","fulfillmentStatus":"READY_FOR_PICKUP","pickupPoint":"Điểm nhận hàng Tạp hóa Cô Lan - Nhổn"}', 'getCustomerOrders'),
(1, 'ASSISTANT', N'Đơn hàng **ORD-261117-0004** của bạn đã được Đại lý Cô Lan - Nhổn kiểm nhận đủ 2 kiện và sẵn sàng để bạn ra nhận từ 16:00 hôm nay nhé!\n\nVới bữa tối EatClean cho 2 người trong ngân sách 100.000đ cho đợt giao ngày mai, mình gợi ý món **Ức gà áp chảo ăn kèm súp lơ xanh và dưa leo**:\n- **Ức gà phi lê (Khay 500g):** 45.000đ (dùng 400g, dư 100g)\n- **Súp lơ xanh Đà Lạt (Túi 500g):** 28.000đ (dùng 300g, dư 200g)\n- **Dưa leo baby (Túi 500g):** 18.000đ (dùng 250g, dư 250g)\n**Tổng cộng:** **91.000đ** (tiết kiệm 9.000đ so với ngân sách). Bạn có muốn thêm 3 gói này vào giỏ hàng không?', NULL);

INSERT INTO dbo.chat_feedback (session_id, message_id, rating, comment) VALUES
(1, 3, 5, N'Báo trạng thái đơn chuẩn và tính tiền theo gói rất chính xác!');
GO

/* =========================================================================================
   PHẦN 3: CÁC TRUY VẤN KIỂM TRA NHANH TRONG SSMS 22 (VERIFICATION QUERIES)
   ========================================================================================= */

-- 1. Kiểm tra danh sách Đơn hàng với sự tách biệt giữa Trạng thái thương mại và Trạng thái vật lý:
SELECT 
    o.order_code,
    u.full_name AS customer_name,
    pp.name AS pickup_point,
    c.cycle_code,
    o.total_amount,
    o.order_status,
    o.fulfillment_status,
    o.payment_status,
    o.completion_type,
    o.customer_confirmed_at,
    o.physical_handed_over_at,
    o.is_unclaimed_at_agent,
    o.has_open_issue
FROM dbo.orders o
JOIN dbo.users u ON o.customer_id = u.id
JOIN dbo.pickup_points pp ON o.pickup_point_id = pp.id
JOIN dbo.order_cycles c ON o.cycle_id = c.id
ORDER BY o.id;

-- 2. Kiểm tra Sổ cái hoa hồng đại lý (Commission Ledger - Append-only & Bút toán âm hoàn tiền):
SELECT 
    cl.id,
    pp.code AS pickup_point_code,
    o.order_code,
    cl.entry_type,
    cl.amount,
    cl.commission_status,
    cl.note,
    cl.created_at
FROM dbo.commission_ledger cl
JOIN dbo.pickup_points pp ON cl.pickup_point_id = pp.id
JOIN dbo.orders o ON cl.order_id = o.id
ORDER BY cl.id;

-- 3. Kiểm tra danh sách Lô hàng theo thứ tự ưu tiên xuất kho FEFO (First-Expired, First-Out):
SELECT 
    pv.sku,
    p.name AS product_name,
    pv.pack_name,
    l.lot_code,
    l.manufactured_date,
    l.expiry_date,
    l.remaining_qty,
    l.status
FROM dbo.lots l
JOIN dbo.product_variants pv ON l.variant_id = pv.id
JOIN dbo.products p ON pv.product_id = p.id
WHERE l.status = 'AVAILABLE' AND l.remaining_qty > 0
ORDER BY pv.id, l.expiry_date ASC, l.received_at ASC;
GO
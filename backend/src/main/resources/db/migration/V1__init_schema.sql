-- ==============================================================================
-- V1__init_schema.sql
-- O2O Food Pickup System — SQL Server 2022 Schema Blueprint
-- Timezone Standard: Asia/Ho_Chi_Minh (UTC+07:00) using DATETIMEOFFSET(7)
-- Currency Standard: DECIMAL(18, 2)
-- Text Standard: NVARCHAR for Vietnamese strings
-- ==============================================================================

-- ------------------------------------------------------------------------------
-- 5.1. Nhóm Người dùng, Phân quyền & Điểm nhận hàng
-- ------------------------------------------------------------------------------
CREATE TABLE users (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    email VARCHAR(150) NOT NULL,
    phone VARCHAR(20) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    full_name NVARCHAR(120) NOT NULL,
    status VARCHAR(30) NOT NULL CONSTRAINT DF_users_status DEFAULT 'ACTIVE',
    created_at DATETIMEOFFSET(7) NOT NULL CONSTRAINT DF_users_created_at DEFAULT SYSDATETIMEOFFSET(),
    version BIGINT NOT NULL CONSTRAINT DF_users_version DEFAULT 0,
    CONSTRAINT UQ_users_email UNIQUE (email),
    CONSTRAINT UQ_users_phone UNIQUE (phone),
    CONSTRAINT CK_users_status CHECK (status IN ('ACTIVE', 'INACTIVE', 'LOCKED'))
);

CREATE TABLE roles (
    id INT IDENTITY(1,1) PRIMARY KEY,
    code VARCHAR(50) NOT NULL,
    CONSTRAINT UQ_roles_code UNIQUE (code),
    CONSTRAINT CK_roles_code CHECK (code IN (
        'ROLE_CUSTOMER',
        'ROLE_WAREHOUSE',
        'ROLE_DISPATCHER',
        'ROLE_DRIVER',
        'ROLE_AGENT',
        'ROLE_ADMIN'
    ))
);

CREATE TABLE user_roles (
    user_id BIGINT NOT NULL,
    role_id INT NOT NULL,
    CONSTRAINT PK_user_roles PRIMARY KEY (user_id, role_id),
    CONSTRAINT FK_user_roles_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT FK_user_roles_role FOREIGN KEY (role_id) REFERENCES roles(id)
);

CREATE TABLE pickup_points (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    code VARCHAR(30) NOT NULL,
    name NVARCHAR(150) NOT NULL,
    address NVARCHAR(300) NOT NULL,
    latitude DECIMAL(9,6) NOT NULL,
    longitude DECIMAL(9,6) NOT NULL,
    cold_storage_capacity INT NOT NULL,
    status VARCHAR(30) NOT NULL CONSTRAINT DF_pickup_points_status DEFAULT 'ACTIVE',
    version BIGINT NOT NULL CONSTRAINT DF_pickup_points_version DEFAULT 0,
    CONSTRAINT UQ_pickup_points_code UNIQUE (code),
    CONSTRAINT CK_pickup_points_capacity CHECK (cold_storage_capacity > 0),
    CONSTRAINT CK_pickup_points_status CHECK (status IN ('ACTIVE', 'SUSPENDED', 'INACTIVE'))
);

CREATE TABLE point_assignments (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    pickup_point_id BIGINT NOT NULL,
    agent_user_id BIGINT NOT NULL,
    active_from DATETIMEOFFSET(7) NOT NULL CONSTRAINT DF_point_assignments_active_from DEFAULT SYSDATETIMEOFFSET(),
    active_to DATETIMEOFFSET(7) NULL,
    CONSTRAINT FK_point_assignments_point FOREIGN KEY (pickup_point_id) REFERENCES pickup_points(id),
    CONSTRAINT FK_point_assignments_agent FOREIGN KEY (agent_user_id) REFERENCES users(id)
);

CREATE INDEX IX_point_assignments_agent_point ON point_assignments(agent_user_id, pickup_point_id);

-- ------------------------------------------------------------------------------
-- 5.2. Nhóm Sản phẩm, Lô hàng FEFO & Giữ tồn
-- ------------------------------------------------------------------------------
CREATE TABLE products (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    sku_prefix VARCHAR(40) NOT NULL,
    name NVARCHAR(200) NOT NULL,
    category NVARCHAR(100) NOT NULL,
    storage_type VARCHAR(30) NOT NULL,
    min_shelf_life_days_at_pickup INT NOT NULL CONSTRAINT DF_products_min_shelf_life DEFAULT 1,
    dietary_tags NVARCHAR(255) NULL,
    allergen_tags NVARCHAR(255) NULL,
    is_active BIT NOT NULL CONSTRAINT DF_products_is_active DEFAULT 1,
    CONSTRAINT UQ_products_sku_prefix UNIQUE (sku_prefix),
    CONSTRAINT CK_products_storage_type CHECK (storage_type IN ('AMBIENT', 'CHILLED', 'FROZEN')),
    CONSTRAINT CK_products_min_shelf_life CHECK (min_shelf_life_days_at_pickup >= 0)
);

CREATE TABLE product_variants (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    product_id BIGINT NOT NULL,
    sku VARCHAR(60) NOT NULL,
    pack_name NVARCHAR(120) NOT NULL,
    pack_weight_grams INT NOT NULL,
    unit_price DECIMAL(18,2) NOT NULL,
    is_active BIT NOT NULL CONSTRAINT DF_product_variants_is_active DEFAULT 1,
    version BIGINT NOT NULL CONSTRAINT DF_product_variants_version DEFAULT 0,
    CONSTRAINT UQ_product_variants_sku UNIQUE (sku),
    CONSTRAINT FK_product_variants_product FOREIGN KEY (product_id) REFERENCES products(id),
    CONSTRAINT CK_product_variants_weight CHECK (pack_weight_grams > 0),
    CONSTRAINT CK_product_variants_price CHECK (unit_price > 0)
);

CREATE TABLE lots (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    variant_id BIGINT NOT NULL,
    lot_code VARCHAR(60) NOT NULL,
    supplier_name NVARCHAR(150) NOT NULL,
    manufactured_date DATE NOT NULL,
    expiry_date DATE NOT NULL,
    initial_qty INT NOT NULL,
    remaining_qty INT NOT NULL,
    status VARCHAR(30) NOT NULL CONSTRAINT DF_lots_status DEFAULT 'AVAILABLE',
    received_at DATETIMEOFFSET(7) NOT NULL CONSTRAINT DF_lots_received_at DEFAULT SYSDATETIMEOFFSET(),
    version BIGINT NOT NULL CONSTRAINT DF_lots_version DEFAULT 0,
    CONSTRAINT UQ_lots_lot_code UNIQUE (lot_code),
    CONSTRAINT FK_lots_variant FOREIGN KEY (variant_id) REFERENCES product_variants(id),
    CONSTRAINT CK_lots_initial_qty CHECK (initial_qty > 0),
    CONSTRAINT CK_lots_remaining_qty CHECK (remaining_qty >= 0 AND remaining_qty <= initial_qty),
    CONSTRAINT CK_lots_dates CHECK (expiry_date >= manufactured_date),
    CONSTRAINT CK_lots_status CHECK (status IN ('AVAILABLE', 'QUARANTINED', 'EXPIRED'))
);

CREATE INDEX IX_lots_fefo ON lots(variant_id, status, expiry_date ASC, received_at ASC, id ASC);

CREATE TABLE order_cycles (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    cycle_code VARCHAR(40) NOT NULL,
    delivery_date DATE NOT NULL,
    open_at DATETIMEOFFSET(7) NOT NULL,
    cutoff_at DATETIMEOFFSET(7) NOT NULL,
    auto_confirm_at DATETIMEOFFSET(7) NOT NULL,
    warehouse_pack_target_at DATETIMEOFFSET(7) NOT NULL,
    dispatch_start_at DATETIMEOFFSET(7) NOT NULL,
    pickup_start_at DATETIMEOFFSET(7) NOT NULL,
    auto_complete_at DATETIMEOFFSET(7) NOT NULL,
    payment_timeout_minutes INT NOT NULL CONSTRAINT DF_order_cycles_timeout DEFAULT 30,
    status VARCHAR(30) NOT NULL CONSTRAINT DF_order_cycles_status DEFAULT 'OPEN',
    version BIGINT NOT NULL CONSTRAINT DF_order_cycles_version DEFAULT 0,
    CONSTRAINT UQ_order_cycles_code UNIQUE (cycle_code),
    CONSTRAINT CK_order_cycles_timeout CHECK (payment_timeout_minutes > 0),
    CONSTRAINT CK_order_cycles_status CHECK (status IN ('OPEN', 'LOCKED', 'FULFILLING', 'CLOSED'))
);

CREATE TABLE point_cycles (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    cycle_id BIGINT NOT NULL,
    pickup_point_id BIGINT NOT NULL,
    max_orders INT NOT NULL,
    current_orders INT NOT NULL CONSTRAINT DF_point_cycles_current_orders DEFAULT 0,
    status VARCHAR(30) NOT NULL CONSTRAINT DF_point_cycles_status DEFAULT 'OPEN',
    CONSTRAINT UQ_point_cycles_cycle_point UNIQUE (cycle_id, pickup_point_id),
    CONSTRAINT FK_point_cycles_cycle FOREIGN KEY (cycle_id) REFERENCES order_cycles(id),
    CONSTRAINT FK_point_cycles_point FOREIGN KEY (pickup_point_id) REFERENCES pickup_points(id),
    CONSTRAINT CK_point_cycles_max_orders CHECK (max_orders > 0),
    CONSTRAINT CK_point_cycles_current_orders CHECK (current_orders >= 0 AND current_orders <= max_orders),
    CONSTRAINT CK_point_cycles_status CHECK (status IN ('OPEN', 'FULL', 'CLOSED'))
);

CREATE TABLE inventory_balances (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    cycle_id BIGINT NOT NULL,
    variant_id BIGINT NOT NULL,
    available_qty INT NOT NULL,
    reserved_qty INT NOT NULL CONSTRAINT DF_inventory_balances_reserved DEFAULT 0,
    committed_qty INT NOT NULL CONSTRAINT DF_inventory_balances_committed DEFAULT 0,
    updated_at DATETIMEOFFSET(7) NOT NULL CONSTRAINT DF_inventory_balances_updated_at DEFAULT SYSDATETIMEOFFSET(),
    version BIGINT NOT NULL CONSTRAINT DF_inventory_balances_version DEFAULT 0,
    CONSTRAINT UQ_inventory_balances_cycle_variant UNIQUE (cycle_id, variant_id),
    CONSTRAINT FK_inventory_balances_cycle FOREIGN KEY (cycle_id) REFERENCES order_cycles(id),
    CONSTRAINT FK_inventory_balances_variant FOREIGN KEY (variant_id) REFERENCES product_variants(id),
    CONSTRAINT CK_inventory_balances_available CHECK (available_qty >= 0),
    CONSTRAINT CK_inventory_balances_reserved CHECK (reserved_qty >= 0),
    CONSTRAINT CK_inventory_balances_committed CHECK (committed_qty >= 0)
);

-- ------------------------------------------------------------------------------
-- 5.3 & 5.4. Nhóm Chính sách hoa hồng, Đơn hàng, Giao nhận & Mã QR
-- ------------------------------------------------------------------------------
CREATE TABLE commission_policies (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    policy_code VARCHAR(40) NOT NULL,
    calc_type VARCHAR(30) NOT NULL,
    fixed_amount DECIMAL(18,2) NULL,
    percentage_rate DECIMAL(5,2) NULL,
    effective_from DATETIMEOFFSET(7) NOT NULL,
    effective_to DATETIMEOFFSET(7) NULL,
    CONSTRAINT UQ_commission_policies_code UNIQUE (policy_code),
    CONSTRAINT CK_commission_policies_calc_type CHECK (calc_type IN ('FIXED_PER_ORDER', 'PERCENTAGE_NET_MERCHANDISE')),
    CONSTRAINT CK_commission_policies_fixed CHECK (fixed_amount IS NULL OR fixed_amount >= 0),
    CONSTRAINT CK_commission_policies_rate CHECK (percentage_rate IS NULL OR (percentage_rate >= 0 AND percentage_rate <= 100))
);

CREATE TABLE orders (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    order_code VARCHAR(40) NOT NULL,
    customer_id BIGINT NOT NULL,
    pickup_point_id BIGINT NOT NULL,
    cycle_id BIGINT NOT NULL,
    commission_policy_id BIGINT NOT NULL,
    subtotal_amount DECIMAL(18,2) NOT NULL,
    discount_amount DECIMAL(18,2) NOT NULL CONSTRAINT DF_orders_discount DEFAULT 0.00,
    total_amount DECIMAL(18,2) NOT NULL,
    order_status VARCHAR(30) NOT NULL,
    fulfillment_status VARCHAR(30) NOT NULL,
    payment_status VARCHAR(30) NOT NULL,
    cancel_reason VARCHAR(60) NULL,
    cutoff_at DATETIMEOFFSET(7) NOT NULL,
    pickup_start_at DATETIMEOFFSET(7) NOT NULL,
    auto_complete_at DATETIMEOFFSET(7) NOT NULL,
    confirmed_at DATETIMEOFFSET(7) NULL,
    completed_at DATETIMEOFFSET(7) NULL,
    completion_type VARCHAR(30) NULL,
    customer_confirmed_at DATETIMEOFFSET(7) NULL,
    physical_handed_over_at DATETIMEOFFSET(7) NULL,
    pickup_qr_token_hash VARCHAR(128) NULL,
    pickup_qr_used_at DATETIMEOFFSET(7) NULL,
    has_open_issue BIT NOT NULL CONSTRAINT DF_orders_has_open_issue DEFAULT 0,
    is_unclaimed_at_agent BIT NOT NULL CONSTRAINT DF_orders_is_unclaimed DEFAULT 0,
    created_at DATETIMEOFFSET(7) NOT NULL CONSTRAINT DF_orders_created_at DEFAULT SYSDATETIMEOFFSET(),
    updated_at DATETIMEOFFSET(7) NOT NULL CONSTRAINT DF_orders_updated_at DEFAULT SYSDATETIMEOFFSET(),
    version BIGINT NOT NULL CONSTRAINT DF_orders_version DEFAULT 0,
    CONSTRAINT UQ_orders_order_code UNIQUE (order_code),
    CONSTRAINT FK_orders_customer FOREIGN KEY (customer_id) REFERENCES users(id),
    CONSTRAINT FK_orders_pickup_point FOREIGN KEY (pickup_point_id) REFERENCES pickup_points(id),
    CONSTRAINT FK_orders_cycle FOREIGN KEY (cycle_id) REFERENCES order_cycles(id),
    CONSTRAINT FK_orders_commission_policy FOREIGN KEY (commission_policy_id) REFERENCES commission_policies(id),
    CONSTRAINT CK_orders_subtotal CHECK (subtotal_amount >= 0),
    CONSTRAINT CK_orders_discount CHECK (discount_amount >= 0),
    CONSTRAINT CK_orders_total CHECK (total_amount >= 0),
    CONSTRAINT CK_orders_order_status CHECK (order_status IN (
        'PENDING_PAYMENT',
        'PAID_WAITING_CUTOFF',
        'CONFIRMED',
        'PROCESSING',
        'COMPLETED',
        'CANCELLED'
    )),
    CONSTRAINT CK_orders_fulfillment_status CHECK (fulfillment_status IN (
        'WAITING_PACKING',
        'PACKED',
        'IN_TRANSIT',
        'AGENT_CHECKING',
        'READY_FOR_PICKUP',
        'HANDED_OVER',
        'ISSUE_HOLD'
    )),
    CONSTRAINT CK_orders_payment_status CHECK (payment_status IN (
        'UNPAID',
        'PAID',
        'LATE_RECONCILIATION',
        'REFUND_REQUESTED',
        'REFUND_APPROVED',
        'REFUNDING',
        'REFUNDED',
        'REFUND_FAILED'
    )),
    CONSTRAINT CK_orders_completion_type CHECK (completion_type IS NULL OR completion_type IN (
        'CUSTOMER_CONFIRMED',
        'SYSTEM_TIMEOUT'
    ))
);

CREATE INDEX IX_orders_customer_created ON orders(customer_id, created_at DESC);
CREATE INDEX IX_orders_cycle_status ON orders(cycle_id, order_status, fulfillment_status);
CREATE INDEX IX_orders_cutoff_scan ON orders(order_status, cutoff_at);
CREATE INDEX IX_orders_autocomplete_scan ON orders(order_status, fulfillment_status, has_open_issue, auto_complete_at);

CREATE TABLE reservations (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    order_id BIGINT NOT NULL,
    cycle_id BIGINT NOT NULL,
    variant_id BIGINT NOT NULL,
    reserved_qty INT NOT NULL,
    status VARCHAR(30) NOT NULL CONSTRAINT DF_reservations_status DEFAULT 'ACTIVE',
    expires_at DATETIMEOFFSET(7) NOT NULL,
    version BIGINT NOT NULL CONSTRAINT DF_reservations_version DEFAULT 0,
    CONSTRAINT FK_reservations_order FOREIGN KEY (order_id) REFERENCES orders(id),
    CONSTRAINT FK_reservations_cycle FOREIGN KEY (cycle_id) REFERENCES order_cycles(id),
    CONSTRAINT FK_reservations_variant FOREIGN KEY (variant_id) REFERENCES product_variants(id),
    CONSTRAINT CK_reservations_qty CHECK (reserved_qty > 0),
    CONSTRAINT CK_reservations_status CHECK (status IN ('ACTIVE', 'COMMITTED', 'RELEASED', 'EXPIRED'))
);

CREATE INDEX IX_reservations_cleanup ON reservations(status, expires_at);
CREATE INDEX IX_reservations_order ON reservations(order_id);

CREATE TABLE stock_movements (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    variant_id BIGINT NOT NULL,
    lot_id BIGINT NULL,
    order_id BIGINT NULL,
    movement_type VARCHAR(40) NOT NULL,
    quantity INT NOT NULL,
    created_by BIGINT NOT NULL,
    created_at DATETIMEOFFSET(7) NOT NULL CONSTRAINT DF_stock_movements_created_at DEFAULT SYSDATETIMEOFFSET(),
    CONSTRAINT FK_stock_movements_variant FOREIGN KEY (variant_id) REFERENCES product_variants(id),
    CONSTRAINT FK_stock_movements_lot FOREIGN KEY (lot_id) REFERENCES lots(id),
    CONSTRAINT FK_stock_movements_order FOREIGN KEY (order_id) REFERENCES orders(id),
    CONSTRAINT FK_stock_movements_user FOREIGN KEY (created_by) REFERENCES users(id),
    CONSTRAINT CK_stock_movements_type CHECK (movement_type IN (
        'INBOUND',
        'ALLOCATE_FEFO',
        'DISPATCH',
        'RETURN_TO_QUARANTINE',
        'ADJUSTMENT'
    ))
);

CREATE TABLE order_items (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    order_id BIGINT NOT NULL,
    variant_id BIGINT NOT NULL,
    product_name_snapshot NVARCHAR(200) NOT NULL,
    pack_name_snapshot NVARCHAR(120) NOT NULL,
    unit_price_snapshot DECIMAL(18,2) NOT NULL,
    quantity INT NOT NULL,
    line_total DECIMAL(18,2) NOT NULL,
    CONSTRAINT FK_order_items_order FOREIGN KEY (order_id) REFERENCES orders(id),
    CONSTRAINT FK_order_items_variant FOREIGN KEY (variant_id) REFERENCES product_variants(id),
    CONSTRAINT CK_order_items_price CHECK (unit_price_snapshot >= 0),
    CONSTRAINT CK_order_items_qty CHECK (quantity > 0),
    CONSTRAINT CK_order_items_total CHECK (line_total >= 0)
);

CREATE TABLE order_lot_allocations (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    order_item_id BIGINT NOT NULL,
    lot_id BIGINT NOT NULL,
    allocated_qty INT NOT NULL,
    CONSTRAINT FK_order_lot_allocations_item FOREIGN KEY (order_item_id) REFERENCES order_items(id),
    CONSTRAINT FK_order_lot_allocations_lot FOREIGN KEY (lot_id) REFERENCES lots(id),
    CONSTRAINT CK_order_lot_allocations_qty CHECK (allocated_qty > 0)
);

CREATE TABLE trips (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    trip_code VARCHAR(40) NOT NULL,
    cycle_id BIGINT NOT NULL,
    driver_id BIGINT NOT NULL,
    trip_type VARCHAR(30) NOT NULL,
    status VARCHAR(30) NOT NULL CONSTRAINT DF_trips_status DEFAULT 'PLANNED',
    departed_at DATETIMEOFFSET(7) NULL,
    completed_at DATETIMEOFFSET(7) NULL,
    version BIGINT NOT NULL CONSTRAINT DF_trips_version DEFAULT 0,
    CONSTRAINT UQ_trips_code UNIQUE (trip_code),
    CONSTRAINT FK_trips_cycle FOREIGN KEY (cycle_id) REFERENCES order_cycles(id),
    CONSTRAINT FK_trips_driver FOREIGN KEY (driver_id) REFERENCES users(id),
    CONSTRAINT CK_trips_type CHECK (trip_type IN ('DELIVERY', 'RETURN_PICKUP')),
    CONSTRAINT CK_trips_status CHECK (status IN ('PLANNED', 'IN_TRANSIT', 'COMPLETED', 'CANCELLED'))
);

CREATE TABLE trip_stops (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    trip_id BIGINT NOT NULL,
    pickup_point_id BIGINT NOT NULL,
    stop_sequence INT NOT NULL,
    eta_at DATETIMEOFFSET(7) NULL,
    arrived_at DATETIMEOFFSET(7) NULL,
    handover_completed_at DATETIMEOFFSET(7) NULL,
    status VARCHAR(30) NOT NULL CONSTRAINT DF_trip_stops_status DEFAULT 'PENDING',
    CONSTRAINT FK_trip_stops_trip FOREIGN KEY (trip_id) REFERENCES trips(id),
    CONSTRAINT FK_trip_stops_point FOREIGN KEY (pickup_point_id) REFERENCES pickup_points(id),
    CONSTRAINT CK_trip_stops_seq CHECK (stop_sequence > 0),
    CONSTRAINT CK_trip_stops_status CHECK (status IN ('PENDING', 'ARRIVED_AT_POINT', 'COMPLETED', 'SKIPPED'))
);

CREATE TABLE packages (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    order_id BIGINT NOT NULL,
    trip_stop_id BIGINT NULL,
    package_code VARCHAR(50) NOT NULL,
    qr_payload VARCHAR(200) NOT NULL,
    package_seq INT NOT NULL,
    total_packages_in_order INT NOT NULL,
    storage_type VARCHAR(30) NOT NULL,
    status VARCHAR(30) NOT NULL CONSTRAINT DF_packages_status DEFAULT 'PACKED',
    version BIGINT NOT NULL CONSTRAINT DF_packages_version DEFAULT 0,
    CONSTRAINT UQ_packages_code UNIQUE (package_code),
    CONSTRAINT UQ_packages_qr_payload UNIQUE (qr_payload),
    CONSTRAINT FK_packages_order FOREIGN KEY (order_id) REFERENCES orders(id),
    CONSTRAINT FK_packages_trip_stop FOREIGN KEY (trip_stop_id) REFERENCES trip_stops(id),
    CONSTRAINT CK_packages_seq CHECK (package_seq > 0 AND total_packages_in_order >= package_seq),
    CONSTRAINT CK_packages_storage CHECK (storage_type IN ('AMBIENT', 'CHILLED', 'FROZEN')),
    CONSTRAINT CK_packages_status CHECK (status IN (
        'PACKED',
        'PACKED_READY_FOR_DISPATCH',
        'HANDED_TO_DRIVER',
        'IN_TRANSIT',
        'DELIVERED_TO_AGENT',
        'MISSING',
        'DAMAGED',
        'HANDED_TO_CUSTOMER'
    ))
);

CREATE TABLE handover_records (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    handover_type VARCHAR(40) NOT NULL,
    trip_stop_id BIGINT NULL,
    from_user_id BIGINT NOT NULL,
    to_user_id BIGINT NOT NULL,
    total_expected_packages INT NOT NULL,
    accepted_packages INT NOT NULL,
    missing_packages INT NOT NULL,
    damaged_packages INT NOT NULL,
    note NVARCHAR(500) NULL,
    signed_at DATETIMEOFFSET(7) NOT NULL CONSTRAINT DF_handover_records_signed_at DEFAULT SYSDATETIMEOFFSET(),
    CONSTRAINT FK_handover_records_stop FOREIGN KEY (trip_stop_id) REFERENCES trip_stops(id),
    CONSTRAINT FK_handover_records_from FOREIGN KEY (from_user_id) REFERENCES users(id),
    CONSTRAINT FK_handover_records_to FOREIGN KEY (to_user_id) REFERENCES users(id),
    CONSTRAINT CK_handover_records_type CHECK (handover_type IN (
        'WAREHOUSE_TO_DRIVER',
        'DRIVER_TO_AGENT',
        'AGENT_TO_DRIVER_RETURN',
        'DRIVER_TO_WAREHOUSE_RETURN'
    )),
    CONSTRAINT CK_handover_records_counts CHECK (
        total_expected_packages >= 0 AND
        accepted_packages >= 0 AND
        missing_packages >= 0 AND
        damaged_packages >= 0 AND
        (accepted_packages + missing_packages + damaged_packages = total_expected_packages)
    )
);

CREATE TABLE handover_items (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    handover_record_id BIGINT NOT NULL,
    package_id BIGINT NOT NULL,
    condition_status VARCHAR(30) NOT NULL,
    evidence_image_url VARCHAR(500) NULL,
    CONSTRAINT FK_handover_items_record FOREIGN KEY (handover_record_id) REFERENCES handover_records(id),
    CONSTRAINT FK_handover_items_package FOREIGN KEY (package_id) REFERENCES packages(id),
    CONSTRAINT CK_handover_items_condition CHECK (condition_status IN ('ACCEPTED', 'MISSING', 'DAMAGED'))
);

-- ------------------------------------------------------------------------------
-- 5.4. Nhóm Thanh toán, Hoàn tiền, Hoa hồng & Sổ cái
-- ------------------------------------------------------------------------------
CREATE TABLE payment_attempts (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    order_id BIGINT NOT NULL,
    txn_ref VARCHAR(64) NOT NULL,
    provider VARCHAR(30) NOT NULL CONSTRAINT DF_payment_attempts_provider DEFAULT 'VNPAY',
    amount DECIMAL(18,2) NOT NULL,
    status VARCHAR(40) NOT NULL CONSTRAINT DF_payment_attempts_status DEFAULT 'INITIATED',
    vnp_transaction_no VARCHAR(64) NULL,
    vnp_pay_date VARCHAR(20) NULL,
    created_at DATETIMEOFFSET(7) NOT NULL CONSTRAINT DF_payment_attempts_created_at DEFAULT SYSDATETIMEOFFSET(),
    confirmed_at DATETIMEOFFSET(7) NULL,
    version BIGINT NOT NULL CONSTRAINT DF_payment_attempts_version DEFAULT 0,
    CONSTRAINT UQ_payment_attempts_txn_ref UNIQUE (txn_ref),
    CONSTRAINT FK_payment_attempts_order FOREIGN KEY (order_id) REFERENCES orders(id),
    CONSTRAINT CK_payment_attempts_amount CHECK (amount > 0),
    CONSTRAINT CK_payment_attempts_status CHECK (status IN (
        'INITIATED',
        'SUCCESS',
        'FAILED',
        'LATE_PAID_RECONCILIATION'
    ))
);

CREATE TABLE payment_events (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    payment_attempt_id BIGINT NOT NULL,
    event_source VARCHAR(30) NOT NULL,
    raw_payload NVARCHAR(MAX) NOT NULL,
    signature_valid BIT NOT NULL,
    received_at DATETIMEOFFSET(7) NOT NULL CONSTRAINT DF_payment_events_received_at DEFAULT SYSDATETIMEOFFSET(),
    CONSTRAINT FK_payment_events_attempt FOREIGN KEY (payment_attempt_id) REFERENCES payment_attempts(id),
    CONSTRAINT CK_payment_events_source CHECK (event_source IN ('RETURN_URL', 'IPN_WEBHOOK'))
);

CREATE TABLE issues (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    order_id BIGINT NOT NULL,
    reported_by_user_id BIGINT NOT NULL,
    reporter_role VARCHAR(50) NOT NULL,
    issue_type VARCHAR(40) NOT NULL,
    description NVARCHAR(1000) NOT NULL,
    evidence_urls NVARCHAR(MAX) NULL,
    status VARCHAR(30) NOT NULL CONSTRAINT DF_issues_status DEFAULT 'OPEN',
    created_at DATETIMEOFFSET(7) NOT NULL CONSTRAINT DF_issues_created_at DEFAULT SYSDATETIMEOFFSET(),
    CONSTRAINT FK_issues_order FOREIGN KEY (order_id) REFERENCES orders(id),
    CONSTRAINT FK_issues_reporter FOREIGN KEY (reported_by_user_id) REFERENCES users(id),
    CONSTRAINT CK_issues_type CHECK (issue_type IN ('DAMAGED', 'MISSING_ITEM', 'WRONG_ITEM', 'LATE_DELIVERY')),
    CONSTRAINT CK_issues_status CHECK (status IN ('OPEN', 'INVESTIGATING', 'RESOLVED', 'REJECTED'))
);

CREATE TABLE return_requests (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    issue_id BIGINT NOT NULL,
    order_id BIGINT NOT NULL,
    pickup_point_id BIGINT NOT NULL,
    return_trip_id BIGINT NULL,
    status VARCHAR(40) NOT NULL CONSTRAINT DF_return_requests_status DEFAULT 'REQUESTED',
    warehouse_inspection_note NVARCHAR(500) NULL,
    version BIGINT NOT NULL CONSTRAINT DF_return_requests_version DEFAULT 0,
    CONSTRAINT FK_return_requests_issue FOREIGN KEY (issue_id) REFERENCES issues(id),
    CONSTRAINT FK_return_requests_order FOREIGN KEY (order_id) REFERENCES orders(id),
    CONSTRAINT FK_return_requests_point FOREIGN KEY (pickup_point_id) REFERENCES pickup_points(id),
    CONSTRAINT FK_return_requests_trip FOREIGN KEY (return_trip_id) REFERENCES trips(id),
    CONSTRAINT CK_return_requests_status CHECK (status IN (
        'REQUESTED',
        'APPROVED_FOR_PICKUP',
        'DRIVER_PICKED_UP',
        'IN_RETURN_TRANSIT',
        'WAREHOUSE_RECEIVED',
        'UNRECOVERABLE_WAIVED',
        'CLOSED'
    ))
);

CREATE TABLE refunds (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    order_id BIGINT NOT NULL,
    return_request_id BIGINT NULL,
    refund_reason VARCHAR(50) NOT NULL,
    amount DECIMAL(18,2) NOT NULL,
    status VARCHAR(40) NOT NULL CONSTRAINT DF_refunds_status DEFAULT 'PENDING_APPROVAL',
    provider_ref VARCHAR(100) NULL,
    approved_by BIGINT NULL,
    completed_at DATETIMEOFFSET(7) NULL,
    created_at DATETIMEOFFSET(7) NOT NULL CONSTRAINT DF_refunds_created_at DEFAULT SYSDATETIMEOFFSET(),
    version BIGINT NOT NULL CONSTRAINT DF_refunds_version DEFAULT 0,
    CONSTRAINT FK_refunds_order FOREIGN KEY (order_id) REFERENCES orders(id),
    CONSTRAINT FK_refunds_return_request FOREIGN KEY (return_request_id) REFERENCES return_requests(id),
    CONSTRAINT FK_refunds_approver FOREIGN KEY (approved_by) REFERENCES users(id),
    CONSTRAINT CK_refunds_reason CHECK (refund_reason IN (
        'CUSTOMER_CANCEL_BEFORE_CUTOFF',
        'ORDER_ISSUE_RETURN',
        'LATE_PAYMENT_REFUND'
    )),
    CONSTRAINT CK_refunds_amount CHECK (amount > 0),
    CONSTRAINT CK_refunds_status CHECK (status IN (
        'PENDING_APPROVAL',
        'APPROVED_PENDING_EXECUTION',
        'APPROVED',
        'PROCESSING',
        'COMPLETED',
        'FAILED_RECONCILIATION'
    ))
);

CREATE TABLE commission_ledger (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    pickup_point_id BIGINT NOT NULL,
    order_id BIGINT NOT NULL,
    policy_id BIGINT NOT NULL,
    entry_type VARCHAR(30) NOT NULL,
    amount DECIMAL(18,2) NOT NULL,
    settlement_batch_id BIGINT NULL,
    note NVARCHAR(300) NULL,
    created_at DATETIMEOFFSET(7) NOT NULL CONSTRAINT DF_commission_ledger_created_at DEFAULT SYSDATETIMEOFFSET(),
    CONSTRAINT FK_commission_ledger_point FOREIGN KEY (pickup_point_id) REFERENCES pickup_points(id),
    CONSTRAINT FK_commission_ledger_order FOREIGN KEY (order_id) REFERENCES orders(id),
    CONSTRAINT FK_commission_ledger_policy FOREIGN KEY (policy_id) REFERENCES commission_policies(id),
    CONSTRAINT CK_commission_ledger_type CHECK (entry_type IN (
        'CREDIT_COMMISSION',
        'DEBIT_REFUND_REVERSAL',
        'MANUAL_ADJUSTMENT'
    )),
    CONSTRAINT CK_commission_ledger_sign CHECK (
        (entry_type = 'CREDIT_COMMISSION' AND amount >= 0) OR
        (entry_type = 'DEBIT_REFUND_REVERSAL' AND amount <= 0) OR
        (entry_type = 'MANUAL_ADJUSTMENT')
    )
);

-- Ràng buộc chống tính trùng hoa hồng cho 1 đơn hàng (Filtered Unique Index)
CREATE UNIQUE INDEX UX_CommissionLedger_Order_Credit
    ON commission_ledger(order_id)
    WHERE entry_type = 'CREDIT_COMMISSION';

GO

-- Trigger bảo vệ tính bất biến (Append-only) của sổ cái hoa hồng
CREATE TRIGGER TR_CommissionLedger_Immutable
ON commission_ledger
INSTEAD OF UPDATE, DELETE
AS
BEGIN
    SET NOCOUNT ON;
    THROW 50001, N'Bảng commission_ledger là sổ cái bất biến (Append-only): Tuyệt đối không được UPDATE hoặc DELETE dòng đã ghi.', 1;
END;
GO

-- ------------------------------------------------------------------------------
-- 5.5. Nhóm Tự động hóa, Thông báo & AI
-- ------------------------------------------------------------------------------
CREATE TABLE shedlock (
    name VARCHAR(64) NOT NULL PRIMARY KEY,
    lock_until DATETIME2(7) NOT NULL,
    locked_at DATETIME2(7) NOT NULL,
    locked_by VARCHAR(255) NOT NULL
);

CREATE TABLE job_runs (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    job_name VARCHAR(80) NOT NULL,
    cycle_id BIGINT NULL,
    started_at DATETIMEOFFSET(7) NOT NULL CONSTRAINT DF_job_runs_started_at DEFAULT SYSDATETIMEOFFSET(),
    finished_at DATETIMEOFFSET(7) NULL,
    processed_count INT NOT NULL CONSTRAINT DF_job_runs_processed DEFAULT 0,
    status VARCHAR(30) NOT NULL,
    error_message NVARCHAR(MAX) NULL,
    CONSTRAINT FK_job_runs_cycle FOREIGN KEY (cycle_id) REFERENCES order_cycles(id),
    CONSTRAINT CK_job_runs_status CHECK (status IN ('RUNNING', 'SUCCESS', 'FAILED'))
);

CREATE TABLE outbox_events (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    aggregate_type VARCHAR(50) NOT NULL,
    aggregate_id VARCHAR(64) NOT NULL,
    event_type VARCHAR(80) NOT NULL,
    payload NVARCHAR(MAX) NOT NULL,
    status VARCHAR(20) NOT NULL CONSTRAINT DF_outbox_events_status DEFAULT 'PENDING',
    created_at DATETIMEOFFSET(7) NOT NULL CONSTRAINT DF_outbox_events_created_at DEFAULT SYSDATETIMEOFFSET(),
    published_at DATETIMEOFFSET(7) NULL,
    CONSTRAINT CK_outbox_events_status CHECK (status IN ('PENDING', 'PUBLISHED', 'FAILED'))
);

CREATE INDEX IX_outbox_events_pending ON outbox_events(status, created_at ASC);

CREATE TABLE recipes (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    name NVARCHAR(150) NOT NULL,
    servings INT NOT NULL,
    cooking_time_minutes INT NOT NULL,
    instructions NVARCHAR(MAX) NOT NULL,
    dietary_tags NVARCHAR(255) NULL,
    is_active BIT NOT NULL CONSTRAINT DF_recipes_is_active DEFAULT 1,
    CONSTRAINT CK_recipes_servings CHECK (servings > 0),
    CONSTRAINT CK_recipes_cooking_time CHECK (cooking_time_minutes > 0)
);

CREATE TABLE recipe_ingredients (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    recipe_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    ingredient_name NVARCHAR(120) NOT NULL,
    required_weight_grams INT NOT NULL,
    is_optional BIT NOT NULL CONSTRAINT DF_recipe_ingredients_optional DEFAULT 0,
    CONSTRAINT FK_recipe_ingredients_recipe FOREIGN KEY (recipe_id) REFERENCES recipes(id),
    CONSTRAINT FK_recipe_ingredients_product FOREIGN KEY (product_id) REFERENCES products(id),
    CONSTRAINT CK_recipe_ingredients_weight CHECK (required_weight_grams > 0)
);

CREATE TABLE knowledge_documents (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    doc_code VARCHAR(50) NOT NULL,
    version_tag VARCHAR(20) NOT NULL,
    title NVARCHAR(200) NOT NULL,
    content_markdown NVARCHAR(MAX) NOT NULL,
    is_active BIT NOT NULL CONSTRAINT DF_knowledge_documents_active DEFAULT 1,
    CONSTRAINT UQ_knowledge_documents_code UNIQUE (doc_code)
);

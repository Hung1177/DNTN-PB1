-- ==============================================================================
-- V2__seed_initial_data.sql
-- Seed Data for O2O Food Pickup System:
-- 1. 6 Roles & Sample Accounts (Admin, Warehouse, Dispatcher, Driver, 3 Agents, Customers)
-- 2. 3 Pickup Points in Hanoi & Point Assignments
-- 3. Commission Policies & Active Order Cycles (UTC+07:00)
-- 4. 15 Fixed-Pack Products & Product Variants
-- 5. Multi-Lot FEFO Batches, Inventory Balances & Stock Movements
-- 6. 30 Vietnamese Cooking Recipes & Recipe Ingredients
-- 7. Policy RAG Knowledge Documents
-- ==============================================================================

-- ------------------------------------------------------------------------------
-- 1. Roles (6 Vai trò cốt lõi)
-- ------------------------------------------------------------------------------
SET IDENTITY_INSERT roles ON;
INSERT INTO roles (id, code) VALUES
(1, 'ROLE_CUSTOMER'),
(2, 'ROLE_WAREHOUSE'),
(3, 'ROLE_DISPATCHER'),
(4, 'ROLE_DRIVER'),
(5, 'ROLE_AGENT'),
(6, 'ROLE_ADMIN');
SET IDENTITY_INSERT roles OFF;

-- ------------------------------------------------------------------------------
-- 2. Users (Mật khẩu mẫu: Password@123 -> BCrypt hash)
-- ------------------------------------------------------------------------------
SET IDENTITY_INSERT users ON;
INSERT INTO users (id, email, phone, password_hash, full_name, status, created_at) VALUES
(1, 'admin@o2ofood.vn',      '0901000001', '$2a$10$8.UnVuG9HHgffUDAlk8qfOuVGkqRzgVymGe07xd00DMxs.AQubh4a', N'Nguyễn Quản Trị (Admin)',         'ACTIVE', '2026-11-01T08:00:00+07:00'),
(2, 'warehouse@o2ofood.vn',  '0901000002', '$2a$10$8.UnVuG9HHgffUDAlk8qfOuVGkqRzgVymGe07xd00DMxs.AQubh4a', N'Trần Văn Kho (Thủ kho trung tâm)', 'ACTIVE', '2026-11-01T08:00:00+07:00'),
(3, 'dispatcher@o2ofood.vn', '0901000003', '$2a$10$8.UnVuG9HHgffUDAlk8qfOuVGkqRzgVymGe07xd00DMxs.AQubh4a', N'Lê Điều Phối (Dispatcher)',        'ACTIVE', '2026-11-01T08:00:00+07:00'),
(4, 'driver1@o2ofood.vn',    '0901000004', '$2a$10$8.UnVuG9HHgffUDAlk8qfOuVGkqRzgVymGe07xd00DMxs.AQubh4a', N'Phạm Tài Xế 01 (Xe lạnh BKS 29H)', 'ACTIVE', '2026-11-01T08:00:00+07:00'),
(5, 'driver2@o2ofood.vn',    '0901000005', '$2a$10$8.UnVuG9HHgffUDAlk8qfOuVGkqRzgVymGe07xd00DMxs.AQubh4a', N'Hoàng Tài Xế 02 (Xe lạnh BKS 30A)','ACTIVE', '2026-11-01T08:00:00+07:00'),
(6, 'agent.nhon@o2ofood.vn', '0901000006', '$2a$10$8.UnVuG9HHgffUDAlk8qfOuVGkqRzgVymGe07xd00DMxs.AQubh4a', N'Đỗ Thị Đại Lý Nhổn (Bắc Từ Liêm)','ACTIVE', '2026-11-01T08:00:00+07:00'),
(7, 'agent.caugiay@o2ofood.vn','0901000007','$2a$10$8.UnVuG9HHgffUDAlk8qfOuVGkqRzgVymGe07xd00DMxs.AQubh4a',N'Vũ Văn Đại Lý Cầu Giấy',           'ACTIVE', '2026-11-01T08:00:00+07:00'),
(8, 'agent.dongda@o2ofood.vn','0901000008','$2a$10$8.UnVuG9HHgffUDAlk8qfOuVGkqRzgVymGe07xd00DMxs.AQubh4a', N'Bùi Thị Đại Lý Đống Đa',          'ACTIVE', '2026-11-01T08:00:00+07:00'),
(9, 'khachhang1@gmail.com',  '0988111222', '$2a$10$8.UnVuG9HHgffUDAlk8qfOuVGkqRzgVymGe07xd00DMxs.AQubh4a', N'Nguyễn Minh Anh (Khách hàng 01)',  'ACTIVE', '2026-11-05T09:00:00+07:00'),
(10,'khachhang2@gmail.com',  '0988333444', '$2a$10$8.UnVuG9HHgffUDAlk8qfOuVGkqRzgVymGe07xd00DMxs.AQubh4a', N'Trần Quốc Bảo (Khách hàng 02)',    'ACTIVE', '2026-11-05T09:30:00+07:00');
SET IDENTITY_INSERT users OFF;

INSERT INTO user_roles (user_id, role_id) VALUES
(1, 6), -- Admin
(2, 2), -- Warehouse
(3, 3), -- Dispatcher
(4, 4), -- Driver 1
(5, 4), -- Driver 2
(6, 5), -- Agent 1 (Nhổn - Bắc Từ Liêm)
(7, 5), -- Agent 2 (Cầu Giấy)
(8, 5), -- Agent 3 (Đống Đa)
(9, 1), -- Customer 1
(10, 1); -- Customer 2

-- ------------------------------------------------------------------------------
-- 3. 3 Đại lý nhận hàng tại Hà Nội (Pickup Points & Point Assignments)
-- ------------------------------------------------------------------------------
SET IDENTITY_INSERT pickup_points ON;
INSERT INTO pickup_points (id, code, name, address, latitude, longitude, cold_storage_capacity, status) VALUES
(1, 'PP-HN-BTL01', N'Điểm nhận Minh Khai - Bắc Từ Liêm (Gần ĐH Công Nghiệp)', N'Số 298 Đường Cầu Diễn, Phường Minh Khai, Quận Bắc Từ Liêm, Hà Nội', 21.053750, 105.735120, 150, 'ACTIVE'),
(2, 'PP-HN-CG02',  N'Điểm nhận Dịch Vọng Hậu - Cầu Giấy',                     N'Số 125 Đường Xuân Thủy, Phường Dịch Vọng Hậu, Quận Cầu Giấy, Hà Nội', 21.036820, 105.782460, 180, 'ACTIVE'),
(3, 'PP-HN-DD03',  N'Điểm nhận Láng Hạ - Đống Đa',                            N'Số 88 Đường Láng Hạ, Phường Láng Hạ, Quận Đống Đa, Hà Nội',          21.015640, 105.812930, 160, 'ACTIVE');
SET IDENTITY_INSERT pickup_points OFF;

SET IDENTITY_INSERT point_assignments ON;
INSERT INTO point_assignments (id, pickup_point_id, agent_user_id, active_from, active_to) VALUES
(1, 1, 6, '2026-11-01T00:00:00+07:00', NULL),
(2, 2, 7, '2026-11-01T00:00:00+07:00', NULL),
(3, 3, 8, '2026-11-01T00:00:00+07:00', NULL);
SET IDENTITY_INSERT point_assignments OFF;

-- ------------------------------------------------------------------------------
-- 4. Chính sách hoa hồng & Chu kỳ đặt hàng (Order Cycles & Point Cycles)
-- ------------------------------------------------------------------------------
SET IDENTITY_INSERT commission_policies ON;
INSERT INTO commission_policies (id, policy_code, calc_type, fixed_amount, percentage_rate, effective_from, effective_to) VALUES
(1, 'POL-NET-5PCT-2026',   'PERCENTAGE_NET_MERCHANDISE', NULL,     5.00, '2026-01-01T00:00:00+07:00', NULL),
(2, 'POL-FIXED-10K-2026',  'FIXED_PER_ORDER',            10000.00, NULL, '2026-01-01T00:00:00+07:00', NULL);
SET IDENTITY_INSERT commission_policies OFF;

-- Đợt 1: Đặt ngày 17/11/2026 (chốt 23:59:59 17/11), Giao 18/11/2026 (nhận từ 16:00 18/11), Tự hoàn tất 00:00 19/11
-- Đợt 2: Đặt ngày 18/11/2026 (chốt 23:59:59 18/11), Giao 19/11/2026 (nhận từ 16:00 19/11), Tự hoàn tất 00:00 20/11
SET IDENTITY_INSERT order_cycles ON;
INSERT INTO order_cycles (
    id, cycle_code, delivery_date,
    open_at, cutoff_at, auto_confirm_at,
    warehouse_pack_target_at, dispatch_start_at, pickup_start_at, auto_complete_at,
    payment_timeout_minutes, status
) VALUES
(1, 'CYCLE-2026-11-18', '2026-11-18',
    '2026-11-17T00:00:00+07:00', '2026-11-17T23:59:59+07:00', '2026-11-18T00:00:00+07:00',
    '2026-11-18T08:00:00+07:00', '2026-11-18T08:00:00+07:00', '2026-11-18T16:00:00+07:00', '2026-11-19T00:00:00+07:00',
    30, 'OPEN'),
(2, 'CYCLE-2026-11-19', '2026-11-19',
    '2026-11-18T00:00:00+07:00', '2026-11-18T23:59:59+07:00', '2026-11-19T00:00:00+07:00',
    '2026-11-19T08:00:00+07:00', '2026-11-19T08:00:00+07:00', '2026-11-19T16:00:00+07:00', '2026-11-20T00:00:00+07:00',
    30, 'OPEN');
SET IDENTITY_INSERT order_cycles OFF;

SET IDENTITY_INSERT point_cycles ON;
INSERT INTO point_cycles (id, cycle_id, pickup_point_id, max_orders, current_orders, status) VALUES
(1, 1, 1, 150, 0, 'OPEN'),
(2, 1, 2, 180, 0, 'OPEN'),
(3, 1, 3, 160, 0, 'OPEN'),
(4, 2, 1, 150, 0, 'OPEN'),
(5, 2, 2, 180, 0, 'OPEN'),
(6, 2, 3, 160, 0, 'OPEN');
SET IDENTITY_INSERT point_cycles OFF;

-- ------------------------------------------------------------------------------
-- 5. 15 Sản phẩm đóng gói cố định (Products & Product Variants)
-- ------------------------------------------------------------------------------
SET IDENTITY_INSERT products ON;
INSERT INTO products (id, sku_prefix, name, category, storage_type, min_shelf_life_days_at_pickup, dietary_tags, allergen_tags, is_active) VALUES
(1,  'MEAT-PORK-BELLY', N'Thịt ba chỉ heo VietGAP',         N'Thịt tươi sống',   'CHILLED', 2, N'high-protein',               NULL,          1),
(2,  'MEAT-BEEF-LOIN',  N'Thịt bò thăn nội Úc',             N'Thịt tươi sống',   'CHILLED', 2, N'high-protein,low-carb',      NULL,          1),
(3,  'MEAT-CHICK-BRST', N'Ức gà phi lê tươi sạch',          N'Thịt gia cầm',     'CHILLED', 2, N'high-protein,low-fat,eat-clean', NULL,      1),
(4,  'SEA-SALMON-FIL',  N'Cá hồi Nauy phi lê tươi',         N'Thủy hải sản',     'CHILLED', 2, N'high-protein,omega-3',       N'fish,seafood', 1),
(5,  'SEA-SHRIMP-PEELED',N'Tôm sú tươi bóc nõn',            N'Thủy hải sản',     'FROZEN',  7, N'high-protein,low-fat',       N'shellfish,seafood', 1),
(6,  'EGG-CHICKEN-10',  N'Trứng gà ta sạch thả vườn',       N'Trứng & Đậu',      'AMBIENT', 5, N'high-protein,vegetarian-egg',N'egg',        1),
(7,  'TOFU-FRESH-BOX',  N'Đậu phụ non làng Mơ',             N'Trứng & Đậu',      'CHILLED', 1, N'vegan,vegetarian,low-calorie',N'soy',       1),
(8,  'VEG-CHOYSUM',     N'Rau cải ngọt hữu cơ',             N'Rau ăn lá',        'CHILLED', 1, N'vegan,vegetarian,high-fiber',NULL,          1),
(9,  'VEG-MORNINGGLORY',N'Rau muống sạch VietGAP',          N'Rau ăn lá',        'CHILLED', 1, N'vegan,vegetarian,high-fiber',NULL,          1),
(10, 'VEG-BROCCOLI',    N'Bông cải xanh Đà Lạt',            N'Rau củ quả',       'CHILLED', 2, N'vegan,vegetarian,eat-clean', NULL,          1),
(11, 'VEG-TOMATO',      N'Cà chua chín cây Đà Lạt',         N'Rau củ quả',       'AMBIENT', 3, N'vegan,vegetarian',           NULL,          1),
(12, 'VEG-POTATO',      N'Khoai tây vàng Đà Lạt',           N'Rau củ quả',       'AMBIENT', 7, N'vegan,vegetarian',           NULL,          1),
(13, 'VEG-CARROT',      N'Cà rốt tươi Đà Lạt',              N'Rau củ quả',       'AMBIENT', 5, N'vegan,vegetarian',           NULL,          1),
(14, 'MUSH-ENOKI',      N'Nấm kim châm trắng Hàn Quốc',     N'Nấm tươi',         'CHILLED', 2, N'vegan,vegetarian,low-calorie',NULL,         1),
(15, 'RICE-ST25-2KG',   N'Gạo thơm ST25 Ông Cua chính hãng',N'Lương thực khô',   'AMBIENT', 30,N'vegan,vegetarian,gluten-free',NULL,         1);
SET IDENTITY_INSERT products OFF;

SET IDENTITY_INSERT product_variants ON;
INSERT INTO product_variants (id, product_id, sku, pack_name, pack_weight_grams, unit_price, is_active) VALUES
(1,  1,  'MEAT-PORK-BELLY-500G',  N'Khay 500g',          500,  85000.00, 1),
(2,  2,  'MEAT-BEEF-LOIN-300G',   N'Khay 300g',          300, 115000.00, 1),
(3,  3,  'MEAT-CHICK-BRST-500G',  N'Khay 500g',          500,  55000.00, 1),
(4,  4,  'SEA-SALMON-FIL-300G',   N'Khay 300g',          300, 145000.00, 1),
(5,  5,  'SEA-SHRIMP-PEELED-300G',N'Khay 300g',          300,  98000.00, 1),
(6,  6,  'EGG-CHICKEN-BOX10',     N'Hộp 10 quả (600g)',  600,  38000.00, 1),
(7,  7,  'TOFU-FRESH-BOX400G',    N'Hộp 4 bìa (400g)',   400,  18000.00, 1),
(8,  8,  'VEG-CHOYSUM-500G',      N'Túi 500g',           500,  19000.00, 1),
(9,  9,  'VEG-MORNINGGLORY-500G', N'Túi 500g',           500,  16000.00, 1),
(10, 10, 'VEG-BROCCOLI-500G',     N'Túi 500g',           500,  32000.00, 1),
(11, 11, 'VEG-TOMATO-500G',       N'Túi 500g',           500,  22000.00, 1),
(12, 12, 'VEG-POTATO-1KG',        N'Túi 1000g',         1000,  35000.00, 1),
(13, 13, 'VEG-CARROT-500G',       N'Túi 500g',           500,  18000.00, 1),
(14, 14, 'MUSH-ENOKI-300G',       N'Gói 300g',           300,  24000.00, 1),
(15, 15, 'RICE-ST25-BAG2KG',      N'Túi 2000g',         2000,  78000.00, 1);
SET IDENTITY_INSERT product_variants OFF;

-- ------------------------------------------------------------------------------
-- 6. Các Lô hàng FEFO (Lots) & Tồn kho theo chu kỳ (Inventory Balances)
-- ------------------------------------------------------------------------------
SET IDENTITY_INSERT lots ON;
INSERT INTO lots (id, variant_id, lot_code, supplier_name, manufactured_date, expiry_date, initial_qty, remaining_qty, status, received_at) VALUES
-- Variant 1 (Thịt ba chỉ heo): 2 lô AVAILABLE có hạn khác nhau + 1 lô QUARANTINED để kiểm thử chuẩn FEFO (AT-07)
(1,  1,  'LOT-PORK-20261116-A', N'Nông trại CP Hà Nội',       '2026-11-16', '2026-11-21', 40, 40, 'AVAILABLE',   '2026-11-16T06:00:00+07:00'),
(2,  1,  'LOT-PORK-20261117-B', N'Nông trại CP Hà Nội',       '2026-11-17', '2026-11-23', 60, 60, 'AVAILABLE',   '2026-11-17T06:00:00+07:00'),
(3,  1,  'LOT-PORK-20261115-Q', N'Nông trại CP Hà Nội',       '2026-11-15', '2026-11-20', 15, 15, 'QUARANTINED', '2026-11-15T06:00:00+07:00'),
-- Variant 2 (Thịt bò Úc): 2 lô FEFO
(4,  2,  'LOT-BEEF-20261116-A', N'Thực phẩm Nhập khẩu Minh Phát','2026-11-16','2026-11-22',30, 30, 'AVAILABLE',   '2026-11-16T07:00:00+07:00'),
(5,  2,  'LOT-BEEF-20261117-B', N'Thực phẩm Nhập khẩu Minh Phát','2026-11-17','2026-11-25',50, 50, 'AVAILABLE',   '2026-11-17T07:00:00+07:00'),
-- Variant 3..15
(6,  3,  'LOT-CHICK-20261117-A',N'Trang trại Dabaco Bắc Ninh', '2026-11-17', '2026-11-22', 80, 80, 'AVAILABLE',   '2026-11-17T05:30:00+07:00'),
(7,  4,  'LOT-SALM-20261117-A', N'Hải sản Bắc Âu Việt Nam',    '2026-11-17', '2026-11-22', 50, 50, 'AVAILABLE',   '2026-11-17T06:30:00+07:00'),
(8,  5,  'LOT-SHRM-20261110-A', N'Thủy sản Minh Phú Cà Mau',   '2026-11-10', '2027-02-10', 90, 90, 'AVAILABLE',   '2026-11-12T08:00:00+07:00'),
(9,  6,  'LOT-EGG-20261116-A',  N'Trại gà sạch Ba Vì',         '2026-11-16', '2026-11-30',120,120, 'AVAILABLE',   '2026-11-16T07:30:00+07:00'),
(10, 7,  'LOT-TOFU-20261117-A', N'Xưởng đậu sạch Hoàng Mai',   '2026-11-17', '2026-11-20',100,100, 'AVAILABLE',   '2026-11-17T05:00:00+07:00'),
(11, 8,  'LOT-CHOY-20261117-A', N'HTX Rau sạch Đông Anh',      '2026-11-17', '2026-11-20',100,100, 'AVAILABLE',   '2026-11-17T05:15:00+07:00'),
(12, 9,  'LOT-MGLR-20261117-A', N'HTX Rau sạch Đông Anh',      '2026-11-17', '2026-11-20',100,100, 'AVAILABLE',   '2026-11-17T05:20:00+07:00'),
(13, 10, 'LOT-BROC-20261116-A', N'Nông trại Cầu Đất Đà Lạt',   '2026-11-16', '2026-11-23', 80, 80, 'AVAILABLE',   '2026-11-16T09:00:00+07:00'),
(14, 11, 'LOT-TOMA-20261116-A', N'Nông trại Cầu Đất Đà Lạt',   '2026-11-16', '2026-11-25',100,100, 'AVAILABLE',   '2026-11-16T09:10:00+07:00'),
(15, 12, 'LOT-POTA-20261112-A', N'Nông sản Lâm Đồng',          '2026-11-12', '2026-12-15',100,100, 'AVAILABLE',   '2026-11-13T10:00:00+07:00'),
(16, 13, 'LOT-CARR-20261114-A', N'Nông sản Lâm Đồng',          '2026-11-14', '2026-12-05',100,100, 'AVAILABLE',   '2026-11-15T10:00:00+07:00'),
(17, 14, 'LOT-ENOK-20261116-A', N'Nấm sạch Kinoko Thanh Hà',   '2026-11-16', '2026-11-24', 90, 90, 'AVAILABLE',   '2026-11-16T08:00:00+07:00'),
(18, 15, 'LOT-ST25-20261001-A', N'DNTN Hồ Quang Trí Sóc Trăng','2026-10-01', '2027-04-01',150,150, 'AVAILABLE',   '2026-10-15T09:00:00+07:00');
SET IDENTITY_INSERT lots OFF;

-- Tồn kho khả dụng cho Đợt 1 (CYCLE-2026-11-18) và Đợt 2 (CYCLE-2026-11-19)
INSERT INTO inventory_balances (cycle_id, variant_id, available_qty, reserved_qty, committed_qty) VALUES
(1, 1, 100, 0, 0), (1, 2,  80, 0, 0), (1, 3,  80, 0, 0), (1, 4,  50, 0, 0), (1, 5,  90, 0, 0),
(1, 6, 120, 0, 0), (1, 7, 100, 0, 0), (1, 8, 100, 0, 0), (1, 9, 100, 0, 0), (1, 10, 80, 0, 0),
(1, 11,100, 0, 0), (1, 12,100, 0, 0), (1, 13,100, 0, 0), (1, 14, 90, 0, 0), (1, 15,150, 0, 0),
(2, 1, 100, 0, 0), (2, 2,  80, 0, 0), (2, 3,  80, 0, 0), (2, 4,  50, 0, 0), (2, 5,  90, 0, 0),
(2, 6, 120, 0, 0), (2, 7, 100, 0, 0), (2, 8, 100, 0, 0), (2, 9, 100, 0, 0), (2, 10, 80, 0, 0),
(2, 11,100, 0, 0), (2, 12,100, 0, 0), (2, 13,100, 0, 0), (2, 14, 90, 0, 0), (2, 15,150, 0, 0);

-- Nhật ký nhập kho ban đầu (Stock Movements)
INSERT INTO stock_movements (variant_id, lot_id, order_id, movement_type, quantity, created_by, created_at) VALUES
(1,  1,  NULL, 'INBOUND',  40, 2, '2026-11-16T06:00:00+07:00'),
(1,  2,  NULL, 'INBOUND',  60, 2, '2026-11-17T06:00:00+07:00'),
(1,  3,  NULL, 'INBOUND',  15, 2, '2026-11-15T06:00:00+07:00'),
(2,  4,  NULL, 'INBOUND',  30, 2, '2026-11-16T07:00:00+07:00'),
(2,  5,  NULL, 'INBOUND',  50, 2, '2026-11-17T07:00:00+07:00'),
(3,  6,  NULL, 'INBOUND',  80, 2, '2026-11-17T05:30:00+07:00'),
(4,  7,  NULL, 'INBOUND',  50, 2, '2026-11-17T06:30:00+07:00'),
(5,  8,  NULL, 'INBOUND',  90, 2, '2026-11-12T08:00:00+07:00'),
(6,  9,  NULL, 'INBOUND', 120, 2, '2026-11-16T07:30:00+07:00'),
(7,  10, NULL, 'INBOUND', 100, 2, '2026-11-17T05:00:00+07:00'),
(8,  11, NULL, 'INBOUND', 100, 2, '2026-11-17T05:15:00+07:00'),
(9,  12, NULL, 'INBOUND', 100, 2, '2026-11-17T05:20:00+07:00'),
(10, 13, NULL, 'INBOUND',  80, 2, '2026-11-16T09:00:00+07:00'),
(11, 14, NULL, 'INBOUND', 100, 2, '2026-11-16T09:10:00+07:00'),
(12, 15, NULL, 'INBOUND', 100, 2, '2026-11-13T10:00:00+07:00'),
(13, 16, NULL, 'INBOUND', 100, 2, '2026-11-15T10:00:00+07:00'),
(14, 17, NULL, 'INBOUND',  90, 2, '2026-11-16T08:00:00+07:00'),
(15, 18, NULL, 'INBOUND', 150, 2, '2026-10-15T09:00:00+07:00');

-- ------------------------------------------------------------------------------
-- 7. 30 Công thức nấu ăn cơ bản (Recipes & Recipe Ingredients)
-- ------------------------------------------------------------------------------
SET IDENTITY_INSERT recipes ON;
INSERT INTO recipes (id, name, servings, cooking_time_minutes, instructions, dietary_tags, is_active) VALUES
(1,  N'Thịt ba chỉ kho tàu trứng gà',        4, 45, N'Thái miếng thịt ba chỉ 500g, ướp nước mắm đường, kho mềm cùng trứng gà luộc bóc vỏ.', N'traditional,high-protein', 1),
(2,  N'Đậu phụ nhồi thịt sốt cà chua',       3, 30, N'Băm nhỏ thịt ba chỉ, nhồi vào đậu phụ, rán vàng rồi sốt cùng cà chua chín.',         N'traditional,family-meal',  1),
(3,  N'Canh cải ngọt nấu thịt băm',          3, 15, N'Phi thơm hành, xào thịt heo băm, thêm nước đun sôi và thả cải ngọt vào nấu chín tới.',N'quick,soup',               1),
(4,  N'Rau muống luộc dầm sấu & Thịt rang',   3, 25, N'Luộc rau muống xanh giòn, thịt ba chỉ thái mỏng rang cháy cạnh mặn ngọt.',            N'traditional,quick',        1),
(5,  N'Bò xào bông cải xanh',                3, 20, N'Thái mỏng thịt bò thăn, ướp tỏi tiêu, xào lửa lớn cùng bông cải xanh chần sơ.',       N'high-protein,low-carb',    1),
(6,  N'Bò lúc lắc khoai tây chiên',          4, 30, N'Thịt bò thái vuông ướp đậm đà áp chảo cùng khoai tây chiên vàng và cà chua.',         N'high-protein,kids-favorite',1),
(7,  N'Canh thịt bò nấu cà chua nấm kim châm',3,20, N'Xào cà chua, thêm nước dùng, thả nấm kim châm và thịt bò thái mỏng vào tắt bếp.',     N'quick,soup,low-carb',      1),
(8,  N'Ức gà áp chảo bông cải xanh Eat Clean',2,20, N'Ức gà ướp muối tiêu áp chảo vàng đều, ăn kèm bông cải xanh và cà rốt luộc.',          N'eat-clean,low-fat,high-protein',1),
(9,  N'Ức gà xào nấm kim châm & cà rốt',     2, 20, N'Ức gà thái sợi xào nhanh với nấm kim châm và cà rốt thái chỉ.',                       N'eat-clean,low-fat',        1),
(10, N'Canh gà nấu khoai tây cà rốt',        4, 35, N'Hầm ức gà cắt quân cờ cùng khoai tây và cà rốt cho ngọt nước.',                       N'family-meal,soup',         1),
(11, N'Cá hồi áp chảo sốt cà chua',          2, 20, N'Cá hồi phi lê áp chảo giòn da, rưới sốt cà chua tươi chua ngọt.',                     N'omega-3,high-protein',     1),
(12, N'Cá hồi nướng bông cải xanh & khoai tây',2,30,N'Nướng cá hồi cùng bông cải xanh và khoai tây cắt múi cau.',                            N'eat-clean,omega-3',        1),
(13, N'Canh chua cá hồi cà chua đậu phụ',    3, 25, N'Nấu canh chua cá hồi với cà chua và đậu phụ non mềm mát.',                            N'omega-3,soup',             1),
(14, N'Tôm sú rim thịt ba chỉ đậm đà',       4, 25, N'Rang thịt ba chỉ cháy cạnh, cho tôm sú bóc nõn vào rim mắm đường.',                   N'traditional,high-protein', 1),
(15, N'Tôm xào bông cải xanh cà rốt',        3, 15, N'Xào tôm sú tươi với bông cải xanh và cà rốt giòn ngọt.',                              N'low-fat,high-protein',     1),
(16, N'Canh cải ngọt nấu tôm tươi',          3, 15, N'Băm tôm sú nấu canh cùng rau cải ngọt thanh mát.',                                    N'quick,soup,low-fat',       1),
(17, N'Trứng chiên cà chua thịt băm',        3, 15, N'Đánh tan trứng gà cùng thịt băm và cà chua thái hạt lựu, chiên vàng.',                N'quick,budget',             1),
(18, N'Đậu phụ sốt cà chua truyền thống',    2, 15, N'Rán vàng đậu phụ làng Mơ, sốt ngập trong nước sốt cà chua chín.',                     N'vegan,vegetarian,budget',  1),
(19, N'Canh đậu phụ nấm kim châm cà chua',   2, 15, N'Nấu cà chua lấy nước ngọt, thả đậu phụ non và nấm kim châm vào đun sôi.',             N'vegan,vegetarian,low-calorie',1),
(20, N'Rau muống xào tỏi truyền thống',      2, 10, N'Chần sơ rau muống, xào lửa lớn với tỏi đập dập cho xanh giòn.',                       N'vegan,vegetarian,budget',  1),
(21, N'Cơm trắng ST25 & Thịt ba chỉ rang',   4, 30, N'Nấu cơm gạo ST25 dẻo thơm ăn kèm thịt ba chỉ rang cháy cạnh và canh cải.',            N'traditional,family-meal',  1),
(22, N'Cháo cá hồi cà rốt dinh dưỡng',       3, 40, N'Ninh gạo ST25 nhừ cùng cà rốt băm nhỏ và cá hồi phi lê xào thơm.',                    N'omega-3,kids-favorite',    1),
(23, N'Cháo ức gà nấm kim châm thanh đạm',   3, 35, N'Nấu cháo gạo ST25 với ức gà xé phay và nấm kim châm cắt nhỏ.',                        N'eat-clean,low-fat',        1),
(24, N'Trứng cuộn nấm kim châm & cà rốt',    2, 15, N'Tráng trứng gà mỏng cuộn nấm kim châm và cà rốt thái sợi.',                           N'vegetarian-egg,budget',    1),
(25, N'Thịt ba chỉ cuộn nấm kim châm áp chảo',3,25, N'Thái mỏng thịt ba chỉ cuộn nấm kim châm, áp chảo sốt tương đậm đà.',                  N'high-protein,popular',     1),
(26, N'Bò hầm khoai tây cà rốt kiểu Việt',   4, 50, N'Hầm thịt bò thăn cùng khoai tây và cà rốt, cà chua cho mềm ngọt.',                    N'family-meal,high-protein', 1),
(27, N'Tôm sốt trứng muối/trứng gà béo ngậy',3, 20, N'Tôm sú chiên sơ đảo cùng lòng đỏ trứng gà đánh bông mịn.',                            N'high-protein,popular',     1),
(28, N'Rau củ luộc thập cẩm kho quẹt thịt',  4, 25, N'Luộc bông cải xanh, cà rốt, cải ngọt chấm cùng thịt ba chỉ kho quẹt.',                N'traditional,high-fiber',   1),
(29, N'Cơm chiên trứng tôm sú cà rốt',       3, 20, N'Cơm nguội nấu từ gạo ST25 chiên săn hạt cùng trứng gà, tôm sú và cà rốt hạt lựu.',   N'quick,family-meal',        1),
(30, N'Lẩu nấm kim châm bò Mỹ & đậu phụ tại gia',4,30,N'Nước dùng cà chua thanh ngọt nhúng thịt bò, nấm kim châm, đậu phụ non và cải ngọt.', N'family-meal,high-protein', 1);
SET IDENTITY_INSERT recipes OFF;

INSERT INTO recipe_ingredients (recipe_id, product_id, ingredient_name, required_weight_grams, is_optional) VALUES
-- 1. Thịt ba chỉ kho tàu trứng gà
(1, 1, N'Thịt ba chỉ heo', 500, 0), (1, 6, N'Trứng gà ta', 300, 0),
-- 2. Đậu phụ nhồi thịt sốt cà chua
(2, 7, N'Đậu phụ non', 400, 0), (2, 1, N'Thịt ba chỉ heo', 250, 0), (2, 11, N'Cà chua', 300, 0),
-- 3. Canh cải ngọt nấu thịt băm
(3, 8, N'Rau cải ngọt', 500, 0), (3, 1, N'Thịt ba chỉ heo', 150, 0),
-- 4. Rau muống luộc & Thịt rang
(4, 9, N'Rau muống sạch', 500, 0), (4, 1, N'Thịt ba chỉ heo', 400, 0),
-- 5. Bò xào bông cải xanh
(5, 2, N'Thịt bò thăn nội', 300, 0), (5, 10, N'Bông cải xanh', 400, 0),
-- 6. Bò lúc lắc khoai tây chiên
(6, 2, N'Thịt bò thăn nội', 300, 0), (6, 12, N'Khoai tây vàng', 500, 0), (6, 11, N'Cà chua', 200, 1),
-- 7. Canh thịt bò nấu cà chua nấm kim châm
(7, 2, N'Thịt bò thăn nội', 200, 0), (7, 11, N'Cà chua', 300, 0), (7, 14, N'Nấm kim châm', 300, 0),
-- 8. Ức gà áp chảo bông cải xanh Eat Clean
(8, 3, N'Ức gà phi lê', 400, 0), (8, 10, N'Bông cải xanh', 300, 0), (8, 13, N'Cà rốt', 200, 1),
-- 9. Ức gà xào nấm kim châm & cà rốt
(9, 3, N'Ức gà phi lê', 350, 0), (9, 14, N'Nấm kim châm', 300, 0), (9, 13, N'Cà rốt', 200, 0),
-- 10. Canh gà nấu khoai tây cà rốt
(10, 3, N'Ức gà phi lê', 400, 0), (10, 12, N'Khoai tây vàng', 500, 0), (10, 13, N'Cà rốt', 300, 0),
-- 11. Cá hồi áp chảo sốt cà chua
(11, 4, N'Cá hồi Nauy phi lê', 300, 0), (11, 11, N'Cà chua', 300, 0),
-- 12. Cá hồi nướng bông cải xanh & khoai tây
(12, 4, N'Cá hồi Nauy phi lê', 300, 0), (12, 10, N'Bông cải xanh', 300, 0), (12, 12, N'Khoai tây vàng', 400, 0),
-- 13. Canh chua cá hồi cà chua đậu phụ
(13, 4, N'Cá hồi Nauy phi lê', 300, 0), (13, 11, N'Cà chua', 300, 0), (13, 7, N'Đậu phụ non', 400, 0),
-- 14. Tôm sú rim thịt ba chỉ đậm đà
(14, 5, N'Tôm sú bóc nõn', 300, 0), (14, 1, N'Thịt ba chỉ heo', 300, 0),
-- 15. Tôm xào bông cải xanh cà rốt
(15, 5, N'Tôm sú bóc nõn', 300, 0), (15, 10, N'Bông cải xanh', 400, 0), (15, 13, N'Cà rốt', 200, 0),
-- 16. Canh cải ngọt nấu tôm tươi
(16, 8, N'Rau cải ngọt', 500, 0), (16, 5, N'Tôm sú bóc nõn', 200, 0),
-- 17. Trứng chiên cà chua thịt băm
(17, 6, N'Trứng gà ta', 300, 0), (17, 11, N'Cà chua', 200, 0), (17, 1, N'Thịt ba chỉ heo', 150, 1),
-- 18. Đậu phụ sốt cà chua truyền thống
(18, 7, N'Đậu phụ non', 400, 0), (18, 11, N'Cà chua', 400, 0),
-- 19. Canh đậu phụ nấm kim châm cà chua
(19, 7, N'Đậu phụ non', 400, 0), (19, 14, N'Nấm kim châm', 300, 0), (19, 11, N'Cà chua', 300, 0),
-- 20. Rau muống xào tỏi truyền thống
(20, 9, N'Rau muống sạch', 500, 0),
-- 21. Cơm trắng ST25 & Thịt ba chỉ rang
(21, 15, N'Gạo ST25', 500, 0), (21, 1, N'Thịt ba chỉ heo', 500, 0), (21, 8, N'Rau cải ngọt', 500, 0),
-- 22. Cháo cá hồi cà rốt dinh dưỡng
(22, 15, N'Gạo ST25', 200, 0), (22, 4, N'Cá hồi Nauy phi lê', 300, 0), (22, 13, N'Cà rốt', 200, 0),
-- 23. Cháo ức gà nấm kim châm thanh đạm
(23, 15, N'Gạo ST25', 200, 0), (23, 3, N'Ức gà phi lê', 300, 0), (23, 14, N'Nấm kim châm', 300, 0),
-- 24. Trứng cuộn nấm kim châm & cà rốt
(24, 6, N'Trứng gà ta', 300, 0), (24, 14, N'Nấm kim châm', 200, 0), (24, 13, N'Cà rốt', 150, 0),
-- 25. Thịt ba chỉ cuộn nấm kim châm áp chảo
(25, 1, N'Thịt ba chỉ heo', 500, 0), (25, 14, N'Nấm kim châm', 300, 0),
-- 26. Bò hầm khoai tây cà rốt kiểu Việt
(26, 2, N'Thịt bò thăn nội', 300, 0), (26, 12, N'Khoai tây vàng', 600, 0), (26, 13, N'Cà rốt', 300, 0), (26, 11, N'Cà chua', 300, 0),
-- 27. Tôm sốt trứng gà béo ngậy
(27, 5, N'Tôm sú bóc nõn', 300, 0), (27, 6, N'Trứng gà ta', 240, 0),
-- 28. Rau củ luộc thập cẩm kho quẹt thịt
(28, 10, N'Bông cải xanh', 400, 0), (28, 13, N'Cà rốt', 300, 0), (28, 8, N'Rau cải ngọt', 400, 0), (28, 1, N'Thịt ba chỉ heo', 300, 0),
-- 29. Cơm chiên trứng tôm sú cà rốt
(29, 15, N'Gạo ST25', 400, 0), (29, 6, N'Trứng gà ta', 240, 0), (29, 5, N'Tôm sú bóc nõn', 300, 0), (29, 13, N'Cà rốt', 200, 0),
-- 30. Lẩu nấm kim châm bò Mỹ & đậu phụ tại gia
(30, 2, N'Thịt bò thăn nội', 300, 0), (30, 14, N'Nấm kim châm', 300, 0), (30, 7, N'Đậu phụ non', 400, 0), (30, 8, N'Rau cải ngọt', 500, 0), (30, 11, N'Cà chua', 300, 0);

-- ------------------------------------------------------------------------------
-- 8. Tài liệu chính sách cho AI RAG (Knowledge Documents)
-- ------------------------------------------------------------------------------
SET IDENTITY_INSERT knowledge_documents ON;
INSERT INTO knowledge_documents (id, doc_code, version_tag, title, content_markdown, is_active) VALUES
(1, 'POL-CUTOFF-SCHEDULE', 'v1.0', N'Chính sách Giờ chốt đơn 23:59 và Khung giờ nhận hàng 16:00 D+1',
 N'### Quy định thời gian đặt hàng và nhận hàng (Múi giờ Asia/Ho_Chi_Minh UTC+07:00)
- **Hạn chót đặt hàng và thanh toán (cutoff_at):** 23:59:59 Ngày D cho đợt giao hàng Ngày D+1.
- **Thời gian giữ tồn chờ thanh toán (payment_timeout_minutes):** Tối đa 30 phút kể từ lúc nhấn Thanh toán (hoặc bị chặn trần bởi mốc 23:59:59 Ngày D). Quá 30 phút chưa có IPN xác nhận từ VNPAY, đơn hàng tự động hủy (PAYMENT_TIMEOUT) và hoàn trả tồn kho.
- **Mốc tự động khóa & xác nhận đơn (auto_confirm_at):** Đúng 00:00:00 Ngày D+1, hệ thống khóa quyền tự hủy và đẩy danh sách đơn sang kho trung tâm.
- **Mốc mở nhận hàng tại Đại lý (pickup_start_at):** Từ 16:00:00 Ngày D+1 (sau khi tài xế giao hàng và đại lý kiểm nhận đủ kiện hàng, không hư hỏng).
- **Mốc tự động hoàn tất đơn (auto_complete_at):** Đúng 00:00:00 Ngày D+2. Nếu khách chưa đến lấy hàng vật lý trước mốc này, đơn chuyển sang danh sách Hàng tồn khách chưa lấy tại đại lý (Unclaimed Inventory).', 1),

(2, 'POL-PAYMENT-CANCEL-REFUND', 'v1.0', N'Chính sách Thanh toán VNPAY 100%, Tự hủy đơn trước giờ chốt và Hoàn tiền',
 N'### Quy định Thanh toán, Hủy đơn và Hoàn tiền
- **Thanh toán trước 100% (No COD):** Hệ thống chỉ chấp nhận thanh toán trực tuyến qua VNPAY, tuyệt đối không thu tiền mặt khi nhận hàng.
- **Tự hủy đơn trước giờ chốt (trước 23:59:59 Ngày D):** Khách hàng được phép tự hủy đơn trên ứng dụng. Nếu đơn đã thanh toán (PAID_WAITING_CUTOFF), hệ thống tự động giải phóng tồn kho đúng 1 lần và tạo yêu cầu hoàn tiền 100%.
- **Sau 00:00:00 Ngày D+1:** Đơn hàng đã chuyển sang kho chuẩn bị nên khách hàng không thể tự hủy.
- **Khiếu nại sự cố & Hoàn tiền hàng lỗi:** Khi phát hiện hàng dập hỏng, giao thiếu hoặc sai quy cách, khách hàng gửi báo cáo sự cố kèm ảnh minh chứng. Đơn hàng có sự cố đang mở sẽ được chặn tự động hoàn tất lúc 00:00 D+2 để xử lý thu hồi về kho cách ly và hoàn tiền.', 1),

(3, 'POL-PACKAGING-FEFO', 'v1.0', N'Chính sách Đóng gói cố định và Phân bổ lô hàng theo hạn dùng FEFO',
 N'### Quy định Đóng gói cố định & An toàn thực phẩm FEFO
- **Quy cách đóng gói cố định:** Mọi sản phẩm được bán theo gói/khay/hộp có khối lượng cố định (ví dụ: Khay 500g, Túi 500g, Hộp 10 quả) để tính chính xác 100% số tiền khi thanh toán trực tuyến.
- **Xuất kho theo FEFO (First-Expired, First-Out):** Kho trung tâm luôn xuất lô có hạn sử dụng gần nhất nhưng bắt buộc phải đảm bảo thời hạn sử dụng còn lại tại ngày nhận hàng lớn hơn hoặc bằng số ngày an toàn tối thiểu của sản phẩm. Hàng hoàn trả về kho được đưa vào khu cách ly, tuyệt đối không cộng ngược vào tồn kho bán thương mại.', 1);
SET IDENTITY_INSERT knowledge_documents OFF;

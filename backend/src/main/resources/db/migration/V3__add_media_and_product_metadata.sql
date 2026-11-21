-- ==============================================================================
-- V3__add_media_and_product_metadata.sql
-- Bổ sung hình ảnh (imgDb), mô tả chi tiết & nguồn cung cấp cho products,
-- ảnh cá nhân (avatar_url) cho users, và ảnh đại lý (image_url) cho pickup_points
-- ==============================================================================

IF COL_LENGTH('dbo.products', 'description') IS NULL
    ALTER TABLE dbo.products ADD description NVARCHAR(1000) NULL;

IF COL_LENGTH('dbo.products', 'supplier_name') IS NULL
    ALTER TABLE dbo.products ADD supplier_name NVARCHAR(200) NULL;

IF COL_LENGTH('dbo.products', 'image_url') IS NULL
    ALTER TABLE dbo.products ADD image_url VARCHAR(500) NULL;

IF COL_LENGTH('dbo.users', 'avatar_url') IS NULL
    ALTER TABLE dbo.users ADD avatar_url VARCHAR(500) NULL;

IF COL_LENGTH('dbo.pickup_points', 'image_url') IS NULL
    ALTER TABLE dbo.pickup_points ADD image_url VARCHAR(500) NULL;

GO

UPDATE dbo.products SET
    image_url = '/imgDb/products/prd-pork-belly.jpg',
    supplier_name = N'Công ty Cổ phần Chăn nuôi C.P. Việt Nam',
    description = N'Thịt ba chỉ heo chuẩn VietGAP tỷ lệ nạc mỡ hài hòa, sơ chế đóng khay cố định 500g bảo quản mát 0–4°C.'
WHERE id = 1;

UPDATE dbo.products SET
    image_url = '/imgDb/products/prd-beef-shank.jpg',
    supplier_name = N'Công ty TNHH Thực phẩm Nhập khẩu Minh Phát',
    description = N'Thịt bò thăn nội Úc nhập khẩu chính ngạch, thớ thịt mềm ngọt tự nhiên, đóng khay cố định 300g.'
WHERE id = 2;

UPDATE dbo.products SET
    image_url = '/imgDb/products/prd-chicken-breast.jpg',
    supplier_name = N'Công ty Cổ phần Tập đoàn Dabaco Bắc Ninh',
    description = N'Ức gà phi lê bỏ da sạch tươi, giàu đạm ít béo, đóng khay cố định 500g phù hợp chế độ Eat Clean.'
WHERE id = 3;

UPDATE dbo.products SET
    image_url = '/imgDb/products/prd-salmon-fillet.jpg',
    supplier_name = N'Công ty TNHH Hải sản Bắc Âu Việt Nam',
    description = N'Cá hồi Nauy phi lê tươi vận chuyển đường hàng không, giàu Omega-3, đóng khay lạnh cố định 300g.'
WHERE id = 4;

UPDATE dbo.products SET
    image_url = '/imgDb/products/prd-shrimp-peeled.jpg',
    supplier_name = N'Công ty Cổ phần Tập đoàn Thủy sản Minh Phú Cà Mau',
    description = N'Tôm sú tươi bóc nõn rút chỉ lưng sạch sẽ, cấp đông nhanh IQF giữ nguyên độ ngọt giòn, khay cố định 300g.'
WHERE id = 5;

UPDATE dbo.products SET
    image_url = '/imgDb/products/prd-egg-chicken.jpg',
    supplier_name = N'Trang trại Chăn nuôi Gà sạch Ba Vì',
    description = N'Trứng gà ta thả vườn lòng đỏ đậm, xử lý diệt khuẩn bề mặt, đóng hộp bảo vệ cố định 10 quả.'
WHERE id = 6;

UPDATE dbo.products SET
    image_url = '/imgDb/products/prd-white-tofu.jpg',
    supplier_name = N'HTX Sản xuất Đậu phụ Làng Mơ Hoàng Mai',
    description = N'Đậu phụ non sản xuất mỗi sáng từ đậu tương chọn lọc, mềm mịn thơm bùi, đóng hộp cố định 4 bìa (400g).'
WHERE id = 7;

UPDATE dbo.products SET
    image_url = '/imgDb/products/prd-choy-sum.jpg',
    supplier_name = N'HTX Rau Hữu cơ Đông Anh Hà Nội',
    description = N'Rau cải ngọt hữu cơ thu hoạch trong ngày, lá xanh tươi không dư lượng hóa chất, túi cố định 500g.'
WHERE id = 8;

UPDATE dbo.products SET
    image_url = '/imgDb/products/prd-morning-glory.jpg',
    supplier_name = N'HTX Rau sạch VietGAP Đông Anh',
    description = N'Rau muống sạch VietGAP cọng nhỏ giòn ngọt, sơ chế nhặt sạch gốc, đóng túi cố định 500g.'
WHERE id = 9;

UPDATE dbo.products SET
    image_url = '/imgDb/products/prd-broccoli.jpg',
    supplier_name = N'Công ty TNHH Nông sản Cầu Đất Đà Lạt',
    description = N'Bông cải xanh Đà Lạt bông đặc chắc, giàu chất xơ và vitamin, bảo quản mát đóng túi cố định 500g.'
WHERE id = 10;

UPDATE dbo.products SET
    image_url = '/imgDb/products/prd-tomato.jpg',
    supplier_name = N'Công ty TNHH Nông sản Cầu Đất Đà Lạt',
    description = N'Cà chua chín cây tự nhiên Đà Lạt thịt dày ít hạt, vị chua ngọt hài hòa, đóng túi cố định 500g.'
WHERE id = 11;

UPDATE dbo.products SET
    image_url = '/imgDb/products/prd-potato.jpg',
    supplier_name = N'Công ty TNHH Nông sản Lâm Đồng',
    description = N'Khoai tây vàng Đà Lạt củ tròn đều bở thơm, bảo quản nơi khô ráo, đóng túi lưới cố định 1kg.'
WHERE id = 12;

UPDATE dbo.products SET
    image_url = '/imgDb/products/prd-carrot.jpg',
    supplier_name = N'Công ty TNHH Nông sản Lâm Đồng',
    description = N'Cà rốt tươi Đà Lạt ruột đỏ ngọt giòn, rửa sạch bùn đất đóng túi cố định 500g.'
WHERE id = 13;

UPDATE dbo.products SET
    image_url = '/imgDb/products/prd-enoki-mushroom.jpg',
    supplier_name = N'Công ty TNHH Nấm sạch Kinoko Thanh Hà',
    description = N'Nấm kim châm trắng nuôi trồng phòng lạnh khép kín chuẩn công nghệ Nhật Bản, gói hút chân không cố định 300g.'
WHERE id = 14;

UPDATE dbo.products SET
    image_url = '/imgDb/products/prd-st25-rice.jpg',
    supplier_name = N'DNTN Hồ Quang Trí - Gạo ST25 Sóc Trăng',
    description = N'Gạo thơm ST25 Ông Cua chính hãng hạt dài trắng trong, cơm dẻo thơm mùi lá dứa cốm non, túi cố định 2kg.'
WHERE id = 15;

UPDATE dbo.users SET avatar_url = '/imgDb/users/user-01-admin.jpg'         WHERE id = 1;
UPDATE dbo.users SET avatar_url = '/imgDb/users/user-02-warehouse.jpg'     WHERE id = 2;
UPDATE dbo.users SET avatar_url = '/imgDb/users/user-03-dispatcher.jpg'    WHERE id = 3;
UPDATE dbo.users SET avatar_url = '/imgDb/users/user-04-driver1.jpg'       WHERE id = 4;
UPDATE dbo.users SET avatar_url = '/imgDb/users/user-05-driver2.jpg'       WHERE id = 5;
UPDATE dbo.users SET avatar_url = '/imgDb/users/user-06-agent-nhon.jpg'    WHERE id = 6;
UPDATE dbo.users SET avatar_url = '/imgDb/users/user-07-agent-caugiay.jpg' WHERE id = 7;
UPDATE dbo.users SET avatar_url = '/imgDb/users/user-08-agent-mydinh.jpg'  WHERE id = 8;
UPDATE dbo.users SET avatar_url = '/imgDb/users/user-09-customer1.jpg'     WHERE id = 9;
UPDATE dbo.users SET avatar_url = '/imgDb/users/user-10-customer2.jpg'     WHERE id = 10;

UPDATE dbo.pickup_points SET image_url = '/imgDb/pickup-points/pp-01-nhon.jpg'    WHERE id = 1;
UPDATE dbo.pickup_points SET image_url = '/imgDb/pickup-points/pp-02-caugiay.jpg' WHERE id = 2;
UPDATE dbo.pickup_points SET image_url = '/imgDb/pickup-points/pp-03-mydinh.jpg'  WHERE id = 3;
GO

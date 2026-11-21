/* =========================================================================================
   CẬP NHẬT DỮ LIỆU HÌNH ẢNH (imgDb), MÔ TẢ & NGUỒN CUNG CẤP CHO O2OFoodPickupDB (SSMS 22)
   Thư mục lưu ảnh: backend/src/main/resources/imgDb/
   ========================================================================================= */
USE O2OFoodPickupDB;
GO

SET NOCOUNT ON;
GO

-- 1. Bổ sung cột supplier_name cho products, avatar_url cho users, image_url cho pickup_points (idempotent)
IF COL_LENGTH('dbo.products', 'supplier_name') IS NULL
    ALTER TABLE dbo.products ADD supplier_name NVARCHAR(200) NULL;
GO

IF COL_LENGTH('dbo.products', 'description') IS NULL
    ALTER TABLE dbo.products ADD description NVARCHAR(1000) NULL;
GO

IF COL_LENGTH('dbo.products', 'image_url') IS NULL
    ALTER TABLE dbo.products ADD image_url VARCHAR(500) NULL;
GO

IF COL_LENGTH('dbo.users', 'avatar_url') IS NULL
    ALTER TABLE dbo.users ADD avatar_url VARCHAR(500) NULL;
GO

IF COL_LENGTH('dbo.pickup_points', 'image_url') IS NULL
    ALTER TABLE dbo.pickup_points ADD image_url VARCHAR(500) NULL;
GO

-- 2. Cập nhật hình ảnh, mô tả chi tiết & nguồn cung cấp cho 10 sản phẩm trong O2OFoodPickupDB
UPDATE dbo.products SET
    image_url     = '/imgDb/products/prd-chicken-breast.jpg',
    supplier_name = N'Công ty Cổ phần Chăn nuôi C.P. Việt Nam',
    description   = N'Ức gà phi lê bỏ da, lọc sạch gân, sơ chế và đóng khay hút chân không cố định 500g tại nhà máy đạt chuẩn VietGAP. Bảo quản mát 0–4°C, thích hợp chế biến các món Eat Clean, áp chảo và luộc.'
WHERE id = 1;

UPDATE dbo.products SET
    image_url     = '/imgDb/products/prd-pork-belly.jpg',
    supplier_name = N'Công ty TNHH Masan MEATLife (MEATDeli Hà Nam)',
    description   = N'Thịt ba chỉ heo mát theo công nghệ chế biến chuẩn châu Âu, tỷ lệ nạc mỡ cân đối, đóng khay cố định 500g có tem truy xuất lô FEFO. Thích hợp kho tàu, rang cháy cạnh và nướng.'
WHERE id = 2;

UPDATE dbo.products SET
    image_url     = '/imgDb/products/prd-beef-shank.jpg',
    supplier_name = N'Công ty TNHH Thực phẩm Nhập khẩu Úc - Việt (AusViet Foods)',
    description   = N'Thịt bắp bò Úc tươi mềm, thớ thịt săn chắc có gân giòn nhẹ, cắt lát đều và đóng khay cố định 300g. Bảo quản mát 0–4°C, thích hợp nhúng lẩu, xào nhanh hoặc hầm rau củ.'
WHERE id = 3;

UPDATE dbo.products SET
    image_url     = '/imgDb/products/prd-egg-chicken.jpg',
    supplier_name = N'Công ty Cổ phần Tập đoàn Dabaco Việt Nam',
    description   = N'Trứng gà ta sạch chăn thả tự nhiên, vỏ dày lòng đỏ đậm giàu dinh dưỡng, đã xử lý tia UV diệt khuẩn bề mặt và đóng hộp nhựa chống sốc cố định 10 quả.'
WHERE id = 4;

UPDATE dbo.products SET
    image_url     = '/imgDb/products/prd-white-tofu.jpg',
    supplier_name = N'HTX Sản xuất Đậu phụ Truyền thống Làng Mơ Hà Nội',
    description   = N'Đậu phụ trắng non sản xuất trong ngày từ 100% đậu tương không biến đổi gen, mềm mịn béo thơm tự nhiên, đóng hộp ngâm nước tinh khiết cố định 400g.'
WHERE id = 5;

UPDATE dbo.products SET
    image_url     = '/imgDb/products/prd-broccoli.jpg',
    supplier_name = N'Công ty TNHH Nông sản Sạch Đà Lạt GAP',
    description   = N'Súp lơ xanh canh tác nhà màng tại Đà Lạt đạt chuẩn VietGAP, bông xanh đặc chắc, ngọt giòn, sơ chế cắt cuống sạch và đóng túi cố định 500g.'
WHERE id = 6;

UPDATE dbo.products SET
    image_url     = '/imgDb/products/prd-cucumber.jpg',
    supplier_name = N'HTX Nông nghiệp Công nghệ cao Mộc Châu - Sơn La',
    description   = N'Dưa leo baby vỏ mỏng ít hạt, vị thanh mát giòn ngọt, thu hoạch sáng sớm tại nông trại Mộc Châu và đóng túi lưới cố định 500g.'
WHERE id = 7;

UPDATE dbo.products SET
    image_url     = '/imgDb/products/prd-morning-glory.jpg',
    supplier_name = N'HTX Rau Hữu cơ Trâu Quỳ - Gia Lâm',
    description   = N'Rau muống cạn hữu cơ trồng theo quy trình không thuốc bảo vệ thực vật tại Trâu Quỳ, cọng xanh nhỏ giòn ngọt, đóng túi thoáng khí cố định 500g.'
WHERE id = 8;

UPDATE dbo.products SET
    image_url     = '/imgDb/products/prd-tomato.jpg',
    supplier_name = N'Công ty TNHH Nông sản Sạch Đà Lạt GAP',
    description   = N'Cà chua chín cây tự nhiên tại Đà Lạt, vỏ căng mọng nhiều bột, vị chua ngọt dịu nhẹ, đóng khay cố định 500g dùng làm sốt cà chua, canh hoặc ăn sống.'
WHERE id = 9;

UPDATE dbo.products SET
    image_url     = '/imgDb/products/prd-rock-apple.jpg',
    supplier_name = N'HTX Nông sản An toàn Đồng Văn - Hà Giang',
    description   = N'Táo đá đặc sản vùng cao Hà Giang quả chắc tay, thịt giòn ngọt thanh tự nhiên, bảo quản lạnh tiêu chuẩn và đóng túi lưới cố định 1kg.'
WHERE id = 10;
GO

-- 3. Cập nhật ảnh đại diện cá nhân cho 12 tài khoản (Admin, Nhân viên Kho, Điều phối, Tài xế, Đại lý, Khách hàng)
UPDATE dbo.users SET avatar_url = '/imgDb/users/user-01-admin.jpg'        WHERE id = 1;
UPDATE dbo.users SET avatar_url = '/imgDb/users/user-02-warehouse.jpg'    WHERE id = 2;
UPDATE dbo.users SET avatar_url = '/imgDb/users/user-03-dispatcher.jpg'   WHERE id = 3;
UPDATE dbo.users SET avatar_url = '/imgDb/users/user-04-driver1.jpg'      WHERE id = 4;
UPDATE dbo.users SET avatar_url = '/imgDb/users/user-05-driver2.jpg'      WHERE id = 5;
UPDATE dbo.users SET avatar_url = '/imgDb/users/user-06-agent-nhon.jpg'   WHERE id = 6;
UPDATE dbo.users SET avatar_url = '/imgDb/users/user-07-agent-caugiay.jpg'WHERE id = 7;
UPDATE dbo.users SET avatar_url = '/imgDb/users/user-08-agent-mydinh.jpg' WHERE id = 8;
UPDATE dbo.users SET avatar_url = '/imgDb/users/user-09-customer1.jpg'    WHERE id = 9;
UPDATE dbo.users SET avatar_url = '/imgDb/users/user-10-customer2.jpg'    WHERE id = 10;
UPDATE dbo.users SET avatar_url = '/imgDb/users/user-11-customer3.jpg'    WHERE id = 11;
UPDATE dbo.users SET avatar_url = '/imgDb/users/user-12-customer4.jpg'    WHERE id = 12;
GO

-- 4. Cập nhật hình ảnh cửa hàng / điểm nhận hàng cho 3 Đại lý
UPDATE dbo.pickup_points SET image_url = '/imgDb/pickup-points/pp-01-nhon.jpg'    WHERE id = 1;
UPDATE dbo.pickup_points SET image_url = '/imgDb/pickup-points/pp-02-caugiay.jpg' WHERE id = 2;
UPDATE dbo.pickup_points SET image_url = '/imgDb/pickup-points/pp-03-mydinh.jpg'  WHERE id = 3;
GO

PRINT N'Đã cập nhật thành công image_url, description, supplier_name, avatar_url cho O2OFoodPickupDB.';
GO

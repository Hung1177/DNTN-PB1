# Tài Liệu Đặc Tả Kỹ Thuật & Nghiệp Vụ Hệ Thống Đặt Mua Thực Phẩm Qua Điểm Nhận Hàng (O2O Food Pickup System) — Bản Khởi Tạo Dự Án Cho Antigravity

**Tên đề tài:** Phân tích, thiết kế và xây dựng hệ thống đặt mua thực phẩm trên Web và Android, phân phối qua mạng lưới đại lý nhận hàng, có hỗ trợ mua sắm bằng trí tuệ nhân tạo.
**Kiến trúc mục tiêu:** Modular Monolith Backend (Java 21 LTS + Spring Boot 3.x) + SQL Server 2022 + Web (Vue 3 + TypeScript) + Mobile (Android Kotlin) + Docker Compose.

## 1. Chỉ Thị Cốt Lõi Dành Cho Antigravity (System Rules & Architecture Guardrails)

Khi sinh mã nguồn, thiết kế cơ sở dữ liệu và xây dựng giao diện cho dự án này, Antigravity phải tuân thủ tuyệt đối các quy tắc kiến trúc và ràng buộc nghiệp vụ sau:

1. **Mô hình bán hàng & định giá:**
   - Hệ thống có **1 đơn vị bán hàng duy nhất**, **1 kho trung tâm** và **mạng lưới nhiều đại lý nhận hàng (Pickup Points)**.
   - Đại lý chỉ là điểm tập kết và bàn giao hàng hưởng hoa hồng, **không tự định giá**, không sở hữu tồn kho thương mại.
   - Nhà cung cấp (Suppliers) chỉ lưu phục vụ quản lý lô nhập nội bộ, không làm sàn thương mại điện tử đa nhà bán (Multi-vendor Marketplace).
   - Mọi sản phẩm bắt buộc bán theo **quy cách đóng gói cố định** (ví dụ: túi rau 500g, khay thịt 500g, hộp trứng 10 quả) để xác định chính xác 100% số tiền khi thanh toán trực tuyến; không bán theo cân nặng biến thiên phải cân lại lúc giao.
2. **Thanh toán trước 100% (No COD):**
   - Tuyệt đối không hỗ trợ thanh toán khi nhận hàng (COD).
   - Đơn hàng chỉ được đưa vào đợt chốt khi và chỉ khi nhận được **IPN hợp lệ từ máy chủ VNPAY** xác nhận đã thu đủ 100% giá trị đơn hàng.
3. **Chuẩn hóa thời gian & múi giờ:**
   - Toàn bộ logic so sánh hạn đặt, hạn thanh toán, giờ chốt đơn, giờ mở nhận hàng và giờ tự hoàn tất bắt buộc lấy từ **đồng hồ máy chủ (Server Clock)** theo múi giờ `Asia/Ho_Chi_Minh` (`UTC+07:00`), tuyệt đối không tin tưởng đồng hồ từ trình duyệt hay thiết bị Android của khách.
   - Cơ sở dữ liệu SQL Server sử dụng kiểu `DATETIMEOFFSET(7)` ánh xạ sang `java.time.OffsetDateTime` trong Java.
4. **Chuẩn hóa tiền tệ & chuỗi tiếng Việt:**
   - Mọi cột số tiền, giá bán, phí, hoàn tiền, hoa hồng dùng `DECIMAL(18, 2)` trong SQL Server và `java.math.BigDecimal` trong Java (làm tròn `RoundingMode.HALF_UP`).
   - Mọi cột văn bản tiếng Việt dùng `NVARCHAR`.
5. **Tách biệt trạng thái thương mại và trạng thái vật lý:**
   - Trạng thái đơn hàng thương mại (`OrderStatus` và `CompletionType`) tách rời hoàn toàn khỏi trạng thái giao nhận vật lý (`FulfillmentStatus` và `PhysicalHandedOverAt`).
   - Khi hệ thống tự hoàn tất đơn lúc `00:00 ngày D+2` (`CompletionType = SYSTEM_TIMEOUT`), nếu khách chưa đến lấy hàng thì `PhysicalHandedOverAt` vẫn phải để `NULL` và đơn hàng phải nằm trong **Danh sách hàng tồn chưa lấy tại đại lý (Unclaimed Inventory)**, tuyệt đối không làm biến mất hàng khỏi sổ kho đại lý.
6. **Phân quyền chặt tại tầng Backend (Zero-Trust UI):**
   - Kiểm soát quyền truy cập ở cả tầng Controller (`@PreAuthorize`) và tầng truy vấn dữ liệu (Row-level ownership filter):
     - `ROLE_CUSTOMER`: Chỉ truy xuất đơn hàng, giỏ hàng, phiên chat và thông báo của chính `userId` đang đăng nhập.
     - `ROLE_AGENT`: Chỉ truy xuất các đơn hàng, kiện hàng, biên bản bàn giao và sổ cái hoa hồng thuộc `pickupPointId` được phân công trong bảng `PointAssignments`.
     - `ROLE_DRIVER`: Chỉ truy xuất và cập nhật các chuyến giao/thu hồi (`Trip`) có `driverId` là chính mình.
     - `ROLE_WAREHOUSE`: Thực hiện nghiệp vụ nhập lô, lấy hàng FEFO, đóng gói, xuất kho, nhận hàng hoàn.
     - `ROLE_DISPATCHER`: Phân công chuyến xe, giám sát tiến độ giao nhận, điều phối chuyến thu hồi.
     - `ROLE_ADMIN`: Quản trị danh mục, cấu hình đợt, chính sách hoa hồng, phê duyệt hoàn tiền, đối soát.
7. **Ranh giới an toàn cho AI (AI Safety Boundary):**
   - LLM/Chatbot tuyệt đối không tự sinh câu lệnh SQL trực tiếp, không tự suy đoán trạng thái đơn hàng, không tự ý xác nhận thanh toán, hủy đơn, hoàn tiền hay tính hoa hồng.
   - Mọi truy vấn đơn hàng qua hội thoại phải đi qua cơ chế **Tool Calling / Function Calling** gọi vào Service của Backend đã xác thực JWT của khách hàng.
   - Luồng gợi ý món ăn chỉ dùng AI để phân tích nhu cầu và giải thích thực đơn; việc quy đổi định lượng ra số gói sản phẩm, kiểm tra tồn kho và tính tổng tiền bắt buộc thực hiện bằng thuật toán xác định (Deterministic Backend Logic).

## 2. Chuẩn Hóa Xung Đột Nội Bộ Trong Tài Liệu Gốc & Tham Số Vận Hành

Trong bản mô tả đề tài gốc, các mục 2, 4 và các mục 3, 6, 7, 8, 18 đang tồn tại sự lệch pha giữa hai phương án giờ chốt (`23:59` so với `12:00 trưa`) và hai mức thời gian giữ tồn chờ thanh toán (`30 phút` so với `15 phút`). Để hệ thống nhất quán tuyệt đối khi lập trình, tài liệu này **chuẩn hóa theo phương án cập nhật ở Mục 2 và Mục 4**, đồng thời đưa toàn bộ mốc giờ vào bảng cấu hình động `OrderCycles`:

| Tham số cấu hình | Ký hiệu cấu hình (`OrderCycles`) | Giá trị mặc định chuẩn hóa | Giải thích & Xử lý xung đột với bản nháp cũ |
| :--- | :--- | :--- | :--- |
| Thời gian giữ tồn chờ thanh toán | `payment_timeout_minutes` | `30 phút` | Thống nhất theo Mục 4 & 6 (thay cho ví dụ 15 phút ở Mục 7). Đơn tạo lúc `23:45` chỉ được giữ tồn tối đa đến `23:59:59` (chặn trần bởi `cutoff_at`). |
| Hạn chót đặt, thanh toán & tự hủy | `cutoff_at` | `23:59:59 Ngày D` | Thống nhất theo Mục 2 & 4 (thay cho mốc 12:00 trưa ở Mục 6, 8). Đơn thanh toán hợp lệ trong khoảng `00:00:00 – 23:59:59 Ngày D` thuộc đợt giao `Ngày D+1`. |
| Mốc tự động chốt & xác nhận đơn | `auto_confirm_at` | `00:00:00 Ngày D+1` | Job nền chạy lúc `00:00:00 Ngày D+1` khóa quyền tự hủy của khách, khóa giá và điểm nhận, đẩy danh sách đơn sang kho. |
| Mục tiêu kho đóng gói xong | `warehouse_pack_target_at` | `08:00:00 Ngày D+1` | Là mốc SLA vận hành để cảnh báo điều phối nếu kho đóng gói trễ; hệ thống **không tự động giả lập** trạng thái đã đóng gói nếu nhân viên kho chưa xác nhận. |
| Mốc tài xế nhận hàng tại kho | `dispatch_start_at` | `08:00:00 Ngày D+1` | Tài xế kiểm đếm kiện hàng theo chuyến, ký nhận điện tử với nhân viên kho để chuyển sang `IN_TRANSIT`. |
| Hạn bàn giao & kiểm nhận tại đại lý | `agent_handover_deadline_at` | `16:00:00 Ngày D+1` | Tài xế và đại lý đối chiếu số kiện, ghi nhận đủ/thiếu/hỏng. Chỉ những đơn đủ hàng, không lỗi mới chuyển sang `READY_FOR_PICKUP`. |
| Mốc mở trả hàng cho khách | `pickup_start_at` | `16:00:00 Ngày D+1` | Hệ thống bắn thông báo Web (SSE/WebSocket) và Android (FCM) khi đơn thực sự đã được đại lý kiểm nhận thành công. |
| Mốc tự động hoàn tất & tính hoa hồng | `auto_complete_at` | `00:00:00 Ngày D+2` | Các đơn ở trạng thái `READY_FOR_PICKUP` hoặc `HANDED_OVER` không có sự cố/tranh chấp đang mở sẽ tự chuyển sang `COMPLETED` và ghi nhận hoa hồng đúng 1 lần. |

## 3. Phân Tích Sâu Các Use Cases Theo Từng Phân Hệ (Comprehensive Use-Case Catalog)

### 3.1. Phân hệ Khách hàng (`ROLE_CUSTOMER` — Web Vue 3 & Android Kotlin)

| Mã UC | Tên Use Case | Tác nhân | Luồng xử lý chính (Main Flow) | Luồng ngoại lệ & Ràng buộc (Exception & Edge Cases) |
| :--- | :--- | :--- | :--- | :--- |
| **UC-C01** | Chọn Điểm nhận hàng & Xem Đợt giao hàng | Khách hàng | 1. Khách xem bản đồ Leaflet (Web) hoặc danh sách điểm nhận (Android).<br>2. Chọn đại lý `PickupPoint`.<br>3. Hệ thống truy vấn `PointCycles` đang mở (`OPEN`), trả về hạn chốt (`23:59 hôm nay`) và khung giờ nhận dự kiến (`Từ 16:00 ngày mai`). | - Nếu đại lý tạm ngưng hoạt động hoặc đợt ngày mai đã đóng/đầy công suất, hệ thống hiển thị rõ đợt kế tiếp (`D+2`) ngay trên thanh tiêu đề trước khi khách thêm món vào giỏ. |
| **UC-C02** | Quản lý Giỏ hàng & Đặt hàng giữ tồn (Checkout) | Khách hàng | 1. Khách thêm các `ProductVariant` (gói cố định) vào giỏ gắn với `PickupPoint` và `OrderCycle`.<br>2. Nhấn Thanh toán: Backend mở Transaction, kiểm tra đợt còn trước `cutoff_at`.<br>3. Thực hiện câu lệnh cập nhật có điều kiện trừ tồn khả dụng (`available_qty >= req_qty`) và tăng `reserved_qty`.<br>4. Tạo bản ghi `Orders` (`PENDING_PAYMENT`), `Reservations` (`ACTIVE`, `expires_at = MIN(now + 30m, cutoff_at)`) và `PaymentAttempts` (mã `txn_ref` duy nhất).<br>5. Sinh URL thanh toán VNPAY Sandbox trả về cho Client chuyển hướng. | - **Bán vượt tồn (Overselling):** Nếu 2 khách cùng mua gói hàng cuối cùng, chỉ 1 giao dịch cập nhật được dòng `InventoryBalances`, giao dịch kia nhận `0 rows affected`, rollback ngay lập tức và trả lỗi `INSUFFICIENT_STOCK` kèm tên món hết hàng.<br>- Nếu thời gian còn lại đến giờ chốt `< 2 phút`, từ chối tạo giao dịch mới để tránh thanh toán treo qua mốc chốt. |
| **UC-C03** | Xác thực thanh toán qua VNPAY IPN | VNPAY Server, Khách hàng | 1. Khách hoàn tất thanh toán trên VNPAY.<br>2. Return URL chỉ dùng để hiển thị màn hình "Đang chờ xác nhận thanh toán".<br>3. VNPAY gọi Webhook `POST /api/v1/payments/vnpay-ipn`.<br>4. Backend kiểm tra chữ ký `vnp_SecureHash` (HMAC-SHA512), kiểm tra `vnp_TxnRef`, đối chiếu `vnp_Amount == order.totalAmount * 100`.<br>5. Nếu hợp lệ và đơn đang `PENDING_PAYMENT` trước giờ chốt: chuyển đơn sang `PAID_WAITING_CUTOFF`, chuyển `Reservation` sang `COMMITTED`, lưu `PaymentEvents`. | - **IPN trùng lặp (Duplicate IPN):** Kiểm tra trạng thái `PaymentAttempt`; nếu đã `SUCCESS` thì trả mã `00` (Confirm Success) cho VNPAY mà không cộng/trừ tồn lần hai.<br>- **IPN thành công đến muộn (Late Payment):** Nếu IPN báo thành công sau khi `Reservation` đã hết hạn 30 phút hoặc sau `cutoff_at` (`00:00`), hệ thống chuyển giao dịch sang `LATE_PAID_RECONCILIATION`, phát thông báo cho khách chọn: (a) Chuyển sang đợt giao kế tiếp nếu còn tồn, hoặc (b) Nhận hoàn tiền 100%. |
| **UC-C04** | Tự hủy đơn hàng trước giờ chốt | Khách hàng | 1. Khách nhấn "Hủy đơn" khi `ServerTime < order.cutoffAt` và trạng thái đơn là `PENDING_PAYMENT` hoặc `PAID_WAITING_CUTOFF`.<br>2. Backend dùng khóa lạc quan (`@Version`) khóa bản ghi `Orders`.<br>3. Nếu đơn là `PENDING_PAYMENT`: chuyển sang `CANCELLED`, giải phóng `Reservation` (`RELEASED`), hoàn lại `available_qty`.<br>4. Nếu đơn là `PAID_WAITING_CUTOFF`: chuyển sang `CANCELLED`, giải phóng `Reservation` đúng 1 lần, tự động tạo bản ghi `Refunds` (`APPROVED_PENDING_EXECUTION`) cho 100% số tiền đã thu. | - **Đua dữ liệu lúc 23:59:59 (Race Condition):** Nếu khách bấm Hủy đúng lúc Job `00:00:00` đang chạy tự xác nhận, nhờ `@Version` và điều kiện `WHERE status = 'PAID_WAITING_CUTOFF'`, chỉ một trong hai luồng thành công. Nếu Job thắng, khách nhận thông báo "Đơn hàng đã qua giờ chốt 00:00 nên không thể tự hủy". |
| **UC-C05** | Xuất mã QR nhận hàng & Xác nhận đã nhận | Khách hàng, Đại lý | 1. Khi đơn ở trạng thái phân phối `READY_FOR_PICKUP`, khách mở ứng dụng hiển thị mã QR nhận hàng (`PickupToken` dạng OTP/Token ngẫu nhiên có chữ ký hoặc dùng 1 lần).<br>2. Đại lý quét/nhập mã để ghi nhận `PhysicalHandedOverAt = now()`, chuyển trạng thái phân phối sang `HANDED_OVER`.<br>3. Khách nhấn nút "Đã nhận hàng": Backend cập nhật `CustomerConfirmedAt = now()`, `CompletedAt = now()`, `CompletionType = CUSTOMER_CONFIRMED`, `OrderStatus = COMPLETED`, và gọi module Hoa hồng ghi sổ cái `CommissionLedger`. | - **Khách bấm "Đã nhận hàng" khi Đại lý chưa bấm bàn giao:** Hệ thống vẫn ghi nhận `CustomerConfirmedAt`, chuyển đơn sang `COMPLETED` (`CUSTOMER_CONFIRMED`), nhưng giữ `PhysicalHandedOverAt = NULL` và sinh cảnh báo đối chiếu (`AuditAlert`) cho Đại lý và Điều phối kiểm tra, không tự ý giả mạo dấu thời gian bàn giao của đại lý. |
| **UC-C06** | Báo sự cố & Yêu cầu hoàn tiền | Khách hàng | 1. Khi nhận hàng tại đại lý (hoặc sau khi mang về trong thời hạn khiếu nại), khách phát hiện hàng dập hỏng/giao sai.<br>2. Khách tải ảnh minh chứng, chọn lý do và gửi báo cáo sự cố (`Issues` + `ReturnRequests`).<br>3. Hệ thống lập tức gắn cờ `has_open_issue = true` trên đơn hàng để **chặn tác vụ tự hoàn tất lúc 00:00** và chặn tính hoa hồng mới. | - Nếu đơn đã tự hoàn tất lúc `00:00` trước khi khách khiếu nại (nhưng vẫn trong thời hạn khiếu nại cho phép), hệ thống vẫn cho phép tạo `Issues`, giữ nguyên lịch sử hoàn tất cũ và xử lý hoàn tiền kèm bút toán âm thu hồi hoa hồng nếu khiếu nại được duyệt. |
| **UC-C07** | Hỏi đáp chính sách & Tra cứu đơn qua AI Chatbot | Khách hàng | 1. Khách đặt câu hỏi trong khung chat.<br>2. Nếu hỏi chính sách (giờ chốt, cách hủy, hoàn tiền): Backend truy xuất tài liệu chính sách có đánh phiên bản trong bảng `KnowledgeDocuments` (RAG) đưa vào ngữ cảnh cho LLM trả lời.<br>3. Nếu hỏi trạng thái đơn hàng: LLM kích hoạt Tool `getCustomerOrders(authenticatedUserId)` hoặc `getOrderDetail(authenticatedUserId, orderCode)`. Backend kiểm tra quyền sở hữu, trả JSON trạng thái thực tế để LLM diễn đạt lại bằng ngôn ngữ tự nhiên. | - Nếu khách hỏi đơn của người khác hoặc chưa đăng nhập: Tool từ chối ngay tại tầng Backend (`403 Forbidden`).<br>- Nếu câu hỏi nằm ngoài phạm vi tài liệu chính sách, Chatbot trả lời rõ chưa có thông tin chính thức và hướng dẫn liên hệ hỗ trợ, không tự bịa chính sách. |
| **UC-C08** | Gợi ý thực đơn & Giỏ nguyên liệu theo ngân sách | Khách hàng | 1. Khách nhập số người ăn, ngân sách (ví dụ: `2 người, 100.000đ`), nguyên liệu sẵn có tại nhà, dị ứng/món kiêng.<br>2. Backend lọc danh sách công thức (`Recipes`) theo khẩu phần, loại trừ nguyên liệu dị ứng.<br>3. Với mỗi công thức ứng viên, Backend ánh xạ nguyên liệu (`RecipeIngredients`) sang `ProductVariants` đang mở bán và còn tồn trong đợt hiện tại, trừ đi nguyên liệu khách đã có, làm tròn lên theo quy cách gói (`packages = CEIL(needed_qty / pack_size)`), tính tổng tiền thực tế từ DB.<br>4. LLM giải thích lý do chọn món, hiển thị rõ bảng kê: Số gói cần mua, Lượng dư sau chế biến, Tổng tiền chính xác.<br>5. Khách duyệt và bấm "Thêm vào giỏ": Backend kiểm tra lại giá và tồn kho một lần nữa khi thêm. | - Nếu sản phẩm tương ứng với nguyên liệu đã hết tồn kho trong đợt hoặc tổng tiền sau khi làm tròn theo gói vượt ngân sách: Hệ thống thông báo rõ khoản chênh lệch do quy cách đóng gói cố định (ví dụ: cần 200g thịt nhưng gói nhỏ nhất là 500g) để khách quyết định. |

### 3.2. Phân hệ Kho trung tâm (`ROLE_WAREHOUSE` — Web)

| Mã UC | Tên Use Case | Tác nhân | Luồng xử lý chính (Main Flow) | Luồng ngoại lệ & Ràng buộc (Exception & Edge Cases) |
| :--- | :--- | :--- | :--- | :--- |
| **UC-W01** | Nhập kho theo Lô & Quản lý hạn sử dụng (Lots) | Nhân viên kho | 1. Tạo phiếu nhập lô mới (`Lots`) cho từng `ProductVariant` kèm `lot_code`, `manufactured_at`, `expiry_at`, `received_qty`.<br>2. Cập nhật `InventoryBalances` và ghi nhật ký `StockMovements` (`INBOUND`). | - Không cho phép nhập lô đã hết hạn hoặc có `expiry_at` nhỏ hơn ngày giao của đợt tiếp theo. |
| **UC-W02** | Tổng hợp nhu cầu (Wave Picking) & Phân bổ lô FEFO | Nhân viên kho | 1. Sau `00:00 Ngày D+1`, kho mở màn hình chuẩn bị hàng của đợt.<br>2. Hệ thống gom tổng số lượng từng mặt hàng theo từng Đại lý nhận hàng để nhân viên đi lấy hàng một lượt (Wave Picking), đồng thời chỉ định chính xác lấy từ `Lot` nào theo thuật toán **FEFO (First-Expired, First-Out)**.<br>3. Khi chia hàng về từng đơn (`Order`), nhân viên đóng gói và in tem nhãn QR riêng cho từng kiện (`Packages`) thuộc từng đơn hàng (`Orders`). Một đơn lớn có thể gồm nhiều kiện (`1 Order : N Packages`). | - **Lô hàng hỏng đột xuất khi lấy hàng:** Nếu phát hiện lô có hạn gần nhất bị hỏng thực tế trong kho, nhân viên kho đánh dấu khóa lô (`QUARANTINED`), hệ thống tự động tái phân bổ sang lô có hạn sử dụng gần kế tiếp. Nếu tổng tồn thực tế không đủ, tạo `Issues` thiếu hàng tại kho và báo Điều phối. |
| **UC-W03** | Xuất kho & Bàn giao cho Tài xế (Từ 08:00 D+1) | Nhân viên kho, Tài xế | 1. Kho hoàn tất đóng gói các kiện chuyển sang trạng thái `PACKED_READY_FOR_DISPATCH`.<br>2. Khi tài xế đến nhận chuyến (`Trip`), tài xế quét QR từng kiện hàng hoặc đối chiếu danh sách kiện theo điểm giao.<br>3. Cả Nhân viên kho và Tài xế xác nhận trên hệ thống để sinh biên bản `HandoverRecords` (`WAREHOUSE_TO_DRIVER`), chuyển các kiện sang `IN_TRANSIT`. | - **Đóng gói trễ sau 08:00:** Nếu đến `08:00` mà đơn chưa được kho xác nhận đóng gói xong, hệ thống phát cảnh báo trễ (`SLA_BREACH_PACKING`), tuyệt đối không tự động chuyển trạng thái sang đã đóng gói hay đang giao. |
| **UC-W04** | Kiểm nhận hàng hoàn về kho & Cách ly hàng lỗi | Nhân viên kho, Tài xế | 1. Tài xế chở hàng thu hồi (`ReturnShipments`) từ đại lý về kho.<br>2. Nhân viên kho kiểm tra thực tế, xác nhận biên bản nhận hàng hoàn (`DRIVER_TO_WAREHOUSE_RETURN`).<br>3. Hệ thống ghi `StockMovements` đưa hàng vào **Kho cách ly hàng lỗi (`QUARANTINE_DAMAGED_STOCK`)**, chuyển `ReturnRequests` sang `WAREHOUSE_RECEIVED` để mở khóa bước duyệt hoàn tiền. | - Tuyệt đối **không tự động cộng hàng hoàn về lại tồn kho bán được (`available_qty`)** vì thực phẩm tươi sống hoàn về có rủi ro mất an toàn vệ sinh thực phẩm. |

### 3.3. Phân hệ Điều phối (`ROLE_DISPATCHER`), Tài xế (`ROLE_DRIVER`) & Đại lý (`ROLE_AGENT`)

| Mã UC | Tên Use Case | Tác nhân | Luồng xử lý chính (Main Flow) | Luồng ngoại lệ & Ràng buộc (Exception & Edge Cases) |
| :--- | :--- | :--- | :--- | :--- |
| **UC-D01** | Phân công Chuyến giao hàng & Tuyến điểm nhận | Điều phối | 1. Tạo chuyến giao (`Trips`) cho đợt `D+1`, gán `driverId` và danh sách điểm dừng (`TripStops`) theo thứ tự giao.<br>2. Hệ thống tự động gán toàn bộ `Shipments` / `Packages` của các đại lý thuộc điểm dừng vào chuyến. | - Chỉ cho phép gán tài xế đang hoạt động; đảm bảo 1 đại lý trong 1 đợt có đầy đủ kiện hàng được phân vào chuyến. |
| **UC-DR01** | Giao hàng đến Đại lý & Lập biên bản bàn giao (08:00 – 16:00) | Tài xế, Đại lý | 1. Tài xế cập nhật trạng thái đến điểm dừng (`ARRIVED_AT_POINT`).<br>2. Tài xế và Đại lý quét mã QR từng kiện (`Package QR`) hoặc kiểm đếm theo đơn.<br>3. Hai bên xác nhận biên bản `HandoverRecords` (`DRIVER_TO_AGENT`) ghi rõ: Số kiện nhận đủ (`ACCEPTED`), số kiện thiếu (`MISSING`), số kiện dập hỏng (`DAMAGED`).<br>4. Sau khi Transaction lưu thành công: Các đơn nhận đủ toàn bộ kiện chuyển sang `READY_FOR_PICKUP` và bắn thông báo cho khách hàng (từ 16:00). | - **Đơn nhiều kiện bị thiếu/hỏng 1 kiện:** Nếu đơn A gồm 2 kiện nhưng chỉ 1 kiện nguyên vẹn, kiện kia bị hỏng/thiếu, thì toàn bộ đơn A **không được chuyển sang `READY_FOR_PICKUP`**, mà phải tự động sinh `Issues` và thông báo ngay cho khách + điều phối.<br>- **Giao trễ sau 16:00:** Nếu đến 16:00 tài xế chưa bàn giao xong tại đại lý, hệ thống gửi thông báo lùi giờ nhận dự kiến cho khách, không báo "Hàng đã sẵn sàng" giả tạo theo giờ cứng. |
| **UC-A01** | Quản lý Hàng tồn khách chưa lấy & Hoa hồng đại lý | Đại lý | 1. Xem danh sách đơn đang chờ khách lấy (`READY_FOR_PICKUP`) và danh sách đơn đã quá `00:00 D+2` tự hoàn tất nhưng khách chưa nhận vật lý (`UNCLAIMED_AT_AGENT`).<br>2. Liên hệ khách hoặc đề xuất thu hồi/tiêu hủy theo hạn bảo quản thực phẩm.<br>3. Xem bảng sao kê `CommissionLedger` (hoa hồng phát sinh theo đơn, điều chỉnh âm do hoàn tiền, tổng số dư kỳ đối soát). | - Đại lý không được sửa/xóa dòng trong `CommissionLedger`. Mọi thay đổi số dư chỉ thực hiện qua bút toán mới. |

## 4. Phân Tích Sâu Nghiệp Vụ Cốt Lõi, State Machines & Giải Thuật Đồng Thời

### 4.1. Năm Máy Trạng Thái Độc Lập Nhưng Ràng Buộc (5 Coupled State Machines)

Hệ thống không dùng 1 cột trạng thái duy nhất mà tách thành 5 miền trạng thái để phản ánh đúng thực tế vận hành O2O:

| Miền trạng thái | Các trạng thái hợp lệ (Enum Values) | Quy tắc chuyển trạng thái & Ràng buộc chéo |
| :--- | :--- | :--- |
| **1. OrderStatus** (Thương mại) | `PENDING_PAYMENT` → `PAID_WAITING_CUTOFF` → `CONFIRMED` → `PROCESSING` → `COMPLETED` \| `CANCELLED` | - Chỉ sang `PAID_WAITING_CUTOFF` khi IPN VNPAY hợp lệ.<br>- Chỉ tự hủy (`CANCELLED`) khi `< cutoff_at` và đang ở `PENDING_PAYMENT` hoặc `PAID_WAITING_CUTOFF`.<br>- Sang `CONFIRMED` bởi Job `00:00 D+1`.<br>- Sang `COMPLETED` khi khách xác nhận (`CUSTOMER_CONFIRMED`) hoặc Job `00:00 D+2` chạy (`SYSTEM_TIMEOUT`). |
| **2. FulfillmentStatus** (Vật lý) | `WAITING_PACKING` → `PACKED` → `IN_TRANSIT` → `AGENT_CHECKING` → `READY_FOR_PICKUP` → `HANDED_OVER` \| `ISSUE_HOLD` | - `READY_FOR_PICKUP` đòi hỏi biên bản bàn giao `DRIVER_TO_AGENT` xác nhận đủ 100% số kiện của đơn.<br>- `HANDED_OVER` chỉ được set khi đại lý quét mã nhận hàng của khách (`PhysicalHandedOverAt IS NOT NULL`). |
| **3. Payment & Refund Status** | `UNPAID` → `PAID` \| `LATE_RECONCILIATION` → `REFUND_REQUESTED` → `REFUND_APPROVED` → `REFUNDING` → `REFUNDED` \| `REFUND_FAILED` | - Hoàn tiền bắt buộc do tự hủy trước giờ chốt hoặc do `ReturnRequest` đã được kho kiểm nhận (trừ trường hợp biên bản xác nhận hàng hỏng không thể thu hồi được Admin duyệt đặc cách). |
| **4. ReturnStatus** (Thu hồi hàng lỗi) | `REQUESTED` → `APPROVED_FOR_PICKUP` → `DRIVER_PICKED_UP` → `IN_RETURN_TRANSIT` → `WAREHOUSE_RECEIVED` → `CLOSED` | - Khi tồn tại `ReturnRequest` chưa `CLOSED` hoặc `Issue` đang mở, đơn hàng bị khóa cờ `has_open_issue = 1`, chặn mọi tác vụ tự hoàn tất. |
| **5. CommissionStatus** (Hoa hồng) | `RECORDED` → `ADJUSTED` → `RECONCILED` → `PAID_OUT` | - Mỗi `order_id` chỉ có tối đa 1 dòng `CREDIT_COMMISSION` trong `CommissionLedger` (ràng buộc bởi Filtered Unique Index). Khi hoàn tiền đơn đã tính hoa hồng, thêm 1 dòng `DEBIT_REVERSAL`. |

### 4.2. Giải Thuật Giữ Tồn Kho Chống Bán Vượt (Anti-Overselling & Reservation TTL)

Khi khách hàng nhấn thanh toán (`UC-C02`), Backend thực thi trong một Transaction `@Transactional(isolation = Isolation.READ_COMMITTED)`:
1. Sắp xếp danh sách `cartItems` theo `variant_id ASC` để **tránh Deadlock** khi nhiều giao dịch khóa nhiều mặt hàng cùng lúc.
2. Thực thi câu lệnh cập nhật nguyên tử (Atomic Conditional Update) cho từng mặt hàng trong đợt:
   ```sql
   UPDATE inventory_balances
   SET available_qty = available_qty - :reqQty,
       reserved_qty = reserved_qty + :reqQty,
       version = version + 1,
       updated_at = SYSDATETIMEOFFSET()
   WHERE variant_id = :variantId
     AND cycle_id = :cycleId
     AND available_qty >= :reqQty;
   ```
3. Nếu bất kỳ mặt hàng nào trả về `updatedRows == 0`, ném ngoại lệ `OutOfStockException` để rollback toàn bộ giao dịch.
4. Tạo bản ghi `reservations` với `status = 'ACTIVE'` và `expires_at = MIN(now.plusMinutes(30), cycle.getCutoffAt())`.
5. **Tác vụ thu hồi giữ tồn quá hạn (`ReservationCleanupJob`):** Chạy mỗi `60 giây` (có khóa `ShedLock`), tìm các bản ghi `reservations` có `status = 'ACTIVE' AND expires_at < SYSDATETIMEOFFSET()`, khóa bản ghi `orders` tương ứng: nếu đơn vẫn đang `PENDING_PAYMENT`, chuyển đơn sang `CANCELLED` (lý do `PAYMENT_TIMEOUT`), chuyển `reservation` sang `EXPIRED` và cộng trả lại `available_qty`.

### 4.3. Giải Thuật Phân Bổ Lô Xuất Kho Theo FEFO (First-Expired, First-Out)

Khi kho chuẩn bị hàng cho đợt giao nhận ngày `pickup_date`, hệ thống chỉ chọn các lô hàng thỏa mãn điều kiện an toàn hạn sử dụng và sắp xếp ưu tiên lô hết hạn sớm nhất:
1. Lọc các lô thuộc `variant_id` có:
   - `status = 'AVAILABLE'`
   - `remaining_qty > 0`
   - `expiry_date >= DATEADD(day, product.min_shelf_life_days_at_pickup, cycle.pickup_date)` (đảm bảo thực phẩm đến tay khách lúc 16:00 ngày nhận vẫn còn đủ số ngày sử dụng tối thiểu).
2. Sắp xếp theo `expiry_date ASC, received_at ASC, id ASC`.
3. Duyệt tuần tự từng lô để cấp phát đủ số lượng cần xuất của đơn hàng, ghi nhận vào bảng `order_lot_allocations` và trừ `remaining_qty` của lô. Nhờ lưu vết `order_lot_allocations`, khi có sự cố vệ sinh thực phẩm ở một lô, hệ thống truy vết được ngay những đơn hàng và đại lý nào đã nhận lô đó.

### 4.4. Giải Thuật Xử Lý Đua Dữ Liệu (Concurrency & Idempotency Rules)

1. **Khách bấm Hủy đơn lúc `23:59:59` đồng thời với Job Tự xác nhận lúc `00:00:00`:**
   - Cả API Hủy đơn và Job Tự xác nhận đều kiểm tra điều kiện thời gian máy chủ và cập nhật bảng `orders` kèm cột `@Version`:
     ```sql
     UPDATE orders
     SET order_status = :newStatus, version = version + 1, updated_at = SYSDATETIMEOFFSET()
     WHERE id = :orderId AND order_status = 'PAID_WAITING_CUTOFF' AND version = :currentVersion;
     ```
   - Chỉ đúng 1 tiến trình cập nhật thành công (`1 row affected`). Tiến trình thua nhận `OptimisticLockingFailureException` và trả kết quả nhất quán, tuyệt đối không xảy ra tình trạng đơn vừa nằm trong danh sách xuất kho vừa được tạo lệnh hoàn tiền.
2. **Khách bấm "Đã nhận hàng" đồng thời với Job Tự hoàn tất lúc `00:00:00 Ngày D+2`:**
   - Cả hai luồng cùng cố gắng chuyển `order_status` từ `PROCESSING` sang `COMPLETED` với `@Version`.
   - Luồng thắng sẽ ghi `completion_type` (`CUSTOMER_CONFIRMED` hoặc `SYSTEM_TIMEOUT`) và chèn bút toán hoa hồng vào `commission_ledger`.
   - Bảng `commission_ledger` có ràng buộc duy nhất `UNIQUE (order_id, entry_type)` cho loại `CREDIT_COMMISSION`, đảm bảo dù tác vụ nền chạy lại hay hai luồng va chạm cũng **không bao giờ tính hoa hồng 2 lần cho 1 đơn hàng**.
3. **Chạy bù tác vụ nền khi máy chủ khởi động lại (Catch-up Recovery):**
   - Các Job `AutoConfirmOrdersJob` (`00:00 D+1`) và `AutoCompleteOrdersJob` (`00:00 D+2`) không chỉ chạy bằng biểu thức cron đúng giây `00:00:00`, mà còn được cấu hình chạy quét định kỳ mỗi `5 phút` (kèm `ApplicationReadyEvent` khi vừa khởi động Spring Boot) với truy vấn dạng `WHERE cutoff_at <= SYSDATETIMEOFFSET() AND order_status = 'PAID_WAITING_CUTOFF'`. Nhờ đó, nếu container `api` bị tắt lúc `23:58` và bật lại lúc `00:10`, hệ thống lập tức chạy bù toàn bộ các đơn đến hạn và ghi log vào bảng `job_runs`.

## 5. Thiết Kế Cơ Sở Dữ Liệu SQL Server 2022 (Database Schema Blueprint)

Dưới đây là cấu trúc các bảng cốt lõi để Antigravity tạo file migration Flyway đầu tiên (`V1__init_schema.sql`):

### 5.1. Nhóm Người dùng, Phân quyền & Điểm nhận hàng
- `users`: `id (BIGINT IDENTITY PK)`, `email (VARCHAR(150) UNIQUE)`, `phone (VARCHAR(20) UNIQUE)`, `password_hash (VARCHAR(255))`, `full_name (NVARCHAR(120))`, `status (VARCHAR(30))`, `created_at (DATETIMEOFFSET)`, `version (BIGINT)`.
- `roles`: `id (INT IDENTITY PK)`, `code (VARCHAR(50) UNIQUE)` — gồm `ROLE_CUSTOMER`, `ROLE_WAREHOUSE`, `ROLE_DISPATCHER`, `ROLE_DRIVER`, `ROLE_AGENT`, `ROLE_ADMIN`.
- `user_roles`: `user_id (BIGINT FK)`, `role_id (INT FK)`, `PRIMARY KEY (user_id, role_id)`.
- `pickup_points`: `id (BIGINT IDENTITY PK)`, `code (VARCHAR(30) UNIQUE)`, `name (NVARCHAR(150))`, `address (NVARCHAR(300))`, `latitude (DECIMAL(9,6))`, `longitude (DECIMAL(9,6))`, `cold_storage_capacity (INT)`, `status (VARCHAR(30))`, `version (BIGINT)`.
- `point_assignments`: `id (BIGINT IDENTITY PK)`, `pickup_point_id (BIGINT FK)`, `agent_user_id (BIGINT FK)`, `active_from (DATETIMEOFFSET)`, `active_to (DATETIMEOFFSET NULL)`.

### 5.2. Nhóm Sản phẩm, Lô hàng FEFO & Giữ tồn
- `products`: `id (BIGINT IDENTITY PK)`, `sku_prefix (VARCHAR(40) UNIQUE)`, `name (NVARCHAR(200))`, `category (NVARCHAR(100))`, `storage_type (VARCHAR(30))` (`AMBIENT`, `CHILLED`, `FROZEN`), `min_shelf_life_days_at_pickup (INT DEFAULT 1)`, `dietary_tags (NVARCHAR(255))`, `allergen_tags (NVARCHAR(255))`, `is_active (BIT)`.
- `product_variants`: `id (BIGINT IDENTITY PK)`, `product_id (BIGINT FK)`, `sku (VARCHAR(60) UNIQUE)`, `pack_name (NVARCHAR(120))` (ví dụ: `Túi 500g`, `Khay 500g`, `Hộp 10 quả`), `pack_weight_grams (INT)`, `unit_price (DECIMAL(18,2))`, `is_active (BIT)`, `version (BIGINT)`.
- `lots`: `id (BIGINT IDENTITY PK)`, `variant_id (BIGINT FK)`, `lot_code (VARCHAR(60) UNIQUE)`, `supplier_name (NVARCHAR(150))`, `manufactured_date (DATE)`, `expiry_date (DATE)`, `initial_qty (INT)`, `remaining_qty (INT)`, `status (VARCHAR(30))` (`AVAILABLE`, `QUARANTINED`, `EXPIRED`), `received_at (DATETIMEOFFSET)`, `version (BIGINT)`.
- `order_cycles`: `id (BIGINT IDENTITY PK)`, `cycle_code (VARCHAR(40) UNIQUE)` (ví dụ: `CYCLE-2026-11-18`), `delivery_date (DATE)`, `open_at (DATETIMEOFFSET)`, `cutoff_at (DATETIMEOFFSET)`, `auto_confirm_at (DATETIMEOFFSET)`, `warehouse_pack_target_at (DATETIMEOFFSET)`, `dispatch_start_at (DATETIMEOFFSET)`, `pickup_start_at (DATETIMEOFFSET)`, `auto_complete_at (DATETIMEOFFSET)`, `payment_timeout_minutes (INT DEFAULT 30)`, `status (VARCHAR(30))` (`OPEN`, `LOCKED`, `FULFILLING`, `CLOSED`), `version (BIGINT)`.
- `point_cycles`: `id (BIGINT IDENTITY PK)`, `cycle_id (BIGINT FK)`, `pickup_point_id (BIGINT FK)`, `max_orders (INT)`, `current_orders (INT)`, `status (VARCHAR(30))`, `UNIQUE (cycle_id, pickup_point_id)`.
- `inventory_balances`: `id (BIGINT IDENTITY PK)`, `cycle_id (BIGINT FK)`, `variant_id (BIGINT FK)`, `available_qty (INT NOT NULL CHECK (available_qty >= 0))`, `reserved_qty (INT NOT NULL CHECK (reserved_qty >= 0))`, `committed_qty (INT NOT NULL CHECK (committed_qty >= 0))`, `version (BIGINT)`, `UNIQUE (cycle_id, variant_id)`.
- `reservations`: `id (BIGINT IDENTITY PK)`, `order_id (BIGINT FK)`, `cycle_id (BIGINT FK)`, `variant_id (BIGINT FK)`, `reserved_qty (INT)`, `status (VARCHAR(30))` (`ACTIVE`, `COMMITTED`, `RELEASED`, `EXPIRED`), `expires_at (DATETIMEOFFSET)`, `version (BIGINT)`.
- `stock_movements`: `id (BIGINT IDENTITY PK)`, `variant_id (BIGINT FK)`, `lot_id (BIGINT FK NULL)`, `order_id (BIGINT FK NULL)`, `movement_type (VARCHAR(40))` (`INBOUND`, `ALLOCATE_FEFO`, `DISPATCH`, `RETURN_TO_QUARANTINE`, `ADJUSTMENT`), `quantity (INT)`, `created_by (BIGINT FK)`, `created_at (DATETIMEOFFSET)`.

### 5.3. Nhóm Đơn hàng, Giao nhận & Mã QR
- `orders`:
  - `id (BIGINT IDENTITY PK)`, `order_code (VARCHAR(40) UNIQUE)`, `customer_id (BIGINT FK)`, `pickup_point_id (BIGINT FK)`, `cycle_id (BIGINT FK)`, `commission_policy_id (BIGINT FK)`
  - `subtotal_amount (DECIMAL(18,2))`, `discount_amount (DECIMAL(18,2))`, `total_amount (DECIMAL(18,2))`
  - `order_status (VARCHAR(30))`, `fulfillment_status (VARCHAR(30))`, `payment_status (VARCHAR(30))`
  - `cutoff_at (DATETIMEOFFSET)`, `pickup_start_at (DATETIMEOFFSET)`, `auto_complete_at (DATETIMEOFFSET)`
  - `confirmed_at (DATETIMEOFFSET NULL)`, `completed_at (DATETIMEOFFSET NULL)`, `completion_type (VARCHAR(30) NULL)` (`CUSTOMER_CONFIRMED`, `SYSTEM_TIMEOUT`)
  - `customer_confirmed_at (DATETIMEOFFSET NULL)`, `physical_handed_over_at (DATETIMEOFFSET NULL)`
  - `pickup_qr_token_hash (VARCHAR(128))`, `pickup_qr_used_at (DATETIMEOFFSET NULL)`
  - `has_open_issue (BIT DEFAULT 0)`, `is_unclaimed_at_agent (BIT DEFAULT 0)`
  - `created_at (DATETIMEOFFSET)`, `updated_at (DATETIMEOFFSET)`, `version (BIGINT)`
- `order_items`: `id (BIGINT IDENTITY PK)`, `order_id (BIGINT FK)`, `variant_id (BIGINT FK)`, `product_name_snapshot (NVARCHAR(200))`, `pack_name_snapshot (NVARCHAR(120))`, `unit_price_snapshot (DECIMAL(18,2))`, `quantity (INT)`, `line_total (DECIMAL(18,2))`.
- `order_lot_allocations`: `id (BIGINT IDENTITY PK)`, `order_item_id (BIGINT FK)`, `lot_id (BIGINT FK)`, `allocated_qty (INT)`.
- `trips`: `id (BIGINT IDENTITY PK)`, `trip_code (VARCHAR(40) UNIQUE)`, `cycle_id (BIGINT FK)`, `driver_id (BIGINT FK)`, `trip_type (VARCHAR(30))` (`DELIVERY`, `RETURN_PICKUP`), `status (VARCHAR(30))`, `departed_at (DATETIMEOFFSET NULL)`, `completed_at (DATETIMEOFFSET NULL)`, `version (BIGINT)`.
- `trip_stops`: `id (BIGINT IDENTITY PK)`, `trip_id (BIGINT FK)`, `pickup_point_id (BIGINT FK)`, `stop_sequence (INT)`, `eta_at (DATETIMEOFFSET NULL)`, `arrived_at (DATETIMEOFFSET NULL)`, `handover_completed_at (DATETIMEOFFSET NULL)`, `status (VARCHAR(30))`.
- `packages`: `id (BIGINT IDENTITY PK)`, `order_id (BIGINT FK)`, `trip_stop_id (BIGINT FK NULL)`, `package_code (VARCHAR(50) UNIQUE)`, `qr_payload (VARCHAR(200) UNIQUE)`, `package_seq (INT)`, `total_packages_in_order (INT)`, `storage_type (VARCHAR(30))`, `status (VARCHAR(30))` (`PACKED`, `HANDED_TO_DRIVER`, `DELIVERED_TO_AGENT`, `MISSING`, `DAMAGED`, `HANDED_TO_CUSTOMER`), `version (BIGINT)`.
- `handover_records`: `id (BIGINT IDENTITY PK)`, `handover_type (VARCHAR(40))` (`WAREHOUSE_TO_DRIVER`, `DRIVER_TO_AGENT`, `AGENT_TO_DRIVER_RETURN`, `DRIVER_TO_WAREHOUSE_RETURN`), `trip_stop_id (BIGINT FK NULL)`, `from_user_id (BIGINT FK)`, `to_user_id (BIGINT FK)`, `total_expected_packages (INT)`, `accepted_packages (INT)`, `missing_packages (INT)`, `damaged_packages (INT)`, `note (NVARCHAR(500))`, `signed_at (DATETIMEOFFSET)`.
- `handover_items`: `id (BIGINT IDENTITY PK)`, `handover_record_id (BIGINT FK)`, `package_id (BIGINT FK)`, `condition_status (VARCHAR(30))` (`ACCEPTED`, `MISSING`, `DAMAGED`), `evidence_image_url (VARCHAR(500) NULL)`.

### 5.4. Nhóm Thanh toán, Hoàn tiền, Hoa hồng & Sổ cái
- `payment_attempts`: `id (BIGINT IDENTITY PK)`, `order_id (BIGINT FK)`, `txn_ref (VARCHAR(64) UNIQUE)`, `provider (VARCHAR(30) DEFAULT 'VNPAY')`, `amount (DECIMAL(18,2))`, `status (VARCHAR(40))` (`INITIATED`, `SUCCESS`, `FAILED`, `LATE_PAID_RECONCILIATION`), `vnp_transaction_no (VARCHAR(64) NULL)`, `vnp_pay_date (VARCHAR(20) NULL)`, `created_at (DATETIMEOFFSET)`, `confirmed_at (DATETIMEOFFSET NULL)`, `version (BIGINT)`.
- `payment_events`: `id (BIGINT IDENTITY PK)`, `payment_attempt_id (BIGINT FK)`, `event_source (VARCHAR(30))` (`RETURN_URL`, `IPN_WEBHOOK`), `raw_payload (NVARCHAR(MAX))`, `signature_valid (BIT)`, `received_at (DATETIMEOFFSET)`.
- `issues` & `return_requests` & `refunds`:
  - `issues`: `id`, `order_id`, `reported_by_user_id`, `reporter_role`, `issue_type` (`DAMAGED`, `MISSING_ITEM`, `WRONG_ITEM`, `LATE_DELIVERY`), `description (NVARCHAR(1000))`, `evidence_urls (NVARCHAR(MAX))`, `status` (`OPEN`, `INVESTIGATING`, `RESOLVED`, `REJECTED`), `created_at`.
  - `return_requests`: `id`, `issue_id`, `order_id`, `pickup_point_id`, `return_trip_id (BIGINT FK NULL)`, `status` (`REQUESTED`, `APPROVED_FOR_PICKUP`, `DRIVER_PICKED_UP`, `WAREHOUSE_RECEIVED`, `UNRECOVERABLE_WAIVED`, `CLOSED`), `warehouse_inspection_note (NVARCHAR(500))`, `version`.
  - `refunds`: `id`, `order_id`, `return_request_id (BIGINT FK NULL)`, `refund_reason` (`CUSTOMER_CANCEL_BEFORE_CUTOFF`, `ORDER_ISSUE_RETURN`, `LATE_PAYMENT_REFUND`), `amount (DECIMAL(18,2))`, `status` (`PENDING_APPROVAL`, `APPROVED`, `PROCESSING`, `COMPLETED`, `FAILED_RECONCILIATION`), `provider_ref (VARCHAR(100) NULL)`, `approved_by (BIGINT FK NULL)`, `completed_at (DATETIMEOFFSET NULL)`, `version`.
- `commission_policies`: `id (BIGINT IDENTITY PK)`, `policy_code (VARCHAR(40) UNIQUE)`, `calc_type (VARCHAR(20))` (`FIXED_PER_ORDER`, `PERCENTAGE_NET_MERCHANDISE`), `fixed_amount (DECIMAL(18,2) NULL)`, `percentage_rate (DECIMAL(5,2) NULL)`, `effective_from (DATETIMEOFFSET)`, `effective_to (DATETIMEOFFSET NULL)`.
- `commission_ledger` (Append-only — Tuyệt đối không UPDATE/DELETE):
  - `id (BIGINT IDENTITY PK)`, `pickup_point_id (BIGINT FK)`, `order_id (BIGINT FK)`, `policy_id (BIGINT FK)`, `entry_type (VARCHAR(30))` (`CREDIT_COMMISSION`, `DEBIT_REFUND_REVERSAL`, `MANUAL_ADJUSTMENT`), `amount (DECIMAL(18,2))` (dương cho Credit, âm cho Debit), `settlement_batch_id (BIGINT FK NULL)`, `note (NVARCHAR(300))`, `created_at (DATETIMEOFFSET)`.
  - **Ràng buộc chống tính trùng:** `CREATE UNIQUE INDEX UX_CommissionLedger_Order_Credit ON commission_ledger(order_id) WHERE entry_type = 'CREDIT_COMMISSION';`

### 5.5. Nhóm Tự động hóa, Thông báo & AI
- `shedlock`: `name (VARCHAR(64) PK)`, `lock_until (DATETIME2)`, `locked_at (DATETIME2)`, `locked_by (VARCHAR(255))`.
- `job_runs`: `id (BIGINT IDENTITY PK)`, `job_name (VARCHAR(80))`, `cycle_id (BIGINT FK NULL)`, `started_at (DATETIMEOFFSET)`, `finished_at (DATETIMEOFFSET NULL)`, `processed_count (INT)`, `status (VARCHAR(30))`, `error_message (NVARCHAR(MAX) NULL)`.
- `outbox_events`: `id (BIGINT IDENTITY PK)`, `aggregate_type (VARCHAR(50))`, `aggregate_id (VARCHAR(64))`, `event_type (VARCHAR(80))`, `payload (NVARCHAR(MAX))`, `status (VARCHAR(20) DEFAULT 'PENDING')`, `created_at (DATETIMEOFFSET)`, `published_at (DATETIMEOFFSET NULL)`.
- `recipes` & `recipe_ingredients`:
  - `recipes`: `id (BIGINT IDENTITY PK)`, `name (NVARCHAR(150))`, `servings (INT)`, `cooking_time_minutes (INT)`, `instructions (NVARCHAR(MAX))`, `dietary_tags (NVARCHAR(255))`, `is_active (BIT)`.
  - `recipe_ingredients`: `id (BIGINT IDENTITY PK)`, `recipe_id (BIGINT FK)`, `product_id (BIGINT FK)`, `ingredient_name (NVARCHAR(120))`, `required_weight_grams (INT)`, `is_optional (BIT)`.
- `knowledge_documents`: `id (BIGINT IDENTITY PK)`, `doc_code (VARCHAR(50))`, `version_tag (VARCHAR(20))`, `title (NVARCHAR(200))`, `content_markdown (NVARCHAR(MAX))`, `is_active (BIT)`.

## 6. Cấu Trúc Thư Mục Dự Án Chuẩn Hóa Cho Antigravity

Khi khởi tạo workspace trong Antigravity, tạo cấu trúc Mono-repo gồm 3 phần chính (`backend`, `web`, `android`) cùng cấu hình `docker-compose.yml`:

```text
o2o-food-pickup-system/
├── docker-compose.yml
├── .env.example
├── docs/
│   ├── ANTIGRAVITY_PROJECT_SPEC.md
│   └── policy_rag_v1.md
├── backend/                                  # Java 21 + Spring Boot 3.3+
│   ├── Dockerfile
│   ├── pom.xml
│   └── src/main/
│       ├── resources/
│       │   ├── application.yml
│       │   └── db/migration/
│       │       ├── V1__init_schema.sql
│       │       └── V2__seed_initial_data.sql
│       └── java/vn/haui/o2ofood/
│           ├── common/                       # TimeProvider (Asia/Ho_Chi_Minh), ApiResponse, GlobalExceptionHandler
│           ├── security/                     # JwtAuthenticationFilter, RoleHierarchy, OwnershipSecurityService
│           ├── modules/
│           │   ├── identity/                 # Users, Roles, PickupPoints, PointAssignments
│           │   ├── catalog/                  # Products, Variants, Lots, FEFO Allocation Service
│           │   ├── cycle/                    # OrderCycles, PointCycles, InventoryBalances, Reservations
│           │   ├── ordering/                 # Carts, Orders, Cutoff Validation, AutoConfirm & AutoComplete Jobs
│           │   ├── payment/                  # VNPAY Sandbox Signer, IPN Webhook, Refunds, Late Payment Reconciliation
│           │   ├── fulfillment/              # Trips, Packages, QR Generator/Verifier, HandoverRecords
│           │   ├── exception/                # Issues, ReturnRequests, Quarantine Warehouse Handling
│           │   ├── commission/               # CommissionPolicies, Immutable CommissionLedger, SettlementBatches
│           │   ├── notification/             # Transactional Outbox Publisher, SSE/WebSocket, FCM Push
│           │   └── ai/                       # Policy RAG Service, Order Lookup ToolCalling, RecipeToCartService
├── web/                                      # Vue 3 + TypeScript + Vite + Pinia + Tailwind CSS
│   ├── Dockerfile
│   ├── nginx.conf
│   └── src/
│       ├── api/                              # Axios client + JWT Refresh Interceptor
│       ├── stores/                           # authStore, cartStore, cycleStore, notificationStore
│       ├── router/                           # Role-based Route Guards (Customer, Warehouse, Dispatcher, Agent, Driver, Admin)
│       └── views/
│           ├── customer/                     # Home, PickupPointMap (Leaflet), Cart, VnpayReturn, OrderDetail & QR, AiAssistant
│           ├── warehouse/                    # LotInbound, WavePickingFefo, PackagePackingQr, DriverHandover, ReturnInspection
│           ├── dispatcher/                   # TripPlanning, LiveDeliveryMonitor, ReturnTripDispatch
│           ├── agent/                        # DriverCheckIn, CustomerQrScanner, UnclaimedStockList, CommissionLedgerView
│           ├── driver/                       # AssignedTrips, WarehousePickupConfirm, StopHandoverForm
│           └── admin/                        # ProductCatalog, CycleConfig, CommissionPolicyConfig, RefundApproval, DashboardCharts
└── android/                                  # Kotlin + Jetpack Compose + Retrofit + Coroutines + ML Kit Barcode
    ├── app/build.gradle.kts
    └── app/src/main/java/vn/haui/o2ofood/
        ├── data/                             # Retrofit APIs, TokenManager (EncryptedSharedPreferences)
        ├── ui/customer/                      # Browse, PickupPointSelector, CheckoutWebView/CustomTab, OrderQrScreen, AiChatScreen
        ├── ui/agent/                         # MlKitQrScannerScreen, PackageCheckInScreen, CustomerHandoverScreen
        └── ui/driver/                        # TripStopListScreen, PackageQrVerifyScreen, HandoverConfirmScreen
```

## 7. Danh Sách Kiểm Thử Nghiệm Thu Bắt Buộc (Automated & Manual Acceptance Test Matrix)

Antigravity phải tạo bộ Integration Tests (`@SpringBootTest` + SQL Server Testcontainers hoặc H2/MSSQL mode) bao phủ 12 kịch bản cốt lõi sau:

| Mã Test | Kịch bản kiểm thử | Kỳ vọng nghiệm thu (Pass Criteria) |
| :--- | :--- | :--- |
| **AT-01** | Hai khách hàng cùng bấm thanh toán 1 gói hàng cuối cùng trong đợt | Chỉ 1 khách tạo được `Reservation` và chuyển sang VNPAY; khách còn lại nhận mã lỗi `409 INSUFFICIENT_STOCK`, `available_qty` không bao giờ âm. |
| **AT-02** | Đơn tạo chờ thanh toán nhưng quá 30 phút (hoặc chạm mốc `23:59:59`) chưa thanh toán | `ReservationCleanupJob` tự động hủy đơn (`PAYMENT_TIMEOUT`), trả lại `available_qty` đúng 1 lần. |
| **AT-03** | Khách tự hủy đơn lúc `23:59:58` và lúc `00:00:00` | Lúc `23:59:58` hủy thành công, giải phóng tồn và tạo yêu cầu hoàn tiền 100%; từ `00:00:00` trở đi trả lỗi `400 CUTOFF_PASSED`. |
| **AT-04** | Khách bấm Hủy đơn đồng thời với Job chốt đơn `00:00:00` | Nhờ `@Version`, chỉ 1 thao tác thắng; không bao giờ xảy ra đơn vừa có lệnh hoàn tiền vừa nằm trong danh sách đóng gói của kho. |
| **AT-05** | VNPAY gọi IPN lặp lại 3 lần hoặc sai chữ ký `vnp_SecureHash` | Sai chữ ký bị từ chối ngay; IPN hợp lệ gọi lặp 3 lần chỉ ghi nhận thanh toán 1 lần, không trừ tồn lặp. |
| **AT-06** | VNPAY gửi IPN thành công muộn sau khi đơn đã hết hạn giữ tồn 30 phút hoặc sau `00:00` | Không tự ý đổi ngày giao; ghi nhận vào trạng thái `LATE_PAID_RECONCILIATION` để khách chọn chuyển đợt hoặc hoàn tiền. |
| **AT-07** | Xuất kho theo FEFO với 3 lô có hạn sử dụng khác nhau | Hệ thống tự động chọn lô hết hạn sớm nhất nhưng vẫn còn hạn sau ngày nhận `D+1`; bỏ qua lô đã hết hạn hoặc bị khóa `QUARANTINED`. |
| **AT-08** | Đại lý kiểm nhận phát hiện 1 kiện bị dập hỏng trong chuyến giao | Đơn có kiện hỏng bị giữ ở `ISSUE_HOLD`, không gửi thông báo "Sẵn sàng nhận hàng" cho khách; các đơn nguyên vẹn khác cùng đại lý vẫn mở nhận hàng bình thường từ 16:00. |
| **AT-09** | Khách bấm "Đã nhận hàng" cùng lúc Job `00:00 D+2` tự hoàn tất | Chỉ sinh đúng 1 dòng `CREDIT_COMMISSION` trong `commission_ledger` (nhờ Unique Filtered Index), lưu đúng `completion_type` của giao dịch thắng. |
| **AT-10** | Đơn đủ điều kiện lúc `00:00 D+2` nhưng khách chưa đến đại lý lấy hàng | Đơn chuyển sang `COMPLETED` (`SYSTEM_TIMEOUT`), đại lý được ghi nhận hoa hồng, nhưng `physical_handed_over_at` vẫn `NULL` và đơn nằm trong danh sách `is_unclaimed_at_agent = 1`. |
| **AT-11** | Hoàn tiền cho đơn hàng đã hoàn tất và đã ghi nhận hoa hồng | Sau khi kho nhận lại hàng hoàn vào khu cách ly và Admin duyệt hoàn tiền, hệ thống tự động ghi 1 dòng `DEBIT_REFUND_REVERSAL` âm tương ứng vào `commission_ledger`. |
| **AT-12** | Khách dùng AI Chatbot hỏi đơn của người khác hoặc ép giá sản phẩm bằng Prompt Injection | Tool Calling kiểm tra `authenticatedUserId`, từ chối truy cập đơn người khác; giá giỏ hàng gợi ý luôn lấy từ bảng `product_variants` của Backend. |

## 8. Kịch Bản Prompt Theo Từng Giai Đoạn Để Bắt Đầu Trong Antigravity

Bạn có thể copy lần lượt các Prompt dưới đây vào Antigravity để Agent xây dựng dự án một cách có kiểm soát, không bị quá tải ngữ cảnh:

### Bước 1 — Khởi tạo Hạ tầng, Docker Compose & Database Schema (Phase 1)
```text
Hãy đọc toàn bộ file docs/ANTIGRAVITY_PROJECT_SPEC.md. 
Bắt đầu Phase 1:
1. Tạo docker-compose.yml gồm sqlserver (mcr.microsoft.com/mssql/server:2022-latest), api (Spring Boot 3.3, Java 21, TZ=Asia/Ho_Chi_Minh), web (Vue 3 + Nginx), và cloudflared tunnel.
2. Khởi tạo project Maven trong thư mục backend/ với các dependency: Spring Web, Spring Data JPA, Spring Security, Bean Validation, Flyway, mssql-jdbc, ShedLock, jjwt, Springdoc OpenAPI.
3. Viết đầy đủ file migration V1__init_schema.sql và V2__seed_initial_data.sql theo đúng mục 5 của tài liệu đặc tả (bao gồm đầy đủ ràng buộc CHECK, Filtered Unique Index cho commission_ledger, và dữ liệu mẫu cho 6 vai trò, 3 đại lý nhận hàng tại Hà Nội, 15 sản phẩm đóng gói cố định, các lô hàng FEFO và 30 công thức nấu ăn cơ bản).
```

### Bước 2 — Xây dựng Core Domain: Chu kỳ đặt hàng, Giữ tồn & VNPAY Sandbox (Phase 2)
```text
Tiếp tục Phase 2 dựa trên docs/ANTIGRAVITY_PROJECT_SPEC.md:
1. Xây dựng module Security (JWT Access/Refresh Token, phân quyền 6 Role, OwnershipSecurityService).
2. Xây dựng module Catalog & Cycle: API xem điểm nhận hàng, đợt giao hàng (OrderCycles), kiểm tra tồn kho theo đợt.
3. Xây dựng module Ordering & Payment:
   - API tạo đơn hàng với Atomic Conditional Update chống bán vượt tồn và tạo Reservation có TTL = MIN(now + 30 phút, cutoff_at).
   - Tích hợp VNPAY Sandbox (tạo URL thanh toán HMAC-SHA512, xử lý Return URL và Webhook IPN chống trùng lặp, xử lý thanh toán muộn LATE_PAID_RECONCILIATION).
   - API Khách tự hủy đơn trước 23:59:59 (có @Version chống đua dữ liệu với Job 00:00:00).
   - Viết 2 Scheduled Jobs có ShedLock: ReservationCleanupJob (mỗi 60s) và AutoConfirmOrdersJob (00:00:00 D+1 kèm cơ chế chạy bù khi khởi động lại).
4. Viết Integration Tests cho AT-01, AT-02, AT-03, AT-04, AT-05, AT-06.
```

### Bước 3 — Xây dựng Luồng Kho (FEFO), Điều phối, Tài xế, Đại lý & Hoa hồng (Phase 3)
```text
Tiếp tục Phase 3 dựa trên docs/ANTIGRAVITY_PROJECT_SPEC.md:
1. Xây dựng module Kho (Warehouse): Quản lý lô nhập (Lots), thuật toán phân bổ lô tự động theo FEFO (kiểm tra hạn dùng >= ngày nhận + min_shelf_life_days), tổng hợp nhu cầu Wave Picking theo đại lý, đóng gói và sinh mã QR riêng cho từng kiện (Packages).
2. Xây dựng module Điều phối, Tài xế & Đại lý:
   - Tạo chuyến giao (Trips, TripStops), biên bản bàn giao Kho -> Tài xế (08:00) và Tài xế -> Đại lý (trước 16:00).
   - Xử lý ngoại lệ khi thiếu/hỏng kiện hàng tại đại lý (chỉ mở READY_FOR_PICKUP và bắn thông báo cho các đơn đủ kiện).
   - Luồng quét mã QR nhận hàng dùng 1 lần của khách tại đại lý và khách xác nhận đã nhận hàng.
3. Xây dựng AutoCompleteOrdersJob (00:00:00 D+2), tách bạch CUSTOMER_CONFIRMED và SYSTEM_TIMEOUT, quản lý danh sách hàng tồn khách chưa lấy (is_unclaimed_at_agent) và sổ cái hoa hồng bất biến (CommissionLedger).
4. Xây dựng luồng Báo sự cố (Issues), Chuyến thu hồi hàng lỗi về kho cách ly (ReturnRequests) và Duyệt hoàn tiền kèm bút toán điều chỉnh âm hoa hồng.
```

### Bước 4 — Xây dựng Module AI (Chatbot RAG, Tra cứu đơn Tool-Calling & Gợi ý thực đơn) (Phase 4)
```text
Tiếp tục Phase 4 dựa trên docs/ANTIGRAVITY_PROJECT_SPEC.md:
1. Xây dựng Policy RAG Service đọc tài liệu chính sách từ bảng knowledge_documents để trả lời câu hỏi về giờ chốt 23:59, thời gian giữ đơn 30 phút, giờ nhận 16:00, quy tắc hủy và hoàn tiền.
2. Cấu hình Function/Tool Calling an toàn cho phép Chatbot tra cứu trạng thái đơn hàng thật của chính khách hàng đang đăng nhập (tuyệt đối không cho phép xem đơn của userId khác).
3. Xây dựng RecipeToCartService: nhận số người, ngân sách, nguyên liệu sẵn có và dị ứng -> lọc công thức trong DB -> quy đổi nguyên liệu ra số gói cố định đang bán và còn tồn trong đợt -> tính tổng tiền chính xác từ DB và dùng AI giải thích gợi ý trước khi khách bấm thêm vào giỏ.
```

### Bước 5 — Xây dựng Web App (Vue 3) & Android App (Kotlin) (Phase 5)
```text
Tiếp tục Phase 5 dựa trên docs/ANTIGRAVITY_PROJECT_SPEC.md:
1. Trong thư mục web/, xây dựng ứng dụng Vue 3 + TypeScript + Pinia + Tailwind CSS + Leaflet:
   - Giao diện Khách hàng: Chọn đại lý trên bản đồ, hiển thị rõ mốc chốt 23:59 và ngày nhận 16:00 D+1, giỏ hàng, thanh toán VNPAY, mã QR nhận hàng, theo dõi tiến độ và khung chat trợ lý AI.
   - Giao diện Kho, Điều phối, Quản trị và Web thích ứng (Responsive) cho Đại lý & Tài xế.
2. Trong thư mục android/, xây dựng ứng dụng Android Kotlin (Jetpack Compose + Retrofit + ML Kit Barcode Scanning) dùng chung Backend API cho Khách hàng, Tài xế và Đại lý (quét QR kiện hàng và QR nhận hàng của khách).
```

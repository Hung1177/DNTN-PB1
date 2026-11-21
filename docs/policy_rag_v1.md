# Tài Liệu Chính Sách Vận Hành Hệ Thống Đặt Mua Thực Phẩm Qua Điểm Nhận Hàng (Phiên Bản v1.0)

## 1. Mô Hình Bán Hàng & Định Giá
- Hệ thống do **1 đơn vị bán hàng duy nhất** vận hành với **1 kho trung tâm** và phân phối qua mạng lưới **Điểm nhận hàng (Đại lý - Pickup Points)**.
- Giá bán niêm yết thống nhất trên toàn hệ thống. Đại lý nhận hàng không tự định giá và không thu thêm phụ phí khi khách đến nhận hàng.
- Toàn bộ thực phẩm được đóng gói theo **quy cách cố định** (ví dụ: túi rau 500g, khay thịt 500g, hộp trứng 10 quả) để xác định chính xác 100% số tiền ngay khi đặt hàng, không bán theo cân nặng biến thiên.

## 2. Quy Định Thanh Toán & Thời Gian Giữ Tồn Kho
- Hệ thống **chỉ áp dụng thanh toán trực tuyến trả trước 100% qua VNPAY** (không hỗ trợ thanh toán tiền mặt COD khi nhận hàng).
- Khi khách hàng nhấn **Thanh toán**, hệ thống tạm giữ tồn kho (`Reservation`) cho đơn hàng trong thời gian tối đa **30 phút** (hoặc đến **23:59:59 Ngày D** nếu thời điểm đặt hàng còn ít hơn 30 phút là tới giờ chốt).
- Nếu quá thời hạn giữ tồn mà hệ thống chưa nhận được xác nhận thanh toán thành công (IPN) từ VNPAY, đơn hàng sẽ tự động hủy (`PAYMENT_TIMEOUT`) và hoàn trả tồn kho cho khách hàng khác mua.
- Trường hợp giao dịch VNPAY báo thành công đến muộn sau khi đơn đã hết hạn giữ tồn hoặc sau giờ chốt 00:00, đơn hàng sẽ chuyển sang trạng thái **Đối soát thanh toán muộn (`LATE_PAID_RECONCILIATION`)** để khách hàng lựa chọn: chuyển sang đợt giao kế tiếp hoặc nhận hoàn tiền 100%.

## 3. Khung Giờ Chốt Đơn, Giao Hàng & Nhận Hàng (Múi Giờ Việt Nam UTC+07:00)
- **Hạn chót đặt hàng và thanh toán (`cutoff_at`):** Từ `00:00:00` đến `23:59:59 Ngày D` cho đợt giao hàng `Ngày D+1`.
- **Mốc khóa đơn tự động (`auto_confirm_at`):** Đúng `00:00:00 Ngày D+1`, hệ thống tự động xác nhận đơn hàng, khóa quyền tự hủy và chuyển danh sách đơn sang kho trung tâm để chuẩn bị hàng.
- **Mốc chuẩn bị và vận chuyển:** Từ `00:00` đến `08:00 Ngày D+1` kho đóng gói theo chuẩn FEFO (ưu tiên lô còn hạn xa ngày nhận nhưng xuất trước lô hết hạn sớm). Từ `08:00` đến `16:00 Ngày D+1`, tài xế vận chuyển và bàn giao cho Đại lý nhận hàng.
- **Mốc mở nhận hàng tại Đại lý (`pickup_start_at`):** Từ `16:00:00 Ngày D+1` (sau khi Đại lý đã kiểm nhận đủ kiện hàng và không phát sinh hư hỏng). Khách hàng xuất trình mã QR nhận hàng trên ứng dụng Web hoặc Android để nhận hàng.
- **Mốc tự động hoàn tất đơn hàng (`auto_complete_at`):** Vào `00:00:00 Ngày D+2`, các đơn hàng đã sẵn sàng tại đại lý hoặc đã bàn giao mà không có khiếu nại/sự cố đang mở sẽ tự động chuyển sang trạng thái Hoàn tất (`COMPLETED`). Nếu khách chưa đến lấy hàng vật lý trước mốc này, kiện hàng được ghi nhận vào danh sách **Hàng tồn khách chưa lấy tại đại lý** để đại lý liên hệ hỗ trợ theo quy định bảo quản thực phẩm.

## 4. Chính Sách Hủy Đơn Hàng
- **Trước 23:59:59 Ngày D (trước giờ chốt):** Khách hàng được quyền tự hủy đơn hàng ngay trên ứng dụng:
  - Nếu đơn đang chờ thanh toán (`PENDING_PAYMENT`): Đơn hủy ngay và giải phóng tồn kho.
  - Nếu đơn đã thanh toán (`PAID_WAITING_CUTOFF`): Đơn hủy ngay, giải phóng tồn kho và tự động khởi tạo lệnh hoàn tiền 100% giá trị đơn hàng.
- **Từ 00:00:00 Ngày D+1 trở đi (sau giờ chốt):** Đơn hàng đã chuyển sang kho chế biến và đóng gói nên khách hàng **không thể tự hủy đơn**.

## 5. Chính Sách Khiếu Nại Sự Cố, Trả Hàng & Hoàn Tiền
- Khi nhận hàng tại đại lý, nếu phát hiện sản phẩm bị dập hỏng, thiếu món, giao sai quy cách hoặc không đảm bảo chất lượng, khách hàng có thể gửi báo cáo sự cố kèm hình ảnh minh chứng ngay trên ứng dụng.
- Đơn hàng có sự cố đang mở (`has_open_issue = true`) sẽ được tạm dừng tự động hoàn tất lúc `00:00 Ngày D+2`.
- Sau khi hàng lỗi được thu hồi về khu cách ly của kho trung tâm (hoặc được Quản trị viên xác nhận hư hỏng không thể thu hồi tại điểm nhận), hệ thống sẽ phê duyệt và hoàn tiền cho khách hàng.

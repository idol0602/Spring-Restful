# Kịch bản Test Access Token & Refresh Token

Hiện tại cấu hình trong `application.properties` đã được thiết lập như sau để phục vụ cho việc test:
- **Access Token:** Hết hạn sau **1 phút** (60 giây)
- **Refresh Token:** Hết hạn sau **2 phút** (120 giây)

Bạn có thể dùng Postman hoặc Swagger UI để làm theo các bước dưới đây.

## Bước 1: Login lấy Token
1. Gọi API `POST /api/auth/login` với body:
   ```json
   {
   "userName": "admin",
   "passWord": "admin123"
   }
   ```
2. **Kỳ vọng:** Đăng nhập thành công. Bạn sẽ nhận được `access_token` và `refresh_token` trả về qua Set-Cookie (và cả trong body response tuỳ cách bạn code).
3. Ghi chú lại thời gian lúc này (ví dụ: `10:00:00`).

## Bước 2: Test Access Token còn hạn (Trong vòng 1 phút đầu)
1. Trong khoảng từ `10:00:00` đến `10:01:00`, gọi một API bất kỳ có yêu cầu xác thực (ví dụ lấy thông tin user). Đảm bảo gửi kèm cookie/header có chứa token.
2. **Kỳ vọng:** Server trả về HTTP 200 OK.

## Bước 3: Test Access Token hết hạn (Sau 1 phút, trước 2 phút)
1. Đợi đến `10:01:05` (tức là sau khi đã qua 1 phút nhưng chưa tới 2 phút).
2. Gọi lại API lấy thông tin user giống ở Bước 2 bằng Access Token cũ.
3. **Kỳ vọng:** Server trả về HTTP 401 Unauthorized hoặc 403 Forbidden (tuỳ theo cách cấu hình exception).

## Bước 4: Test Refresh Token để lấy Access Token mới
1. Lúc này (vẫn trước khi hết 2 phút, ví dụ `10:01:30`), gọi API `POST /api/auth/refresh`.
   - Postman sẽ tự động gửi cookie `refresh_token` lên nếu bạn không thay đổi thiết lập.
2. **Kỳ vọng:** Server trả về thành công và cấp cho bạn một Set-Cookie `access_token` mới.

## Bước 5: Test Refresh Token hết hạn (Sau 2 phút)
1. Login lại để lấy token mới (Bước 1) và ghi nhớ thời gian (ví dụ: `10:05:00`).
2. Đợi đúng **2 phút 10 giây** (tức là `10:07:10`).
3. Lúc này cả Access Token (1 phút) và Refresh Token (2 phút) đều đã hết hạn.
4. Gọi API `POST /api/auth/refresh`.
5. **Kỳ vọng:** Server báo lỗi "Refresh token đã hết hạn. Vui lòng đăng nhập lại." (HTTP 401 Unauthorized), đồng thời cookie bị xoá/xóa khỏi Database (theo code bạn viết). Bạn bắt buộc phải gọi lại API `/login`.

> [!TIP]
> Bạn có thể kiểm tra trực tiếp Database (bảng `refresh_token`) để xem giá trị `expiry_date` có đúng là 2 phút tính từ lúc khởi tạo hay không.

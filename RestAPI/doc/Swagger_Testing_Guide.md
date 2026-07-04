# Hướng Dẫn Test Toàn Bộ API (Kèm Dữ Liệu Mẫu)

Tài liệu này cung cấp đầy đủ các bước, dữ liệu mẫu (JSON) và kết quả mong đợi để bạn test chi tiết từng API trên Swagger UI (`http://localhost:8080/swagger-ui/index.html`).

---

## 1. Xác thực và Lấy Token (Auth API)

Hệ thống có 3 tài khoản mặc định. Bạn cần dùng API Login để lấy token và gắn vào nút **Authorize** của Swagger trước khi test các API bên dưới.

### 1.1 Login bằng tài khoản ADMIN (Toàn quyền)
- **API:** `POST /api/auth/login`
- **Request Body:**
```json
{
  "userName": "admin",
  "passWord": "admin123"
}
```
- **Kết quả mong đợi:** `200 OK`, trả về JSON chứa trường `token`. Lấy chuỗi token này copy vào mục Authorize.

### 1.2 Login bằng tài khoản TEACHER (Quản lý Course)
- **API:** `POST /api/auth/login`
- **Request Body:**
```json
{
  "userName": "teacher",
  "passWord": "teacher123"
}
```
- **Kết quả mong đợi:** `200 OK`.

### 1.3 Login bằng tài khoản STUDENT (Chỉ xem Course)
- **API:** `POST /api/auth/login`
- **Request Body:**
```json
{
  "userName": "student",
  "passWord": "student123"
}
```
- **Kết quả mong đợi:** `200 OK`.

---

## 2. Test Role API (`/api/roles`)
*Điều kiện tiên quyết: Đã Authorize bằng token của tài khoản **ADMIN**.*

### 2.1 Tạo mới Role (POST)
- **API:** `POST /api/roles`
- **Request Body:**
```json
{
  "roleName": "GUEST",
  "description": "Guest access only"
}
```
- **Kết quả mong đợi:** `201 Created`
- **Lỗi mong đợi nếu test bằng tài khoản Teacher/Student:** `403 Forbidden`

### 2.2 Lấy danh sách Role (GET)
- **API:** `GET /api/roles`
- **Kết quả mong đợi:** `200 OK`, trả về mảng các roles (gồm ADMIN, TEACHER, STUDENT và GUEST vừa tạo).

### 2.3 Sửa Role (PUT)
- **API:** `PUT /api/roles/{id}` *(Thay {id} bằng ID của Role GUEST vừa tạo từ danh sách trên)*
- **Request Body:**
```json
{
  "roleName": "GUEST_UPDATED",
  "description": "Updated guest description"
}
```
- **Kết quả mong đợi:** `200 OK`, bản ghi thay đổi roleName thành `GUEST_UPDATED`.

### 2.4 Xoá Role (DELETE)
- **API:** `DELETE /api/roles/{id}`
- **Kết quả mong đợi:** `200 OK` (Message: Role deleted successfully).

---

## 3. Test User API (`/api/users`)
*Điều kiện tiên quyết: Đã Authorize bằng token của tài khoản **ADMIN**.*

### 3.1 Tạo mới User (POST)
- **API:** `POST /api/users`
- **Request Body:**
```json
{
  "userName": "newuser",
  "passWord": "newuser123",
  "fullName": "New User Name",
  "email": "newuser@example.com",
  "status": "Active",
  "roleName": "STUDENT"
}
```
*(Trường `roleName` bắt buộc phải là một role có tồn tại như STUDENT, TEACHER)*
- **Kết quả mong đợi:** `201 Created`.
- **Lỗi mong đợi nếu test bằng tài khoản Teacher/Student:** `403 Forbidden`.

### 3.2 Lấy danh sách User (GET)
- **API:** `GET /api/users`
- **Kết quả mong đợi:** `200 OK`, danh sách kèm theo `newuser` vừa tạo.

### 3.3 Lấy danh sách phân trang (GET /page)
- **API:** `GET /api/users/page`
- **Tham số URL:** `page=0`, `size=10`
- **Kết quả mong đợi:** `200 OK`, trả về object Page với các user. (Nên thử các tham số sort nhưng hiện tại API này không yêu cầu).

### 3.4 Sửa thông tin User (PUT)
- **API:** `PUT /api/users/{id}` *(Thay {id} bằng ID của `newuser` vừa sinh ra)*
- **Request Body:**
```json
{
  "userName": "newuser",
  "passWord": "newpassword456",
  "fullName": "Updated Name",
  "email": "updated@example.com",
  "status": "Inactive",
  "roleName": "STUDENT"
}
```
- **Kết quả mong đợi:** `200 OK`, `fullName` và `status` đã được đổi.

### 3.5 Xoá User (DELETE)
- **API:** `DELETE /api/users/{id}`
- **Kết quả mong đợi:** `200 OK` (Message: User deleted successfully).

---

## 4. Test Course API (`/api/courses`)

### 4.1 Tạo mới Course (POST)
- **Quyền:** ADMIN hoặc TEACHER
- **API:** `POST /api/courses`
- **Request Body:**
```json
{
  "courseName": "Lập trình Java Spring Boot",
  "duration": 40,
  "description": "Khóa học thực chiến xây dựng REST API"
}
```
- **Kết quả mong đợi:** `201 Created`.
- **Lỗi mong đợi nếu test bằng token STUDENT:** `403 Forbidden`.

### 4.2 Lấy danh sách Course (GET)
- **Quyền:** ADMIN, TEACHER, hoặc STUDENT
- **API:** `GET /api/courses`
- **Kết quả mong đợi:** `200 OK`, danh sách khóa học, trong đó có khóa "Lập trình Java Spring Boot" vừa tạo. (Cả STUDENT cũng gọi được thành công).

### 4.3 Xem chi tiết 1 Course (GET /{id})
- **Quyền:** ADMIN, TEACHER, hoặc STUDENT
- **API:** `GET /api/courses/{id}` *(Thử thay bằng 1)*
- **Kết quả mong đợi:** `200 OK`, chi tiết khóa học.

### 4.4 Cập nhật Course (PUT)
- **Quyền:** ADMIN hoặc TEACHER
- **API:** `PUT /api/courses/{id}` *(Thay {id} bằng ID khóa học vừa tạo)*
- **Request Body:**
```json
{
  "courseName": "Lập trình Java Spring Boot Nâng Cao",
  "duration": 60,
  "description": "Khóa học thực chiến có Security"
}
```
- **Kết quả mong đợi:** `200 OK`.

### 4.5 Xoá Course (DELETE)
- **Quyền:** ADMIN hoặc TEACHER
- **API:** `DELETE /api/courses/{id}`
- **Kết quả mong đợi:** `200 OK`. Lỗi `403` nếu dùng tài khoản STUDENT.

### 4.6 Xoá Hàng Loạt (DELETE /batch)
- **Quyền:** ADMIN hoặc TEACHER
- **API:** `DELETE /api/courses/batch`
- **Request Body:**
```json
[1, 2]
```
*(Trong đó 1 và 2 là mảng danh sách ID của các Course muốn xóa)*
- **Kết quả mong đợi:** `200 OK`

package fa.training.restapi.controller;

import fa.training.restapi.dto.request.UserRequest;
import fa.training.restapi.dto.response.ApiResponse;
import fa.training.restapi.entity.Role;
import fa.training.restapi.entity.User;
import fa.training.restapi.exception.AppException;
import fa.training.restapi.exception.ErrorCode;
import fa.training.restapi.service.RoleSevice;
import fa.training.restapi.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class UserController {
    private final UserService userService;
    private final RoleSevice roleSevice;
    private final PasswordEncoder passwordEncoder;

    // Create a new user
    @PostMapping
    public ResponseEntity<ApiResponse<User>> createUser(@Valid @RequestBody UserRequest userRequest) {

        Role role = roleSevice.findByRoleName(userRequest.getRoleName())
                .orElseThrow(() -> new AppException(ErrorCode.ROLE_NOT_FOUND));

        User user = User.builder()
                .userName(userRequest.getUserName())
                .passWord(passwordEncoder.encode(userRequest.getPassWord()))
                .fullName(userRequest.getFullName())
                .email(userRequest.getEmail())
                .status(userRequest.getStatus())
                .role(role)
                .build();

        // Đồng bộ 2 chiều
        role.addUser(user);

        User savedUser = userService.save(user);
        ApiResponse<User> responseBody = ApiResponse.<User>builder()
                .success(true)
                .message("User created successfully")
                .result(savedUser)
                .build();
        return new ResponseEntity<>(responseBody, HttpStatus.CREATED);
    }

    // Get all users
    @GetMapping
    public ResponseEntity<ApiResponse<List<User>>> getAllUsers() {
        List<User> users = userService.findAll();
        ApiResponse<List<User>> responseBody = ApiResponse.<List<User>>builder()
                .success(true)
                .message("Users retrieved successfully")
                .result(users)
                .build();
        return ResponseEntity.ok(responseBody);
    }

    // Get a user by ID
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<User>> getUserById(@PathVariable Long id) {
        User user = userService.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND));
        ApiResponse<User> responseBody = ApiResponse.<User>builder()
                .success(true)
                .message("User retrieved successfully")
                .result(user)
                .build();
        return ResponseEntity.ok(responseBody);
    }

    // Get users with pagination
    @GetMapping("/page")
    public ResponseEntity<ApiResponse<Page<User>>> getUsersPage(Pageable pageable) {
        Page<User> page = userService.findAll(pageable);
        ApiResponse<Page<User>> responseBody = ApiResponse.<Page<User>>builder()
                .success(true)
                .message("Users page retrieved successfully")
                .result(page)
                .build();
        return ResponseEntity.ok(responseBody);
    }

    // Update a user by ID
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<User>> updateUser(@PathVariable Long id,
                                                        @Valid @RequestBody UserRequest userRequest) {
        // Tìm user cũ trong DB
        User existingUser = userService.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND,
                        "Không tìm thấy user cần cập nhật với ID: " + id));

        // Kiểm tra Role mới có tồn tại không
        Role newRole = roleSevice.findByRoleName(userRequest.getRoleName())
                .orElseThrow(() -> new AppException(ErrorCode.ROLE_NOT_FOUND));

        // Đồng bộ 2 chiều nếu Role thay đổi
        Role oldRole = existingUser.getRole();
        if (oldRole != null && !oldRole.getRoleId().equals(newRole.getRoleId())) {
            oldRole.deleteUser(existingUser);
            newRole.addUser(existingUser);
        }

        // Cập nhật các trường
        existingUser.setUserName(userRequest.getUserName());
        existingUser.setPassWord(passwordEncoder.encode(userRequest.getPassWord()));
        existingUser.setFullName(userRequest.getFullName());
        existingUser.setEmail(userRequest.getEmail());
        existingUser.setStatus(userRequest.getStatus());
        existingUser.setRole(newRole);

        User updatedUser = userService.save(existingUser);
        ApiResponse<User> responseBody = ApiResponse.<User>builder()
                .success(true)
                .message("User updated successfully")
                .result(updatedUser)
                .build();
        return ResponseEntity.ok(responseBody);
    }

    // Delete a user by ID
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteUser(@PathVariable Long id) {
        // Tìm user để đồng bộ 2 chiều trước khi xóa
        User user = userService.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND,
                        "Không tìm thấy user cần xóa với ID: " + id));

        // Đồng bộ 2 chiều: xóa user khỏi collection của Role
        Role role = user.getRole();
        if (role != null) {
            role.deleteUser(user);
        }

        userService.deleteById(id);
        ApiResponse<Void> responseBody = ApiResponse.<Void>builder()
                .success(true)
                .message("User deleted successfully")
                .build();
        return ResponseEntity.ok(responseBody);
    }

    // Delete many users
    @DeleteMapping("/batch")
    public ResponseEntity<ApiResponse<Void>> deleteManyUsers(@RequestBody List<Long> ids) {
        userService.deleteAll(ids);
        ApiResponse<Void> responseBody = ApiResponse.<Void>builder()
                .success(true)
                .message("Batch deletion completed successfully")
                .build();
        return ResponseEntity.ok(responseBody);
    }
}

package fa.training.restapi.controller;

import fa.training.restapi.dto.request.RoleRequest;
import fa.training.restapi.dto.response.ApiResponse;
import fa.training.restapi.entity.Role;
import fa.training.restapi.exception.AppException;
import fa.training.restapi.exception.ErrorCode;
import fa.training.restapi.service.RoleSevice;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/roles")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class RoleController {
    private final RoleSevice roleSevice;

    // Create a new role
    @PostMapping
    public ResponseEntity<ApiResponse<Role>> createRole(@Valid @RequestBody RoleRequest request) {
        Role role = Role.builder()
                .roleName(request.getRoleName())
                .description(request.getDescription())
                .build();

        Role savedRole = roleSevice.save(role);
        ApiResponse<Role> responseBody = ApiResponse.<Role>builder()
                .success(true)
                .message("Role created successfully")
                .result(savedRole)
                .build();
        return new ResponseEntity<>(responseBody, HttpStatus.CREATED);
    }

    // Get all roles
    @GetMapping
    public ResponseEntity<ApiResponse<List<Role>>> getAllRoles() {
        List<Role> roles = roleSevice.findAll();
        ApiResponse<List<Role>> responseBody = ApiResponse.<List<Role>>builder()
                .success(true)
                .message("Roles retrieved successfully")
                .result(roles)
                .build();
        return ResponseEntity.ok(responseBody);
    }

    // Get a role by ID
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<Role>> getRoleById(@PathVariable Long id) {
        Role role = roleSevice.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND));
        ApiResponse<Role> responseBody = ApiResponse.<Role>builder()
                .success(true)
                .message("Role retrieved successfully")
                .result(role)
                .build();
        return ResponseEntity.ok(responseBody);
    }

    // Get roles with pagination
    @GetMapping("/page")
    public ResponseEntity<ApiResponse<Page<Role>>> getRolesPage(Pageable pageable) {
        Page<Role> page = roleSevice.findAll(pageable);
        ApiResponse<Page<Role>> responseBody = ApiResponse.<Page<Role>>builder()
                .success(true)
                .message("Roles page retrieved successfully")
                .result(page)
                .build();
        return ResponseEntity.ok(responseBody);
    }

    // Update a role by ID
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<Role>> updateRole(@PathVariable Long id,
                                                        @Valid @RequestBody RoleRequest request) {
        Role role = Role.builder()
                .roleId(id)
                .roleName(request.getRoleName())
                .description(request.getDescription())
                .build();

        Role updatedRole = roleSevice.save(role);
        ApiResponse<Role> responseBody = ApiResponse.<Role>builder()
                .success(true)
                .message("Role updated successfully")
                .result(updatedRole)
                .build();
        return ResponseEntity.ok(responseBody);
    }

    // Delete a role by ID
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteRole(@PathVariable Long id) {
        roleSevice.deleteById(id);
        ApiResponse<Void> responseBody = ApiResponse.<Void>builder()
                .success(true)
                .message("Role deleted successfully")
                .build();
        return ResponseEntity.ok(responseBody);
    }

    // Delete many roles
    @DeleteMapping("/batch")
    public ResponseEntity<ApiResponse<Void>> deleteManyRoles(@RequestBody List<Long> ids) {
        roleSevice.deleteAll(ids);
        ApiResponse<Void> responseBody = ApiResponse.<Void>builder()
                .success(true)
                .message("Batch deletion completed successfully")
                .build();
        return ResponseEntity.ok(responseBody);
    }
}

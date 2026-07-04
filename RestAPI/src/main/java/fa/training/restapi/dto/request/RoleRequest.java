package fa.training.restapi.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RoleRequest {
    @NotBlank(message = "Role name is required")
    private String roleName;

    @NotBlank(message = "Description is required")
    private String description;
}

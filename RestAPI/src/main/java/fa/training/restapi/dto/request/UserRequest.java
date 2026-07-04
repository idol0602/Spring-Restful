package fa.training.restapi.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.validator.constraints.Length;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserRequest {
    @NotBlank(message = "User name is required")
    private String userName;

    @NotBlank(message = "Password is required")
    @Length(min = 6, message = "Password must be at least 6 characters long")
    private String passWord;

    @NotBlank(message = "Full name is required")
    private String fullName;

    @NotBlank(message = "Email is required")
    @Email(message = "Email should be valid")
    private String email;

    @NotBlank(message = "Status is required")
    private String status;

    @NotBlank(message = "Role name is required")
    private String roleName;
}

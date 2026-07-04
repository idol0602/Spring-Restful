package fa.training.restapi.dto.request;

import fa.training.restapi.entity.DefaultRoles;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class RegisterRequest {
    @NotBlank(message = "USERNAME_NOT_BLANK")
    @Size(min = 3, message = "USERNAME_INVALID")
    private String userName;

    @NotBlank(message = "PASSWORD_NOT_BLANK")
    @Size(min = 8, message = "INVALID_PASSWORD")
    private String passWord;

    @NotBlank(message = "FULLNAME_NOT_BLANK")
    private String fullName;

    @NotBlank(message = "EMAIL_NOT_BLANK")
    @Email(message = "EMAIL_INVALID")
    private String email;

    @NotBlank(message = "STATUS_NOT_BLANK")
    private String status;

    @Builder.Default
    private String Role = DefaultRoles.STUDENT.name();
}

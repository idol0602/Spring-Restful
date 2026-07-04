package fa.training.restapi.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LoginRequest {
    @NotBlank(message = "USERNAME_NOT_BLANK")
    @Size(min = 3, message = "USERNAME_INVALID")
    private String userName;

    @NotBlank(message = "PASSWORD_NOT_BLANK")
    @Size(min = 8, message = "INVALID_PASSWORD")
    private String passWord;
}

package fa.training.restapi.dto.response;


import fa.training.restapi.entity.RefreshToken;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LoginResult {
    private String accessToken;
    private RefreshToken refreshToken;
}

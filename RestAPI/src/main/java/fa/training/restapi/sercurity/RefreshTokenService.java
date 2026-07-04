package fa.training.restapi.sercurity;

import fa.training.restapi.entity.RefreshToken;
import fa.training.restapi.entity.User;
import fa.training.restapi.exception.AppException;
import fa.training.restapi.exception.ErrorCode;
import fa.training.restapi.repository.RefreshTokenRepository;
import fa.training.restapi.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RefreshTokenService {
    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;

    private static final long REFRESH_TOKEN_EXPIRATION_DAYS = 7;

    @Transactional
    public RefreshToken verifyExpiration(RefreshToken token) {
        if (token.isExpired()) {
            refreshTokenRepository.delete(token);
            throw new AppException(ErrorCode.UNAUTHENTICATED,
                    "Refresh token đã hết hạn. Vui lòng đăng nhập lại.");
        }
        return token;
    }


    @Transactional
    public void deleteByUser(User user) {
        refreshTokenRepository.deleteByUser(user);
    }

    public RefreshToken createRefreshToken(String userName) {
        User user = userRepository.findByUserName(userName)
                .orElseThrow(() -> new RuntimeException("User not found with username: " + userName));

        refreshTokenRepository.deleteByUser(user);

        RefreshToken refreshToken = RefreshToken.builder()
                .token(UUID.randomUUID().toString())
                .user(user)
                .expiryDate(LocalDateTime.now().plusDays(REFRESH_TOKEN_EXPIRATION_DAYS))
                .build();
        return refreshTokenRepository.save(refreshToken);
    }

    public RefreshToken findByToken(String token) {
        return refreshTokenRepository.findByToken(token)
                .orElseThrow(() -> new AppException(ErrorCode.UNAUTHENTICATED,
                        "Refresh token not found with token: " + token));
    }
}

package fa.training.restapi.service;

import fa.training.restapi.dto.request.LoginRequest;
import fa.training.restapi.dto.request.RegisterRequest;
import fa.training.restapi.dto.response.LoginResult;
import fa.training.restapi.entity.DefaultRoles;
import fa.training.restapi.entity.RefreshToken;
import fa.training.restapi.entity.Role;
import fa.training.restapi.entity.User;
import fa.training.restapi.exception.AppException;
import fa.training.restapi.exception.ErrorCode;
import fa.training.restapi.repository.RoleRepository;
import fa.training.restapi.repository.UserRepository;
import fa.training.restapi.sercurity.BlackListService;
import fa.training.restapi.sercurity.CustomUserDetails;
import fa.training.restapi.sercurity.JwtService;
import fa.training.restapi.sercurity.RefreshTokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final RefreshTokenService refreshTokenService;

    private final BlackListService blackListService;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public LoginResult login(LoginRequest request) {
        log.info("Processing authentication for username: {}", request.getUserName());

        var user = userRepository.findByUserName(request.getUserName())
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED));

        boolean authenticated = passwordEncoder.matches(request.getPassWord(), user.getPassWord());
        if (!authenticated) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }

        String accessToken = jwtService.generateAccessToken(new CustomUserDetails(user));
        RefreshToken refreshToken = refreshTokenService.createRefreshToken(user.getUserName());

        return LoginResult.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .build();
    }

    @Transactional
    public void register(RegisterRequest request) {
        log.info("Processing registration for username: {}", request.getUserName());

        if (userRepository.existsByUserName(request.getUserName())) {
            throw new AppException(ErrorCode.USER_EXISTED);
        }
        Optional<Role> optionalRole = roleRepository.findByRoleName(DefaultRoles.STUDENT.name());
        if (!optionalRole.isPresent()) {
            throw new AppException(ErrorCode.ROLE_NOT_FOUND);
        }

        User newUser = User.builder()
                .userName(request.getUserName())
                .passWord(passwordEncoder.encode(request.getPassWord()))
                .fullName(request.getFullName())
                .email(request.getEmail())
                .role(optionalRole.get())
                .status(request.getStatus())
                .build();

        optionalRole.get().addUser(newUser);
        userRepository.save(newUser);
    }

    public String refreshAccessToken(String refreshTokenStr) {
        log.info("Processing refresh access token for refresh token: {}", refreshTokenStr);
        RefreshToken refreshToken = refreshTokenService.findByToken(refreshTokenStr);
        refreshTokenService.verifyExpiration(refreshToken);
        User user = refreshToken.getUser();
        return jwtService.generateAccessToken(new CustomUserDetails(user));
    }

    // NEED CHECK
    @Transactional
    public void logout(String username) {
        User user = userRepository.findByUserName(username)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED));
        refreshTokenService.deleteByUser(user);
        SecurityContextHolder.clearContext();
    }
}

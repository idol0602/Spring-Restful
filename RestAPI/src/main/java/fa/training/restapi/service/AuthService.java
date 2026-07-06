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
import fa.training.restapi.sercurity.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
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
    private final AuthenticationManager authenticationManager;

    @Transactional
    public LoginResult login(LoginRequest request) {
        log.info("Processing authentication for username: {}", request.getUserName());
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            request.getUserName(),
                            request.getPassWord()
                    )
            );

            CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
            String accessToken = jwtService.generateAccessToken(userDetails);
            RefreshToken refreshToken = refreshTokenService.createRefreshToken(userDetails.getUsername());

            return LoginResult.builder()
                    .accessToken(accessToken)
                    .refreshToken(refreshToken)
                    .build();

        } catch (AuthenticationException e) {
            log.error("Authentication failed for user: {}", request.getUserName(), e);
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }
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

    @Transactional
    public void logout(String accessToken, String refreshToken, String username) {
        log.info("Processing logout for username: {}", username);

        if (accessToken != null && !accessToken.isEmpty()) {
            try {
                LocalDateTime timeExpire = jwtService.extractExpirationAsLocalDateTime(accessToken);
                blackListService.revokeToken(accessToken, timeExpire);
            } catch (Exception e) {
                log.warn("Could not blacklist access token: {}", e.getMessage());
            }
        }

        boolean refreshTokenDeleted = false;
        if (refreshToken != null && !refreshToken.isEmpty()) {
            try {
                RefreshToken tokenEntity = refreshTokenService.findByToken(refreshToken);
                refreshTokenService.deleteByUser(tokenEntity.getUser());
                refreshTokenDeleted = true;
            } catch (Exception e) {
                log.warn("Could not delete refresh token from DB: {}", e.getMessage());
            }
        }

        if (!refreshTokenDeleted && username != null) {
            try {
                userRepository.findByUserName(username)
                        .ifPresent(refreshTokenService::deleteByUser);
            } catch (Exception e) {
                log.warn("Could not delete refresh token by username: {}", e.getMessage());
            }
        }

        SecurityContextHolder.clearContext();
    }
}

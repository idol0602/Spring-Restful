package fa.training.restapi.controller;

import fa.training.restapi.dto.request.LoginRequest;
import fa.training.restapi.dto.request.RegisterRequest;
import fa.training.restapi.dto.response.ApiResponse;
import fa.training.restapi.dto.response.AuthenticationResponse;
import fa.training.restapi.dto.response.LoginResult;
import fa.training.restapi.sercurity.BlackListService;
import fa.training.restapi.sercurity.CookieService;
import fa.training.restapi.sercurity.JwtService;
import fa.training.restapi.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthService authService;
    private final CookieService cookieService;
    private final BlackListService blackListService;
    private final JwtService jwtService;

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthenticationResponse>> login(
            @Valid @RequestBody LoginRequest loginRequest
            , HttpServletResponse response) {
        LoginResult loginResult = authService.login(loginRequest);
        cookieService.addAccessTokenCookie(response, loginResult.getAccessToken());
        cookieService.addRefreshTokenCookie(response, loginResult.getRefreshToken().getToken());

        AuthenticationResponse authResponse = AuthenticationResponse.builder()
                .token(loginResult.getAccessToken())
                .authenticated(true)
                .build();

        ApiResponse<AuthenticationResponse> responseBody = ApiResponse.<AuthenticationResponse>builder()
                .success(true)
                .message("Login successful")
                .result(authResponse)
                .build();
        return ResponseEntity.ok(responseBody);
    }

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<Void>> register(@Valid @RequestBody RegisterRequest request) {
        authService.register(request);
        ApiResponse<Void> responseBody = ApiResponse.<Void>builder()
                .success(true)
                .message("Register successful")
                .build();
        return ResponseEntity.status(HttpStatus.CREATED).body(responseBody);
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<Void>> refresh(HttpServletRequest request,
                                                     HttpServletResponse response) {
        String refreshToken = cookieService.extractTokenFromCookie(
                request, CookieService.REFRESH_TOKEN_COOKIE);

        if (refreshToken == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.<Void>builder()
                            .success(false)
                            .message("Refresh token not exist")
                            .build());
        }

        String newAccessToken = authService.refreshAccessToken(refreshToken);
        cookieService.addAccessTokenCookie(response, newAccessToken);

        ApiResponse<Void> responseBody = ApiResponse.<Void>builder()
                .success(true)
                .message("Refresh token successful")
                .build();
        return ResponseEntity.ok(responseBody);
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(HttpServletRequest request, HttpServletResponse response) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if(authentication != null && authentication.getName() != null) {
            authService.logout(authentication.getName());
        }

        cookieService.clearTokenCookies(response);
        
        String accessToken = cookieService.extractTokenFromCookie(request, CookieService.ACCESS_TOKEN_COOKIE);
        if (accessToken == null || accessToken.isEmpty()) {
            String authenHeader = request.getHeader("Authorization");
            if (authenHeader != null && authenHeader.startsWith("Bearer ")) {
                accessToken = authenHeader.substring(7);
            }
        }

        if (accessToken != null && !accessToken.isEmpty()) {
            try {
                blackListService.revokeToken(accessToken, jwtService.extractExpirationAsLocalDateTime(accessToken));
            } catch (Exception e) {
                // Ignore invalid token on logout
            }
        }

        ApiResponse<Void> responseBody = ApiResponse.<Void>builder()
                .success(true)
                .message("Logout successful")
                .build();
        return ResponseEntity.ok(responseBody);
    }
}
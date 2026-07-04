# Hướng Dẫn Triển Khai Refresh Token + Access Token Lưu Cookie

## Mục Lục
1. [Tổng quan kiến trúc](#1-tổng-quan-kiến-trúc)
2. [Bước 1: Tạo Entity RefreshToken](#bước-1-tạo-entity-refreshtoken)
3. [Bước 2: Tạo RefreshTokenRepository](#bước-2-tạo-refreshtokenrepository)
4. [Bước 3: Sửa JwtService — Tạo 2 loại token](#bước-3-sửa-jwtservice--tạo-2-loại-token)
5. [Bước 4: Tạo RefreshTokenService](#bước-4-tạo-refreshtokenservice)
6. [Bước 5: Tạo CookieService](#bước-5-tạo-cookieservice)
7. [Bước 6: Sửa AuthService](#bước-6-sửa-authservice)
8. [Bước 7: Sửa AuthController — Lưu token vào Cookie](#bước-7-sửa-authcontroller--lưu-token-vào-cookie)
9. [Bước 8: Sửa JwtAuthenticationFilter — Đọc token từ Cookie](#bước-8-sửa-jwtauthenticationfilter--đọc-token-từ-cookie)
10. [Bước 9: Cập nhật SecurityConfig](#bước-9-cập-nhật-securityconfig)
11. [Bước 10: Sửa AuthenticationResponse](#bước-10-sửa-authenticationresponse)
12. [Luồng hoạt động](#luồng-hoạt-động)
13. [Test trên Swagger/Postman](#test-trên-swaggerpostman)

---

## 1. Tổng Quan Kiến Trúc

```
Client (Browser)
    |
    |-- POST /api/auth/login  (username + password)
    |       ↓
    |   Server tạo:
    |     • Access Token  (JWT, thời hạn ngắn: 15 phút)
    |     • Refresh Token (UUID, thời hạn dài: 7 ngày, lưu DB)
    |       ↓
    |   Server trả về:
    |     • Set-Cookie: access_token=<jwt>; HttpOnly; Secure; Path=/
    |     • Set-Cookie: refresh_token=<uuid>; HttpOnly; Secure; Path=/api/auth
    |
    |-- Mỗi request sau đó:
    |     Cookie access_token tự đính kèm → Filter đọc và xác thực
    |
    |-- Khi access_token hết hạn:
    |     POST /api/auth/refresh  (Cookie refresh_token tự đính kèm)
    |       ↓
    |     Server kiểm tra refresh_token trong DB
    |       ↓
    |     Tạo access_token mới → Set-Cookie lại
    |
    |-- POST /api/auth/logout
    |     Xóa cả 2 Cookie + xóa refresh_token khỏi DB
```

**Tại sao dùng Cookie HttpOnly?**
- JavaScript không thể đọc được → chống XSS
- Browser tự đính kèm Cookie mỗi request → không cần code thêm ở Frontend
- `Secure` flag bảo đảm chỉ gửi qua HTTPS (trong production)

---

## Bước 1: Tạo Entity RefreshToken

📁 `src/main/java/fa/training/restapi/entity/RefreshToken.java`

```java
package fa.training.restapi.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "refresh_tokens")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RefreshToken {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE)
    private Long id;

    // Token value (UUID random, không phải JWT)
    @Column(nullable = false, unique = true)
    private String token;

    // Liên kết với User
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // Thời điểm hết hạn
    @Column(nullable = false)
    private LocalDateTime expiryDate;

    // Kiểm tra đã hết hạn chưa
    public boolean isExpired() {
        return LocalDateTime.now().isAfter(this.expiryDate);
    }
}
```

> **Giải thích:** Refresh token được lưu vào DB thay vì JWT. Lý do: ta có thể thu hồi (revoke) refresh token bất cứ lúc nào bằng cách xóa nó khỏi DB.

---

## Bước 2: Tạo RefreshTokenRepository

📁 `src/main/java/fa/training/restapi/repository/RefreshTokenRepository.java`

```java
package fa.training.restapi.repository;

import fa.training.restapi.entity.RefreshToken;
import fa.training.restapi.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {
    Optional<RefreshToken> findByToken(String token);
    void deleteByUser(User user);
}
```

---

## Bước 3: Sửa JwtService — Tạo 2 loại token

📁 `src/main/java/fa/training/restapi/sercurity/JwtService.java`

**Thay đổi chính:**
- Tách biệt `ACCESS_TOKEN_EXPIRATION` (15 phút) và thêm method `generateAccessToken`
- Giữ nguyên các method extract/validate hiện có

```java
package fa.training.restapi.sercurity;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import java.security.Key;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

@Service
public class JwtService {

    private static final String SECRET_STRING = "enterprise_secret_key_must_be_extremely_long_and_secure_2026_jsfw_l_a102";
    private final Key signingKey = Keys.hmacShaKeyFor(SECRET_STRING.getBytes());

    // ===== THAY ĐỔI: Giảm thời hạn access token xuống 15 phút =====
    private static final long ACCESS_TOKEN_EXPIRATION_MINUTES = 15;

    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    // ===== THAY ĐỔI: Đổi tên method cho rõ ràng =====
    public String generateAccessToken(UserDetails userDetails) {
        return generateAccessToken(new HashMap<>(), userDetails);
    }

    public String generateAccessToken(Map<String, Object> extraClaims, UserDetails userDetails) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expirationTime = now.plusMinutes(ACCESS_TOKEN_EXPIRATION_MINUTES);

        Date issuedAtDate = Date.from(now.atZone(ZoneId.systemDefault()).toInstant());
        Date expirationDate = Date.from(expirationTime.atZone(ZoneId.systemDefault()).toInstant());

        return Jwts.builder()
                .setClaims(extraClaims)
                .setSubject(userDetails.getUsername())
                .setIssuedAt(issuedAtDate)
                .setExpiration(expirationDate)
                .signWith(signingKey, SignatureAlgorithm.HS256)
                .compact();
    }

    // ===== GIỮ NGUYÊN: Giữ lại method cũ để backward compatible =====
    public String generateToken(UserDetails userDetails) {
        return generateAccessToken(userDetails);
    }

    public String generateToken(Map<String, Object> extraClaims, UserDetails userDetails) {
        return generateAccessToken(extraClaims, userDetails);
    }

    public boolean isTokenValid(String token, UserDetails userDetails) {
        final String username = extractUsername(token);
        return (username.equals(userDetails.getUsername())) && !isTokenExpired(token);
    }

    private boolean isTokenExpired(String token) {
        LocalDateTime expiration = extractExpirationAsLocalDateTime(token);
        return expiration.isBefore(LocalDateTime.now());
    }

    public Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    public LocalDateTime extractExpirationAsLocalDateTime(String token) {
        Date expirationDate = extractExpiration(token);
        return expirationDate.toInstant()
                .atZone(ZoneId.systemDefault())
                .toLocalDateTime();
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(signingKey)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }
}
```

---

## Bước 4: Tạo RefreshTokenService

📁 `src/main/java/fa/training/restapi/sercurity/RefreshTokenService.java`

```java
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

    // Refresh token có hiệu lực 7 ngày
    private static final long REFRESH_TOKEN_EXPIRATION_DAYS = 7;

    /**
     * Tạo refresh token mới cho user.
     * Nếu user đã có refresh token cũ → xóa đi trước.
     */
    @Transactional
    public RefreshToken createRefreshToken(String username) {
        User user = userRepository.findByUserName(username)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED));

        // Xóa refresh token cũ của user (nếu có)
        refreshTokenRepository.deleteByUser(user);

        RefreshToken refreshToken = RefreshToken.builder()
                .token(UUID.randomUUID().toString())
                .user(user)
                .expiryDate(LocalDateTime.now().plusDays(REFRESH_TOKEN_EXPIRATION_DAYS))
                .build();

        return refreshTokenRepository.save(refreshToken);
    }

    /**
     * Tìm refresh token theo giá trị token string.
     */
    public RefreshToken findByToken(String token) {
        return refreshTokenRepository.findByToken(token)
                .orElseThrow(() -> new AppException(ErrorCode.UNAUTHENTICATED,
                        "Refresh token không hợp lệ"));
    }

    /**
     * Xác minh refresh token chưa hết hạn.
     * Nếu hết hạn → xóa khỏi DB và throw exception.
     */
    @Transactional
    public RefreshToken verifyExpiration(RefreshToken token) {
        if (token.isExpired()) {
            refreshTokenRepository.delete(token);
            throw new AppException(ErrorCode.UNAUTHENTICATED,
                    "Refresh token đã hết hạn. Vui lòng đăng nhập lại.");
        }
        return token;
    }

    /**
     * Xóa tất cả refresh token của user (dùng khi logout).
     */
    @Transactional
    public void deleteByUser(User user) {
        refreshTokenRepository.deleteByUser(user);
    }
}
```

---

## Bước 5: Tạo CookieService

📁 `src/main/java/fa/training/restapi/sercurity/CookieService.java`

```java
package fa.training.restapi.sercurity;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Service;

@Service
public class CookieService {

    public static final String ACCESS_TOKEN_COOKIE = "access_token";
    public static final String REFRESH_TOKEN_COOKIE = "refresh_token";

    // Access token: 15 phút = 900 giây
    private static final int ACCESS_TOKEN_MAX_AGE = 15 * 60;

    // Refresh token: 7 ngày = 604800 giây
    private static final int REFRESH_TOKEN_MAX_AGE = 7 * 24 * 60 * 60;

    /**
     * Tạo cookie chứa Access Token
     */
    public void addAccessTokenCookie(HttpServletResponse response, String token) {
        Cookie cookie = new Cookie(ACCESS_TOKEN_COOKIE, token);
        cookie.setHttpOnly(true);   // JS không đọc được → chống XSS
        cookie.setSecure(false);    // Đổi thành true khi deploy HTTPS
        cookie.setPath("/");        // Gửi kèm mọi request
        cookie.setMaxAge(ACCESS_TOKEN_MAX_AGE);
        response.addCookie(cookie);
    }

    /**
     * Tạo cookie chứa Refresh Token
     */
    public void addRefreshTokenCookie(HttpServletResponse response, String token) {
        Cookie cookie = new Cookie(REFRESH_TOKEN_COOKIE, token);
        cookie.setHttpOnly(true);
        cookie.setSecure(false);     // Đổi thành true khi deploy HTTPS
        cookie.setPath("/api/auth"); // Chỉ gửi kèm các request tới /api/auth
        cookie.setMaxAge(REFRESH_TOKEN_MAX_AGE);
        response.addCookie(cookie);
    }

    /**
     * Xóa cả 2 cookie (dùng khi logout)
     */
    public void clearTokenCookies(HttpServletResponse response) {
        Cookie accessCookie = new Cookie(ACCESS_TOKEN_COOKIE, "");
        accessCookie.setHttpOnly(true);
        accessCookie.setPath("/");
        accessCookie.setMaxAge(0);  // MaxAge=0 → xóa cookie ngay
        response.addCookie(accessCookie);

        Cookie refreshCookie = new Cookie(REFRESH_TOKEN_COOKIE, "");
        refreshCookie.setHttpOnly(true);
        refreshCookie.setPath("/api/auth");
        refreshCookie.setMaxAge(0);
        response.addCookie(refreshCookie);
    }

    /**
     * Đọc giá trị cookie từ request theo tên
     */
    public String extractTokenFromCookie(HttpServletRequest request, String cookieName) {
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (cookieName.equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }
        return null;
    }
}
```

---

## Bước 6: Sửa AuthService

📁 `src/main/java/fa/training/restapi/service/AuthService.java`

**Thay đổi chính:**
- Login trả về access token string (không còn trả `AuthenticationResponse` trực tiếp)
- Tạo refresh token khi login
- Logout xóa refresh token khỏi DB

```java
package fa.training.restapi.service;

import fa.training.restapi.dto.request.LoginRequest;
import fa.training.restapi.dto.request.RegisterRequest;
import fa.training.restapi.entity.DefaultRoles;
import fa.training.restapi.entity.RefreshToken;
import fa.training.restapi.entity.Role;
import fa.training.restapi.entity.User;
import fa.training.restapi.exception.AppException;
import fa.training.restapi.exception.ErrorCode;
import fa.training.restapi.repository.RoleRepository;
import fa.training.restapi.repository.UserRepository;
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

    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final PasswordEncoder passwordEncoder;

    /**
     * Login: xác thực → tạo access token + refresh token
     * @return access token string (refresh token được lưu DB)
     */
    public record LoginResult(String accessToken, RefreshToken refreshToken) {}

    @Transactional
    public LoginResult login(LoginRequest request) {
        log.info("Processing authentication for username: {}", request.getUsername());

        var user = userRepository.findByUserName(request.getUsername())
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED));

        boolean authenticated = passwordEncoder.matches(request.getPassword(), user.getPassWord());
        if (!authenticated) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }

        // Tạo Access Token (JWT)
        String accessToken = jwtService.generateAccessToken(new CustomUserDetails(user));

        // Tạo Refresh Token (UUID, lưu DB)
        RefreshToken refreshToken = refreshTokenService.createRefreshToken(user.getUserName());

        return new LoginResult(accessToken, refreshToken);
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

    /**
     * Refresh: dùng refresh token cũ để tạo access token mới
     */
    @Transactional
    public String refreshAccessToken(String refreshTokenStr) {
        RefreshToken refreshToken = refreshTokenService.findByToken(refreshTokenStr);
        refreshTokenService.verifyExpiration(refreshToken);

        User user = refreshToken.getUser();
        return jwtService.generateAccessToken(new CustomUserDetails(user));
    }

    /**
     * Logout: xóa refresh token khỏi DB + clear security context
     */
    @Transactional
    public void logout(String username) {
        User user = userRepository.findByUserName(username)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED));
        refreshTokenService.deleteByUser(user);
        SecurityContextHolder.clearContext();
    }
}
```

---

## Bước 7: Sửa AuthController — Lưu Token Vào Cookie

📁 `src/main/java/fa/training/restapi/controller/AuthController.java`

```java
package fa.training.restapi.controller;

import fa.training.restapi.dto.request.LoginRequest;
import fa.training.restapi.dto.request.RegisterRequest;
import fa.training.restapi.dto.response.ApiResponse;
import fa.training.restapi.sercurity.CookieService;
import fa.training.restapi.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final CookieService cookieService;

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<Void>> login(@Valid @RequestBody LoginRequest request,
                                                    HttpServletResponse response) {
        AuthService.LoginResult result = authService.login(request);

        // Lưu token vào Cookie HttpOnly
        cookieService.addAccessTokenCookie(response, result.accessToken());
        cookieService.addRefreshTokenCookie(response, result.refreshToken().getToken());

        ApiResponse<Void> responseBody = ApiResponse.<Void>builder()
                .success(true)
                .message("Đăng nhập thành công")
                .build();
        return ResponseEntity.ok(responseBody);
    }

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<Void>> register(@Valid @RequestBody RegisterRequest request) {
        authService.register(request);

        ApiResponse<Void> responseBody = ApiResponse.<Void>builder()
                .success(true)
                .message("Đăng ký thành công")
                .build();
        return ResponseEntity.status(HttpStatus.CREATED).body(responseBody);
    }

    /**
     * Refresh: Đọc refresh_token từ Cookie → tạo access_token mới → Set-Cookie lại
     */
    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<Void>> refresh(HttpServletRequest request,
                                                      HttpServletResponse response) {
        String refreshToken = cookieService.extractTokenFromCookie(
                request, CookieService.REFRESH_TOKEN_COOKIE);

        if (refreshToken == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.<Void>builder()
                            .success(false)
                            .message("Không tìm thấy refresh token")
                            .build());
        }

        String newAccessToken = authService.refreshAccessToken(refreshToken);
        cookieService.addAccessTokenCookie(response, newAccessToken);

        ApiResponse<Void> responseBody = ApiResponse.<Void>builder()
                .success(true)
                .message("Token đã được làm mới")
                .build();
        return ResponseEntity.ok(responseBody);
    }

    /**
     * Logout: Xóa refresh token khỏi DB + Xóa cả 2 cookie
     */
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(HttpServletResponse response) {
        // Lấy username từ security context
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getName() != null) {
            authService.logout(authentication.getName());
        }

        // Xóa cookie
        cookieService.clearTokenCookies(response);

        ApiResponse<Void> responseBody = ApiResponse.<Void>builder()
                .success(true)
                .message("Đăng xuất thành công")
                .build();
        return ResponseEntity.ok(responseBody);
    }
}
```

---

## Bước 8: Sửa JwtAuthenticationFilter — Đọc Token Từ Cookie

📁 `src/main/java/fa/training/restapi/sercurity/JwtAuthenticationFilter.java`

**Thay đổi chính:** Ưu tiên đọc token từ Cookie `access_token`, fallback sang header `Authorization`.

```java
package fa.training.restapi.sercurity;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String jwt = null;

        // ===== THAY ĐỔI: Ưu tiên đọc từ Cookie =====
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if ("access_token".equals(cookie.getName())) {
                    jwt = cookie.getValue();
                    break;
                }
            }
        }

        // Fallback: đọc từ header Authorization (để Swagger vẫn hoạt động)
        if (jwt == null) {
            final String authenHeader = request.getHeader("Authorization");
            if (authenHeader != null && authenHeader.startsWith("Bearer ")) {
                jwt = authenHeader.substring(7);
            }
        }

        // Nếu không tìm thấy token ở cả 2 nơi → bỏ qua
        if (jwt == null) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            String username = jwtService.extractUsername(jwt);
            if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                UserDetails userDetails = this.userDetailsService.loadUserByUsername(username);
                if (jwtService.isTokenValid(jwt, userDetails)) {
                    UsernamePasswordAuthenticationToken authToken =
                            new UsernamePasswordAuthenticationToken(
                                    userDetails, null, userDetails.getAuthorities());
                    authToken.setDetails(
                            new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                }
            }
        } catch (Exception e) {
            // Token không hợp lệ → bỏ qua, tiếp tục filter chain
            // Không throw exception để các endpoint public vẫn hoạt động
        }
        filterChain.doFilter(request, response);
    }
}
```

---

## Bước 9: Cập Nhật SecurityConfig

📁 `src/main/java/fa/training/restapi/config/SecurityConfig.java`

**Thay đổi chính:** Thêm `/api/auth/refresh` vào danh sách permitAll.

```java
// Trong method securityFilterChain, thêm endpoint refresh:
.requestMatchers(
        "/api/auth/**",       // ← đã bao gồm /api/auth/refresh
        "/swagger-ui/**",
        "/swagger-ui.html",
        "/v3/api-docs/**",
        "/api/health/**",
        "/api/courses/**",
        "/api/roles/**")
.permitAll()
```

> Vì `/api/auth/**` đã bao gồm `/api/auth/refresh`, bạn **không cần** thay đổi gì thêm ở `SecurityConfig`. Chỉ cần đảm bảo rằng pattern `/api/auth/**` vẫn còn trong danh sách `permitAll()`.

---

## Bước 10: Sửa AuthenticationResponse

📁 `src/main/java/fa/training/restapi/dto/response/AuthenticationResponse.java`

Vì token giờ được lưu vào Cookie (không trả về trong body nữa), bạn có thể:
- **Giữ nguyên** nếu muốn backward compatible
- **Hoặc** xóa class này nếu không còn dùng ở đâu khác

---

## Luồng Hoạt Động

```
┌─────────────────────────────────────────────────────────────────┐
│                        LUỒNG LOGIN                              │
├─────────────────────────────────────────────────────────────────┤
│ 1. Client gửi POST /api/auth/login {username, password}        │
│ 2. AuthService xác thực username/password                       │
│ 3. Tạo Access Token (JWT, 15 phút)                             │
│ 4. Tạo Refresh Token (UUID, 7 ngày) → lưu vào DB              │
│ 5. Set-Cookie: access_token + refresh_token (HttpOnly)         │
│ 6. Response: { success: true, message: "Đăng nhập thành công" }│
└─────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│                     LUỒNG GỌI API                               │
├─────────────────────────────────────────────────────────────────┤
│ 1. Browser tự đính kèm Cookie access_token                     │
│ 2. JwtAuthenticationFilter đọc Cookie → extract username        │
│ 3. Xác thực JWT → set SecurityContext                           │
│ 4. Controller xử lý request bình thường                        │
└─────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│                    LUỒNG REFRESH TOKEN                          │
├─────────────────────────────────────────────────────────────────┤
│ 1. Access token hết hạn → API trả về 401                       │
│ 2. Client gửi POST /api/auth/refresh (Cookie tự đính kèm)     │
│ 3. Server kiểm tra refresh_token trong DB                       │
│ 4. Nếu hợp lệ → tạo access_token mới → Set-Cookie lại        │
│ 5. Client retry request ban đầu                                │
└─────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│                      LUỒNG LOGOUT                               │
├─────────────────────────────────────────────────────────────────┤
│ 1. Client gửi POST /api/auth/logout                            │
│ 2. Server xóa refresh_token khỏi DB                            │
│ 3. Server xóa cả 2 Cookie (MaxAge=0)                           │
│ 4. Clear SecurityContext                                        │
└─────────────────────────────────────────────────────────────────┘
```

---

## Test Trên Swagger/Postman

### Test Login
```
POST http://localhost:8080/api/auth/login
Content-Type: application/json

{
  "username": "admin",
  "password": "admin123"
}
```
→ Kiểm tra Response Headers có `Set-Cookie: access_token=...` và `Set-Cookie: refresh_token=...`

### Test Gọi API có xác thực
- Sau khi login, Cookie tự đính kèm (trên Postman bật "Save cookies")
- Hoặc trên Swagger, copy access_token và dán vào Authorize → `Bearer <token>`

### Test Refresh Token
```
POST http://localhost:8080/api/auth/refresh
```
→ Cookie `refresh_token` tự đính kèm → Response có `Set-Cookie: access_token=<new_token>`

### Test Logout
```
POST http://localhost:8080/api/auth/logout
```
→ Cả 2 Cookie bị xóa, refresh token bị xóa khỏi DB

---

> **💡 Lưu ý quan trọng:**
> - Khi deploy lên production, đặt `cookie.setSecure(true)` để Cookie chỉ gửi qua HTTPS
> - Có thể thêm thuộc tính `SameSite=Strict` hoặc `SameSite=Lax` để chống CSRF
> - BlackListService hiện tại có thể xóa bỏ vì refresh token trong DB đã thay thế vai trò thu hồi token

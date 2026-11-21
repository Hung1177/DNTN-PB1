package vn.haui.o2ofood.security;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import vn.haui.o2ofood.common.ApiResponse;
import vn.haui.o2ofood.common.BusinessException;
import vn.haui.o2ofood.common.ErrorCode;
import vn.haui.o2ofood.common.TimeProvider;

import java.util.Set;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;
    private final CustomUserDetailsService userDetailsService;
    private final TimeProvider timeProvider;

    public AuthController(AuthenticationManager authenticationManager,
                          JwtTokenProvider jwtTokenProvider,
                          CustomUserDetailsService userDetailsService,
                          TimeProvider timeProvider) {
        this.authenticationManager = authenticationManager;
        this.jwtTokenProvider = jwtTokenProvider;
        this.userDetailsService = userDetailsService;
        this.timeProvider = timeProvider;
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthTokenResponse>> login(@Valid @RequestBody LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.email(), request.password())
        );
        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        String accessToken = jwtTokenProvider.generateAccessToken(principal);
        String refreshToken = jwtTokenProvider.generateRefreshToken(principal);

        AuthTokenResponse tokenResponse = new AuthTokenResponse(
                accessToken,
                refreshToken,
                "Bearer",
                jwtTokenProvider.getAccessExpirationMs() / 1000,
                new UserProfileResponse(
                        principal.getId(),
                        principal.getEmail(),
                        principal.getFullName(),
                        principal.getRoleCodes()
                )
        );
        return ResponseEntity.ok(ApiResponse.ok("Đăng nhập thành công", tokenResponse, timeProvider.now()));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<AuthTokenResponse>> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        if (!jwtTokenProvider.validateToken(request.refreshToken(), "REFRESH")) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED_ACCESS, "Refresh token không hợp lệ hoặc đã hết hạn");
        }
        Long userId = Long.parseLong(jwtTokenProvider.parseClaims(request.refreshToken()).getSubject());
        UserPrincipal principal = userDetailsService.loadUserById(userId);
        String newAccessToken = jwtTokenProvider.generateAccessToken(principal);
        String newRefreshToken = jwtTokenProvider.generateRefreshToken(principal);

        AuthTokenResponse tokenResponse = new AuthTokenResponse(
                newAccessToken,
                newRefreshToken,
                "Bearer",
                jwtTokenProvider.getAccessExpirationMs() / 1000,
                new UserProfileResponse(
                        principal.getId(),
                        principal.getEmail(),
                        principal.getFullName(),
                        principal.getRoleCodes()
                )
        );
        return ResponseEntity.ok(ApiResponse.ok("Làm mới phiên đăng nhập thành công", tokenResponse, timeProvider.now()));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserProfileResponse>> me(@AuthenticationPrincipal UserPrincipal principal) {
        if (principal == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED_ACCESS);
        }
        UserProfileResponse profile = new UserProfileResponse(
                principal.getId(),
                principal.getEmail(),
                principal.getFullName(),
                principal.getRoleCodes()
        );
        return ResponseEntity.ok(ApiResponse.ok(profile, timeProvider.now()));
    }

    public record LoginRequest(
            @NotBlank(message = "Email không được để trống")
            @Email(message = "Định dạng email không hợp lệ")
            String email,
            @NotBlank(message = "Mật khẩu không được để trống")
            String password
    ) {
    }

    public record RefreshTokenRequest(
            @NotBlank(message = "Refresh token không được để trống")
            String refreshToken
    ) {
    }

    public record UserProfileResponse(
            Long userId,
            String email,
            String fullName,
            Set<String> roles
    ) {
    }

    public record AuthTokenResponse(
            String accessToken,
            String refreshToken,
            String tokenType,
            long expiresInSeconds,
            UserProfileResponse user
    ) {
    }
}

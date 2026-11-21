package vn.haui.o2ofood.security;

/**
 * Định nghĩa các hằng số Role chuẩn hóa (Zero-Trust UI).
 * Cấu hình chi tiết JwtAuthenticationFilter và OwnershipSecurityService sẽ được triển khai ở Phase 2.
 */
public final class SecurityConstants {

    public static final String ROLE_CUSTOMER = "ROLE_CUSTOMER";
    public static final String ROLE_WAREHOUSE = "ROLE_WAREHOUSE";
    public static final String ROLE_DISPATCHER = "ROLE_DISPATCHER";
    public static final String ROLE_DRIVER = "ROLE_DRIVER";
    public static final String ROLE_AGENT = "ROLE_AGENT";
    public static final String ROLE_ADMIN = "ROLE_ADMIN";

    private SecurityConstants() {
    }
}

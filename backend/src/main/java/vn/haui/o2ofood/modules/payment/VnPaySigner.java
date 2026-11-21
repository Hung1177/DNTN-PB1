package vn.haui.o2ofood.modules.payment;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import vn.haui.o2ofood.common.TimeProvider;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.TreeMap;

/**
 * Bộ ký và xác thực chữ ký VNPAY Sandbox (HMAC-SHA512) theo chuẩn VNPAY v2.1.0.
 */
@Component
public class VnPaySigner {

    private static final DateTimeFormatter VNP_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final String tmnCode;
    private final String hashSecret;
    private final String payUrl;
    private final String returnUrl;

    public VnPaySigner(
            @Value("${app.vnpay.tmn-code:DEMO_O2OFOOD}") String tmnCode,
            @Value("${app.vnpay.hash-secret:VNPAYSANDBOXSECRETKEY2026O2OFOODPICKUP}") String hashSecret,
            @Value("${app.vnpay.pay-url:https://sandbox.vnpayment.vn/paymentv2/vpcpay.html}") String payUrl,
            @Value("${app.vnpay.return-url:http://localhost:5173/payment/vnpay-return}") String returnUrl) {
        this.tmnCode = tmnCode;
        this.hashSecret = hashSecret;
        this.payUrl = payUrl;
        this.returnUrl = returnUrl;
    }

    public String buildPaymentUrl(String txnRef,
                                  BigDecimal amountVnd,
                                  String orderInfo,
                                  String clientIp,
                                  OffsetDateTime createdAt,
                                  OffsetDateTime expiresAt) {
        long vnpAmount = amountVnd.multiply(BigDecimal.valueOf(100)).longValue();
        TreeMap<String, String> vnpParams = new TreeMap<>();
        vnpParams.put("vnp_Version", "2.1.0");
        vnpParams.put("vnp_Command", "pay");
        vnpParams.put("vnp_TmnCode", tmnCode);
        vnpParams.put("vnp_Amount", String.valueOf(vnpAmount));
        vnpParams.put("vnp_CurrCode", "VND");
        vnpParams.put("vnp_TxnRef", txnRef);
        vnpParams.put("vnp_OrderInfo", orderInfo);
        vnpParams.put("vnp_OrderType", "food_pickup");
        vnpParams.put("vnp_Locale", "vn");
        vnpParams.put("vnp_ReturnUrl", returnUrl);
        vnpParams.put("vnp_IpAddr", clientIp != null && !clientIp.isBlank() ? clientIp : "127.0.0.1");
        vnpParams.put("vnp_CreateDate", createdAt.atZoneSameInstant(TimeProvider.VN_ZONE).format(VNP_DATE_FORMAT));
        vnpParams.put("vnp_ExpireDate", expiresAt.atZoneSameInstant(TimeProvider.VN_ZONE).format(VNP_DATE_FORMAT));

        String hashData = buildHashData(vnpParams);
        String secureHash = hmacSha512(hashSecret, hashData);
        return payUrl + "?" + hashData + "&vnp_SecureHash=" + secureHash;
    }

    public String signParams(Map<String, String> params) {
        TreeMap<String, String> sorted = new TreeMap<>();
        for (Map.Entry<String, String> entry : params.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            if (key != null
                    && value != null
                    && !value.isEmpty()
                    && !"vnp_SecureHash".equalsIgnoreCase(key)
                    && !"vnp_SecureHashType".equalsIgnoreCase(key)) {
                sorted.put(key, value);
            }
        }
        String hashData = buildHashData(sorted);
        return hmacSha512(hashSecret, hashData);
    }

    public boolean verifySignature(Map<String, String> params) {
        if (params == null || params.isEmpty()) {
            return false;
        }
        String receivedHash = params.get("vnp_SecureHash");
        if (receivedHash == null || receivedHash.isBlank()) {
            return false;
        }
        String computedHash = signParams(params);
        return computedHash.equalsIgnoreCase(receivedHash.trim());
    }

    private String buildHashData(TreeMap<String, String> sortedParams) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry : sortedParams.entrySet()) {
            if (!sb.isEmpty()) {
                sb.append('&');
            }
            sb.append(URLEncoder.encode(entry.getKey(), StandardCharsets.US_ASCII))
                    .append('=')
                    .append(URLEncoder.encode(entry.getValue(), StandardCharsets.US_ASCII));
        }
        return sb.toString();
    }

    public static String hmacSha512(String key, String data) {
        try {
            Mac hmac512 = Mac.getInstance("HmacSHA512");
            SecretKeySpec secretKeySpec = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA512");
            hmac512.init(secretKeySpec);
            byte[] bytes = hmac512.doFinal(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder hash = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                hash.append(String.format("%02x", b));
            }
            return hash.toString();
        } catch (Exception e) {
            throw new IllegalStateException("Không thể khởi tạo chữ ký HMAC-SHA512 cho VNPAY", e);
        }
    }
}

package com.loopers.support.auth;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * 어드민 API 인증. /api-admin/** 경로 적용 (02 §0).
 * X-Loopers-Ldap(식별자) + X-Loopers-Admin-Signature(HMAC-SHA256 서명) 검증. 실패 시 401.
 * 시크릿: loopers.admin.auth.signing-secret (또는 LOOPERS_ADMIN_SIGNING_SECRET). 미설정 시 기본값(운영에서 변경 권장).
 */
@Component
public class AdminAuthInterceptor implements HandlerInterceptor {

    private static final String HMAC_SHA256 = "HmacSHA256";

    /** 관리자 식별자 헤더. */
    public static final String HEADER_LDAP = "X-Loopers-Ldap";
    /** 서명 헤더 (HMAC-SHA256 hex). */
    public static final String HEADER_ADMIN_SIGNATURE = "X-Loopers-Admin-Signature";

    private final String signingSecret;

    public AdminAuthInterceptor(
        @Value("${loopers.admin.auth.signing-secret:change-me-in-production}") String signingSecret
    ) {
        this.signingSecret = signingSecret;
    }

    /** LDAP 식별자에 대한 HMAC-SHA256 서명(hex) 생성. 에이전트·테스트에서 유효 헤더 만들 때 사용. */
    public static String sign(String ldap, String secret) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_SHA256));
            byte[] hash = mac.doFinal(ldap.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalArgumentException("Admin auth signing failed", e);
        }
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String ldap = request.getHeader(HEADER_LDAP);
        String signature = request.getHeader(HEADER_ADMIN_SIGNATURE);
        if (!verify(ldap, signature)) {
            throw new CoreException(ErrorType.UNAUTHORIZED, "어드민 인증이 필요합니다.");
        }
        return true;
    }

    /** 상수 시간 비교로 서명 검증. */
    private boolean verify(String ldap, String providedSignature) {
        if (ldap == null || ldap.isBlank() || providedSignature == null || providedSignature.isBlank()) {
            return false;
        }
        String expectedHex = sign(ldap, signingSecret);
        byte[] expectedBytes;
        byte[] providedBytes;
        try {
            expectedBytes = HexFormat.of().parseHex(expectedHex);
            providedBytes = HexFormat.of().parseHex(providedSignature);
        } catch (IllegalArgumentException e) {
            return false;
        }
        if (expectedBytes.length != providedBytes.length) {
            return false;
        }
        return java.security.MessageDigest.isEqual(expectedBytes, providedBytes);
    }
}

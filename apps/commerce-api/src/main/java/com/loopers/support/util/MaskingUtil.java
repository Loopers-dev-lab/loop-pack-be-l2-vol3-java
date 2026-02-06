package com.loopers.support.util;

public class MaskingUtil {

    private static final char MASK_CHAR = '*';

    private MaskingUtil() {}

    /**
     * 마지막 N자를 마스킹한다.
     * 예: maskLast("홍길동", 1) → "홍길*"
     */
    public static String maskLast(String value, int count) {
        if (value == null) {
            return null;
        }
        if (value.isEmpty()) {
            return value;
        }

        int length = value.length();
        int maskStart = Math.max(0, length - count);
        int maskCount = length - maskStart;

        return value.substring(0, maskStart) + String.valueOf(MASK_CHAR).repeat(maskCount);
    }

    /**
     * 이메일의 로컬 파트(@ 앞부분)를 마스킹한다.
     * 앞 2자만 남기고 나머지 마스킹.
     * 예: maskEmail("testuser@example.com") → "te******@example.com"
     */
    public static String maskEmail(String email) {
        if (email == null) {
            return null;
        }

        int atIndex = email.indexOf('@');
        if (atIndex <= 2) {
            return email;
        }

        String localPart = email.substring(0, atIndex);
        String domain = email.substring(atIndex);

        String visiblePart = localPart.substring(0, 2);
        String maskedPart = String.valueOf(MASK_CHAR).repeat(localPart.length() - 2);

        return visiblePart + maskedPart + domain;
    }
}

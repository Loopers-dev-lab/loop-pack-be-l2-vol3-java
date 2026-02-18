package com.loopers.support;

public final class MaskingUtils {

    private MaskingUtils() {}

    public static String maskLastCharacter(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        return value.substring(0, value.length() - 1) + "*";
    }
}

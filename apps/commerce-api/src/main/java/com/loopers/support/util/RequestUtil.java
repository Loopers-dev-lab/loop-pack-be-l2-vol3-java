package com.loopers.support.util;

import jakarta.servlet.http.HttpServletRequest;

public class RequestUtil {

    private RequestUtil() {}

    public static String getClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].strip();
        }
        return request.getRemoteAddr();
    }
}

package com.loopers.interfaces.api.common;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.UUID;

@Component
public class AnonymousCookieInterceptor implements HandlerInterceptor {

    public static final String COOKIE_NAME = "anonymous_id";
    public static final String REQUEST_ATTRIBUTE = "anonymous_id";
    public static final int MAX_AGE_SECONDS = 31536000;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String anonymousId = readCookie(request);
        if (anonymousId == null) {
            anonymousId = UUID.randomUUID().toString();
            writeCookie(response, anonymousId);
        }
        request.setAttribute(REQUEST_ATTRIBUTE, anonymousId);
        return true;
    }

    private String readCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (COOKIE_NAME.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    private void writeCookie(HttpServletResponse response, String anonymousId) {
        String header = COOKIE_NAME + "=" + anonymousId
                + "; Max-Age=" + MAX_AGE_SECONDS
                + "; Path=/"
                + "; HttpOnly"
                + "; SameSite=Lax";
        response.addHeader("Set-Cookie", header);
    }
}

package com.loopers.interfaces.api.common;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class AnonymousCookieInterceptorTest {

    private final AnonymousCookieInterceptor interceptor = new AnonymousCookieInterceptor();

    @Test
    @DisplayName("cookie에 anonymous_id가 있으면 그대로 request attribute에 저장하고 Set-Cookie 헤더는 추가하지 않는다")
    void existingCookie_isReusedWithoutSetCookie() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("anonymous_id", "existing-uuid"));
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean result = interceptor.preHandle(request, response, new Object());

        assertThat(result).isTrue();
        assertThat(request.getAttribute("anonymous_id")).isEqualTo("existing-uuid");
        assertThat(response.getHeader("Set-Cookie")).isNull();
    }

    @Test
    @DisplayName("cookie가 없으면 UUID를 발급해 request attribute에 저장하고 Set-Cookie 헤더를 추가한다")
    void noCookie_issuesNewIdAndSetsCookie() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean result = interceptor.preHandle(request, response, new Object());

        assertThat(result).isTrue();
        String anonymousId = (String) request.getAttribute("anonymous_id");
        assertThat(anonymousId).isNotBlank();

        String setCookie = response.getHeader("Set-Cookie");
        assertThat(setCookie).isNotNull();
        assertThat(setCookie).contains("anonymous_id=" + anonymousId);
        assertThat(setCookie).contains("Max-Age=31536000");
        assertThat(setCookie).contains("HttpOnly");
        assertThat(setCookie).contains("SameSite=Lax");
        assertThat(setCookie).contains("Path=/");
    }
}

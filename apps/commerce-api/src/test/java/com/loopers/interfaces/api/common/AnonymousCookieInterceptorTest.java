package com.loopers.interfaces.api.common;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class AnonymousCookieInterceptorTest {

    private final AnonymousCookieInterceptor interceptor = new AnonymousCookieInterceptor();

    @Nested
    class preHandle {

        @Test
        void 쿠키에_anonymous_id가_있으면_그대로_attribute에_저장하고_Set_Cookie는_추가하지_않는다() {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setCookies(new Cookie("anonymous_id", "existing-uuid"));
            MockHttpServletResponse response = new MockHttpServletResponse();

            boolean result = interceptor.preHandle(request, response, new Object());

            assertThat(result).isTrue();
            assertThat(request.getAttribute("anonymous_id")).isEqualTo("existing-uuid");
            assertThat(response.getHeader("Set-Cookie")).isNull();
        }

        @Test
        void 쿠키가_없으면_UUID를_발급해_attribute에_저장하고_Set_Cookie를_추가한다() {
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
}

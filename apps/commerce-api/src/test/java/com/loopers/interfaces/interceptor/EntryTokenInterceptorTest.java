package com.loopers.interfaces.interceptor;

import com.loopers.application.queue.TokenService;
import com.loopers.domain.member.Member;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class EntryTokenInterceptorTest {

    @Mock
    private TokenService tokenService;

    private EntryTokenInterceptor interceptor;

    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        interceptor = new EntryTokenInterceptor(tokenService, new ObjectMapper());
        request = new MockHttpServletRequest("POST", "/api/v1/orders");
        response = new MockHttpServletResponse();
    }

    @Test
    void 유효한_토큰이_있으면_통과한다() throws Exception {
        Member member = mock(Member.class);
        given(member.getId()).willReturn(1L);
        request.setAttribute("authenticatedMember", member);
        given(tokenService.validate(1L)).willReturn(true);

        boolean result = interceptor.preHandle(request, response, new Object());

        assertThat(result).isTrue();
    }

    @Test
    void 토큰이_없으면_401을_반환한다() throws Exception {
        Member member = mock(Member.class);
        given(member.getId()).willReturn(1L);
        request.setAttribute("authenticatedMember", member);
        given(tokenService.validate(1L)).willReturn(false);

        boolean result = interceptor.preHandle(request, response, new Object());

        assertThat(result).isFalse();
        assertThat(response.getStatus()).isEqualTo(401);
    }

    @Test
    void 인증된_멤버가_없으면_401을_반환한다() throws Exception {
        boolean result = interceptor.preHandle(request, response, new Object());

        assertThat(result).isFalse();
        assertThat(response.getStatus()).isEqualTo(401);
    }
}

package com.loopers.config;

import com.loopers.application.queue.QueueTokenService;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class QueueTokenInterceptorTest {

    @InjectMocks
    private QueueTokenInterceptor queueTokenInterceptor;

    @Mock
    private QueueTokenService queueTokenService;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Test
    @DisplayName("유효한 헤더가 있으면 true를 반환한다")
    void preHandle_withValidHeaders_returnsTrue() throws Exception {
        // given
        given(request.getHeader("X-User-Id")).willReturn("1");
        given(request.getHeader("X-Queue-Token")).willReturn("tok_abc");
        given(request.getHeader("X-Event-Id")).willReturn("bf2024");

        // when
        boolean result = queueTokenInterceptor.preHandle(request, response, new Object());

        // then
        assertThat(result).isTrue();
        then(queueTokenService).should().validateToken("bf2024", 1L, "tok_abc");
    }

    @Test
    @DisplayName("X-User-Id 헤더가 없으면 UNAUTHORIZED 예외가 발생한다")
    void preHandle_withoutUserId_throwsUnauthorized() {
        // given
        given(request.getHeader("X-User-Id")).willReturn(null);

        // when
        CoreException exception = assertThrows(CoreException.class,
                () -> queueTokenInterceptor.preHandle(request, response, new Object()));

        // then
        assertThat(exception.getErrorType()).isEqualTo(ErrorType.UNAUTHORIZED);
    }

    @Test
    @DisplayName("X-Queue-Token 헤더가 없으면 UNAUTHORIZED 예외가 발생한다")
    void preHandle_withoutToken_throwsUnauthorized() {
        // given
        given(request.getHeader("X-User-Id")).willReturn("1");
        given(request.getHeader("X-Queue-Token")).willReturn(null);

        // when
        CoreException exception = assertThrows(CoreException.class,
                () -> queueTokenInterceptor.preHandle(request, response, new Object()));

        // then
        assertThat(exception.getErrorType()).isEqualTo(ErrorType.UNAUTHORIZED);
    }

    @Test
    @DisplayName("주문 성공 후 afterCompletion에서 토큰이 삭제된다")
    void afterCompletion_onSuccess_removesToken() {
        // given
        given(request.getAttribute("queue.eventId")).willReturn("bf2024");
        given(request.getAttribute("queue.userId")).willReturn(1L);
        given(response.getStatus()).willReturn(200);

        // when
        queueTokenInterceptor.afterCompletion(request, response, new Object(), null);

        // then
        then(queueTokenService).should().removeToken("bf2024", 1L);
    }

    @Test
    @DisplayName("주문 실패 시 afterCompletion에서 토큰을 삭제하지 않는다")
    void afterCompletion_onError_doesNotRemoveToken() {
        // given
        given(response.getStatus()).willReturn(500);

        // when
        queueTokenInterceptor.afterCompletion(request, response, new Object(), null);

        // then
        verify(queueTokenService, never()).removeToken("bf2024", 1L);
    }

    @Test
    @DisplayName("예외 발생 시 afterCompletion에서 토큰을 삭제하지 않는다")
    void afterCompletion_withException_doesNotRemoveToken() {
        // when
        queueTokenInterceptor.afterCompletion(request, response, new Object(), new RuntimeException("error"));

        // then
        verify(queueTokenService, never()).removeToken("bf2024", 1L);
    }
}

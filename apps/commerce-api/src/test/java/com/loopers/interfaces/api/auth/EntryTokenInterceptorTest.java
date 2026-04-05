package com.loopers.interfaces.api.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.loopers.support.entry.EntryTokenStore;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.support.queue.QueueProperties;

@ExtendWith(MockitoExtension.class)
class EntryTokenInterceptorTest {

    private static final String HEADER_ENTRY_TOKEN = "X-Entry-Token";
    private static final String USER_ID_ATTRIBUTE = "userId";

    @Mock
    private EntryTokenStore entryTokenStore;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    private EntryTokenInterceptor interceptor;

    @DisplayName("preHandle을 수행할 때,")
    @Nested
    class PreHandle {

        @DisplayName("대기열이 비활성 상태이면, 토큰 검증 없이 통과한다.")
        @Test
        void returnsTrue_whenQueueDisabled() {
            // arrange
            interceptor = new EntryTokenInterceptor(entryTokenStore, new QueueProperties(false, 10, 1000));

            // act
            boolean result = interceptor.preHandle(request, response, new Object());

            // assert
            assertThat(result).isTrue();
            verifyNoInteractions(entryTokenStore);
        }

        @DisplayName("POST가 아닌 요청이면, 토큰 검증 없이 통과한다.")
        @Test
        void returnsTrue_whenNotPostRequest() {
            // arrange
            interceptor = new EntryTokenInterceptor(entryTokenStore, new QueueProperties(true, 10, 1000));
            given(request.getMethod()).willReturn("GET");

            // act
            boolean result = interceptor.preHandle(request, response, new Object());

            // assert
            assertThat(result).isTrue();
            verifyNoInteractions(entryTokenStore);
        }

        @DisplayName("유효한 입장 토큰이면, 검증 후 통과한다.")
        @Test
        void returnsTrue_whenValidEntryToken() {
            // arrange
            interceptor = new EntryTokenInterceptor(entryTokenStore, new QueueProperties(true, 10, 1000));
            given(request.getMethod()).willReturn("POST");
            given(request.getHeader(HEADER_ENTRY_TOKEN)).willReturn("valid-token");
            given(request.getAttribute(USER_ID_ATTRIBUTE)).willReturn(1L);

            // act
            boolean result = interceptor.preHandle(request, response, new Object());

            // assert
            assertThat(result).isTrue();
            verify(entryTokenStore).validate(1L, "valid-token");
        }

        @DisplayName("입장 토큰 헤더가 없으면, INVALID_ENTRY_TOKEN 예외가 발생한다.")
        @Test
        void throwsException_whenEntryTokenHeaderMissing() {
            // arrange
            interceptor = new EntryTokenInterceptor(entryTokenStore, new QueueProperties(true, 10, 1000));
            given(request.getMethod()).willReturn("POST");
            given(request.getHeader(HEADER_ENTRY_TOKEN)).willReturn(null);

            // act & assert
            assertThatThrownBy(() -> interceptor.preHandle(request, response, new Object()))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.INVALID_ENTRY_TOKEN));
        }

        @DisplayName("입장 토큰이 유효하지 않으면, INVALID_ENTRY_TOKEN 예외가 발생한다.")
        @Test
        void throwsException_whenEntryTokenInvalid() {
            // arrange
            interceptor = new EntryTokenInterceptor(entryTokenStore, new QueueProperties(true, 10, 1000));
            given(request.getMethod()).willReturn("POST");
            given(request.getHeader(HEADER_ENTRY_TOKEN)).willReturn("invalid-token");
            given(request.getAttribute(USER_ID_ATTRIBUTE)).willReturn(1L);
            org.mockito.BDDMockito.willThrow(new CoreException(ErrorType.INVALID_ENTRY_TOKEN))
                    .given(entryTokenStore).validate(1L, "invalid-token");

            // act & assert
            assertThatThrownBy(() -> interceptor.preHandle(request, response, new Object()))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.INVALID_ENTRY_TOKEN));
        }
    }
}

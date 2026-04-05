package com.loopers.interfaces.api.queue;

import com.loopers.domain.member.BirthDate;
import com.loopers.domain.member.Email;
import com.loopers.domain.member.LoginId;
import com.loopers.domain.member.Member;
import com.loopers.domain.member.MemberName;
import com.loopers.domain.queue.QueueService;
import com.loopers.domain.queue.QueueTokenService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// [단위 테스트 - Interfaces Layer]
//
// 테스트 대상: QueueTokenInterceptor
// 테스트 유형: 단위 테스트 (Mock: QueueService, QueueTokenService, HttpServletRequest/Response)
// 테스트 범위: 주문 API 진입 시 토큰 검증 및 사용 후 삭제 로직
//
// preHandle - 토큰 소모:
//   - POST + Flag ON + 토큰 있음 → GETDEL로 원자적 소모 후 통과
//   - POST + Flag ON + 토큰 없음/만료/loginMember null → 차단 (예외)
//   - POST + Flag OFF → 토큰 검증 자체를 수행하지 않고 통과
//   - GET/PUT/DELETE → 토큰 검증 없이 통과
//
// afterCompletion - 토큰 복구:
//   - POST + Flag ON + 토큰 소모됨 + 주문 성공(2xx) → 아무 작업 없음 (이미 소모됨)
//   - POST + Flag ON + 토큰 소모됨 + 주문 실패(4xx/5xx/예외) → 토큰 재발급 (재시도 기회 보장)
//   - POST + Flag OFF 또는 loginMember null → 복구 미수행
//   - GET → afterCompletion에서 아무 작업 없음
@ExtendWith(MockitoExtension.class)
@DisplayName("QueueTokenInterceptor 단위 테스트")
class QueueTokenInterceptorTest {

    @Mock
    private QueueService queueService;

    @Mock
    private QueueTokenService queueTokenService;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @InjectMocks
    private QueueTokenInterceptor interceptor;

    private Member createMemberWithId(Long id) {
        Member member = new Member(
                new LoginId("testuser" + id),
                "encodedPassword1!",
                new MemberName("테스터"),
                new Email("test" + id + "@example.com"),
                new BirthDate("19900101"));
        ReflectionTestUtils.setField(member, "id", id);
        return member;
    }

    @Nested
    @DisplayName("preHandle - 토큰 소모")
    class PreHandle {

        @Test
        @DisplayName("POST + flag ON + 토큰 있음 → GETDEL로 소모 후 통과")
        void post_flagOn_tokenExists_consumes_and_passes() {
            // given
            when(request.getMethod()).thenReturn("POST");
            Member member = createMemberWithId(1L);
            when(request.getAttribute("loginMember")).thenReturn(member);
            when(queueService.isQueueEnabled()).thenReturn(true);
            when(queueTokenService.consumeToken(1L)).thenReturn(Optional.of("token-value"));

            // when
            boolean result = interceptor.preHandle(request, response, new Object());

            // then
            assertThat(result).isTrue();
            verify(queueTokenService).consumeToken(1L);
            verify(request).setAttribute("tokenConsumed", true);
        }

        @Test
        @DisplayName("POST + flag ON + 토큰 없음 → 차단 (예외)")
        void post_flagOn_noToken_blocked() {
            // given
            when(request.getMethod()).thenReturn("POST");
            Member member = createMemberWithId(1L);
            when(request.getAttribute("loginMember")).thenReturn(member);
            when(queueService.isQueueEnabled()).thenReturn(true);
            when(queueTokenService.consumeToken(1L)).thenReturn(Optional.empty());

            // when & then
            assertThrows(Exception.class,
                    () -> interceptor.preHandle(request, response, new Object()));
        }

        @Test
        @DisplayName("POST + flag OFF → 토큰 소모 없이 통과")
        void post_flagOff_noToken_passes() {
            // given
            when(request.getMethod()).thenReturn("POST");
            when(queueService.isQueueEnabled()).thenReturn(false);

            // when
            boolean result = interceptor.preHandle(request, response, new Object());

            // then
            assertThat(result).isTrue();
            verify(queueTokenService, never()).consumeToken(anyLong());
        }

        @Test
        @DisplayName("POST + flag OFF → 토큰 검증 자체를 수행하지 않는다")
        void post_flagOff_skips_validation() {
            // given
            when(request.getMethod()).thenReturn("POST");
            when(queueService.isQueueEnabled()).thenReturn(false);

            // when
            interceptor.preHandle(request, response, new Object());

            // then
            verify(request, never()).getAttribute("loginMember");
            verify(queueTokenService, never()).consumeToken(anyLong());
        }

        @Test
        @DisplayName("POST + flag ON + 토큰 만료 → 차단")
        void post_flagOn_tokenExpired_blocked() {
            // given
            when(request.getMethod()).thenReturn("POST");
            Member member = createMemberWithId(1L);
            when(request.getAttribute("loginMember")).thenReturn(member);
            when(queueService.isQueueEnabled()).thenReturn(true);
            when(queueTokenService.consumeToken(1L)).thenReturn(Optional.empty());

            // when & then
            assertThrows(Exception.class,
                    () -> interceptor.preHandle(request, response, new Object()));
        }

        @Test
        @DisplayName("POST + flag ON + loginMember가 null이면 예외")
        void post_flagOn_noLoginMember_throws() {
            // given
            when(request.getMethod()).thenReturn("POST");
            when(queueService.isQueueEnabled()).thenReturn(true);
            when(request.getAttribute("loginMember")).thenReturn(null);

            // when & then
            assertThrows(Exception.class,
                    () -> interceptor.preHandle(request, response, new Object()));
        }

        @Test
        @DisplayName("GET 요청 → 토큰 검증 없이 통과")
        void get_request_passes_without_validation() {
            // given
            when(request.getMethod()).thenReturn("GET");

            // when
            boolean result = interceptor.preHandle(request, response, new Object());

            // then
            assertThat(result).isTrue();
            verify(queueService, never()).isQueueEnabled();
            verify(queueTokenService, never()).consumeToken(anyLong());
        }

        @Test
        @DisplayName("PUT 요청 → 토큰 검증 없이 통과")
        void put_request_passes_without_validation() {
            // given
            when(request.getMethod()).thenReturn("PUT");

            // when
            boolean result = interceptor.preHandle(request, response, new Object());

            // then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("DELETE 요청 → 토큰 검증 없이 통과")
        void delete_request_passes_without_validation() {
            // given
            when(request.getMethod()).thenReturn("DELETE");

            // when
            boolean result = interceptor.preHandle(request, response, new Object());

            // then
            assertThat(result).isTrue();
        }
    }

    @Nested
    @DisplayName("afterCompletion - 토큰 복구")
    class AfterCompletion {

        @Test
        @DisplayName("POST + flag ON + 토큰 소모됨 + 주문 성공(2xx) → 아무 작업 없음")
        void post_success_no_action() {
            // given
            when(request.getMethod()).thenReturn("POST");
            Member member = createMemberWithId(1L);
            when(request.getAttribute("queueEnabled")).thenReturn(true);
            when(request.getAttribute("tokenConsumed")).thenReturn(true);
            when(request.getAttribute("loginMember")).thenReturn(member);
            when(response.getStatus()).thenReturn(200);

            // when
            interceptor.afterCompletion(request, response, new Object(), null);

            // then: 성공 시 토큰 재발급하지 않음 (이미 preHandle에서 소모됨)
            verify(queueTokenService, never()).issueToken(anyLong());
        }

        @Test
        @DisplayName("POST + flag ON + 토큰 소모됨 + 주문 실패(4xx) → 토큰 재발급")
        void post_error_response_restores_token() {
            // given
            when(request.getMethod()).thenReturn("POST");
            Member member = createMemberWithId(1L);
            when(request.getAttribute("queueEnabled")).thenReturn(true);
            when(request.getAttribute("tokenConsumed")).thenReturn(true);
            when(request.getAttribute("loginMember")).thenReturn(member);
            when(response.getStatus()).thenReturn(400);
            when(queueTokenService.issueToken(1L)).thenReturn(Optional.of("restored-token"));

            // when
            interceptor.afterCompletion(request, response, new Object(), null);

            // then
            verify(queueTokenService).issueToken(1L);
        }

        @Test
        @DisplayName("POST + flag ON + 토큰 소모됨 + 주문 실패(5xx) → 토큰 재발급")
        void post_server_error_restores_token() {
            // given
            when(request.getMethod()).thenReturn("POST");
            Member member = createMemberWithId(1L);
            when(request.getAttribute("queueEnabled")).thenReturn(true);
            when(request.getAttribute("tokenConsumed")).thenReturn(true);
            when(request.getAttribute("loginMember")).thenReturn(member);
            when(response.getStatus()).thenReturn(500);
            when(queueTokenService.issueToken(1L)).thenReturn(Optional.of("restored-token"));

            // when
            interceptor.afterCompletion(request, response, new Object(), null);

            // then
            verify(queueTokenService).issueToken(1L);
        }

        @Test
        @DisplayName("POST + flag ON + 토큰 소모됨 + 예외 발생 → 토큰 재발급")
        void post_exception_restores_token() {
            // given
            when(request.getMethod()).thenReturn("POST");
            Member member = createMemberWithId(1L);
            when(request.getAttribute("queueEnabled")).thenReturn(true);
            when(request.getAttribute("tokenConsumed")).thenReturn(true);
            when(request.getAttribute("loginMember")).thenReturn(member);
            when(queueTokenService.issueToken(1L)).thenReturn(Optional.of("restored-token"));

            // when
            interceptor.afterCompletion(request, response, new Object(), new RuntimeException("주문 실패"));

            // then
            verify(queueTokenService).issueToken(1L);
        }

        @Test
        @DisplayName("POST + flag ON + 토큰 소모됨 + 주문 실패 + 재발급도 실패 → 예외 전파 없이 종료")
        void post_failure_restore_failure_does_not_propagate() {
            // given
            when(request.getMethod()).thenReturn("POST");
            Member member = createMemberWithId(1L);
            when(request.getAttribute("queueEnabled")).thenReturn(true);
            when(request.getAttribute("tokenConsumed")).thenReturn(true);
            when(request.getAttribute("loginMember")).thenReturn(member);
            when(response.getStatus()).thenReturn(500);
            when(queueTokenService.issueToken(1L)).thenThrow(new RuntimeException("Redis error"));

            // when & then: 재발급 실패해도 예외가 전파되지 않는다
            assertDoesNotThrow(() ->
                    interceptor.afterCompletion(request, response, new Object(), null));
        }

        @Test
        @DisplayName("POST + flag OFF → 토큰 복구하지 않음")
        void post_flagOff_no_restore() {
            // given
            when(request.getMethod()).thenReturn("POST");
            when(request.getAttribute("queueEnabled")).thenReturn(false);

            // when
            interceptor.afterCompletion(request, response, new Object(), null);

            // then
            verify(queueTokenService, never()).issueToken(anyLong());
        }

        @Test
        @DisplayName("POST + 토큰 소모되지 않았으면 복구하지 않음")
        void post_tokenNotConsumed_no_restore() {
            // given
            when(request.getMethod()).thenReturn("POST");
            when(request.getAttribute("queueEnabled")).thenReturn(true);
            when(request.getAttribute("tokenConsumed")).thenReturn(null);

            // when
            interceptor.afterCompletion(request, response, new Object(), null);

            // then
            verify(queueTokenService, never()).issueToken(anyLong());
        }

        @Test
        @DisplayName("POST + loginMember가 null이면 복구를 시도하지 않는다")
        void post_noLoginMember_no_restore() {
            // given
            when(request.getMethod()).thenReturn("POST");
            when(request.getAttribute("queueEnabled")).thenReturn(true);
            when(request.getAttribute("tokenConsumed")).thenReturn(true);
            when(request.getAttribute("loginMember")).thenReturn(null);

            // when
            assertDoesNotThrow(() ->
                    interceptor.afterCompletion(request, response, new Object(), null));

            // then
            verify(queueTokenService, never()).issueToken(anyLong());
        }

        @Test
        @DisplayName("GET 요청 → afterCompletion에서 아무 작업 없음")
        void get_request_no_action() {
            // given
            when(request.getMethod()).thenReturn("GET");

            // when
            interceptor.afterCompletion(request, response, new Object(), null);

            // then
            verify(queueService, never()).isQueueEnabled();
            verify(queueTokenService, never()).issueToken(anyLong());
        }
    }
}

package com.loopers.application.queue;

import com.loopers.domain.queue.QueueTokenRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class QueueTokenServiceTest {

    @InjectMocks
    private QueueTokenService queueTokenService;

    @Mock
    private QueueTokenRepository queueTokenRepository;

    @DisplayName("토큰 발급")
    @Nested
    class IssueToken {

        @DisplayName("UUID 토큰을 생성하고 TTL 300초로 저장한 뒤 토큰을 반환한다")
        @Test
        void issuesTokenWithUuidAndTtl() {
            // given
            String eventId = "event-1";
            Long userId = 1L;

            // when
            String token = queueTokenService.issueToken(eventId, userId);

            // then
            assertThat(token).isNotBlank();
            then(queueTokenRepository).should()
                    .issueToken(eq(eventId), eq(userId), anyString(), eq(300L));
        }
    }

    @DisplayName("토큰 정보 조회")
    @Nested
    class GetTokenInfo {

        @DisplayName("토큰이 존재하면 TokenInfo를 반환한다")
        @Test
        void returnsTokenInfoWhenTokenExists() {
            // given
            String eventId = "event-1";
            Long userId = 1L;
            given(queueTokenRepository.getToken(eventId, userId)).willReturn(Optional.of("test-token"));
            given(queueTokenRepository.getTokenTtl(eventId, userId)).willReturn(250L);

            // when
            Optional<QueueTokenService.TokenInfo> result = queueTokenService.getTokenInfo(eventId, userId);

            // then
            assertThat(result).isPresent();
            assertThat(result.get().token()).isEqualTo("test-token");
            assertThat(result.get().expiresIn()).isEqualTo(250L);
        }

        @DisplayName("토큰이 없으면 empty를 반환한다")
        @Test
        void returnsEmptyWhenNoToken() {
            // given
            String eventId = "event-1";
            Long userId = 1L;
            given(queueTokenRepository.getToken(eventId, userId)).willReturn(Optional.empty());

            // when
            Optional<QueueTokenService.TokenInfo> result = queueTokenService.getTokenInfo(eventId, userId);

            // then
            assertThat(result).isEmpty();
        }
    }

    @DisplayName("토큰 검증")
    @Nested
    class ValidateToken {

        @DisplayName("유효한 토큰이면 예외가 발생하지 않는다")
        @Test
        void doesNotThrowForValidToken() {
            // given
            String eventId = "event-1";
            Long userId = 1L;
            String token = "valid-token";
            given(queueTokenRepository.getToken(eventId, userId)).willReturn(Optional.of(token));

            // when & then
            queueTokenService.validateToken(eventId, userId, token);
        }

        @DisplayName("토큰이 존재하지 않으면 UNAUTHORIZED 예외가 발생한다")
        @Test
        void throwsUnauthorizedWhenTokenMissing() {
            // given
            String eventId = "event-1";
            Long userId = 1L;
            given(queueTokenRepository.getToken(eventId, userId)).willReturn(Optional.empty());

            // when
            CoreException exception = assertThrows(CoreException.class, () -> {
                queueTokenService.validateToken(eventId, userId, "some-token");
            });

            // then
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.UNAUTHORIZED);
        }

        @DisplayName("토큰이 일치하지 않으면 UNAUTHORIZED 예외가 발생한다")
        @Test
        void throwsUnauthorizedWhenTokenMismatch() {
            // given
            String eventId = "event-1";
            Long userId = 1L;
            given(queueTokenRepository.getToken(eventId, userId)).willReturn(Optional.of("correct-token"));

            // when
            CoreException exception = assertThrows(CoreException.class, () -> {
                queueTokenService.validateToken(eventId, userId, "wrong-token");
            });

            // then
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.UNAUTHORIZED);
        }
    }
}

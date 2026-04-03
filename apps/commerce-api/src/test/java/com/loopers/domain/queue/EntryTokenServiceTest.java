package com.loopers.domain.queue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@DisplayName("EntryTokenService 단위 테스트")
@ExtendWith(MockitoExtension.class)
class EntryTokenServiceTest {

    @Mock
    private EntryTokenRepository entryTokenRepository;

    @Mock
    private QueueProperties queueProperties;

    @InjectMocks
    private EntryTokenService entryTokenService;

    // ============================
    // issueToken()
    // ============================
    @Nested
    @DisplayName("issueToken()")
    class IssueToken {

        @Test
        @DisplayName("신규 발급 시 UUID 토큰을 반환한다")
        void issueToken_NewUser_ShouldReturnUUID() {
            // given
            Long userId = 1L;
            given(queueProperties.getTokenTtlSeconds()).willReturn(300);
            given(entryTokenRepository.setIfAbsent(eq(userId), anyString(), any(Duration.class)))
                    .willReturn(true);

            // when
            String token = entryTokenService.issueToken(userId);

            // then
            assertThat(token).isNotNull();
            assertThat(token).matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
            verify(entryTokenRepository).setIfAbsent(eq(userId), eq(token), eq(Duration.ofSeconds(300)));
        }

        @Test
        @DisplayName("이미 토큰이 있으면 기존 토큰을 반환한다 (멱등)")
        void issueToken_AlreadyExists_ShouldReturnExistingToken() {
            // given
            Long userId = 1L;
            String existingToken = "existing-uuid-token";
            given(queueProperties.getTokenTtlSeconds()).willReturn(300);
            given(entryTokenRepository.setIfAbsent(eq(userId), anyString(), any(Duration.class)))
                    .willReturn(false); // SET NX 실패 — 이미 존재
            given(entryTokenRepository.get(userId)).willReturn(existingToken);

            // when
            String token = entryTokenService.issueToken(userId);

            // then
            assertThat(token).isEqualTo(existingToken);
        }

        @Test
        @DisplayName("SET NX 실패 + GET null (TTL 만료) → 재발급 시도")
        void issueToken_RaceCondition_ShouldRetrySetIfAbsent() {
            // given: SET NX=false, GET=null (TTL 만료), 재시도 SET NX, GET=재발급 토큰
            Long userId = 1L;
            given(queueProperties.getTokenTtlSeconds()).willReturn(300);
            given(entryTokenRepository.setIfAbsent(eq(userId), anyString(), any(Duration.class)))
                    .willReturn(false)  // 1차 실패
                    .willReturn(true);  // 2차 성공
            given(entryTokenRepository.get(userId))
                    .willReturn(null)          // 1차 GET — TTL 만료
                    .willReturn("new-token");  // 2차 GET — 재발급 토큰

            // when
            String token = entryTokenService.issueToken(userId);

            // then
            assertThat(token).isEqualTo("new-token");
        }
    }

    // ============================
    // validateAndConsume()
    // ============================
    @Nested
    @DisplayName("validateAndConsume()")
    class ValidateAndConsume {

        @Test
        @DisplayName("유효한 토큰 검증 시 true를 반환한다")
        void validateAndConsume_ValidToken_ShouldReturnTrue() {
            // given
            Long userId = 1L;
            String token = "valid-token";
            given(entryTokenRepository.validateAndDelete(userId, token)).willReturn(true);

            // when
            boolean result = entryTokenService.validateAndConsume(userId, token);

            // then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("잘못된 토큰 검증 시 false를 반환한다")
        void validateAndConsume_InvalidToken_ShouldReturnFalse() {
            // given
            Long userId = 1L;
            String token = "wrong-token";
            given(entryTokenRepository.validateAndDelete(userId, token)).willReturn(false);

            // when
            boolean result = entryTokenService.validateAndConsume(userId, token);

            // then
            assertThat(result).isFalse();
        }
    }

    // ============================
    // validate() (검증만, 삭제 없음)
    // ============================
    @Nested
    @DisplayName("validate()")
    class Validate {

        @Test
        @DisplayName("유효한 토큰 검증 시 true를 반환한다 (삭제 없음)")
        void validate_ValidToken_ShouldReturnTrue() {
            // given
            Long userId = 1L;
            String token = "valid-token";
            given(entryTokenRepository.validate(userId, token)).willReturn(true);

            // when
            boolean result = entryTokenService.validate(userId, token);

            // then
            assertThat(result).isTrue();
            verify(entryTokenRepository, never()).delete(userId);
            verify(entryTokenRepository, never()).validateAndDelete(userId, token);
        }

        @Test
        @DisplayName("잘못된 토큰 검증 시 false를 반환한다")
        void validate_InvalidToken_ShouldReturnFalse() {
            // given
            Long userId = 1L;
            String token = "wrong-token";
            given(entryTokenRepository.validate(userId, token)).willReturn(false);

            // when
            boolean result = entryTokenService.validate(userId, token);

            // then
            assertThat(result).isFalse();
        }
    }

    // ============================
    // consume() (삭제만)
    // ============================
    @Nested
    @DisplayName("consume()")
    class Consume {

        @Test
        @DisplayName("토큰을 삭제한다")
        void consume_ShouldDeleteToken() {
            // given
            Long userId = 1L;
            given(entryTokenRepository.delete(userId)).willReturn(true);

            // when
            entryTokenService.consume(userId);

            // then
            verify(entryTokenRepository).delete(userId);
        }
    }

    // ============================
    // getToken() / hasToken()
    // ============================
    @Nested
    @DisplayName("getToken() / hasToken()")
    class TokenQuery {

        @Test
        @DisplayName("토큰이 있으면 값을 반환한다")
        void getToken_Exists_ShouldReturnToken() {
            given(entryTokenRepository.get(1L)).willReturn("my-token");
            assertThat(entryTokenService.getToken(1L)).isEqualTo("my-token");
        }

        @Test
        @DisplayName("토큰이 없으면 null을 반환한다")
        void getToken_NotExists_ShouldReturnNull() {
            given(entryTokenRepository.get(1L)).willReturn(null);
            assertThat(entryTokenService.getToken(1L)).isNull();
        }

        @Test
        @DisplayName("토큰이 있으면 hasToken()은 true를 반환한다")
        void hasToken_Exists_ShouldReturnTrue() {
            given(entryTokenRepository.get(1L)).willReturn("my-token");
            assertThat(entryTokenService.hasToken(1L)).isTrue();
        }

        @Test
        @DisplayName("토큰이 없으면 hasToken()은 false를 반환한다")
        void hasToken_NotExists_ShouldReturnFalse() {
            given(entryTokenRepository.get(1L)).willReturn(null);
            assertThat(entryTokenService.hasToken(1L)).isFalse();
        }
    }
}

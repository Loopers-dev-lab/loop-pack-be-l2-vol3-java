package com.loopers.domain.queue;

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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// [단위 테스트 - Domain Service]
//
// 테스트 대상: QueueTokenService
// 테스트 유형: 단위 테스트 (Mock: QueueTokenRepository)
// 테스트 범위: 입장 토큰 발급/조회/삭제 로직
//
// 토큰 발급 (issueToken):
//   - UUID 형식 토큰 발급, TTL 300초(5분) 설정
//   - 이미 토큰이 있는 유저에게는 발급하지 않음 (NX)
//   - 서로 다른 유저에게 각각 고유 토큰 발급
//
// 토큰 존재 확인 (hasToken): 보유 여부 반환
// 토큰 삭제 (deleteToken): 삭제 및 미존재 시 예외 미발생 검증
// 토큰 조회 (findToken): Optional 기반 조회
@ExtendWith(MockitoExtension.class)
@DisplayName("QueueTokenService 단위 테스트")
class QueueTokenServiceTest {

    @Mock
    private QueueTokenRepository queueTokenRepository;

    @InjectMocks
    private QueueTokenService queueTokenService;

    @Nested
    @DisplayName("토큰 발급")
    class IssueToken {

        @Test
        @DisplayName("성공 - 토큰이 없는 유저에게 UUID 형식의 토큰을 발급한다")
        void issueToken_success() {
            // given
            Long userId = 1L;
            when(queueTokenRepository.issue(eq(userId), anyString(), eq(300L))).thenReturn(true);

            // when
            Optional<String> token = queueTokenService.issueToken(userId);

            // then
            assertThat(token).isPresent();
            assertThat(token.get()).isNotBlank();
            verify(queueTokenRepository).issue(eq(userId), anyString(), eq(300L));
        }

        @Test
        @DisplayName("성공 - 발급된 토큰은 UUID 형식이다")
        void issueToken_uuid_format() {
            // given
            Long userId = 1L;
            when(queueTokenRepository.issue(eq(userId), anyString(), eq(300L))).thenReturn(true);

            // when
            Optional<String> token = queueTokenService.issueToken(userId);

            // then
            assertThat(token).isPresent();
            assertThat(token.get()).matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
        }

        @Test
        @DisplayName("성공 - 이미 토큰이 있는 유저에게는 발급하지 않고 empty를 반환한다")
        void issueToken_already_exists_returns_empty() {
            // given
            Long userId = 1L;
            when(queueTokenRepository.issue(eq(userId), anyString(), eq(300L))).thenReturn(false);

            // when
            Optional<String> token = queueTokenService.issueToken(userId);

            // then
            assertThat(token).isEmpty();
        }

        @Test
        @DisplayName("성공 - 서로 다른 유저에게 각각 토큰을 발급할 수 있다")
        void issueToken_different_users() {
            // given
            when(queueTokenRepository.issue(eq(1L), anyString(), eq(300L))).thenReturn(true);
            when(queueTokenRepository.issue(eq(2L), anyString(), eq(300L))).thenReturn(true);

            // when
            Optional<String> token1 = queueTokenService.issueToken(1L);
            Optional<String> token2 = queueTokenService.issueToken(2L);

            // then
            assertThat(token1).isPresent();
            assertThat(token2).isPresent();
            assertThat(token1.get()).isNotEqualTo(token2.get());
        }

        @Test
        @DisplayName("성공 - 토큰 발급 시 TTL은 300초(5분)이다")
        void issueToken_ttl_is_300_seconds() {
            // given
            Long userId = 1L;
            when(queueTokenRepository.issue(eq(userId), anyString(), eq(300L))).thenReturn(true);

            // when
            queueTokenService.issueToken(userId);

            // then
            verify(queueTokenRepository).issue(eq(userId), anyString(), eq(300L));
        }
    }

    @Nested
    @DisplayName("토큰 존재 확인")
    class HasToken {

        @Test
        @DisplayName("토큰이 있는 유저 - true")
        void hasToken_exists() {
            // given
            Long userId = 1L;
            when(queueTokenRepository.hasToken(userId)).thenReturn(true);

            // when
            boolean result = queueTokenService.hasToken(userId);

            // then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("토큰이 없는 유저 - false")
        void hasToken_not_exists() {
            // given
            Long userId = 1L;
            when(queueTokenRepository.hasToken(userId)).thenReturn(false);

            // when
            boolean result = queueTokenService.hasToken(userId);

            // then
            assertThat(result).isFalse();
        }
    }

    @Nested
    @DisplayName("토큰 삭제")
    class DeleteToken {

        @Test
        @DisplayName("성공 - 토큰을 삭제한다")
        void deleteToken_success() {
            // given
            Long userId = 1L;

            // when
            queueTokenService.deleteToken(userId);

            // then
            verify(queueTokenRepository).delete(userId);
        }

        @Test
        @DisplayName("성공 - 토큰이 없는 유저를 삭제해도 예외가 발생하지 않는다")
        void deleteToken_not_exists_no_exception() {
            // given
            Long userId = 999L;

            // when & then (예외 없이 정상 수행)
            queueTokenService.deleteToken(userId);
            verify(queueTokenRepository).delete(userId);
        }
    }

    @Nested
    @DisplayName("토큰 조회 (findToken)")
    class FindToken {

        @Test
        @DisplayName("성공 - 토큰이 존재하면 Optional에 담아 반환한다")
        void findToken_exists() {
            // given
            Long userId = 1L;
            when(queueTokenRepository.getToken(userId)).thenReturn(Optional.of("some-token"));

            // when
            Optional<String> result = queueTokenService.findToken(userId);

            // then
            assertThat(result).isPresent();
            assertThat(result.get()).isEqualTo("some-token");
        }

        @Test
        @DisplayName("성공 - 토큰이 없으면 empty를 반환한다")
        void findToken_not_exists() {
            // given
            Long userId = 1L;
            when(queueTokenRepository.getToken(userId)).thenReturn(Optional.empty());

            // when
            Optional<String> result = queueTokenService.findToken(userId);

            // then
            assertThat(result).isEmpty();
        }
    }

}

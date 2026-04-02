package com.loopers.domain.queue;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
@DisplayName("QueueService 단위 테스트")
class QueueServiceTest {

    @Mock
    private QueueRepository queueRepository;

    @Mock
    private TokenRepository tokenRepository;

    @InjectMocks
    private QueueService queueService;

    @Nested
    @DisplayName("대기열 진입")
    class EnterQueue {

        @Test
        @DisplayName("성공: 대기열에 진입하고 순번을 반환한다")
        void enterQueue_success() {
            // Given
            Long userId = 1L;
            given(queueRepository.enter(eq(userId), anyDouble())).willReturn(true);
            given(queueRepository.getPosition(userId)).willReturn(0L);
            given(queueRepository.getTotalSize()).willReturn(1L);

            // When
            QueueEntryResult result = queueService.enterQueue(userId);

            // Then
            assertThat(result.userId()).isEqualTo(userId);
            assertThat(result.position()).isEqualTo(1);
            assertThat(result.totalWaiting()).isEqualTo(1);
            assertThat(result.newEntry()).isTrue();
        }

        @Test
        @DisplayName("중복 진입 시 기존 순번을 반환한다")
        void enterQueue_duplicate() {
            // Given
            Long userId = 1L;
            given(queueRepository.enter(eq(userId), anyDouble())).willReturn(false);
            given(queueRepository.getPosition(userId)).willReturn(5L);
            given(queueRepository.getTotalSize()).willReturn(10L);

            // When
            QueueEntryResult result = queueService.enterQueue(userId);

            // Then
            assertThat(result.position()).isEqualTo(6);
            assertThat(result.newEntry()).isFalse();
        }
    }

    @Nested
    @DisplayName("순번 조회")
    class GetPosition {

        @Test
        @DisplayName("대기열에 있으면 순번과 예상 대기 시간을 반환한다")
        void getPosition_inQueue() {
            // Given
            Long userId = 1L;
            given(queueRepository.getPosition(userId)).willReturn(69L); // 0-based → 70번째
            given(queueRepository.getTotalSize()).willReturn(500L);

            // When
            QueuePositionResult result = queueService.getPosition(userId);

            // Then
            assertThat(result.position()).isEqualTo(70);
            assertThat(result.totalWaiting()).isEqualTo(500);
            assertThat(result.estimatedWaitSeconds()).isEqualTo(1); // 70/70 = 1초
            assertThat(result.token()).isNull();
        }

        @Test
        @DisplayName("대기열에 없지만 토큰이 있으면 토큰을 반환한다")
        void getPosition_hasToken() {
            // Given
            Long userId = 1L;
            given(queueRepository.getPosition(userId)).willReturn(null);
            given(tokenRepository.getToken(userId)).willReturn("abc-token");

            // When
            QueuePositionResult result = queueService.getPosition(userId);

            // Then
            assertThat(result.position()).isEqualTo(0);
            assertThat(result.token()).isEqualTo("abc-token");
        }

        @Test
        @DisplayName("대기열에도 없고 토큰도 없으면 null을 반환한다")
        void getPosition_notFound() {
            // Given
            Long userId = 1L;
            given(queueRepository.getPosition(userId)).willReturn(null);
            given(tokenRepository.getToken(userId)).willReturn(null);

            // When
            QueuePositionResult result = queueService.getPosition(userId);

            // Then
            assertThat(result).isNull();
        }

        @Test
        @DisplayName("순번 1~100이면 pollingInterval 1초")
        void getPosition_pollingInterval_fast() {
            // Given
            given(queueRepository.getPosition(1L)).willReturn(49L); // 50번째
            given(queueRepository.getTotalSize()).willReturn(100L);

            // When
            QueuePositionResult result = queueService.getPosition(1L);

            // Then
            assertThat(result.pollingIntervalSeconds()).isEqualTo(1);
        }

        @Test
        @DisplayName("순번 100~1000이면 pollingInterval 4초")
        void getPosition_pollingInterval_mid() {
            // Given
            given(queueRepository.getPosition(1L)).willReturn(499L); // 500번째
            given(queueRepository.getTotalSize()).willReturn(1000L);

            // When
            QueuePositionResult result = queueService.getPosition(1L);

            // Then
            assertThat(result.pollingIntervalSeconds()).isEqualTo(4);
        }

        @Test
        @DisplayName("순번 1000+이면 pollingInterval 10초")
        void getPosition_pollingInterval_slow() {
            // Given
            given(queueRepository.getPosition(1L)).willReturn(4999L); // 5000번째
            given(queueRepository.getTotalSize()).willReturn(10000L);

            // When
            QueuePositionResult result = queueService.getPosition(1L);

            // Then
            assertThat(result.pollingIntervalSeconds()).isEqualTo(10);
        }
    }

    @Nested
    @DisplayName("배치 처리 (토큰 발급)")
    class ProcessBatch {

        @Test
        @DisplayName("N명을 꺼내 토큰을 발급한다")
        void processBatch_success() {
            // Given
            Set<String> userIds = new LinkedHashSet<>(List.of("1", "2", "3"));
            given(queueRepository.pollBatch(7)).willReturn(userIds);

            // When
            List<Long> result = queueService.processBatch(7);

            // Then
            assertThat(result).containsExactly(1L, 2L, 3L);
            then(tokenRepository).should().saveToken(eq(1L), anyString(), eq(300L));
            then(tokenRepository).should().saveToken(eq(2L), anyString(), eq(300L));
            then(tokenRepository).should().saveToken(eq(3L), anyString(), eq(300L));
        }
    }

    @Nested
    @DisplayName("토큰 검증")
    class ValidateToken {

        @Test
        @DisplayName("유효한 토큰이면 통과한다")
        void validateToken_success() {
            // Given
            given(tokenRepository.getToken(1L)).willReturn("valid-token");

            // When & Then — 예외 없음
            queueService.validateToken(1L, "valid-token");
        }

        @Test
        @DisplayName("토큰이 없으면 FORBIDDEN")
        void validateToken_noToken() {
            // Given
            given(tokenRepository.getToken(1L)).willReturn(null);

            // When & Then
            assertThatThrownBy(() -> queueService.validateToken(1L, "any"))
                    .isInstanceOf(CoreException.class)
                    .hasFieldOrPropertyWithValue("errorType", ErrorType.FORBIDDEN);
        }

        @Test
        @DisplayName("토큰이 불일치하면 FORBIDDEN")
        void validateToken_mismatch() {
            // Given
            given(tokenRepository.getToken(1L)).willReturn("real-token");

            // When & Then
            assertThatThrownBy(() -> queueService.validateToken(1L, "wrong-token"))
                    .isInstanceOf(CoreException.class)
                    .hasFieldOrPropertyWithValue("errorType", ErrorType.FORBIDDEN);
        }
    }

    @Nested
    @DisplayName("토큰 삭제")
    class ConsumeToken {

        @Test
        @DisplayName("토큰을 삭제한다")
        void consumeToken_success() {
            // When
            queueService.consumeToken(1L);

            // Then
            then(tokenRepository).should().deleteToken(1L);
        }
    }
}

package com.loopers.application.queue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.BDDMockito.given;

import java.util.Optional;

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
import com.loopers.support.queue.WaitingQueue;

@ExtendWith(MockitoExtension.class)
@DisplayName("ReadQueuePositionUseCase 단위 테스트")
class ReadQueuePositionUseCaseTest {

    @Mock
    private WaitingQueue waitingQueue;

    @Mock
    private EntryTokenStore entryTokenStore;

    private final QueuePositionCalculator queuePositionCalculator = new QueuePositionCalculator(
            new QueueProperties(true, 2, 400)
    );

    private ReadQueuePositionUseCase useCase() {
        return new ReadQueuePositionUseCase(waitingQueue, entryTokenStore, queuePositionCalculator);
    }

    @DisplayName("대기열 순번을 조회할 때,")
    @Nested
    class Execute {

        @DisplayName("대기 중이면, 실측 처리량 기반 예상 대기 시간을 반환한다.")
        @Test
        void returnsEstimatedWait_whenWaiting() {
            // arrange
            Long userId = 1L;
            given(waitingQueue.getPosition(userId)).willReturn(120L);
            given(waitingQueue.getTotalCount()).willReturn(200L);

            // act
            QueuePositionResult result = useCase().execute(userId);

            // assert — position 121, ceil(121/5) = 25초
            assertAll(
                () -> assertThat(result.position()).isEqualTo(121),
                () -> assertThat(result.totalWaiting()).isEqualTo(200),
                () -> assertThat(result.estimatedWaitSeconds()).isEqualTo(25),
                () -> assertThat(result.token()).isNull()
            );
        }

        @DisplayName("처리량으로 나누어 떨어지면, 올림 없이 정확한 초를 반환한다.")
        @Test
        void returnsExactSeconds_whenDivisible() {
            // arrange
            Long userId = 1L;
            given(waitingQueue.getPosition(userId)).willReturn(59L);
            given(waitingQueue.getTotalCount()).willReturn(100L);

            // act
            QueuePositionResult result = useCase().execute(userId);

            // assert — position 60, 60/5 = 12초
            assertAll(
                () -> assertThat(result.position()).isEqualTo(60),
                () -> assertThat(result.estimatedWaitSeconds()).isEqualTo(12)
            );
        }

        @DisplayName("이미 입장한 사용자이면, 토큰과 함께 대기 시간 0을 반환한다.")
        @Test
        void returnsToken_whenAlreadyAdmitted() {
            // arrange
            Long userId = 1L;
            given(waitingQueue.getPosition(userId)).willReturn(null);
            given(entryTokenStore.getToken(userId)).willReturn(Optional.of("test-token"));

            // act
            QueuePositionResult result = useCase().execute(userId);

            // assert
            assertAll(
                () -> assertThat(result.position()).isZero(),
                () -> assertThat(result.estimatedWaitSeconds()).isZero(),
                () -> assertThat(result.token()).isEqualTo("test-token")
            );
        }

        @DisplayName("대기열에 진입하지 않은 사용자이면, 예외가 발생한다.")
        @Test
        void throwsException_whenNotEntered() {
            // arrange
            Long userId = 1L;
            given(waitingQueue.getPosition(userId)).willReturn(null);
            given(entryTokenStore.getToken(userId)).willReturn(Optional.empty());

            // act & assert
            assertThatThrownBy(() -> useCase().execute(userId))
                .isInstanceOf(CoreException.class)
                .hasFieldOrPropertyWithValue("errorType", ErrorType.QUEUE_NOT_ENTERED);
        }
    }
}

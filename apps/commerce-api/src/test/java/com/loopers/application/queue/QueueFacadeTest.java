package com.loopers.application.queue;

import com.loopers.domain.queue.QueueRepository;
import com.loopers.infrastructure.queue.QueueProperties;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class QueueFacadeTest {

    QueueRepository queueRepository = mock(QueueRepository.class);
    QueueProperties queueProperties = new QueueProperties(1, 1000L, 180L);
    QueueFacade queueFacade = new QueueFacade(queueRepository, queueProperties);

    @DisplayName("대기열 진입 시, ")
    @Nested
    class Enter {

        @DisplayName("이미 대기 중인 유저는 중복 진입되지 않는다.")
        @Test
        void doesNotEnter_whenAlreadyWaiting() {
            // arrange
            long userId = 1L;
            when(queueRepository.isInWaiting(userId)).thenReturn(true);

            // act
            queueFacade.enter(userId);

            // assert
            verify(queueRepository, never()).enter(anyLong(), anyDouble());
        }

        @DisplayName("이미 토큰이 있는 유저는 중복 진입되지 않는다.")
        @Test
        void doesNotEnter_whenAlreadyHasToken() {
            // arrange
            long userId = 1L;
            when(queueRepository.isInWaiting(userId)).thenReturn(false);
            when(queueRepository.findToken(userId)).thenReturn(Optional.of("existing-token"));

            // act
            queueFacade.enter(userId);

            // assert
            verify(queueRepository, never()).enter(anyLong(), anyDouble());
        }

        @DisplayName("신규 유저는 대기열에 진입된다.")
        @Test
        void entersQueue_whenNewUser() {
            // arrange
            long userId = 1L;
            when(queueRepository.isInWaiting(userId)).thenReturn(false);
            when(queueRepository.findToken(userId)).thenReturn(Optional.empty());

            // act
            queueFacade.enter(userId);

            // assert
            verify(queueRepository).enter(anyLong(), anyDouble());
        }
    }

    @DisplayName("순번 조회 시, ")
    @Nested
    class GetPosition {

        @DisplayName("입장 토큰이 있는 유저는 Entered 결과를 반환한다.")
        @Test
        void returnsEntered_whenTokenExists() {
            // arrange
            long userId = 1L;
            when(queueRepository.findToken(userId)).thenReturn(Optional.of("test-token"));

            // act
            QueuePositionResult result = queueFacade.getPosition(userId);

            // assert
            assertThat(result).isInstanceOf(QueuePositionResult.Entered.class);
            assertThat(((QueuePositionResult.Entered) result).token()).isEqualTo("test-token");
        }

        @DisplayName("대기 중인 유저는 Waiting 결과를 반환한다.")
        @Test
        void returnsWaiting_whenInQueue() {
            // arrange
            long userId = 1L;
            when(queueRepository.findToken(userId)).thenReturn(Optional.empty());
            when(queueRepository.getRank(userId)).thenReturn(Optional.of(5L));

            // act
            QueuePositionResult result = queueFacade.getPosition(userId);

            // assert
            assertThat(result).isInstanceOf(QueuePositionResult.Waiting.class);
            QueuePositionResult.Waiting waiting = (QueuePositionResult.Waiting) result;
            assertThat(waiting.rank()).isEqualTo(5L);
            assertThat(waiting.estimatedWaitSeconds()).isEqualTo(5L);
            assertThat(waiting.nextPollAfter()).isEqualTo(1L);
        }

        @DisplayName("대기열에 진입하지 않은 유저는 NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenNotInQueue() {
            // arrange
            long userId = 1L;
            when(queueRepository.findToken(userId)).thenReturn(Optional.empty());
            when(queueRepository.getRank(userId)).thenReturn(Optional.empty());

            // act & assert
            assertThatThrownBy(() -> queueFacade.getPosition(userId))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.NOT_FOUND));
        }
    }
}

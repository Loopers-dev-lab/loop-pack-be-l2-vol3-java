package com.loopers.application.queue;

import com.loopers.domain.queue.QueueRepository;
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
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class QueueServiceTest {

    @InjectMocks
    private QueueService queueService;

    @Mock
    private QueueRepository queueRepository;

    @Mock
    private QueueMetrics queueMetrics;

    @DisplayName("대기열 진입")
    @Nested
    class Enter {

        @DisplayName("신규 유저가 대기열에 진입하면 QueueStatus를 반환한다")
        @Test
        void returnsQueueStatusForNewUser() {
            // given
            String eventId = "event-1";
            Long userId = 1L;
            given(queueRepository.add(eventId, userId)).willReturn(true);
            given(queueRepository.getPosition(eventId, userId)).willReturn(Optional.of(0L));
            given(queueRepository.getTotalCount(eventId)).willReturn(1L);

            // when
            QueueService.QueueStatus result = queueService.enter(eventId, userId);

            // then
            assertThat(result.position()).isEqualTo(1L);
            assertThat(result.totalWaiting()).isEqualTo(1L);
            assertThat(result.estimatedWaitSeconds()).isGreaterThan(0);
        }

        @DisplayName("이미 등록된 유저가 진입하면 CONFLICT 예외가 발생한다")
        @Test
        void throwsConflictForDuplicateUser() {
            // given
            String eventId = "event-1";
            Long userId = 1L;
            given(queueRepository.add(eventId, userId)).willReturn(false);

            // when
            CoreException exception = assertThrows(CoreException.class, () -> {
                queueService.enter(eventId, userId);
            });

            // then
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.CONFLICT);
        }
    }

    @DisplayName("대기열 위치 조회")
    @Nested
    class GetPosition {

        @DisplayName("대기 중인 유저의 위치와 예상 대기 시간을 반환한다")
        @Test
        void returnsPositionForWaitingUser() {
            // given
            String eventId = "event-1";
            Long userId = 1L;
            given(queueRepository.getPosition(eventId, userId)).willReturn(Optional.of(4L));
            given(queueRepository.getTotalCount(eventId)).willReturn(10L);

            // when
            QueueService.QueueStatus result = queueService.getPosition(eventId, userId);

            // then
            assertThat(result.position()).isEqualTo(5L);
            assertThat(result.totalWaiting()).isEqualTo(10L);
            assertThat(result.estimatedWaitSeconds()).isGreaterThan(0);
        }

        @DisplayName("미등록 유저는 position=0, estimatedWait=0을 반환한다")
        @Test
        void returnsZeroForUnregisteredUser() {
            // given
            String eventId = "event-1";
            Long userId = 999L;
            given(queueRepository.getPosition(eventId, userId)).willReturn(Optional.empty());
            given(queueRepository.getTotalCount(eventId)).willReturn(10L);

            // when
            QueueService.QueueStatus result = queueService.getPosition(eventId, userId);

            // then
            assertThat(result.position()).isEqualTo(0L);
            assertThat(result.totalWaiting()).isEqualTo(10L);
            assertThat(result.estimatedWaitSeconds()).isEqualTo(0);
        }
    }
}

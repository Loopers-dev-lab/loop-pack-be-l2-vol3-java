package com.loopers.application.queue;

import com.loopers.domain.queue.QueueRepository;
import com.loopers.infrastructure.queue.QueueProperties;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
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

    @DisplayName("토큰 발급 시, ")
    @Nested
    class IssueTokens {

        @Test
        @DisplayName("설정된 batch size와 ttl로 repository에 발급을 위임한다.")
        @SuppressWarnings("unchecked")
        void delegatesIssueTokensWithConfiguredValues() {
            // act
            queueFacade.issueTokens();

            // assert
            org.mockito.ArgumentCaptor<List<String>> uuidsCaptor = org.mockito.ArgumentCaptor.forClass(List.class);
            verify(queueRepository).issueTokens(eq(1), eq(180L), uuidsCaptor.capture());
            List<String> issuedUuids = uuidsCaptor.getValue();
            assertThat(issuedUuids).hasSize(1);
            assertThatCode(() -> UUID.fromString(issuedUuids.get(0))).doesNotThrowAnyException();
        }
    }

    @DisplayName("토큰 검증 시, ")
    @Nested
    class ValidateToken {

        @Test
        @DisplayName("저장된 토큰과 일치하면 true를 반환한다.")
        void returnsTrue_whenTokenMatches() {
            // arrange
            long userId = 1L;
            when(queueRepository.findToken(userId)).thenReturn(Optional.of("valid-token"));

            // act
            boolean result = queueFacade.validateToken(userId, "valid-token");

            // assert
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("저장된 토큰과 다르면 false를 반환한다.")
        void returnsFalse_whenTokenDoesNotMatch() {
            // arrange
            long userId = 1L;
            when(queueRepository.findToken(userId)).thenReturn(Optional.of("valid-token"));

            // act
            boolean result = queueFacade.validateToken(userId, "other-token");

            // assert
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("저장된 토큰이 없으면 false를 반환한다.")
        void returnsFalse_whenTokenDoesNotExist() {
            // arrange
            long userId = 1L;
            when(queueRepository.findToken(userId)).thenReturn(Optional.empty());

            // act
            boolean result = queueFacade.validateToken(userId, "any-token");

            // assert
            assertThat(result).isFalse();
        }
    }

    @DisplayName("토큰 삭제 시, ")
    @Nested
    class RemoveToken {

        @Test
        @DisplayName("repository에 삭제를 위임한다.")
        void delegatesTokenRemoval() {
            // arrange
            long userId = 1L;

            // act
            queueFacade.removeToken(userId);

            // assert
            verify(queueRepository).removeToken(userId);
        }
    }
}

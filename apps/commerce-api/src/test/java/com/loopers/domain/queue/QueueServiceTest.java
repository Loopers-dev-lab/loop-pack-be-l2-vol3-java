package com.loopers.domain.queue;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.support.enums.QueueStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@DisplayName("QueueService 단위 테스트")
@ExtendWith(MockitoExtension.class)
class QueueServiceTest {

    @Mock
    private QueueRepository queueRepository;

    @Mock
    private QueueProperties queueProperties;

    @Mock
    private EntryTokenService entryTokenService;

    @InjectMocks
    private QueueService queueService;

    // ============================
    // enter()
    // ============================
    @Nested
    @DisplayName("enter()")
    class Enter {

        @Test
        @DisplayName("신규 유저 진입 시 순번과 isNew=true를 반환한다")
        void enter_NewUser_ShouldReturnPositionAndIsNewTrue() {
            // given
            Long userId = 1L;
            given(queueRepository.getSize()).willReturn(100L);
            given(queueProperties.canAccept(100L)).willReturn(true);
            given(queueRepository.addIfAbsent(eq(userId), anyDouble())).willReturn(true);
            given(queueRepository.getRank(userId)).willReturn(100L);
            given(queueProperties.calculateEstimatedWaitSeconds(100L)).willReturn(1);

            // when
            EnterResult result = queueService.enter(userId);

            // then
            assertThat(result.isNew()).isTrue();
            assertThat(result.position().status()).isEqualTo(QueueStatus.WAITING);
            assertThat(result.position().position()).isEqualTo(100L);
            assertThat(result.position().estimatedWaitSeconds()).isEqualTo(1);
        }

        @Test
        @DisplayName("이미 대기 중인 유저 재진입 시 기존 순번과 isNew=false를 반환한다 (멱등)")
        void enter_AlreadyInQueue_ShouldReturnExistingPositionAndIsNewFalse() {
            // given
            Long userId = 1L;
            given(queueRepository.getSize()).willReturn(200L);
            given(queueProperties.canAccept(200L)).willReturn(true);
            given(queueRepository.addIfAbsent(eq(userId), anyDouble())).willReturn(false); // 이미 존재
            given(queueRepository.getRank(userId)).willReturn(50L);
            given(queueProperties.calculateEstimatedWaitSeconds(50L)).willReturn(1);

            // when
            EnterResult result = queueService.enter(userId);

            // then
            assertThat(result.isNew()).isFalse();
            assertThat(result.position().position()).isEqualTo(50L);
        }

        @Test
        @DisplayName("대기열이 가득 차면 QUEUE_FULL 예외가 발생한다")
        void enter_QueueFull_ShouldThrowException() {
            // given
            Long userId = 1L;
            given(queueRepository.getSize()).willReturn(10000L);
            given(queueProperties.canAccept(10000L)).willReturn(false);

            // when & then
            assertThatThrownBy(() -> queueService.enter(userId))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> {
                        CoreException ce = (CoreException) e;
                        assertThat(ce.getErrorType()).isEqualTo(ErrorType.QUEUE_FULL);
                    });

            verify(queueRepository, never()).addIfAbsent(anyLong(), anyDouble());
        }

        @Test
        @DisplayName("ZADD 직후 ZRANK가 null이면 getPosition()으로 위임한다")
        void enter_RankNullAfterAdd_ShouldDelegateToGetPosition() {
            // given: 스케줄러가 ZADD 직후 ZPOPMIN으로 즉시 꺼낸 상황
            Long userId = 1L;
            given(queueRepository.getSize()).willReturn(100L);
            given(queueProperties.canAccept(100L)).willReturn(true);
            given(queueRepository.addIfAbsent(eq(userId), anyDouble())).willReturn(true);
            given(queueRepository.getRank(userId)).willReturn(null); // ZPOPMIN으로 제거됨
            // getPosition() 내부에서 Lua 스냅샷 호출
            given(queueRepository.getPositionSnapshot(eq(userId), anyString()))
                    .willReturn(new QueueRepository.PositionSnapshot(null, 100, null));
            // Master fallback에서도 토큰 없음
            given(queueRepository.getTokenFromMaster(userId)).willReturn(null);

            // when
            EnterResult result = queueService.enter(userId);

            // then — getPosition()에서 NOT_IN_QUEUE 반환
            assertThat(result.position().status()).isEqualTo(QueueStatus.NOT_IN_QUEUE);
        }
    }

    // ============================
    // getPosition()
    // ============================
    @Nested
    @DisplayName("getPosition()")
    class GetPosition {

        @Test
        @DisplayName("대기열에 있는 유저는 WAITING 상태와 순번을 반환한다")
        void getPosition_InQueue_ShouldReturnWaiting() {
            // given — Lua 스냅샷 mock
            Long userId = 1L;
            given(queueRepository.getPositionSnapshot(eq(userId), anyString()))
                    .willReturn(new QueueRepository.PositionSnapshot(50L, 200, null));
            given(queueProperties.calculateEstimatedWaitSeconds(50L)).willReturn(1);

            // when
            QueuePosition position = queueService.getPosition(userId);

            // then
            assertThat(position.status()).isEqualTo(QueueStatus.WAITING);
            assertThat(position.position()).isEqualTo(50L);
            assertThat(position.totalWaiting()).isEqualTo(200L);
            assertThat(position.estimatedWaitSeconds()).isEqualTo(1);
            assertThat(position.token()).isNull();
        }

        @Test
        @DisplayName("대기열에 없고 Master에도 토큰 없으면 NOT_IN_QUEUE를 반환한다")
        void getPosition_NotInQueue_ShouldReturnNotInQueue() {
            // given
            Long userId = 1L;
            given(queueRepository.getPositionSnapshot(eq(userId), anyString()))
                    .willReturn(new QueueRepository.PositionSnapshot(null, 200, null));
            given(queueRepository.getTokenFromMaster(userId)).willReturn(null);

            // when
            QueuePosition position = queueService.getPosition(userId);

            // then
            assertThat(position.status()).isEqualTo(QueueStatus.NOT_IN_QUEUE);
            assertThat(position.position()).isEqualTo(-1);
            assertThat(position.token()).isNull();
        }

        @Test
        @DisplayName("Replica에서 NOT_IN_QUEUE지만 Master에 토큰이 있으면 READY를 반환한다 (Replica 지연 대응)")
        void getPosition_ReplicaLag_MasterHasToken_ShouldReturnReady() {
            // given — Replica: rank=null, token=null / Master: token 존재
            Long userId = 1L;
            given(queueRepository.getPositionSnapshot(eq(userId), anyString()))
                    .willReturn(new QueueRepository.PositionSnapshot(null, 200, null));
            given(queueRepository.getTokenFromMaster(userId)).willReturn("master-token-uuid");

            // when
            QueuePosition position = queueService.getPosition(userId);

            // then
            assertThat(position.status()).isEqualTo(QueueStatus.READY);
            assertThat(position.token()).isEqualTo("master-token-uuid");
        }

        @Test
        @DisplayName("토큰이 발급된 유저는 READY 상태와 토큰을 반환한다")
        void getPosition_WithToken_ShouldReturnReady() {
            // given
            Long userId = 1L;
            given(queueRepository.getPositionSnapshot(eq(userId), anyString()))
                    .willReturn(new QueueRepository.PositionSnapshot(null, 200, "my-token-uuid"));

            // when
            QueuePosition position = queueService.getPosition(userId);

            // then
            assertThat(position.status()).isEqualTo(QueueStatus.READY);
            assertThat(position.token()).isEqualTo("my-token-uuid");
            assertThat(position.position()).isEqualTo(0);
        }

        @Test
        @DisplayName("대기열에도 있고 토큰도 있으면 READY를 반환한다 (비정상이지만 안전 처리)")
        void getPosition_BothQueueAndToken_ShouldReturnReady() {
            // given
            Long userId = 1L;
            given(queueRepository.getPositionSnapshot(eq(userId), anyString()))
                    .willReturn(new QueueRepository.PositionSnapshot(50L, 200, "my-token-uuid"));

            // when
            QueuePosition position = queueService.getPosition(userId);

            // then
            assertThat(position.status()).isEqualTo(QueueStatus.READY);
            assertThat(position.token()).isEqualTo("my-token-uuid");
        }
    }

    // ============================
    // popBatch()
    // ============================
    @Nested
    @DisplayName("popBatch()")
    class PopBatch {

        @Test
        @DisplayName("N명을 대기열에서 꺼내 반환한다")
        void popBatch_ShouldReturnEntries() {
            // given
            List<QueueEntry> entries = List.of(
                    new QueueEntry(1L, 1000.0),
                    new QueueEntry(2L, 1001.0),
                    new QueueEntry(3L, 1002.0)
            );
            given(queueRepository.popMin(3)).willReturn(entries);

            // when
            List<QueueEntry> result = queueService.popBatch(3);

            // then
            assertThat(result).hasSize(3);
            assertThat(result.get(0).userId()).isEqualTo(1L);
            assertThat(result.get(2).userId()).isEqualTo(3L);
        }

        @Test
        @DisplayName("대기열이 비어있으면 빈 리스트를 반환한다")
        void popBatch_EmptyQueue_ShouldReturnEmptyList() {
            // given
            given(queueRepository.popMin(14)).willReturn(List.of());

            // when
            List<QueueEntry> result = queueService.popBatch(14);

            // then
            assertThat(result).isEmpty();
        }
    }
}

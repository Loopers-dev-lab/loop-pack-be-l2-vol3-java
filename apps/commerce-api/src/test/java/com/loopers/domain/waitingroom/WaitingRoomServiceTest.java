package com.loopers.domain.waitingroom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

import com.loopers.domain.waitingroom.event.WaitingRoomCompletedEvent;
import com.loopers.support.error.CoreException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

@DisplayName("WaitingRoomService 단위 테스트")
class WaitingRoomServiceTest {

    private WaitingRoomService waitingRoomService;
    private FakeWaitingQueue waitingQueue;
    private FakeEntryGate entryGate;
    private final List<Object> publishedEvents = new ArrayList<>();
    private final ApplicationEventPublisher eventPublisher = publishedEvents::add;

    @BeforeEach
    void setUp() {
        publishedEvents.clear();
        waitingQueue = new FakeWaitingQueue();
        entryGate = new FakeEntryGate();
        waitingRoomService = new WaitingRoomService(waitingQueue, entryGate, eventPublisher);
    }

    /** 테스트 헬퍼: 대기열에서 꺼내 토큰 발급 (processQueue 없이 직접 조작) */
    private void admitFromQueue(int count) {
        List<Long> popped = waitingQueue.popFront(count);
        for (Long userId : popped) {
            entryGate.issueToken(userId);
        }
    }

    /** 테스트 헬퍼: processQueue를 대기열이 빌 때까지 반복 (BATCH_SIZE=2라 여러 번 호출 필요) */
    private void drainQueue() {
        while (waitingQueue.getTotalWaiting() > 0) {
            waitingRoomService.processQueue();
        }
    }

    @DisplayName("(1) 줄 서기 — 대기열 진입")
    @Nested
    class Enter {

        @DisplayName("1-1. 유저가 대기열에 진입하면 순번 정보를 반환한다.")
        @Test
        void enter_returnsPosition() {
            WaitingRoomPosition result = waitingRoomService.enter(1L);
            assertAll(
                    () -> assertThat(result.position()).isEqualTo(1),
                    () -> assertThat(result.totalWaiting()).isEqualTo(1));
        }

        @DisplayName("1-2. 같은 유저가 두 번 진입해도 중복 등록되지 않는다 (멱등).")
        @Test
        void enter_whenAlreadyInQueue_returnsExistingPosition() {
            waitingRoomService.enter(1L);
            waitingRoomService.enter(2L);
            WaitingRoomPosition result = waitingRoomService.enter(1L);
            assertAll(
                    () -> assertThat(result.position()).isEqualTo(1),
                    () -> assertThat(result.totalWaiting()).isEqualTo(2));
        }

        @DisplayName("1-3. 진입 순서대로 순번이 매겨진다.")
        @Test
        void enter_assignsRankInOrder() {
            WaitingRoomPosition a = waitingRoomService.enter(10L);
            WaitingRoomPosition b = waitingRoomService.enter(20L);
            WaitingRoomPosition c = waitingRoomService.enter(30L);
            assertAll(
                    () -> assertThat(a.position()).isEqualTo(1),
                    () -> assertThat(b.position()).isEqualTo(2),
                    () -> assertThat(c.position()).isEqualTo(3));
        }

        @DisplayName("1-4. 진입 시 순번 + 전체 대기 인원 + 예상 대기시간이 응답된다.")
        @Test
        void enter_returnsFullPositionInfo() {
            for (long i = 1; i <= 40; i++) {
                waitingRoomService.enter(i);
            }
            WaitingRoomPosition result = waitingRoomService.enter(41L);
            assertAll(
                    () -> assertThat(result.position()).isEqualTo(41),
                    () -> assertThat(result.totalWaiting()).isEqualTo(41),
                    () -> assertThat(result.estimatedWaitSeconds()).isEqualTo(2),
                    () -> assertThat(result.token()).isNull());
        }
    }

    @DisplayName("(2) 내 순위 요청 — Polling")
    @Nested
    class GetPosition {

        @DisplayName("2-1. 대기열에 있는 유저가 조회하면 WAITING 상태 + rank가 반환된다.")
        @Test
        void getPosition_returnsCurrentPosition() {
            waitingRoomService.enter(1L);
            waitingRoomService.enter(2L);
            waitingRoomService.enter(3L);
            WaitingRoomPosition result = waitingRoomService.getPosition(2L);
            assertAll(
                    () -> assertThat(result.position()).isEqualTo(2),
                    () -> assertThat(result.totalWaiting()).isEqualTo(3),
                    () -> assertThat(result.isEntryReady()).isFalse());
        }

        @DisplayName("2-2. 앞 사람이 빠지면 내 순번이 줄어든다.")
        @Test
        void getPosition_decreasesWhenFrontUserLeaves() {
            waitingRoomService.enter(1L);
            waitingRoomService.enter(2L);
            waitingRoomService.enter(3L);
            assertThat(waitingRoomService.getPosition(3L).position()).isEqualTo(3);

            admitFromQueue(1);

            assertThat(waitingRoomService.getPosition(3L).position()).isEqualTo(2);
        }

        @DisplayName("2-3. 입장 토큰이 발급된 유저가 조회하면 ENTERED 상태 + 토큰이 반환된다.")
        @Test
        void getPosition_whenTokenIssued_returnsEnteredStatus() {
            waitingRoomService.enter(1L);
            admitFromQueue(1);
            WaitingRoomPosition result = waitingRoomService.getPosition(1L);
            assertAll(
                    () -> assertThat(result.isEntryReady()).isTrue(),
                    () -> assertThat(result.token()).isNotNull(),
                    () -> assertThat(result.position()).isEqualTo(0));
        }

        @DisplayName("2-4. 대기열에도 없고 토큰도 없는 유저가 조회하면 NOT_IN_QUEUE가 반환된다.")
        @Test
        void getPosition_whenNotInQueue_throwsException() {
            assertThatThrownBy(() -> waitingRoomService.getPosition(999L))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorCode())
                            .isEqualTo(WaitingRoomErrorCode.NOT_IN_QUEUE));
        }
    }

    @DisplayName("(3) N명 꺼내기 — Scheduler → 대기열")
    @Nested
    class ProcessQueue {

        @DisplayName("3-1. 스케줄러가 1회 실행되면 배치 크기(2)만큼 꺼낸다.")
        @Test
        void processQueue_admitsBatchSize() {
            for (long i = 1; i <= 5; i++) {
                waitingRoomService.enter(i);
            }
            waitingRoomService.processQueue();
            assertThat(waitingQueue.getTotalWaiting()).isEqualTo(3);
        }

        @DisplayName("3-2. 대기열에 20명 미만이면 있는 만큼만 꺼낸다.")
        @Test
        void processQueue_whenLessThanBatchSize_admitsAll() {
            waitingRoomService.enter(1L);
            waitingRoomService.enter(2L);
            waitingRoomService.processQueue();
            assertAll(
                    () -> assertThat(entryGate.getToken(1L)).isNotNull(),
                    () -> assertThat(entryGate.getToken(2L)).isNotNull(),
                    () -> assertThat(waitingQueue.getTotalWaiting()).isEqualTo(0));
        }

        @DisplayName("3-3. 대기열이 비어있으면 아무 일도 일어나지 않는다.")
        @Test
        void processQueue_whenEmpty_doesNothing() {
            waitingRoomService.processQueue();
            assertThat(waitingQueue.getTotalWaiting()).isEqualTo(0);
        }

        @DisplayName("3-4. 먼저 진입한 유저부터 꺼낸다.")
        @Test
        void processQueue_admitsInEntryOrder() {
            waitingRoomService.enter(100L);
            waitingRoomService.enter(200L);
            waitingRoomService.enter(300L);
            drainQueue();
            assertAll(
                    () -> assertThat(entryGate.getToken(100L)).isNotNull(),
                    () -> assertThat(entryGate.getToken(200L)).isNotNull(),
                    () -> assertThat(entryGate.getToken(300L)).isNotNull());
        }

    }

    @DisplayName("(4) 입장 토큰 발급")
    @Nested
    class TokenIssuance {

        @DisplayName("4-1. 꺼낸 유저에게 입장 토큰이 발급된다.")
        @Test
        void processQueue_issuesTokenToUser() {
            waitingRoomService.enter(1L);
            waitingRoomService.processQueue();
            assertThat(entryGate.getToken(1L)).isNotNull();
        }

        @DisplayName("4-3. 토큰 값이 유니크하다.")
        @Test
        void processQueue_issuesUniqueTokens() {
            for (long i = 1; i <= 10; i++) {
                waitingRoomService.enter(i);
            }
            drainQueue();
            Set<String> tokens = new HashSet<>();
            for (long i = 1; i <= 10; i++) {
                tokens.add(entryGate.getToken(i));
            }
            assertThat(tokens).hasSize(10);
        }

        @DisplayName("4-4. 대기열 감소 수 = 토큰 발급 수이다.")
        @Test
        void processQueue_countMatchesBetweenQueueAndTokens() {
            for (long i = 1; i <= 5; i++) {
                waitingRoomService.enter(i);
            }
            long beforeWaiting = waitingQueue.getTotalWaiting();
            waitingRoomService.processQueue();
            long removed = beforeWaiting - waitingQueue.getTotalWaiting();
            long tokensIssued = 0;
            for (long i = 1; i <= 5; i++) {
                if (entryGate.getToken(i) != null) tokensIssued++;
            }
            assertThat(removed).isEqualTo(tokensIssued);
        }
    }

    @DisplayName("(5a) 토큰 검증 → 주문 → 토큰 삭제")
    @Nested
    class ValidateToken {

        @DisplayName("5a-1. 유효한 토큰이면 통과한다.")
        @Test
        void validateToken_whenValid_passes() {
            waitingRoomService.enter(1L);
            admitFromQueue(1);
            String token = entryGate.getToken(1L);
            waitingRoomService.validateToken(1L, token);
        }

        @DisplayName("5a-2. 토큰이 없으면 거부된다.")
        @Test
        void validateToken_whenNoToken_throwsException() {
            assertThatThrownBy(() -> waitingRoomService.validateToken(1L, "any-token"))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorCode())
                            .isEqualTo(WaitingRoomErrorCode.INVALID_TOKEN));
        }

        @DisplayName("5a-3/불일치. 토큰 값이 불일치하면 거부된다.")
        @Test
        void validateToken_whenMismatch_throwsException() {
            waitingRoomService.enter(1L);
            admitFromQueue(1);
            assertThatThrownBy(() -> waitingRoomService.validateToken(1L, "wrong-token"))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorCode())
                            .isEqualTo(WaitingRoomErrorCode.INVALID_TOKEN));
        }

        @DisplayName("5a-4. 주문 완료 후 토큰이 삭제된다.")
        @Test
        void completeEntry_deletesToken() {
            waitingRoomService.enter(1L);
            admitFromQueue(1);
            waitingRoomService.completeEntry(1L);
            assertThat(entryGate.getToken(1L)).isNull();
        }

        @DisplayName("5a-5. 토큰 삭제 후 같은 토큰으로 재주문이 불가능하다.")
        @Test
        void completeEntry_preventsReuse() {
            waitingRoomService.enter(1L);
            admitFromQueue(1);
            String token = entryGate.getToken(1L);
            waitingRoomService.completeEntry(1L);
            assertThatThrownBy(() -> waitingRoomService.validateToken(1L, token))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorCode())
                            .isEqualTo(WaitingRoomErrorCode.INVALID_TOKEN));
        }

        @DisplayName("5a-6. 주문 완료 시 WaitingRoomCompletedEvent가 발행된다.")
        @Test
        void completeEntry_publishesCompletedEvent() {
            waitingRoomService.enter(1L);
            admitFromQueue(1);
            publishedEvents.clear();

            waitingRoomService.completeEntry(1L);

            assertThat(publishedEvents).hasSize(1);
            assertThat(publishedEvents.get(0)).isInstanceOf(WaitingRoomCompletedEvent.class);
            assertThat(((WaitingRoomCompletedEvent) publishedEvents.get(0)).userId()).isEqualTo(1L);
        }
    }

    @DisplayName("(6) 순환 — 자리 발생 후 다음 유저 입장")
    @Nested
    class Cycle {

        @DisplayName("6-1. 주문 완료(토큰 삭제) 후 다음 스케줄러 실행 시 새 유저가 입장한다.")
        @Test
        void cycle_afterComplete_nextUserGetsToken() {
            for (long i = 1; i <= 5; i++) {
                waitingRoomService.enter(i);
            }
            // 1차: 2명 입장
            waitingRoomService.processQueue();
            assertThat(waitingQueue.getTotalWaiting()).isEqualTo(3);

            // 나머지 전부 입장
            drainQueue();
            assertAll(
                    () -> assertThat(entryGate.getToken(3L)).isNotNull(),
                    () -> assertThat(entryGate.getToken(5L)).isNotNull(),
                    () -> assertThat(waitingQueue.getTotalWaiting()).isEqualTo(0));
        }
    }

    @DisplayName("대기열 취소")
    @Nested
    class Cancel {

        @DisplayName("C-1. 유저가 대기열을 취소하면 Sorted Set에서 제거된다.")
        @Test
        void cancel_removesFromQueue() {
            waitingRoomService.enter(1L);
            waitingRoomService.enter(2L);
            waitingRoomService.cancel(1L);
            assertAll(
                    () -> assertThat(waitingQueue.getTotalWaiting()).isEqualTo(1),
                    () -> assertThatThrownBy(() -> waitingRoomService.getPosition(1L))
                            .isInstanceOf(CoreException.class));
        }

        @DisplayName("C-2. 대기열에 없는 유저가 취소해도 에러가 나지 않는다.")
        @Test
        void cancel_whenNotInQueue_doesNotThrow() {
            waitingRoomService.cancel(999L);
        }

        @DisplayName("C-3. 취소 후 뒤에 있던 유저들의 순번이 앞당겨진다.")
        @Test
        void cancel_shiftsRanksForward() {
            waitingRoomService.enter(1L);
            waitingRoomService.enter(2L);
            waitingRoomService.enter(3L);
            assertThat(waitingRoomService.getPosition(3L).position()).isEqualTo(3);
            waitingRoomService.cancel(1L);
            assertAll(
                    () -> assertThat(waitingRoomService.getPosition(2L).position()).isEqualTo(1),
                    () -> assertThat(waitingRoomService.getPosition(3L).position()).isEqualTo(2));
        }
    }
}

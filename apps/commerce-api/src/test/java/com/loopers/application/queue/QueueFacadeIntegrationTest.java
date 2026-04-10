package com.loopers.application.queue;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.utils.DatabaseCleanUp;
import com.loopers.utils.RedisCleanUp;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.loopers.domain.queue.QueueMode;
import com.loopers.domain.queue.SessionStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class QueueFacadeIntegrationTest {

    @Autowired
    private QueueFacade queueFacade;

    @Autowired
    private SessionService sessionService;

    @Autowired
    private QueueService queueService;

    @Autowired
    private ModeManager modeManager;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @Autowired
    private RedisCleanUp redisCleanUp;

    @BeforeEach
    void setUp() {
        databaseCleanUp.truncateAllTables();
        redisCleanUp.truncateAll();
        modeManager.switchToNormal();
    }

    @Nested
    class 대기열_진입 {

        @Test
        void EVENT_모드에서_진입하면_대기_순번을_반환한다() {
            modeManager.switchToEvent();

            var response = queueFacade.enter(1L);

            assertThat(response.status()).isEqualTo("WAITING");
            assertThat(response.position()).isGreaterThanOrEqualTo(1);
        }

        @Test
        void NORMAL_모드에서_진입하면_예외가_발생한다() {
            assertThatThrownBy(() -> queueFacade.enter(1L))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType())
                            .isEqualTo(ErrorType.BAD_REQUEST));
        }

        @Test
        void ACTIVE_세션이_있는_유저가_진입하면_CONFLICT_예외가_발생한다() {
            modeManager.switchToEvent();
            sessionService.createSession(1L);

            assertThatThrownBy(() -> queueFacade.enter(1L))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType())
                            .isEqualTo(ErrorType.CONFLICT));
        }

        @Test
        void fallbackMode에서_진입하면_SERVICE_UNAVAILABLE_예외가_발생한다() {
            modeManager.switchToEvent();
            modeManager.enterFallbackMode();

            assertThatThrownBy(() -> queueFacade.enter(1L))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType())
                            .isEqualTo(ErrorType.SERVICE_UNAVAILABLE));

            modeManager.exitFallbackMode();
        }

        @Test
        void CONSUMED_세션이_있는_유저는_세션_삭제_후_진입할_수_있다() {
            modeManager.switchToEvent();
            sessionService.createSession(1L);
            sessionService.compareAndSwap(1L, SessionStatus.ACTIVE, SessionStatus.CONSUMED);

            var response = queueFacade.enter(1L);

            assertThat(response.status()).isEqualTo("WAITING");
            assertThat(sessionService.hasSession(1L)).isFalse();
        }
    }

    @Nested
    class 순번_조회 {

        @Test
        void EVENT_모드에서_대기_중이면_순번을_반환한다() {
            modeManager.switchToEvent();
            queueFacade.enter(1L);

            var response = queueFacade.getPosition(1L);

            assertThat(response.status()).isEqualTo("WAITING");
            assertThat(response.position()).isGreaterThanOrEqualTo(1);
        }

        @Test
        void ACTIVE_세션이_있으면_READY를_반환한다() {
            modeManager.switchToEvent();
            sessionService.createSession(1L);

            var response = queueFacade.getPosition(1L);

            assertThat(response.status()).isEqualTo("READY");
            assertThat(response.position()).isEqualTo(0);
        }

        @Test
        void 대기열에_없으면_NOT_IN_QUEUE를_반환한다() {
            modeManager.switchToEvent();

            var response = queueFacade.getPosition(999L);

            assertThat(response.status()).isEqualTo("NOT_IN_QUEUE");
        }

        @Test
        void DRAIN_모드에서_세션이_없으면_EVENT_ENDED를_반환한다() {
            modeManager.switchToEvent();
            modeManager.switchToDrain();

            var response = queueFacade.getPosition(999L);

            assertThat(response.status()).isEqualTo("EVENT_ENDED");
        }
    }

    @Nested
    class 입장_배치 {

        @Test
        void 대기열_상위_유저에게_세션을_발급하고_대기열에서_제거한다() {
            modeManager.switchToEvent();
            queueFacade.enter(1L);
            queueFacade.enter(2L);
            queueFacade.enter(3L);

            queueFacade.admitBatch();

            assertThat(sessionService.hasActiveSession(1L)).isTrue();
            assertThat(sessionService.hasActiveSession(2L)).isTrue();
            assertThat(sessionService.hasActiveSession(3L)).isTrue();
            assertThat(queueService.getQueueSize()).isEqualTo(0);
        }

        @Test
        void NORMAL_모드에서는_입장_배치가_실행되지_않는다() {
            queueFacade.admitBatch();

            // 예외 없이 정상 종료 (skip)
        }
    }

    @Nested
    class 모드_전환 {

        @Test
        void EVENT_모드로_전환할_수_있다() {
            queueFacade.changeMode(QueueMode.EVENT);

            assertThat(modeManager.isEvent()).isTrue();
        }

        @Test
        void DRAIN_모드로_전환할_수_있다() {
            queueFacade.changeMode(QueueMode.EVENT);

            queueFacade.changeMode(QueueMode.DRAIN);

            assertThat(modeManager.isDrain()).isTrue();
        }

        @Test
        void NORMAL_모드로_전환할_수_있다() {
            queueFacade.changeMode(QueueMode.EVENT);

            queueFacade.changeMode(QueueMode.NORMAL);

            assertThat(modeManager.isEvent()).isFalse();
            assertThat(modeManager.isDrain()).isFalse();
        }
    }
}

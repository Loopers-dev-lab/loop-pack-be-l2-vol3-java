package com.loopers.application.queue;

import com.loopers.utils.DatabaseCleanUp;
import com.loopers.utils.RedisCleanUp;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class SessionServiceIntegrationTest {

    @Autowired
    private SessionService sessionService;

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
        modeManager.switchToEvent();
    }

    @Nested
    class 세션_생성 {

        @Test
        void 세션을_생성하면_ACTIVE_상태로_존재한다() {
            sessionService.createSession(1L);

            assertThat(sessionService.hasSession(1L)).isTrue();
            assertThat(sessionService.hasActiveSession(1L)).isTrue();
        }

        @Test
        void 세션_정보를_조회하면_상태와_생성시각이_반환된다() {
            sessionService.createSession(1L);

            SessionService.SessionInfo info = sessionService.getSession(1L);

            assertThat(info).isNotNull();
            assertThat(info.status()).isEqualTo(SessionService.STATUS_ACTIVE);
            assertThat(info.createdAt()).isNotNull();
        }
    }

    @Nested
    class CAS {

        @Test
        void ACTIVE에서_CONSUMED로_전환하면_성공을_반환한다() {
            sessionService.createSession(1L);

            long result = sessionService.compareAndSwap(1L, SessionService.STATUS_ACTIVE, SessionService.STATUS_CONSUMED);

            assertThat(result).isEqualTo(SessionService.CAS_SUCCESS);
        }

        @Test
        void 상태가_기대값과_다르면_불일치를_반환한다() {
            sessionService.createSession(1L);
            sessionService.compareAndSwap(1L, SessionService.STATUS_ACTIVE, SessionService.STATUS_CONSUMED);

            long result = sessionService.compareAndSwap(1L, SessionService.STATUS_ACTIVE, SessionService.STATUS_CONSUMED);

            assertThat(result).isEqualTo(SessionService.CAS_STATUS_MISMATCH);
        }

        @Test
        void 세션이_없으면_키_없음을_반환한다() {
            long result = sessionService.compareAndSwap(999L, SessionService.STATUS_ACTIVE, SessionService.STATUS_CONSUMED);

            assertThat(result).isEqualTo(SessionService.CAS_KEY_NOT_FOUND);
        }

        @Test
        void CONSUMED에서_ACTIVE로_복원할_수_있다() {
            sessionService.createSession(1L);
            sessionService.compareAndSwap(1L, SessionService.STATUS_ACTIVE, SessionService.STATUS_CONSUMED);

            long result = sessionService.compareAndSwap(1L, SessionService.STATUS_CONSUMED, SessionService.STATUS_ACTIVE);

            assertThat(result).isEqualTo(SessionService.CAS_SUCCESS);
            assertThat(sessionService.hasActiveSession(1L)).isTrue();
        }
    }

    @Nested
    class 세션_삭제 {

        @Test
        void 세션을_삭제하면_존재하지_않는다() {
            sessionService.createSession(1L);

            sessionService.deleteSession(1L);

            assertThat(sessionService.hasSession(1L)).isFalse();
        }
    }

    @Nested
    class 접근_검증 {

        @Test
        void ACTIVE_세션이면_QUERY_접근이_허용된다() {
            sessionService.createSession(1L);

            SessionService.SessionValidation result = sessionService.validateAccess(1L, SessionService.AccessType.QUERY);

            assertThat(result.isAllowed()).isTrue();
        }

        @Test
        void ACTIVE_세션이면_ORDER_접근이_허용된다() {
            sessionService.createSession(1L);

            SessionService.SessionValidation result = sessionService.validateAccess(1L, SessionService.AccessType.ORDER);

            assertThat(result.isAllowed()).isTrue();
        }

        @Test
        void CONSUMED_세션이면_QUERY_접근이_허용된다() {
            sessionService.createSession(1L);
            sessionService.compareAndSwap(1L, SessionService.STATUS_ACTIVE, SessionService.STATUS_CONSUMED);

            SessionService.SessionValidation result = sessionService.validateAccess(1L, SessionService.AccessType.QUERY);

            assertThat(result.isAllowed()).isTrue();
        }

        @Test
        void CONSUMED_세션이면_ORDER_접근이_거부된다() {
            sessionService.createSession(1L);
            sessionService.compareAndSwap(1L, SessionService.STATUS_ACTIVE, SessionService.STATUS_CONSUMED);

            SessionService.SessionValidation result = sessionService.validateAccess(1L, SessionService.AccessType.ORDER);

            assertThat(result.isAllowed()).isFalse();
        }

        @Test
        void 세션이_없으면_접근이_거부된다() {
            SessionService.SessionValidation result = sessionService.validateAccess(999L, SessionService.AccessType.QUERY);

            assertThat(result.isAllowed()).isFalse();
        }
    }
}

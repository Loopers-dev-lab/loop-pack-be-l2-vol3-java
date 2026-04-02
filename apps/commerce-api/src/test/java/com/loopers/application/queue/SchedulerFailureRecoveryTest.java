package com.loopers.application.queue;

import com.loopers.config.QueueProperties;
import com.loopers.domain.queue.EntryTokenService;
import com.loopers.domain.queue.QueueModeRepository;
import com.loopers.domain.queue.WaitingQueueService;
import com.loopers.infrastructure.queue.QueueStatusLuaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("스케줄러 장애 — Position API 경고 테스트")
class SchedulerFailureRecoveryTest {

    private QueueStatusLuaRepository luaRepository;
    private QueueApp queueApp;

    @BeforeEach
    void setUp() {
        WaitingQueueService waitingQueueService = mock(WaitingQueueService.class);
        EntryTokenService entryTokenService = mock(EntryTokenService.class);
        QueueModeRepository queueModeRepository = mock(QueueModeRepository.class);
        luaRepository = mock(QueueStatusLuaRepository.class);
        QueueProperties queueProperties = new QueueProperties(true, 14, 100, 300, 140, 100000, 2);
        ThroughputTracker throughputTracker = new ThroughputTracker(queueProperties);
        queueApp = new QueueApp(waitingQueueService, entryTokenService,
                throughputTracker, queueModeRepository, luaRepository);
    }

    @Nested
    @DisplayName("Position API 경고 응답")
    class PositionApiWarning {

        @Test
        @DisplayName("heartbeat 정상 → schedulerHealthy=true")
        void healthy_trueFlag() {
            when(luaRepository.getStatus(eq(1L), anyString()))
                    .thenReturn(new QueueStatusLuaRepository.QueueStatusResult(true, "WAITING", null, 10, 100));

            QueueInfo info = queueApp.getQueueStatus(1L);

            assertThat(info.schedulerHealthy()).isTrue();
        }

        @Test
        @DisplayName("heartbeat 만료 → schedulerHealthy=false — 클라이언트 경고")
        void unhealthy_falseFlag() {
            when(luaRepository.getStatus(eq(1L), anyString()))
                    .thenReturn(new QueueStatusLuaRepository.QueueStatusResult(false, "WAITING", null, 10, 100));

            QueueInfo info = queueApp.getQueueStatus(1L);

            assertThat(info.schedulerHealthy()).isFalse();
        }

        @Test
        @DisplayName("토큰 발급된 상태에서도 schedulerHealthy 포함")
        void tokenIssued_includesHealthFlag() {
            when(luaRepository.getStatus(eq(1L), anyString()))
                    .thenReturn(new QueueStatusLuaRepository.QueueStatusResult(true, "TOKEN", "token-123", 0, 50));

            QueueInfo info = queueApp.getQueueStatus(1L);

            assertThat(info.status()).isEqualTo(QueueStatus.TOKEN_ISSUED);
            assertThat(info.schedulerHealthy()).isTrue();
        }
    }
}

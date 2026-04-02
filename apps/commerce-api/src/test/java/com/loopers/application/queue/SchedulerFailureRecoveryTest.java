package com.loopers.application.queue;

import com.loopers.config.QueueProperties;
import com.loopers.domain.queue.EntryTokenService;
import com.loopers.domain.queue.QueueMode;
import com.loopers.domain.queue.QueueModeRepository;
import com.loopers.domain.queue.WaitingQueueService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("스케줄러 장애 — Position API 경고 테스트")
class SchedulerFailureRecoveryTest {

    private WaitingQueueService waitingQueueService;
    private EntryTokenService entryTokenService;
    private QueueModeRepository queueModeRepository;
    private SchedulerHealthChecker schedulerHealthChecker;
    private QueueApp queueApp;

    @BeforeEach
    void setUp() {
        waitingQueueService = mock(WaitingQueueService.class);
        entryTokenService = mock(EntryTokenService.class);
        queueModeRepository = mock(QueueModeRepository.class);
        schedulerHealthChecker = mock(SchedulerHealthChecker.class);
        QueueProperties queueProperties = new QueueProperties(true, 14, 100, 300, 140, 100000, 2);
        ThroughputTracker throughputTracker = new ThroughputTracker(queueProperties);
        queueApp = new QueueApp(waitingQueueService, entryTokenService, queueProperties,
                throughputTracker, queueModeRepository, schedulerHealthChecker);
    }

    @Nested
    @DisplayName("Position API 경고 응답")
    class PositionApiWarning {

        @Test
        @DisplayName("heartbeat 정상 → schedulerHealthy=true")
        void healthy_trueFlag() {
            // given
            when(schedulerHealthChecker.isAliveByHeartbeat()).thenReturn(true);
            when(entryTokenService.findToken(1L)).thenReturn(Optional.empty());
            when(waitingQueueService.getPosition(1L)).thenReturn(Optional.of(10L));
            when(waitingQueueService.getTotalCount()).thenReturn(100L);

            // when
            QueueInfo info = queueApp.getQueueStatus(1L);

            // then
            assertThat(info.schedulerHealthy()).isTrue();
        }

        @Test
        @DisplayName("heartbeat 만료 → schedulerHealthy=false — 클라이언트 경고")
        void unhealthy_falseFlag() {
            // given
            when(schedulerHealthChecker.isAliveByHeartbeat()).thenReturn(false);
            when(entryTokenService.findToken(1L)).thenReturn(Optional.empty());
            when(waitingQueueService.getPosition(1L)).thenReturn(Optional.of(10L));
            when(waitingQueueService.getTotalCount()).thenReturn(100L);

            // when
            QueueInfo info = queueApp.getQueueStatus(1L);

            // then
            assertThat(info.schedulerHealthy()).isFalse();
        }

        @Test
        @DisplayName("토큰 발급된 상태에서도 schedulerHealthy 포함")
        void tokenIssued_includesHealthFlag() {
            // given
            when(schedulerHealthChecker.isAliveByHeartbeat()).thenReturn(true);
            when(entryTokenService.findToken(1L)).thenReturn(Optional.of("token-123"));
            when(waitingQueueService.getTotalCount()).thenReturn(50L);

            // when
            QueueInfo info = queueApp.getQueueStatus(1L);

            // then
            assertThat(info.status()).isEqualTo(QueueStatus.TOKEN_ISSUED);
            assertThat(info.schedulerHealthy()).isTrue();
        }
    }
}

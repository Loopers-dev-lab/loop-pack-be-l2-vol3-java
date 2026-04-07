package com.loopers.application.cleanup;

import com.loopers.infrastructure.collector.EventHandledJpaRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EventHandledCleanupServiceTest {

    @Mock
    private EventHandledJpaRepository eventHandledJpaRepository;

    @InjectMocks
    private EventHandledCleanupService cleanupService;

    @Test
    @DisplayName("deleteOlderThanWithinSchedule는 삭제할 행이 없으면 한 번만 DELETE를 시도한다.")
    void deleteOlderThanWithinSchedule_stopsWhenNoRowsDeleted() {
        Instant cutoff = Instant.parse("2020-01-01T00:00:00Z");
        when(eventHandledJpaRepository.deleteHandledBefore(eq(cutoff), eq(500))).thenReturn(0);

        int total = cleanupService.deleteOlderThanWithinSchedule(cutoff, 500, 10, 100_000L);

        assertThat(total).isZero();
        verify(eventHandledJpaRepository, times(1)).deleteHandledBefore(cutoff, 500);
    }

    @Test
    @DisplayName("deleteOlderThanWithinSchedule는 maxLoopsPerRun에 도달하면 더 이상 DELETE하지 않는다.")
    void deleteOlderThanWithinSchedule_respectsMaxLoops() {
        Instant cutoff = Instant.parse("2020-01-01T00:00:00Z");
        when(eventHandledJpaRepository.deleteHandledBefore(eq(cutoff), any(Integer.class))).thenReturn(500);

        int total = cleanupService.deleteOlderThanWithinSchedule(cutoff, 500, 2, 100_000L);

        assertThat(total).isEqualTo(1000);
        verify(eventHandledJpaRepository, times(2)).deleteHandledBefore(eq(cutoff), eq(500));
    }

    @Test
    @DisplayName("deleteOlderThanWithinSchedule는 maxRowsPerRun 총량에 도달하면 루프를 멈춘다.")
    void deleteOlderThanWithinSchedule_respectsMaxRowsPerRun() {
        Instant cutoff = Instant.parse("2020-01-01T00:00:00Z");
        when(eventHandledJpaRepository.deleteHandledBefore(eq(cutoff), eq(500))).thenReturn(500);
        when(eventHandledJpaRepository.deleteHandledBefore(eq(cutoff), eq(300))).thenReturn(300);

        int total = cleanupService.deleteOlderThanWithinSchedule(cutoff, 500, 10, 800L);

        assertThat(total).isEqualTo(800);
        verify(eventHandledJpaRepository, times(1)).deleteHandledBefore(cutoff, 500);
        verify(eventHandledJpaRepository, times(1)).deleteHandledBefore(cutoff, 300);
    }

    @Test
    @DisplayName("deleteOlderThanWithinSchedule는 마지막 배치가 남은 예산보다 작을 때 limit을 줄인다.")
    void deleteOlderThanWithinSchedule_capsBatchByRemainingBudget() {
        Instant cutoff = Instant.parse("2020-01-01T00:00:00Z");
        when(eventHandledJpaRepository.deleteHandledBefore(eq(cutoff), eq(300))).thenReturn(300);

        int total = cleanupService.deleteOlderThanWithinSchedule(cutoff, 500, 10, 300L);

        assertThat(total).isEqualTo(300);
        verify(eventHandledJpaRepository, times(1)).deleteHandledBefore(cutoff, 300);
    }
}

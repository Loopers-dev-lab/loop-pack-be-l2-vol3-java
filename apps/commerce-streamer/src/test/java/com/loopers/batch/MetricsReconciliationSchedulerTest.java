package com.loopers.batch;

import com.loopers.domain.metrics.MetricsLikeCountMismatch;
import com.loopers.domain.metrics.ProductMetricsRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("MetricsReconciliationScheduler 단위 테스트")
class MetricsReconciliationSchedulerTest {

    @Mock
    ProductMetricsRepository metricsRepository;

    @InjectMocks
    MetricsReconciliationScheduler scheduler;

    @Test
    @DisplayName("불일치 없으면 forceUpdateLikeCount 호출 안 함")
    void reconcile_NoMismatch_ShouldSkip() {
        when(metricsRepository.findLikeCountMismatches()).thenReturn(List.of());

        scheduler.reconcileMetrics();

        verify(metricsRepository, never()).forceUpdateLikeCount(anyLong(), anyLong());
    }

    @Test
    @DisplayName("불일치 감지 시 건별 보정 -- like_count만 갱신")
    void reconcile_WithMismatch_ShouldFix() {
        when(metricsRepository.findLikeCountMismatches())
            .thenReturn(List.of(new MetricsLikeCountMismatch(1L, 10L, 12L)));

        scheduler.reconcileMetrics();

        verify(metricsRepository).forceUpdateLikeCount(1L, 12L);
    }
}

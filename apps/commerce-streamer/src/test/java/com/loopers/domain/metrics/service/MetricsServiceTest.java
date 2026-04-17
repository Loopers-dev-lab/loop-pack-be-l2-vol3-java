package com.loopers.domain.metrics.service;

import com.loopers.domain.metrics.repository.ProductMetricsDailyRepository;
import com.loopers.domain.metrics.repository.ProductMetricsRepository;
import com.loopers.infrastructure.metrics.entity.ProductMetricsEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MetricsServiceTest {

    @Mock
    private ProductMetricsRepository metricsRepository;

    @Mock
    private ProductMetricsDailyRepository dailyMetricsRepository;

    @InjectMocks
    private MetricsService metricsService;

    @DisplayName("조회 수 증가")
    @Nested
    class IncrementViewCount {

        @DisplayName("기존 version 값과 무관하게 조회 수를 증가시킨다")
        @Test
        void incrementWithoutVersionGuard() {
            ProductMetricsEntity metrics = ProductMetricsEntity.createNew(1L);
            metrics.updateVersion(100L);
            when(metricsRepository.findById(1L)).thenReturn(Optional.of(metrics));

            metricsService.incrementViewCount(1L);

            assertThat(metrics.getViewCount()).isEqualTo(1L);
            verify(metricsRepository).save(metrics);
            verify(dailyMetricsRepository).incrementViewCount(any(), eq(1L), eq(0.1d));
        }
    }

    @DisplayName("메트릭 조회")
    @Nested
    class FindChangedAfter {

        @DisplayName("변경된 엔티티를 도메인 모델로 변환한다")
        @Test
        void mapChangedMetrics() {
            ProductMetricsEntity first = ProductMetricsEntity.createNew(1L);
            first.incrementViewCount();
            ProductMetricsEntity second = ProductMetricsEntity.createNew(2L);
            second.incrementLikeCount();

            when(metricsRepository.findByUpdatedAtAfter(any(LocalDateTime.class)))
                    .thenReturn(List.of(first, second));

            var result = metricsService.findChangedAfter(LocalDateTime.now().minusMinutes(5));

            assertThat(result)
                    .extracting("productId", "viewCount", "likeCount", "orderCount")
                    .containsExactly(
                            org.assertj.core.groups.Tuple.tuple(1L, 1L, 0L, 0L),
                            org.assertj.core.groups.Tuple.tuple(2L, 0L, 1L, 0L)
                    );
        }
    }
}

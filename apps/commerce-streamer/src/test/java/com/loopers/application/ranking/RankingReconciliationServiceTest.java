package com.loopers.application.ranking;

import com.loopers.infrastructure.collector.ProductMetricsJpaRepository;
import com.loopers.infrastructure.collector.ProductMetricsModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RankingReconciliationServiceTest {

    @Mock
    private ProductMetricsJpaRepository productMetricsJpaRepository;

    @Mock
    private RankingMetricsRedisSyncService rankingMetricsRedisSyncService;

    private RankingReconciliationService reconciliationService;

    @BeforeEach
    void setUp() {
        reconciliationService = new RankingReconciliationService(
                productMetricsJpaRepository,
                rankingMetricsRedisSyncService
        );
    }

    @Test
    @DisplayName("reconcileAll은 페이지 단위로 매트릭을 읽고 각 행을 Redis 동기화에 넘긴다.")
    void reconcileAll_shouldPageThroughAndSync() {
        ProductMetricsModel m = org.mockito.Mockito.mock(ProductMetricsModel.class);
        Instant at = Instant.parse("2026-03-26T00:00:00Z");
        when(m.getLastEventOccurredAt()).thenReturn(at);

        when(productMetricsJpaRepository.findAll(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(m), PageRequest.of(0, 500, Sort.by("productId")), 1));

        reconciliationService.reconcileAll();

        verify(rankingMetricsRedisSyncService).upsertFromMetrics(eq(m), eq(at));
    }
}

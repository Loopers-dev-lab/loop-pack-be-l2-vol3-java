package com.loopers.application.ranking;

import com.loopers.infrastructure.collector.ProductMetricsJpaRepository;
import com.loopers.infrastructure.collector.ProductMetricsModel;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

/**
 * RDBMS {@code product_metrics}를 원천으로 Redis 일간 ZSET을 재동기화한다(보정 배치).
 * <p>
 * 유실·Redis 실패 등으로 ZSET이 DB와 어긋난 경우를 줄이기 위한 주기 작업이다.
 */
@Service
public class RankingReconciliationService {

    private static final int PAGE_SIZE = 500;

    private final ProductMetricsJpaRepository productMetricsJpaRepository;
    private final RankingMetricsRedisSyncService rankingMetricsRedisSyncService;

    public RankingReconciliationService(
            ProductMetricsJpaRepository productMetricsJpaRepository,
            RankingMetricsRedisSyncService rankingMetricsRedisSyncService) {
        this.productMetricsJpaRepository = productMetricsJpaRepository;
        this.rankingMetricsRedisSyncService = rankingMetricsRedisSyncService;
    }

    /**
     * 전체 매트릭 행을 페이지로 읽어, 각 행의 {@code last_event_occurred_at} 일자 키에 점수를 ZADD한다.
     */
    public void reconcileAll() {
        Pageable pageable = PageRequest.of(0, PAGE_SIZE, Sort.by("productId"));
        Page<ProductMetricsModel> page;
        do {
            page = productMetricsJpaRepository.findAll(pageable);
            for (ProductMetricsModel metrics : page.getContent()) {
                rankingMetricsRedisSyncService.upsertFromMetrics(metrics, metrics.getLastEventOccurredAt());
            }
            pageable = page.nextPageable();
        } while (page.hasNext());
    }
}

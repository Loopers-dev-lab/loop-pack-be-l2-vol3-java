package com.loopers.domain.ranking;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * ranking_metrics 집계 서비스.
 *
 * 모든 카운터 연산은 INSERT ... ON DUPLICATE KEY UPDATE 원자적 쿼리로 처리한다.
 * UPSERT 시 dirty=true로 설정하여 SyncScheduler가 Redis 동기화 대상으로 인식한다.
 */
@RequiredArgsConstructor
@Component
public class RankingMetricsService {

    private final RankingMetricsRepository rankingMetricsRepository;

    @Transactional
    public void incrementViewCount(Long productId, LocalDate date, int hour) {
        rankingMetricsRepository.upsertViewCount(productId, date, hour, 1);
    }

    @Transactional
    public void incrementLikeCount(Long productId, LocalDate date, int hour) {
        rankingMetricsRepository.upsertLikeCount(productId, date, hour, 1);
    }

    @Transactional
    public void decrementLikeCount(Long productId, LocalDate date, int hour) {
        rankingMetricsRepository.upsertLikeCount(productId, date, hour, -1);
    }

    @Transactional
    public void addOrderRevenue(Long productId, LocalDate date, int hour, BigDecimal revenue) {
        rankingMetricsRepository.upsertOrderRevenue(productId, date, hour, revenue);
    }

    /**
     * dirty=true인 (productId, hour) 쌍을 productId 기준으로 그룹핑하여 반환.
     * SyncScheduler가 일간 랭킹은 productId당 1회, 시간별 랭킹은 hour당 1회 처리하는 데 사용.
     */
    @Transactional(readOnly = true)
    public Map<Long, List<Integer>> findDirtyEntriesGroupedByProduct(LocalDate date) {
        return rankingMetricsRepository.findDirtyEntries(date).stream()
                .collect(Collectors.groupingBy(
                        DirtyEntry::productId,
                        Collectors.mapping(DirtyEntry::metricsHour, Collectors.toList())
                ));
    }

    @Transactional(readOnly = true)
    public RankingMetricsSummary sumByProductIdAndDate(Long productId, LocalDate date) {
        return rankingMetricsRepository.sumByProductIdAndDate(productId, date);
    }

    @Transactional(readOnly = true)
    public RankingMetricsSummary sumByProductIdAndDateAndHour(Long productId, LocalDate date, int hour) {
        return rankingMetricsRepository.sumByProductIdAndDateAndHour(productId, date, hour);
    }

    @Transactional
    public void clearDirtyByHour(Long productId, LocalDate date, int hour) {
        rankingMetricsRepository.clearDirtyByHour(productId, date, hour);
    }
}

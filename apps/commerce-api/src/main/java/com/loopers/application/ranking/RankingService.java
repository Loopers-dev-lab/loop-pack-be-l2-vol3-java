package com.loopers.application.ranking;

import com.loopers.domain.ranking.RankEntry;
import com.loopers.domain.ranking.RankingPeriod;
import com.loopers.domain.ranking.mv.MvRankEntry;
import com.loopers.domain.ranking.mv.MvRankingQueryRepository;
import com.loopers.infrastructure.ranking.RankingRedisRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Service
public class RankingService {

    private final RankingRedisRepository rankingRedisRepository;
    private final RankingKeyResolver keyResolver;
    private final RankingFallbackAggregator fallbackAggregator;
    private final MvRankingQueryRepository mvRankingQueryRepository;
    private final ExperimentGroupResolver experimentGroupResolver;

    public RankingService(RankingRedisRepository rankingRedisRepository,
                          RankingKeyResolver keyResolver,
                          RankingFallbackAggregator fallbackAggregator,
                          MvRankingQueryRepository mvRankingQueryRepository,
                          ExperimentGroupResolver experimentGroupResolver) {
        this.rankingRedisRepository = rankingRedisRepository;
        this.keyResolver = keyResolver;
        this.fallbackAggregator = fallbackAggregator;
        this.mvRankingQueryRepository = mvRankingQueryRepository;
        this.experimentGroupResolver = experimentGroupResolver;
    }

    // Query

    public String resolveGroup(Long userId) {
        return experimentGroupResolver.resolve(userId);
    }

    public List<RankEntry> getRankEntries(RankingPeriod period, LocalDate date, int page, int size, String group) {
        return loadRankEntries(period, date, page, size, group);
    }

    public long getTotalCount(RankingPeriod period, LocalDate date, String group) {
        String key = keyResolver.resolve(period, date, group);
        try {
            Long count = rankingRedisRepository.getTotalCount(key);
            if (count != null && count > 0) {
                return count;
            }
        } catch (Exception e) {
            log.warn("Redis totalCount 조회 실패: {}", e.getMessage());
        }
        // Redis miss 또는 0일 때 MV 카운트 fallback (LAST_7D / LAST_30D 만 해당)
        return switch (period) {
            case LAST_7D  -> mvRankingQueryRepository.countLast7d(keyResolver.anchorDateOf(date), group);
            case LAST_30D -> mvRankingQueryRepository.countLast30d(keyResolver.anchorDateOf(date), group);
            default       -> 0;
        };
    }

    public Integer getProductRank(Long productId, RankingPeriod period, LocalDate date) {
        String key = keyResolver.resolve(period, date, "control");
        return rankingRedisRepository.getRank(key, productId);
    }

    private List<RankEntry> loadRankEntries(RankingPeriod period, LocalDate date, int page, int size, String group) {
        try {
            String zsetKey = keyResolver.resolve(period, date, group);
            List<RankEntry> fromRedis = rankingRedisRepository.getRankings(zsetKey, page, size);
            if (!fromRedis.isEmpty()) {
                return fromRedis;
            }
        } catch (Exception e) {
            log.warn("Redis 랭킹 조회 실패, fallback. period={}, date={}, group={}: {}",
                    period, date, group, e.getMessage());
        }

        return switch (period) {
            // 롤링 랭킹: MV 테이블 직접 조회 (확정된 TOP N 을 그대로 투영, bucket SUM 재계산 아님)
            case LAST_7D, LAST_30D -> fallbackFromMv(period, date, page, size, group);
            // 실시간/일간: 기존 bucket 집계 재계산 경로
            case REALTIME, DAILY   -> fallbackFromBucketAggregation(period, date, page, size);
        };
    }

    private List<RankEntry> fallbackFromMv(RankingPeriod period, LocalDate date, int page, int size, String group) {
        try {
            LocalDate anchorDate = keyResolver.anchorDateOf(date);
            int offset = page * size;
            List<MvRankEntry> rows = switch (period) {
                case LAST_7D  -> mvRankingQueryRepository.findLast7d(anchorDate, group, offset, size);
                case LAST_30D -> mvRankingQueryRepository.findLast30d(anchorDate, group, offset, size);
                default -> List.of();
            };
            return rows.stream()
                    .map(r -> new RankEntry(r.productId(), r.score(), r.rankPosition()))
                    .toList();
        } catch (Exception e) {
            log.error("MV fallback 실패. period={}, date={}, group={}", period, date, group, e);
            return List.of();
        }
    }

    private List<RankEntry> fallbackFromBucketAggregation(RankingPeriod period, LocalDate date, int page, int size) {
        try {
            LocalDateTime from = calculateFrom(period, date);
            LocalDateTime to = RankingDateUtils.kstDateToUtcBoundary(date.plusDays(1));

            Map<Long, Double> allScores = fallbackAggregator.aggregate(from, to);

            if (allScores.isEmpty()) {
                return List.of();
            }

            int startRank = page * size + 1;
            AtomicInteger rankCounter = new AtomicInteger(startRank);

            return allScores.entrySet().stream()
                    .skip((long) page * size)
                    .limit(size)
                    .map(e -> new RankEntry(e.getKey(), e.getValue(), rankCounter.getAndIncrement()))
                    .toList();
        } catch (Exception e) {
            log.error("bucket 집계 fallback 실패. period={}, date={}", period, date, e);
            return List.of();
        }
    }

    private LocalDateTime calculateFrom(RankingPeriod period, LocalDate date) {
        return switch (period) {
            case REALTIME -> {
                long epochSecond = java.time.Instant.now().getEpochSecond();
                long currentBucket = epochSecond - (epochSecond % 300);
                yield LocalDateTime.ofInstant(
                        java.time.Instant.ofEpochSecond(currentBucket - 3600 + 300),
                        java.time.ZoneOffset.UTC);
            }
            case DAILY -> RankingDateUtils.kstDateToUtcBoundary(date);
            // LAST_7D / LAST_30D 는 MV fallback 경로라 여기 들어올 일 없음
            case LAST_7D, LAST_30D -> throw new IllegalStateException(
                    "rolling period fallback 은 MV 경로로만 처리되어야 한다: " + period);
        };
    }
}

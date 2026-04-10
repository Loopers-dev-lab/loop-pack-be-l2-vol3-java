package com.loopers.application.ranking;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.loopers.domain.ranking.RankEntry;
import com.loopers.domain.ranking.RankingPeriod;
import com.loopers.infrastructure.ranking.RankingRedisRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Service
public class RankingService {

    private final RankingRedisRepository rankingRedisRepository;
    private final RankingKeyResolver keyResolver;
    private final RankingFallbackAggregator fallbackAggregator;
    private final ExperimentGroupResolver experimentGroupResolver;

    private final Cache<String, List<RankEntry>> rankingCache = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofSeconds(30))
            .maximumSize(200)
            .build();

    public RankingService(RankingRedisRepository rankingRedisRepository,
                          RankingKeyResolver keyResolver,
                          RankingFallbackAggregator fallbackAggregator,
                          ExperimentGroupResolver experimentGroupResolver) {
        this.rankingRedisRepository = rankingRedisRepository;
        this.keyResolver = keyResolver;
        this.fallbackAggregator = fallbackAggregator;
        this.experimentGroupResolver = experimentGroupResolver;
    }

    // Query

    public String resolveGroup(Long userId) {
        return experimentGroupResolver.resolve(userId);
    }

    public List<RankEntry> getRankEntries(RankingPeriod period, LocalDate date, int page, int size, String group) {
        String cacheKey = period + ":" + date + ":" + page + ":" + size + ":" + group;
        return rankingCache.get(cacheKey, key -> loadRankEntries(period, date, page, size, group));
    }

    public long getTotalCount(RankingPeriod period, LocalDate date, String group) {
        String key = keyResolver.resolve(period, date, group);
        try {
            Long count = rankingRedisRepository.getTotalCount(key);
            return count == null ? 0 : count;
        } catch (Exception e) {
            log.warn("Redis totalCount 조회 실패: {}", e.getMessage());
            return 0;
        }
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
            log.warn("Redis 랭킹 조회 실패, DB fallback. period={}, date={}, group={}: {}",
                    period, date, group, e.getMessage());
        }

        return fallbackFromDb(period, date, page, size);
    }

    private List<RankEntry> fallbackFromDb(RankingPeriod period, LocalDate date, int page, int size) {
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
            log.error("DB fallback도 실패. period={}, date={}", period, date, e);
            return List.of();
        }
    }

    private LocalDateTime calculateFrom(RankingPeriod period, LocalDate date) {
        return switch (period) {
            case DAILY -> RankingDateUtils.kstDateToUtcBoundary(date);
            case WEEKLY -> {
                LocalDate monday = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
                yield RankingDateUtils.kstDateToUtcBoundary(monday);
            }
            case MONTHLY -> {
                LocalDate firstOfMonth = date.withDayOfMonth(1);
                yield RankingDateUtils.kstDateToUtcBoundary(firstOfMonth);
            }
        };
    }
}

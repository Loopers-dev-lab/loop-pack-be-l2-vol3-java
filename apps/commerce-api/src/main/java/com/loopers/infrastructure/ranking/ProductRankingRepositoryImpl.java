package com.loopers.infrastructure.ranking;

import com.loopers.domain.ranking.RankingDetailInfo;
import com.loopers.domain.ranking.RankingInfo;
import com.loopers.domain.ranking.ProductRankingRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public class ProductRankingRepositoryImpl implements ProductRankingRepository {

    private static final String RANKING_KEY_PREFIX = "ranking:all:";

    private final RedisTemplate<String, String> redisTemplate;
    private final WeeklyProductRankJpaRepository weeklyRepository;
    private final MonthlyProductRankJpaRepository monthlyRepository;

    public ProductRankingRepositoryImpl(
        @Qualifier("defaultRedisTemplate") RedisTemplate<String, String> redisTemplate,
        WeeklyProductRankJpaRepository weeklyRepository,
        MonthlyProductRankJpaRepository monthlyRepository
    ) {
        this.redisTemplate = redisTemplate;
        this.weeklyRepository = weeklyRepository;
        this.monthlyRepository = monthlyRepository;
    }

    @Override
    public List<RankingInfo> getDailyRankings(String dateKey, int cursor, int size) {
        String key = RANKING_KEY_PREFIX + dateKey;
        Set<ZSetOperations.TypedTuple<String>> tuples =
            redisTemplate.opsForZSet().reverseRangeWithScores(key, cursor, (long) cursor + size - 1);

        if (tuples == null || tuples.isEmpty()) {
            return List.of();
        }

        List<RankingInfo> rankings = new ArrayList<>();
        int rank = cursor + 1;
        for (ZSetOperations.TypedTuple<String> tuple : tuples) {
            rankings.add(new RankingInfo(
                Long.parseLong(tuple.getValue()),
                rank++,
                tuple.getScore(),
                null, null, null, null
            ));
        }
        return rankings;
    }

    @Override
    public Optional<RankingDetailInfo> getProductDailyRanking(String dateKey, Long productId) {
        String key = RANKING_KEY_PREFIX + dateKey;
        Long rank = redisTemplate.opsForZSet().reverseRank(key, String.valueOf(productId));
        if (rank == null) {
            return Optional.empty();
        }
        Double score = redisTemplate.opsForZSet().score(key, String.valueOf(productId));
        if (score == null) {
            return Optional.empty();
        }
        return Optional.of(new RankingDetailInfo(rank + 1, score));
    }

    @Override
    public List<RankingInfo> getWeeklyRankings(String yearWeek, int cursor, int size) {
        return weeklyRepository.findByYearWeekAndRankGreaterThanOrderByRankAsc(
            yearWeek, cursor, PageRequest.of(0, size)
        ).stream()
            .map(entity -> new RankingInfo(
                entity.getProductId(),
                entity.getRank(),
                entity.getScore(),
                entity.getLikeCount(),
                entity.getViewCount(),
                entity.getSalesCount(),
                entity.getSalesAmount()
            ))
            .toList();
    }

    @Override
    public List<RankingInfo> getMonthlyRankings(String yearMonth, int cursor, int size) {
        return monthlyRepository.findByYearMonthAndRankGreaterThanOrderByRankAsc(
            yearMonth, cursor, PageRequest.of(0, size)
        ).stream()
            .map(entity -> new RankingInfo(
                entity.getProductId(),
                entity.getRank(),
                entity.getScore(),
                entity.getLikeCount(),
                entity.getViewCount(),
                entity.getSalesCount(),
                entity.getSalesAmount()
            ))
            .toList();
    }
}

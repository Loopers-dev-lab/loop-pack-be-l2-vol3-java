package com.loopers.infrastructure.ranking;

import com.loopers.config.redis.RedisConfig;
import com.loopers.domain.ranking.RankingEntry;
import com.loopers.domain.ranking.RankingRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * `RankingRepository` 의 Redis 구현.
 *
 * <p>`masterRedisTemplate` 을 사용한다 — 기존 대기열 ZSET 컴포넌트의 관례 및
 * "streamer 가 방금 쓴 값을 api 가 바로 읽어야 한다" 는 요구사항과 정합.
 */
@Repository
public class RedisRankingRepository implements RankingRepository {

    private final RedisTemplate<String, String> masterRedisTemplate;

    public RedisRankingRepository(
            @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER) RedisTemplate<String, String> masterRedisTemplate
    ) {
        this.masterRedisTemplate = masterRedisTemplate;
    }

    @Override
    public List<RankingEntry> getTopN(String key, int pageOneBased, int size) {
        if (key == null || size <= 0) return Collections.emptyList();
        int page = Math.max(pageOneBased, 1);
        long start = (long) (page - 1) * size;
        long end = start + size - 1;

        Set<ZSetOperations.TypedTuple<String>> tuples =
                masterRedisTemplate.opsForZSet().reverseRangeWithScores(key, start, end);
        if (tuples == null || tuples.isEmpty()) return Collections.emptyList();

        List<RankingEntry> result = new ArrayList<>(tuples.size());
        int index = 0;
        for (ZSetOperations.TypedTuple<String> tuple : tuples) {
            String value = tuple.getValue();
            Double score = tuple.getScore();
            if (value == null || score == null) continue;
            try {
                Long productId = Long.parseLong(value);
                long rank = start + index + 1;  // 1-based
                result.add(new RankingEntry(productId, rank, score));
            } catch (NumberFormatException ignored) {
                // 잘못된 멤버 값은 skip
            }
            index++;
        }
        return result;
    }

    @Override
    public Long getRank(String key, Long productId) {
        if (key == null || productId == null) return null;
        Long zeroBased = masterRedisTemplate.opsForZSet().reverseRank(key, productId.toString());
        return zeroBased == null ? null : zeroBased + 1;
    }

    @Override
    public long getTotal(String key) {
        if (key == null) return 0L;
        Long total = masterRedisTemplate.opsForZSet().zCard(key);
        return total == null ? 0L : total;
    }
}

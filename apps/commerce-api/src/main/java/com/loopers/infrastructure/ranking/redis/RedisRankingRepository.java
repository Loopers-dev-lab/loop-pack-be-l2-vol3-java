package com.loopers.infrastructure.ranking.redis;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Repository;

import com.loopers.domain.ranking.RankingItem;
import com.loopers.domain.ranking.RankingRepository;

/**
 * Redis Sorted Set 기반 랭킹 조회 구현체.
 *
 * <p>ZREVRANGE, ZCARD, ZREVRANK 명령으로 랭킹 데이터를 읽기 전용으로 제공한다.
 * 기본 RedisTemplate(REPLICA_PREFERRED)을 사용하여 읽기 부하를 분산한다.</p>
 */
@Repository
public class RedisRankingRepository implements RankingRepository {

    private final RedisTemplate<String, String> redisTemplate;

    public RedisRankingRepository(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<RankingItem> readTopRanked(String key, int offset, int count) {
        Set<ZSetOperations.TypedTuple<String>> tuples =
                redisTemplate.opsForZSet().reverseRangeWithScores(key, offset, (long) offset + count - 1);

        if (Objects.isNull(tuples) || tuples.isEmpty()) {
            return Collections.emptyList();
        }

        List<RankingItem> items = new ArrayList<>(tuples.size());
        int rank = offset + 1;
        for (ZSetOperations.TypedTuple<String> tuple : tuples) {
            items.add(new RankingItem(
                    rank++,
                    Long.valueOf(Objects.requireNonNull(tuple.getValue())),
                    Objects.requireNonNull(tuple.getScore())
            ));
        }
        return items;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long countAll(String key) {
        Long count = redisTemplate.opsForZSet().zCard(key);
        return Objects.isNull(count) ? 0L : count;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Integer findRank(String key, Long productId) {
        Long rank = redisTemplate.opsForZSet().reverseRank(key, String.valueOf(productId));
        return Objects.isNull(rank) ? null : rank.intValue() + 1;
    }
}

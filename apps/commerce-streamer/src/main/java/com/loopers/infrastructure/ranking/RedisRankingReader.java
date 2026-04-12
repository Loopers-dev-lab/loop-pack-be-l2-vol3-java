package com.loopers.infrastructure.ranking;

import com.loopers.config.redis.RedisConfig;
import com.loopers.domain.ranking.RankingReader;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.function.ObjDoubleConsumer;

/**
 * `RankingReader` 의 Redis 구현.
 *
 * 스케줄러 Carry-Over 경로에서만 사용되며, 페이지 단위 ZRANGE WITHSCORES 로
 * 오늘 키의 전체 엔트리를 청크 순회한다. (전체 로드 OOM 방어)
 */
@Component
public class RedisRankingReader implements RankingReader {

    static final int PAGE_SIZE = 500;

    private final RedisTemplate<String, String> masterRedisTemplate;

    public RedisRankingReader(
            @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER) RedisTemplate<String, String> masterRedisTemplate
    ) {
        this.masterRedisTemplate = masterRedisTemplate;
    }

    @Override
    public void forEachWithScore(String key, ObjDoubleConsumer<Long> consumer) {
        if (key == null || consumer == null) {
            return;
        }
        long offset = 0;
        while (true) {
            Set<ZSetOperations.TypedTuple<String>> tuples =
                    masterRedisTemplate.opsForZSet().rangeWithScores(key, offset, offset + PAGE_SIZE - 1);
            if (tuples == null || tuples.isEmpty()) {
                break;
            }
            for (ZSetOperations.TypedTuple<String> tuple : tuples) {
                String value = tuple.getValue();
                Double score = tuple.getScore();
                if (value == null || score == null) continue;
                try {
                    Long productId = Long.parseLong(value);
                    consumer.accept(productId, score);
                } catch (NumberFormatException ignored) {
                    // 잘못된 멤버는 skip — 운영상 발생하지 않아야 하나 방어
                }
            }
            if (tuples.size() < PAGE_SIZE) {
                break;
            }
            offset += PAGE_SIZE;
        }
    }
}

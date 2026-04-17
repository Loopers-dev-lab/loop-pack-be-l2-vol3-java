package com.loopers.domain.ranking;

import com.loopers.config.redis.RankingKeys;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Redis ZSET 기반 랭킹 조회 서비스.
 *
 * 읽기는 defaultRedisTemplate(REPLICA_PREFERRED)을 사용해 Replica 분산 읽기.
 * 쓰기는 commerce-streamer의 SyncScheduler가 담당하므로 이 서비스는 조회만 수행.
 */
@RequiredArgsConstructor
@Service
public class RankingService {

    private final RedisTemplate<String, String> defaultRedisTemplate;

    /**
     * 일간 랭킹 상위 N개 조회.
     *
     * @param date 조회 날짜
     * @param offset 시작 위치 (0-based)
     * @param size 조회 건수
     * @return (productId, score) 쌍 목록, 높은 점수 순
     */
    public List<ZSetOperations.TypedTuple<String>> findDailyRanking(LocalDate date, long offset, long size) {
        String key = RankingKeys.dailyKey(date);
        Set<ZSetOperations.TypedTuple<String>> result =
                defaultRedisTemplate.opsForZSet().reverseRangeWithScores(key, offset, offset + size - 1);
        if (result == null) return Collections.emptyList();
        return List.copyOf(result);
    }

    /**
     * 일간 랭킹 전체 상품 수 조회 (페이지네이션 totalElements용).
     */
    public long countDailyRanking(LocalDate date) {
        Long count = defaultRedisTemplate.opsForZSet().size(RankingKeys.dailyKey(date));
        return count != null ? count : 0L;
    }

    /**
     * 특정 상품의 일간 랭킹 순위 조회 (1-based).
     * 랭킹에 없으면 null 반환.
     */
    public Long findProductRank(LocalDate date, Long productId) {
        Long rank = defaultRedisTemplate.opsForZSet()
                .reverseRank(RankingKeys.dailyKey(date), String.valueOf(productId));
        return rank != null ? rank + 1 : null;  // 0-based → 1-based
    }

    public List<ZSetOperations.TypedTuple<String>> findHourlyRanking(LocalDate date, int hour, long offset, long size) {
        String key = RankingKeys.hourlyKey(date, hour);
        Set<ZSetOperations.TypedTuple<String>> result =
                defaultRedisTemplate.opsForZSet().reverseRangeWithScores(key, offset, offset + size - 1);
        if (result == null) return Collections.emptyList();
        return List.copyOf(result);
    }

    public long countHourlyRanking(LocalDate date, int hour) {
        Long count = defaultRedisTemplate.opsForZSet().size(RankingKeys.hourlyKey(date, hour));
        return count != null ? count : 0L;
    }
}

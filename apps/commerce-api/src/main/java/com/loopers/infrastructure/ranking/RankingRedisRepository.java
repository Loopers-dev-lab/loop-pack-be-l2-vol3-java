package com.loopers.infrastructure.ranking;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;

@RequiredArgsConstructor
@Repository
public class RankingRedisRepository {

    private static final String KEY_PREFIX = "ranking:all:";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");

    // Replica Preferred — 읽기 전용 조회
    private final RedisTemplate<String, String> redisTemplate;

    /**
     * 날짜 기반 일간 랭킹 상위 N개를 페이징으로 조회한다.
     * ZREVRANGEBYSCORE가 아닌 ZREVRANGE 기반으로 score 내림차순 정렬.
     *
     * @return (productId, score) 쌍 목록, score 내림차순 정렬
     */
    public List<ZSetOperations.TypedTuple<String>> findTopN(LocalDate date, int page, int size) {
        String key = buildKey(date);
        long start = (long) page * size;
        long end = start + size - 1;
        Set<ZSetOperations.TypedTuple<String>> result = redisTemplate.opsForZSet()
            .reverseRangeWithScores(key, start, end);
        if (result == null) {
            return List.of();
        }
        return List.copyOf(result);
    }

    /**
     * 특정 상품의 오늘 기준 랭킹 순위를 반환한다.
     * ZREVRANK는 0-based이므로 +1 변환해서 반환한다.
     *
     * @return 1-based 순위. 랭킹에 없으면 null.
     */
    public Integer findRank(LocalDate date, Long productId) {
        String key = buildKey(date);
        Long rank = redisTemplate.opsForZSet().reverseRank(key, String.valueOf(productId));
        return rank != null ? (int) (rank + 1) : null;
    }

    public static String buildKey(LocalDate date) {
        return KEY_PREFIX + date.format(DATE_FORMATTER);
    }
}

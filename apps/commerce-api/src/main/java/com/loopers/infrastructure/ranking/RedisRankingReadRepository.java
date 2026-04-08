package com.loopers.infrastructure.ranking;

import com.loopers.domain.ranking.RankingReadRepository;
import com.loopers.domain.ranking.RankingZsetEntry;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalLong;
import java.util.Set;

/**
 * Redis ZSET 기반 랭킹 읽기. {@code ZREVRANGE}/{@code ZCARD} 등 상위 커맨드로 슬라이스·개수만 조회한다.
 * 랭킹 페이지 JSON 전체를 캐싱하지 않으며, 단일 ZSET 조회로 충분한 경우가 많다(설계 §4.2.3).
 */
@Repository
public class RedisRankingReadRepository implements RankingReadRepository {

    private final RedisTemplate<String, String> redisTemplate;

    public RedisRankingReadRepository(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 랭킹 ZSET의 크기를 조회한다.
     *
     * @param key 랭킹 ZSET 키
     * @return 랭킹 ZSET의 크기
     */
    @Override
    public long count(String key) {
        Long size = redisTemplate.opsForZSet().size(key);
        return size == null ? 0L : size;
    }

    /**
     * 랭킹 ZSET의 범위를 조회한다.
     *
     * @param key 랭킹 ZSET 키
     * @param start 시작 인덱스
     * @param end 종료 인덱스
     * @return 랭킹 ZSET의 범위
     */
    @Override
    public List<RankingZsetEntry> findReverseRangeWithScores(String key, long start, long end) {
        Set<ZSetOperations.TypedTuple<String>> tuples =
                redisTemplate.opsForZSet().reverseRangeWithScores(key, start, end);
        if (tuples == null || tuples.isEmpty()) {
            return List.of();
        }
        
        List<RankingZsetEntry> out = new ArrayList<>(tuples.size());
        for (ZSetOperations.TypedTuple<String> t : tuples) {
            if (t.getValue() == null || t.getScore() == null) {
                continue;
            }
            out.add(new RankingZsetEntry(t.getValue(), t.getScore()));
        }
        return out;
    }

    /**
     * 점수 내림차순 기준 전역 순위(1-based). 최고점이 1위.
     * ZSET에 member가 없으면 empty를 반환한다.
     *
     * @param key 랭킹 ZSET 키
     * @param member 랭킹 멤버
     * @return 점수 내림차순 기준 전역 순위(1-based)
     *          ZSET에 member가 없으면 empty
     */
    @Override
    public OptionalLong findOneBasedReverseRank(String key, String member) {
        Long zeroBased = redisTemplate.opsForZSet().reverseRank(key, member);
        if (zeroBased == null) {
            return OptionalLong.empty();
        }
        return OptionalLong.of(zeroBased + 1L);
    }
}

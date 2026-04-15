package com.loopers.infrastructure.ranking;

import com.loopers.domain.ranking.RankingSnapshotRepository;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * {@code ZUNIONSTORE}로 일간 ZSET을 스냅샷 키에 복제하고 {@code EXPIRE}로 TTL을 건다.
 */
@Repository
public class RedisRankingSnapshotRepository implements RankingSnapshotRepository {

    private final RedisTemplate<String, String> redisTemplate;

    public RedisRankingSnapshotRepository(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 일간 ZSET을 스냅샷 키에 복제하고 TTL을 설정한다.
     * @param sourceKey 일간 랭킹 키
     * @param snapshotKey 스냅샷 키
     * @param ttl 스냅샷 보존 기간
     * @return 복사 후 ZSET 원소 수
     */
    @Override
    public long materialize(String sourceKey, String snapshotKey, Duration ttl) {
        byte[] dest = snapshotKey.getBytes(StandardCharsets.UTF_8);
        byte[] src = sourceKey.getBytes(StandardCharsets.UTF_8);
        Long count = redisTemplate.execute((RedisCallback<Long>) connection -> connection.zUnionStore(dest, src));
        long size = count == null ? 0L : count;
        redisTemplate.expire(snapshotKey, ttl);
        return size;
    }

    /**
     * 스냅샷 키가 존재하는지 여부
     * @param snapshotKey 스냅샷 키
     * @return 존재하면 true
     */
    @Override
    public boolean exists(String snapshotKey) {
        Boolean exists = redisTemplate.hasKey(snapshotKey);
        return Boolean.TRUE.equals(exists);
    }
}

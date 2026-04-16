package com.loopers.infrastructure.queue;

import com.loopers.config.redis.RedisConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Component
public class QueueCacheService {

    static final String QUEUE_KEY = "waiting-queue";
    static final String TOKEN_KEY_PREFIX = "entry-token:";

    private final Duration tokenTtl;
    private final RedisTemplate<String, String> defaultRedisTemplate;
    private final RedisTemplate<String, String> masterRedisTemplate;

    public QueueCacheService(
        @Value("${queue.token.ttl-seconds:300}") long tokenTtlSeconds,
        RedisTemplate<String, String> defaultRedisTemplate,
        @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER) RedisTemplate<String, String> masterRedisTemplate
    ) {
        this.tokenTtl = Duration.ofSeconds(tokenTtlSeconds);
        this.defaultRedisTemplate = defaultRedisTemplate;
        this.masterRedisTemplate = masterRedisTemplate;
    }

    /**
     * 대기열 진입.
     * score = 진입 시각(timestamp)으로 선착순 보장.
     * member = userId로 중복 진입 자동 방지 (Sorted Set 특성).
     *
     * @return true: 신규 진입, false: 이미 대기 중
     */
    public boolean enter(Long userId) {
        double score = System.currentTimeMillis();
        Boolean added = masterRedisTemplate.opsForZSet()
            .addIfAbsent(QUEUE_KEY, String.valueOf(userId), score);
        return Boolean.TRUE.equals(added);
    }

    /**
     * 현재 순번 조회 (1-based)
     */
    public Optional<Long> getRank(Long userId) {
        Long rank = defaultRedisTemplate.opsForZSet().rank(QUEUE_KEY, String.valueOf(userId));
        return Optional.ofNullable(rank).map(r -> r + 1);
    }

    /**
     * 전체 대기 인원
     */
    public long getTotalCount() {
        Long count = defaultRedisTemplate.opsForZSet().zCard(QUEUE_KEY);
        return count != null ? count : 0L;
    }

    /**
     * 앞에서 N명 꺼내기 (스케줄러용, Thundering Herd 완화)
     */
    public Set<ZSetOperations.TypedTuple<String>> popN(int count) {
        return masterRedisTemplate.opsForZSet().popMin(QUEUE_KEY, count);
    }

    /**
     * 입장 토큰 발급 (TTL 5분)
     */
    public String issueToken(Long userId) {
        String token = UUID.randomUUID().toString();
        masterRedisTemplate.opsForValue().set(tokenKey(userId), token, tokenTtl);
        return token;
    }

    /**
     * 토큰 조회
     */
    public Optional<String> getToken(Long userId) {
        String token = defaultRedisTemplate.opsForValue().get(tokenKey(userId));
        return Optional.ofNullable(token);
    }

    /**
     * 토큰 검증
     */
    public boolean validateToken(Long userId, String token) {
        return getToken(userId).map(t -> t.equals(token)).orElse(false);
    }

    /**
     * 토큰 삭제 (주문 완료 후)
     */
    public void deleteToken(Long userId) {
        masterRedisTemplate.delete(tokenKey(userId));
    }

    /**
     * 오래된 대기열 항목 정리.
     * ZADD 항목은 TTL이 없으므로, 서버 장애 등으로 처리되지 못한 항목이 잔류할 수 있음.
     * score(진입 시각) 기준으로 maxAgeMs 이상 경과한 항목을 제거.
     *
     * @return 제거된 항목 수
     */
    public long cleanStaleEntries(long maxAgeMs) {
        double maxScore = System.currentTimeMillis() - maxAgeMs;
        Long removed = masterRedisTemplate.opsForZSet().removeRangeByScore(QUEUE_KEY, 0, maxScore);
        return removed != null ? removed : 0L;
    }

    private String tokenKey(Long userId) {
        return TOKEN_KEY_PREFIX + userId;
    }
}

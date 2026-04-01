package com.loopers.infrastructure.queue;

import com.loopers.config.redis.RedisConfig;
import com.loopers.domain.queue.WaitingQueueRepository;
import com.loopers.domain.queue.QueuePositionSnapshot;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Redis ZSET 기반 대기열 저장소 구현.
 * <p>
 * 키는 {@code queue:waiting:{eventId}} 형태이며, 멤버는 {@code userId}, score는 대기 순서를 나타낸다.
 * 작은 score일수록 먼저 처리되며, {@link #popOldest(String, long)}에서 {@code ZPOPMIN}으로 꺼낸다.
 */
@Repository
public class RedisWaitingQueueRepository implements WaitingQueueRepository {

    private static final String WAITING_QUEUE_KEY_PREFIX = "queue:waiting:";

    private static final DefaultRedisScript<List<Long>> RANK_AND_COUNT_SCRIPT = new DefaultRedisScript<>();

    /** Lua 스크립트: ZRANK + ZCARD를 한 번에 실행해 레이스 윈도우를 줄인다. */
    static {
        RANK_AND_COUNT_SCRIPT.setScriptText(
                """
                        local rank = redis.call('ZRANK', KEYS[1], ARGV[1])
                        if rank == false then
                          return {-1}
                        end
                        local count = redis.call('ZCARD', KEYS[1])
                        return {rank, count}
                        """
        );
        @SuppressWarnings("unchecked")
        Class<List<Long>> resultType = (Class<List<Long>>) (Class<?>) List.class;
        RANK_AND_COUNT_SCRIPT.setResultType(resultType);
    }

    private final RedisTemplate<String, String> redisTemplate;

    public RedisWaitingQueueRepository(
        @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER) RedisTemplate<String, String> redisTemplate
    ) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public boolean addIfAbsent(String eventId, Long userId, long score) {
        // 동일 userId가 이미 있으면 false. 중복 대기 진입 방지.
        Boolean added = redisTemplate.opsForZSet().addIfAbsent(waitingKey(eventId), member(userId), score);
        return Boolean.TRUE.equals(added);
    }

    @Override
    public Optional<Long> findRank(String eventId, Long userId) {
        Long rank = redisTemplate.opsForZSet().rank(waitingKey(eventId), member(userId));
        return Optional.ofNullable(rank);
    }

    @Override
    public long countWaiting(String eventId) {
        Long size = redisTemplate.opsForZSet().zCard(waitingKey(eventId));
        return size == null ? 0L : size;
    }

    @Override
    public Optional<QueuePositionSnapshot> findPositionSnapshot(String eventId, Long userId) {
        // ZRANK + ZCARD를 Lua로 한 번에 실행해 레이스 윈도우를 줄인다.
        List<Long> raw = redisTemplate.execute(
                RANK_AND_COUNT_SCRIPT,
                List.of(waitingKey(eventId)),
                member(userId)
        );
        if (raw == null || raw.isEmpty()) {
            return Optional.empty();
        }
        if (raw.size() == 1 && raw.get(0) != null && raw.get(0).longValue() == -1L) {
            return Optional.empty();
        }
        if (raw.size() != 2) {
            // 스크립트 반환 형태가 예상과 다르면 안전하게 비어 있음으로 처리.
            return Optional.empty();
        }
        // rank는 0-based position, count는 전체 대기 인원.
        return Optional.of(new QueuePositionSnapshot(raw.get(0), raw.get(1)));
    }

    @Override
    public List<Long> popOldest(String eventId, long count) {
        // ZPOPMIN: 가장 작은 score(가장 오래 대기한 사용자)부터 count개 제거+반환.
        Set<ZSetOperations.TypedTuple<String>> tuples = redisTemplate.opsForZSet().popMin(waitingKey(eventId), count);
        if (tuples == null || tuples.isEmpty()) {
            return List.of();
        }
        return tuples.stream()
            .map(ZSetOperations.TypedTuple::getValue)
            .filter(value -> value != null && !value.isBlank())
            .map(Long::parseLong)
            .toList();
    }

    private String waitingKey(String eventId) {
        return WAITING_QUEUE_KEY_PREFIX + eventId;
    }

    private String member(Long userId) {
        return String.valueOf(userId);
    }
}


package com.loopers.infrastructure.queue;

import com.loopers.domain.queue.QueueConstants;
import com.loopers.domain.queue.QueueEntry;
import com.loopers.domain.queue.QueueRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import static com.loopers.config.redis.RedisConfig.REDIS_TEMPLATE_MASTER;

// Redis Sorted Set을 이용한 대기열 저장소 구현체.
// key: "waiting-queue", member: userId, score: 진입 시각(ms)으로 구성된다.
// Sorted Set의 score 기반 정렬로 선착순(FIFO) 대기열을 구현하며,
// ZRANK로 순번 조회, ZRANGE로 배치 조회가 가능하다.
// Master 전용 RedisTemplate을 주입받아 쓰기 작업의 일관성을 보장한다.
@Repository
public class QueueRedisRepository implements QueueRepository {

    private static final String QUEUE_KEY = QueueConstants.QUEUE_KEY;

    private final RedisTemplate<String, String> redisTemplate;

    public QueueRedisRepository(
            @Qualifier(REDIS_TEMPLATE_MASTER) RedisTemplate<String, String> redisTemplate
    ) {
        this.redisTemplate = redisTemplate;
    }

    // ZADD NX: member가 없을 때만 추가. 이미 대기 중인 유저의 score를 덮어쓰지 않는다.
    @Override
    public boolean enter(Long userId, double score) {
        Boolean added = redisTemplate.opsForZSet().addIfAbsent(QUEUE_KEY, userId.toString(), score);
        return Boolean.TRUE.equals(added);
    }

    // ZRANK: 해당 member의 0-based 순위를 반환한다. 대기열에 없으면 null.
    @Override
    public Optional<Long> getRank(Long userId) {
        Long rank = redisTemplate.opsForZSet().rank(QUEUE_KEY, userId.toString());
        return Optional.ofNullable(rank);
    }

    // ZCARD: Sorted Set의 전체 member 수를 반환한다.
    @Override
    public long getTotalCount() {
        Long count = redisTemplate.opsForZSet().zCard(QUEUE_KEY);
        return count != null ? count : 0L;
    }

    // ZPOPMIN: score가 가장 낮은(먼저 진입한) 유저부터 count명을 원자적으로 꺼낸다.
    // 조회와 제거가 하나의 Redis 명령으로 수행되므로 peek+remove보다 안전하다.
    // 반환되는 QueueEntry에 score를 포함하여 실패 시 원래 순서로 재삽입할 수 있다.
    @Override
    public List<QueueEntry> popFront(int count) {
        Set<ZSetOperations.TypedTuple<String>> tuples =
                redisTemplate.opsForZSet().popMin(QUEUE_KEY, count);
        if (tuples == null || tuples.isEmpty()) {
            return List.of();
        }
        return tuples.stream()
                .map(tuple -> new QueueEntry(
                        Long.parseLong(Objects.requireNonNull(tuple.getValue())),
                        Objects.requireNonNull(tuple.getScore())))
                .toList();
    }
}


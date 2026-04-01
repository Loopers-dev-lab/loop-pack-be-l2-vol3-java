package com.loopers.infrastructure.queue;

import com.loopers.domain.queue.QueueEntry;
import com.loopers.domain.queue.QueueRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Set;

/**
 * Redis Sorted Set 기반 대기열 Repository 구현체.
 *
 * <p>기존 {@code PaymentLockService}와 동일하게 {@code redisTemplateMaster}를 사용하여
 * 읽기/쓰기 모두 Master에서 수행한다. Replica 복제 지연으로 인한
 * "ZADD 직후 ZRANK null" 문제를 방지한다.</p>
 *
 * <p>Redis 키: {@code order:waiting-queue} (Sorted Set)</p>
 */
@Repository
public class QueueRepositoryImpl implements QueueRepository {

    private final RedisTemplate<String, String> redisTemplateMaster;

    private static final String QUEUE_KEY = "order:waiting-queue";

    public QueueRepositoryImpl(
            @Qualifier("redisTemplateMaster") RedisTemplate<String, String> redisTemplateMaster) {
        this.redisTemplateMaster = redisTemplateMaster;
    }

    /**
     * ZADD NX — 이미 존재하면 무시 (중복 방지 + 새치기 방지).
     *
     * <p>NX 옵션: member가 없을 때만 추가. 이미 있으면 score 갱신하지 않음 → 멱등.
     * 같은 userId로 재요청해도 기존 순번(score) 유지.</p>
     */
    @Override
    public boolean addIfAbsent(Long userId, double score) {
        Boolean added = redisTemplateMaster.opsForZSet()
                .addIfAbsent(QUEUE_KEY, String.valueOf(userId), score);
        return Boolean.TRUE.equals(added);
    }

    /**
     * ZRANK — 0-based 순번. 대기열에 없으면 null.
     *
     * <p>score(타임스탬프)가 작을수록 앞순번. ZRANK는 score 오름차순 기준.</p>
     */
    @Override
    public Long getRank(Long userId) {
        return redisTemplateMaster.opsForZSet()
                .rank(QUEUE_KEY, String.valueOf(userId));
    }

    /**
     * ZCARD — 전체 대기 인원.
     */
    @Override
    public long getSize() {
        Long size = redisTemplateMaster.opsForZSet().size(QUEUE_KEY);
        return size != null ? size : 0;
    }

    /**
     * ZPOPMIN — 앞에서 count명을 원자적으로 제거하고 반환.
     *
     * <p>스케줄러가 100ms마다 호출. 원자적이므로 다중 인스턴스에서도 중복 추출 없음.</p>
     */
    @Override
    public List<QueueEntry> popMin(int count) {
        Set<ZSetOperations.TypedTuple<String>> tuples =
                redisTemplateMaster.opsForZSet().popMin(QUEUE_KEY, count);

        if (tuples == null || tuples.isEmpty()) {
            return List.of();
        }

        return tuples.stream()
                .map(t -> new QueueEntry(
                        Long.parseLong(t.getValue()),
                        t.getScore() != null ? t.getScore() : 0
                ))
                .toList();
    }
}

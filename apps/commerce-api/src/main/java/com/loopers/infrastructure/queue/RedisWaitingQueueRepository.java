package com.loopers.infrastructure.queue;

import com.loopers.domain.queue.WaitingQueueRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

import java.util.Collections;
import java.util.List;
import java.util.Set;

@Repository
@RequiredArgsConstructor
public class RedisWaitingQueueRepository implements WaitingQueueRepository {

    static final String QUEUE_KEY = "waiting-queue:order";

    private final RedisTemplate<String, String> redisTemplateMaster;

    @Override
    public boolean enqueue(Long userId, double score) {
        // ZADD (NX 없음) — 재진입 시 score(타임스탬프)가 현재 시각으로 갱신 → 맨 뒤로 이동
        // POST /enter는 명시적 진입 행동이므로, 재호출 시 순번 초기화가 정책적으로 명확하다
        return Boolean.TRUE.equals(
                redisTemplateMaster.opsForZSet().add(QUEUE_KEY, String.valueOf(userId), score)
        );
    }

    @Override
    public Long getPosition(Long userId) {
        // ZRANK — 0-based rank, 큐에 없으면 null
        return redisTemplateMaster.opsForZSet().rank(QUEUE_KEY, String.valueOf(userId));
    }

    @Override
    public long getTotalCount() {
        Long count = redisTemplateMaster.opsForZSet().zCard(QUEUE_KEY);
        return count != null ? count : 0;
    }

    @Override
    public List<Long> peekBatch(int batchSize) {
        // ZRANGE 0 (batchSize-1) — 큐에서 제거하지 않고 상위 N명 조회
        Set<String> members = redisTemplateMaster.opsForZSet().range(QUEUE_KEY, 0, batchSize - 1);
        if (members == null || members.isEmpty()) {
            return Collections.emptyList();
        }
        return members.stream()
                .map(Long::parseLong)
                .toList();
    }

    @Override
    public void remove(Long userId) {
        // ZREM — 폴링 API에서 토큰 확인 후 큐에서 제거
        redisTemplateMaster.opsForZSet().remove(QUEUE_KEY, String.valueOf(userId));
    }
}

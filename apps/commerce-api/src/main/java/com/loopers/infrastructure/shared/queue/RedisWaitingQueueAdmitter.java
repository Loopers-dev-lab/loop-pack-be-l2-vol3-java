package com.loopers.infrastructure.shared.queue;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import com.loopers.config.redis.RedisConfig;
import com.loopers.support.queue.WaitingQueueAdmitter;

/**
 * 개별 Redis 명령어 기반 대기열 → 토큰 발급 전환 구현체.
 *
 * <p>ZRANGE(조회) → SET NX EX(토큰 발급) → ZREM(대기열 제거) 순서로
 * 개별 명령어를 실행한다. SET NX의 멱등성으로 중간 실패 시에도 다음 사이클에서 자연 복구된다.</p>
 */
@Component
public class RedisWaitingQueueAdmitter implements WaitingQueueAdmitter {

    private static final String WAITING_KEY = "queue:waiting";
    private static final int TOKEN_TTL_SECONDS = 120;

    private final RedisTemplate<String, String> redisTemplate;

    public RedisWaitingQueueAdmitter(
            @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER) RedisTemplate<String, String> redisTemplate
    ) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public List<Long> admit(int count) {
        if (count <= 0) {
            return Collections.emptyList();
        }

        Set<String> members = fetchWaitingMembers(count);
        if (members == null || members.isEmpty()) {
            return Collections.emptyList();
        }

        List<String> admitted = issueTokens(members);
        removeFromWaitingQueue(admitted);

        return admitted.stream()
                .map(Long::valueOf)
                .toList();
    }

    private Set<String> fetchWaitingMembers(int count) {
        return redisTemplate.opsForZSet()
                .range(WAITING_KEY, 0, count - 1);
    }

    private List<String> issueTokens(Set<String> members) {
        List<String> admitted = new ArrayList<>();
        for (String member : members) {
            Boolean success = redisTemplate.opsForValue()
                    .setIfAbsent(
                            "entry-token:" + member,
                            UUID.randomUUID().toString(),
                            Duration.ofSeconds(TOKEN_TTL_SECONDS)
                    );
            if (Boolean.TRUE.equals(success)) {
                admitted.add(member);
            }
        }
        return admitted;
    }

    private void removeFromWaitingQueue(List<String> admitted) {
        if (admitted.isEmpty()) {
            return;
        }
        redisTemplate.opsForZSet()
                .remove(WAITING_KEY, admitted.toArray());
    }
}

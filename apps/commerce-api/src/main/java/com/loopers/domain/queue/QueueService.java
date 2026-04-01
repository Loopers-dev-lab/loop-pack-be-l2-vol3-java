package com.loopers.domain.queue;

import com.loopers.config.redis.RedisConfig;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Component;

import java.util.Set;

@EnableConfigurationProperties(QueueProperties.class)
@RequiredArgsConstructor
@Component
public class QueueService {

    private static final String QUEUE_KEY = "queue:waiting";

    private final RedisTemplate<String, String> redisTemplate;

    @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER)
    private final RedisTemplate<String, String> masterRedisTemplate;

    /**
     * 대기열 진입. 이미 대기 중이면 예외 처리
     * ZADD NX - member가 없을 때만 추가 (중복 방지)
     */
    public Long enter(Long userId) {
        Boolean added = masterRedisTemplate.opsForZSet()
                .addIfAbsent(QUEUE_KEY, String.valueOf(userId), System.currentTimeMillis());

        if (Boolean.FALSE.equals(added)) {
            throw new CoreException(ErrorType.CONFLICT, "이미 대기열에 진입한 유저입니다.");
        }

        return getRank(userId);
    }

    /**
     * 내 순번 조회
     * ZRANK - 0-based이므로 +1해서 1-based로 반환한다.
     * 대기열에 없으면 null 반환
     */
    public Long getRank(Long userId) {
        Long rank = redisTemplate.opsForZSet()
                .rank(QUEUE_KEY, String.valueOf(userId));

        if (rank == null) {
            return null;
        }

        return rank + 1;
    }

    /**
     * 전체 대기 인원
     * ZCARD - Sorted Set의 전체 member 수
     */
    public Long getQueueSize() {
        Long size = redisTemplate.opsForZSet().zCard(QUEUE_KEY);
        return size != null ? size : 0L;
    }

    /**
     * 앞에서 N명 꺼내기 (스케줄러가 호출)
     * ZPOPMIN - score가 가장 낮은(= 가장 먼저 진입한) N명을 꺼내면서 삭제
     * 주의점 : 꺼내면 대기열에서 사라짐. 조회만 하려면 "ZRANGE" 사용
     */
    public Set<ZSetOperations.TypedTuple<String>> popUsers(int count) {
        return masterRedisTemplate.opsForZSet().popMin(QUEUE_KEY, count);
    }
}

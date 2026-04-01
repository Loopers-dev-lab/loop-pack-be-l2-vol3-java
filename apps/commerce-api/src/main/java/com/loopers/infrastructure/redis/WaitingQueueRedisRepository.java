package com.loopers.infrastructure.redis;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations.TypedTuple;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Set;

/**
 * 주문 대기열 Redis 저장소.
 *
 * <p>Sorted Set을 활용하여 FIFO 대기열을 구현한다.
 * Score는 진입 시각(millis)으로 설정하여 선착순 보장.</p>
 */
@Slf4j
@Component
public class WaitingQueueRedisRepository {

    private static final String KEY = "queue:waiting:order";

    private final RedisTemplate<String, String> readTemplate;
    private final RedisTemplate<String, String> writeTemplate;

    public WaitingQueueRedisRepository(
        RedisTemplate<String, String> readTemplate,
        @Qualifier("redisTemplateMaster") RedisTemplate<String, String> writeTemplate
    ) {
        this.readTemplate = readTemplate;
        this.writeTemplate = writeTemplate;
    }

    /**
     * 대기열 진입. 중복 시 기존 순번 유지 (ZADD NX).
     *
     * @return true: 신규 진입, false: 이미 대기 중
     */
    public boolean add(Long memberId) {
        Boolean added = writeTemplate.opsForZSet()
            .addIfAbsent(KEY, String.valueOf(memberId), System.currentTimeMillis());
        return Boolean.TRUE.equals(added);
    }

    /**
     * 현재 순번 조회 (0-based).
     *
     * @return 순번 (큐에 없으면 null)
     */
    public Long getRank(Long memberId) {
        return readTemplate.opsForZSet().rank(KEY, String.valueOf(memberId));
    }

    /**
     * 전체 대기 인원.
     */
    public long size() {
        Long size = readTemplate.opsForZSet().zCard(KEY);
        return size != null ? size : 0;
    }

    /**
     * 앞에서 N명 꺼내기 (ZPOPMIN).
     */
    public Set<TypedTuple<String>> popMin(int count) {
        Set<TypedTuple<String>> result = writeTemplate.opsForZSet().popMin(KEY, count);
        return result != null ? result : Collections.emptySet();
    }

    /**
     * 특정 유저 제거.
     */
    public void remove(Long memberId) {
        writeTemplate.opsForZSet().remove(KEY, String.valueOf(memberId));
    }
}

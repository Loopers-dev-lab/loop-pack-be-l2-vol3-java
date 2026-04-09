package com.loopers.infrastructure.redis;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations.TypedTuple;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
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
    private static final String TOKEN_KEY_PREFIX = "queue:token:";

    /**
     * ZPOPMIN + 토큰 발급을 원자적으로 실행하는 Lua 스크립트.
     *
     * <p>KEYS[1] = queue:waiting:order</p>
     * <p>ARGV[1] = 배치 크기, ARGV[2] = 토큰 TTL(초)</p>
     * <p>반환: 발급된 memberId 목록</p>
     */
    private static final String POP_AND_ISSUE_SCRIPT =
        "local members = redis.call('ZPOPMIN', KEYS[1], ARGV[1]) " +
        "local issued = {} " +
        "for i = 1, #members, 2 do " +
        "    local memberId = members[i] " +
        "    redis.call('SET', '" + TOKEN_KEY_PREFIX + "' .. memberId, '1', 'EX', ARGV[2]) " +
        "    issued[#issued + 1] = memberId " +
        "end " +
        "return issued";

    private final DefaultRedisScript<List> popAndIssueScript;
    private final long tokenTtlSeconds;
    private final RedisTemplate<String, String> readTemplate;
    private final RedisTemplate<String, String> writeTemplate;

    public WaitingQueueRedisRepository(
        @Value("${queue.token.ttl-seconds:900}") long tokenTtlSeconds,
        RedisTemplate<String, String> readTemplate,
        @Qualifier("redisTemplateMaster") RedisTemplate<String, String> writeTemplate
    ) {
        this.tokenTtlSeconds = tokenTtlSeconds;
        this.readTemplate = readTemplate;
        this.writeTemplate = writeTemplate;
        this.popAndIssueScript = new DefaultRedisScript<>(POP_AND_ISSUE_SCRIPT, List.class);
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
     * 대기열에서 N명을 꺼내면서 동시에 토큰을 발급한다 (Lua 스크립트, 원자적).
     *
     * <p>ZPOPMIN과 SET EX를 하나의 Lua 스크립트로 실행하여,
     * "대기열에서 빠짐 = 토큰 발급됨"을 보장한다.
     * 중간에 서버 크래시가 발생해도 유저가 유실되지 않는다.</p>
     *
     * @return 토큰이 발급된 memberId 목록
     */
    @SuppressWarnings("unchecked")
    public List<String> popMinAndIssueTokens(int count) {
        List<String> result = writeTemplate.execute(
            popAndIssueScript,
            List.of(KEY),
            String.valueOf(count),
            String.valueOf(tokenTtlSeconds)
        );
        return result != null ? result : Collections.emptyList();
    }

    /**
     * 대기 시간 초과 엔트리 일괄 제거.
     *
     * <p>score(진입 시각 millis) 기준으로 cutoff 이전에 진입한 엔트리를 제거한다.
     * ZREMRANGEBYSCORE queue:waiting:order -inf {cutoffTimeMillis}</p>
     *
     * @return 제거된 엔트리 수
     */
    public long removeExpiredEntries(long cutoffTimeMillis) {
        Long removed = writeTemplate.opsForZSet()
            .removeRangeByScore(KEY, Double.NEGATIVE_INFINITY, cutoffTimeMillis);
        return removed != null ? removed : 0;
    }

    /**
     * 특정 유저 제거.
     */
    public void remove(Long memberId) {
        writeTemplate.opsForZSet().remove(KEY, String.valueOf(memberId));
    }
}

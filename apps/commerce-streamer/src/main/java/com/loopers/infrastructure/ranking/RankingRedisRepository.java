package com.loopers.infrastructure.ranking;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;

import java.util.Collections;

/**
 * 랭킹 ZSET Redis 저장소 — 쓰기 전용 (commerce-streamer)
 *
 * Master 노드에 직접 쓴다 (ZINCRBY는 쓰기 연산).
 * Lua 스크립트로 ZINCRBY + 조건부 EXPIRE를 원자적으로 실행한다.
 *
 * Lua 스크립트를 사용하는 이유:
 * - ZINCRBY로 키가 처음 생성될 때 TTL이 없으므로 EXPIRE 설정 필요
 * - 두 명령이 별도로 실행되면 ZINCRBY 후 EXPIRE 전에 다른 이벤트가 끼어들 수 있음
 * - 원자적 실행으로 "TTL 없는 키"가 남는 상황 방지
 */
@Repository
public class RankingRedisRepository {

    private static final Logger log = LoggerFactory.getLogger(RankingRedisRepository.class);

    /**
     * KEYS[1] = ranking key (e.g., "ranking:all:20260408")
     * ARGV[1] = delta (score increment, e.g., "0.6")
     * ARGV[2] = member (productId, e.g., "123")
     * ARGV[3] = ttl in seconds (e.g., "172800")
     *
     * TTL 조건:
     * - TTL 반환값 -1 = 키 존재하지만 만료 없음 → EXPIRE 설정
     * - TTL 반환값 -2 = 키 미존재 (ZINCRBY가 방금 생성했으므로 이 경우는 발생하지 않음)
     * - TTL 반환값 > 0 = 이미 TTL 설정됨 → 건드리지 않음
     */
    private static final String ZINCRBY_WITH_TTL_SCRIPT =
            "redis.call('ZINCRBY', KEYS[1], ARGV[1], ARGV[2]) " +
            "local ttl = redis.call('TTL', KEYS[1]) " +
            "if ttl == -1 then " +
            "  redis.call('EXPIRE', KEYS[1], ARGV[3]) " +
            "end " +
            "return 1";

    private static final DefaultRedisScript<Long> ZINCRBY_SCRIPT;

    static {
        ZINCRBY_SCRIPT = new DefaultRedisScript<>();
        ZINCRBY_SCRIPT.setScriptText(ZINCRBY_WITH_TTL_SCRIPT);
        ZINCRBY_SCRIPT.setResultType(Long.class);
    }

    private final StringRedisTemplate redisTemplate;

    public RankingRedisRepository(@Qualifier("redisTemplateMaster") StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 상품의 랭킹 점수를 원자적으로 증가시킨다.
     *
     * @param key       ZSET 키 (e.g., "ranking:all:20260408")
     * @param productId 상품 ID (ZSET member)
     * @param delta     점수 증분 (e.g., 0.1, 0.2, 0.6)
     * @param ttlSeconds 키의 TTL (초). 키에 TTL이 없을 때만 설정
     */
    public void incrementScore(String key, Long productId, double delta, long ttlSeconds) {
        redisTemplate.execute(
                ZINCRBY_SCRIPT,
                Collections.singletonList(key),
                String.valueOf(delta),
                String.valueOf(productId),
                String.valueOf(ttlSeconds)
        );
        log.debug("[Ranking] 점수 반영 — key={}, productId={}, delta={}", key, productId, delta);
    }
}

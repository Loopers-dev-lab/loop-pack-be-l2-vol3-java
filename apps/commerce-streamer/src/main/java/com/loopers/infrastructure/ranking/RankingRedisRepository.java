package com.loopers.infrastructure.ranking;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;

import java.util.Collections;
import java.util.Map;

/**
 * 랭킹 ZSET Redis 저장소 — 쓰기 전용 (commerce-streamer)
 *
 * Master 노드에 직접 쓴다 (ZINCRBY는 쓰기 연산).
 *
 * 두 가지 쓰기 방식 제공:
 * - incrementScore(): 단건 ZINCRBY + 조건부 EXPIRE (Lua 원자적)
 * - incrementScoreBatch(): 배치 ZINCRBY (Pipeline) + 조건부 EXPIRE
 *
 * Pipeline을 사용하는 이유:
 * - 배치 내 동일 상품을 미리 합산한 뒤 유니크 상품 수만큼만 ZINCRBY 실행
 * - N개 명령을 1회 RTT로 전송 → 네트워크 왕복 횟수 대폭 감소
 * - 단건 Lua(ZINCRBY+EXPIRE)를 N번 호출하는 것보다 Pipeline이 효율적
 */
@Repository
public class RankingRedisRepository {

    private static final Logger log = LoggerFactory.getLogger(RankingRedisRepository.class);

    /**
     * 단건 ZINCRBY + 조건부 EXPIRE Lua 스크립트
     *
     * KEYS[1] = ranking key
     * ARGV[1] = delta, ARGV[2] = member (productId), ARGV[3] = ttl seconds
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
     * 단건: 상품의 랭킹 점수를 원자적으로 증가시킨다.
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

    /**
     * 배치: 합산된 상품별 점수를 Pipeline으로 일괄 적재한다.
     *
     * 배치 내 동일 상품이 미리 합산된 Map을 받으므로,
     * ZINCRBY 호출 수 = 유니크 상품 수 (원본 이벤트 수보다 훨씬 적다).
     *
     * Pipeline 후 TTL이 없으면 설정한다 (키 최초 생성 시).
     *
     * @param key        ZSET 키 (e.g., "ranking:all:20260408")
     * @param scores     상품별 합산 점수 (productId → totalDelta)
     * @param ttlSeconds 키의 TTL (초). 키에 TTL이 없을 때만 설정
     */
    public void incrementScoreBatch(String key, Map<Long, Double> scores, long ttlSeconds) {
        if (scores.isEmpty()) {
            return;
        }

        redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
            byte[] rawKey = redisTemplate.getStringSerializer().serialize(key);
            for (Map.Entry<Long, Double> entry : scores.entrySet()) {
                byte[] member = redisTemplate.getStringSerializer().serialize(
                        String.valueOf(entry.getKey()));
                connection.zSetCommands().zIncrBy(rawKey, entry.getValue(), member);
            }
            return null;
        });

        // Pipeline 완료 후 조건부 TTL 설정
        setTtlIfAbsent(key, ttlSeconds);

        log.info("[Ranking] 배치 점수 반영 — key={}, products={}건", key, scores.size());
    }

    /**
     * 키에 TTL이 없으면(-1) 설정한다.
     * ZINCRBY로 키가 처음 생성되면 TTL이 없으므로 이 메서드로 보정한다.
     */
    private void setTtlIfAbsent(String key, long ttlSeconds) {
        Long ttl = redisTemplate.getExpire(key);
        if (ttl != null && ttl == -1) {
            redisTemplate.expire(key, java.time.Duration.ofSeconds(ttlSeconds));
        }
    }
}

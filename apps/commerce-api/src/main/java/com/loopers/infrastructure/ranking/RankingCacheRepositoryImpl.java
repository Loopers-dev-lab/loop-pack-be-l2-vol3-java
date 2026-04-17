package com.loopers.infrastructure.ranking;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.application.ranking.RankingCacheRepository;
import com.loopers.application.ranking.RankingResult;
import com.loopers.config.CacheProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.Optional;

/**
 * RedisTemplate을 직접 사용하는 랭킹 캐시 구현체.
 * - 읽기: defaultRedisTemplate (REPLICA_PREFERRED) → Replica 분산 읽기
 * - 쓰기: masterRedisTemplate (MASTER) → 복제 지연 없이 즉시 반영
 * - 모든 예외를 내부에서 흡수 → Redis 장애 시 캐시 미스로 처리 (DB 직접 조회 fallback)
 */
@Slf4j
@Repository
public class RankingCacheRepositoryImpl implements RankingCacheRepository {

    private final RedisTemplate<String, String> readTemplate;
    private final RedisTemplate<String, String> writeTemplate;
    private final ObjectMapper objectMapper;
    private final CacheProperties cacheProperties;

    public RankingCacheRepositoryImpl(
            RedisTemplate<String, String> defaultRedisTemplate,
            @Qualifier("redisTemplateMaster") RedisTemplate<String, String> masterRedisTemplate,
            ObjectMapper objectMapper,
            CacheProperties cacheProperties
    ) {
        this.readTemplate = defaultRedisTemplate;
        this.writeTemplate = masterRedisTemplate;
        this.objectMapper = objectMapper;
        this.cacheProperties = cacheProperties;
    }

    @Override
    public Optional<RankingResult> get(String cacheKey) {
        try {
            String json = readTemplate.opsForValue().get(cacheKey);
            if (json == null) {
                return Optional.empty();
            }
            return Optional.of(objectMapper.readValue(json, RankingResult.class));
        } catch (Exception e) {
            log.warn("랭킹 캐시 조회 실패 (key={}): {}", cacheKey, e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public void save(String cacheKey, RankingResult result) {
        try {
            String json = objectMapper.writeValueAsString(result);
            long ttlSeconds = cacheProperties.getTtlSeconds("ranking");
            writeTemplate.opsForValue().set(cacheKey, json, Duration.ofSeconds(ttlSeconds));
        } catch (Exception e) {
            log.warn("랭킹 캐시 저장 실패 (key={}): {}", cacheKey, e.getMessage());
        }
    }
}

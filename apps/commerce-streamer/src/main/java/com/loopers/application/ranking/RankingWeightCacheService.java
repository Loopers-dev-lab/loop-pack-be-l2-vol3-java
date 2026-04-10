package com.loopers.application.ranking;

import com.loopers.config.redis.RankingKeys;
import com.loopers.domain.ranking.RankingWeightRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;

/**
 * 랭킹 가중치 Redis 캐시 서비스 (Cache-Aside 패턴).
 *
 * Redis 우선 조회 → 미스 시 DB fallback + Redis 저장.
 * TTL 만료 또는 관리자 API가 evict하면 다음 조회 시 DB에서 재적재된다.
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class RankingWeightCacheService {

    private static final Duration WEIGHT_TTL = Duration.ofSeconds(300);

    private final RedisTemplate<String, String> masterRedisTemplate;
    private final RankingWeightRepository rankingWeightRepository;

    /**
     * 이벤트 유형에 해당하는 가중치를 반환한다.
     * Redis 미스 시 DB에서 조회 후 캐시에 저장한다.
     *
     * @throws IllegalStateException ranking_weight 테이블에 해당 eventType이 없을 때
     */
    public BigDecimal getWeight(String eventType) {
        String key = RankingKeys.weightKey(eventType);
        String cached = masterRedisTemplate.opsForValue().get(key);

        if (cached != null) {
            return new BigDecimal(cached);
        }

        // 캐시 미스 — DB fallback
        BigDecimal weight = rankingWeightRepository.findByEventType(eventType)
                .map(w -> w.getWeight())
                .orElseThrow(() -> new IllegalStateException("ranking_weight에 eventType이 없음: " + eventType));

        masterRedisTemplate.opsForValue().set(key, weight.toPlainString(), WEIGHT_TTL);
        log.debug("[RankingWeightCacheService] 캐시 미스 → DB fallback: eventType={}, weight={}", eventType, weight);

        return weight;
    }
}

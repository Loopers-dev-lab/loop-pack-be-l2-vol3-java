package com.loopers.application.ranking;

import com.loopers.event.EventType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
@EnableConfigurationProperties(RankingProperties.class)
public class RankingService {

    private final RedisTemplate<String, String> redisTemplate;
    private final RankingScoreCalculator calculator;
    private final RankingKeyGenerator keyGenerator;

    public void addScore(Long productId, EventType eventType, Double eventValue) {
        addScore(productId, eventType, eventValue, LocalDate.now());
    }

    public void addScore(Long productId, EventType eventType, Double eventValue, LocalDate date) {
        double score = calculator.calculate(eventType, eventValue);

        if (score == 0.0) {
            log.debug("Skipping ranking update for unsupported event type: {}", eventType);
            return;
        }

        String key = keyGenerator.generateDailyKey(date);
        String member = String.valueOf(productId);

        // 키 존재 여부 체크 후 조건부 TTL 설정 (불필요한 TTL 갱신 방지)
        boolean isNewKey = Boolean.FALSE.equals(redisTemplate.hasKey(key));

        Double newScore = redisTemplate.opsForZSet().incrementScore(key, member, score);

        if (isNewKey) {
            redisTemplate.expire(key, keyGenerator.getTtlDays(), TimeUnit.DAYS);
            log.debug("TTL set for new ranking key: key={}, ttlDays={}", key, keyGenerator.getTtlDays());
        }

        log.debug("Ranking score updated: key={}, productId={}, score={}, newTotal={}",
            key, productId, score, newScore);
    }
}

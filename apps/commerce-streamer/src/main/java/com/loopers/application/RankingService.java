package com.loopers.application;

import com.loopers.config.redis.RedisConfig;
import com.loopers.kafka.event.CatalogEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class RankingService {

    private static final DateTimeFormatter KEY_DATE_FMT = DateTimeFormatter.BASIC_ISO_DATE; // yyyyMMdd
    private static final long TTL_DAYS = 2;

    private final RedisTemplate<String, String> redisTemplate;

    public RankingService(@Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER) RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void updateRanking(CatalogEvent event) {
        double score = calculateScore(event);
        if (score == 0) {
            return;
        }

        String key = rankingKey();
        String member = String.valueOf(event.productId());

        redisTemplate.opsForZSet().incrementScore(key, member, score);

        // 키가 새로 생성된 경우에만 TTL 설정 (TTL 미설정 = -1)
        Long ttl = redisTemplate.getExpire(key);
        if (ttl != null && ttl == -1) {
            redisTemplate.expire(key, TTL_DAYS, TimeUnit.DAYS);
        }

        log.debug("[Ranking] productId={} score={} key={}", event.productId(), score, key);
    }

    private double calculateScore(CatalogEvent event) {
        return switch (CatalogEvent.Type.valueOf(event.eventType())) {
            case VIEWED -> 1;
            case LIKED -> 2;
            case UNLIKED -> -2;
            case ORDERED -> 7 * Math.log(safeOrderValue(event) + 1);
        };
    }

    private long safeOrderValue(CatalogEvent event) {
        long price = event.price() != null ? event.price() : 0;
        int quantity = event.quantity() != null ? event.quantity() : 0;
        return price * quantity;
    }

    private String rankingKey() {
        return "ranking:all:" + LocalDate.now().format(KEY_DATE_FMT);
    }
}

package com.loopers.batch;

import com.loopers.config.redis.RedisConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Component
@Slf4j
public class RankingCarryOverScheduler {

    private static final String KEY_PREFIX = "ranking:all:";
    private static final double CARRY_OVER_WEIGHT = 0.1;
    private static final Duration TTL = Duration.ofDays(2);
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.BASIC_ISO_DATE;

    private static final DefaultRedisScript<Long> CARRY_OVER_SCRIPT;
    static {
        CARRY_OVER_SCRIPT = new DefaultRedisScript<>();
        CARRY_OVER_SCRIPT.setScriptText("""
            local exists = redis.call('EXISTS', KEYS[2])
            if exists == 0 then
                return 0
            end
            redis.call('ZUNIONSTORE', KEYS[1], 1, KEYS[2], 'WEIGHTS', ARGV[1])
            redis.call('EXPIRE', KEYS[1], ARGV[2])
            return 1
            """);
        CARRY_OVER_SCRIPT.setResultType(Long.class);
    }

    private final RedisTemplate<String, String> redisTemplateMaster;

    public RankingCarryOverScheduler(
            @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER) RedisTemplate<String, String> redisTemplateMaster) {
        this.redisTemplateMaster = redisTemplateMaster;
    }

    /**
     * 매일 23:50 실행. 오늘 ZSET 점수의 10%를 내일 키에 복사하여 콜드 스타트를 완화한다.
     */
    @Scheduled(cron = "0 50 23 * * *")
    public void carryOver() {
        LocalDate today = LocalDate.now();
        LocalDate tomorrow = today.plusDays(1);

        String todayKey = KEY_PREFIX + today.format(DATE_FORMAT);
        String tomorrowKey = KEY_PREFIX + tomorrow.format(DATE_FORMAT);

        Long result = redisTemplateMaster.execute(
                CARRY_OVER_SCRIPT,
                List.of(tomorrowKey, todayKey),
                String.valueOf(CARRY_OVER_WEIGHT),
                String.valueOf(TTL.getSeconds())
        );

        if (result != null && result == 1) {
            log.info("[RankingCarryOver] 성공: {} → {} (weight={})", todayKey, tomorrowKey, CARRY_OVER_WEIGHT);
        } else {
            log.info("[RankingCarryOver] 스킵: 오늘 키 없음 ({})", todayKey);
        }
    }
}

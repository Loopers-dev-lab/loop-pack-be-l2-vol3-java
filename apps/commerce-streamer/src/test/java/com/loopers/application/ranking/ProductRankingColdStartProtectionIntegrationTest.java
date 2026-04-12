package com.loopers.application.ranking;

import com.loopers.config.redis.RedisConfig;
import com.loopers.infrastructure.metrics.ProductMetricsDailyQueryRepository;
import com.loopers.infrastructure.metrics.ProductMetricsHourlyQueryRepository;
import com.loopers.infrastructure.ranking.redis.RedisProductRankingRepository;
import com.loopers.testcontainers.RedisTestContainersConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.testcontainers.context.ImportTestcontainers;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.boot.test.mock.mockito.MockReset.BEFORE;

@SpringBootTest(properties = {
        "demo-kafka.test.topic-name=demo.test",
        "spring.kafka.listener.auto-startup=false"
})
@ImportTestcontainers(RedisTestContainersConfig.class)
@Import(ProductRankingColdStartProtectionIntegrationTest.MutableClockConfig.class)
@ActiveProfiles("test")
@EnabledIfSystemProperty(named = "docker.tests", matches = "true")
class ProductRankingColdStartProtectionIntegrationTest {

    private static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");

    @Autowired
    private ProductRankingSyncApplicationService productRankingSyncApplicationService;

    @Autowired
    private RedisProductRankingRepository redisProductRankingRepository;

    @Autowired
    @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER)
    private RedisTemplate<String, String> redisTemplate;

    @Autowired
    private MutableClock mutableClock;

    @MockBean(reset = BEFORE)
    private ProductMetricsDailyQueryRepository productMetricsDailyQueryRepository;

    @MockBean(reset = BEFORE)
    private ProductMetricsHourlyQueryRepository productMetricsHourlyQueryRepository;

    @AfterEach
    void tearDown() {
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushDb();
    }

    @Test
    @DisplayName("carry-over로 미리 만든 다음 일간 랭킹판은 자정 직후 빈 sync에도 유지된다")
    void keepsCarriedOverDailyRankingWhenCurrentMetricsAreEmpty() {
        mutableClock.setInstant(Instant.parse("2025-09-07T14:50:00Z"));
        LocalDate today = LocalDate.now(mutableClock.withZone(KOREA_ZONE));
        LocalDate tomorrow = today.plusDays(1);
        String todayKey = redisProductRankingRepository.buildDailyRankingKey(today);
        String tomorrowKey = redisProductRankingRepository.buildDailyRankingKey(tomorrow);

        redisTemplate.opsForZSet().add(todayKey, "product-1", 100.0d);
        redisTemplate.expire(todayKey, Duration.ofHours(48));

        productRankingSyncApplicationService.prepareTomorrowDailyRanking();

        assertThat(redisTemplate.opsForZSet().score(tomorrowKey, "product-1")).isEqualTo(10.0d);

        mutableClock.setInstant(Instant.parse("2025-09-07T15:00:00Z"));
        when(productMetricsDailyQueryRepository.findByMetricDate(tomorrow)).thenReturn(List.of());

        productRankingSyncApplicationService.syncCurrentDailyRanking();

        assertThat(redisProductRankingRepository.hasDailyRanking(tomorrow)).isTrue();
        assertThat(redisTemplate.opsForZSet().score(tomorrowKey, "product-1")).isEqualTo(10.0d);
    }

    @Test
    @DisplayName("carry-over로 미리 만든 다음 시간 랭킹판은 정시 직후 빈 sync에도 유지된다")
    void keepsCarriedOverHourlyRankingWhenCurrentMetricsAreEmpty() {
        mutableClock.setInstant(Instant.parse("2025-09-07T00:50:00Z"));
        LocalDateTime currentHour = LocalDateTime.ofInstant(mutableClock.instant(), KOREA_ZONE)
                .withMinute(0)
                .withSecond(0)
                .withNano(0);
        LocalDateTime nextHour = currentHour.plusHours(1);
        String currentHourKey = redisProductRankingRepository.buildHourlyRankingKey(currentHour);
        String nextHourKey = redisProductRankingRepository.buildHourlyRankingKey(nextHour);

        redisTemplate.opsForZSet().add(currentHourKey, "product-1", 70.0d);
        redisTemplate.expire(currentHourKey, Duration.ofHours(2));

        productRankingSyncApplicationService.prepareNextHourlyRanking();

        assertThat(redisTemplate.opsForZSet().score(nextHourKey, "product-1")).isEqualTo(7.0d);

        mutableClock.setInstant(Instant.parse("2025-09-07T01:00:00Z"));
        when(productMetricsHourlyQueryRepository.findByMetricHour(nextHour)).thenReturn(List.of());

        productRankingSyncApplicationService.syncCurrentHourlyRanking();

        assertThat(redisProductRankingRepository.hasHourlyRanking(nextHour)).isTrue();
        assertThat(redisTemplate.opsForZSet().score(nextHourKey, "product-1")).isEqualTo(7.0d);
    }

    static class MutableClock extends Clock {
        private Instant instant;
        private final ZoneId zone;

        MutableClock(Instant instant, ZoneId zone) {
            this.instant = instant;
            this.zone = zone;
        }

        void setInstant(Instant instant) {
            this.instant = instant;
        }

        @Override
        public ZoneId getZone() {
            return zone;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return new MutableClock(instant, zone);
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }

    @TestConfiguration
    static class MutableClockConfig {
        @Primary
        @Bean
        MutableClock testClock() {
            return new MutableClock(Instant.parse("2025-09-07T14:50:00Z"), ZoneOffset.UTC);
        }
    }
}

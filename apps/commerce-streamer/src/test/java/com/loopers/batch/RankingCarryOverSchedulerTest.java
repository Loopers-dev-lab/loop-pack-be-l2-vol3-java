package com.loopers.batch;

import com.loopers.testcontainers.MySqlTestContainersConfig;
import com.redis.testcontainers.RedisContainer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

@SpringBootTest
@ActiveProfiles("test")
@Import(MySqlTestContainersConfig.class)
@Testcontainers
@DisplayName("RankingCarryOverScheduler 통합 테스트")
class RankingCarryOverSchedulerTest {

    @Container
    private static final RedisContainer REDIS = new RedisContainer(DockerImageName.parse("redis:latest"));

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("datasource.redis.database", () -> 0);
        registry.add("datasource.redis.master.host", REDIS::getHost);
        registry.add("datasource.redis.master.port", REDIS::getFirstMappedPort);
        registry.add("datasource.redis.replicas[0].host", REDIS::getHost);
        registry.add("datasource.redis.replicas[0].port", REDIS::getFirstMappedPort);
    }

    @Autowired
    private RankingCarryOverScheduler scheduler;

    @Autowired
    private RedisTemplate<String, String> redisTemplateMaster;

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.BASIC_ISO_DATE;

    @BeforeEach
    void setUp() {
        LocalDate today = LocalDate.now();
        LocalDate tomorrow = today.plusDays(1);
        String todayKey = "ranking:all:" + today.format(DATE_FORMAT);
        String tomorrowKey = "ranking:all:" + tomorrow.format(DATE_FORMAT);
        redisTemplateMaster.delete(todayKey);
        redisTemplateMaster.delete(tomorrowKey);
    }

    @Test
    @DisplayName("carryOver — 오늘 키가 있으면 내일 키에 10% 점수가 복사된다")
    void carryOver_CopiesScoreToTomorrow() {
        LocalDate today = LocalDate.now();
        LocalDate tomorrow = today.plusDays(1);
        String todayKey = "ranking:all:" + today.format(DATE_FORMAT);
        String tomorrowKey = "ranking:all:" + tomorrow.format(DATE_FORMAT);

        redisTemplateMaster.opsForZSet().add(todayKey, "101", 100.0);
        redisTemplateMaster.opsForZSet().add(todayKey, "102", 50.0);

        scheduler.carryOver();

        Double score101 = redisTemplateMaster.opsForZSet().score(tomorrowKey, "101");
        Double score102 = redisTemplateMaster.opsForZSet().score(tomorrowKey, "102");
        assertThat(score101).isCloseTo(10.0, within(0.001));
        assertThat(score102).isCloseTo(5.0, within(0.001));
    }

    @Test
    @DisplayName("carryOver — 오늘 키가 없으면 내일 키가 생성되지 않는다")
    void carryOver_SkipsWhenTodayKeyMissing() {
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        String tomorrowKey = "ranking:all:" + tomorrow.format(DATE_FORMAT);

        scheduler.carryOver();

        Boolean exists = redisTemplateMaster.hasKey(tomorrowKey);
        assertThat(exists).isFalse();
    }

    @Test
    @DisplayName("carryOver — 내일 키에 TTL이 설정된다")
    void carryOver_SetsTTL() {
        LocalDate today = LocalDate.now();
        LocalDate tomorrow = today.plusDays(1);
        String todayKey = "ranking:all:" + today.format(DATE_FORMAT);
        String tomorrowKey = "ranking:all:" + tomorrow.format(DATE_FORMAT);

        redisTemplateMaster.opsForZSet().add(todayKey, "101", 100.0);

        scheduler.carryOver();

        Long ttl = redisTemplateMaster.getExpire(tomorrowKey);
        assertThat(ttl).isGreaterThan(0);
        assertThat(ttl).isLessThanOrEqualTo(172800);
    }
}

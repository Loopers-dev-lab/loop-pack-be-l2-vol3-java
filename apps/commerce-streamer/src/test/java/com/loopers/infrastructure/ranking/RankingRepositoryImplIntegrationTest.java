package com.loopers.infrastructure.ranking;

import com.loopers.testcontainers.MySqlTestContainersConfig;
import com.loopers.domain.ranking.RankingRepository;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

@SpringBootTest
@ActiveProfiles("test")
@Import(MySqlTestContainersConfig.class)
@Testcontainers
@DisplayName("RankingRepositoryImpl 통합 테스트")
class RankingRepositoryImplIntegrationTest {

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
    private RankingRepository rankingRepository;

    @Autowired
    private RedisTemplate<String, String> redisTemplateMaster;

    private static final LocalDate TODAY = LocalDate.of(2026, 4, 7);
    private static final String RANKING_KEY = "ranking:all:20260407";
    private static final String LIKED_KEY = "ranking:liked:20260407";

    @BeforeEach
    void setUp() {
        redisTemplateMaster.delete(RANKING_KEY);
        redisTemplateMaster.delete(LIKED_KEY);
    }

    @Test
    @DisplayName("incrementScore — ZINCRBY로 점수가 누적된다")
    void incrementScore_AccumulatesScore() {
        rankingRepository.incrementScore(101L, 0.01, TODAY);
        rankingRepository.incrementScore(101L, 0.3, TODAY);

        Double score = redisTemplateMaster.opsForZSet().score(RANKING_KEY, "101");
        assertThat(score).isCloseTo(0.31, within(0.001));
    }

    @Test
    @DisplayName("incrementScore — TTL 2일이 설정된다")
    void incrementScore_SetsTTL() {
        rankingRepository.incrementScore(101L, 0.01, TODAY);

        Long ttl = redisTemplateMaster.getExpire(RANKING_KEY);
        assertThat(ttl).isGreaterThan(0);
        assertThat(ttl).isLessThanOrEqualTo(172800); // 2일
    }

    @Test
    @DisplayName("addLikeIfAbsent — 첫 좋아요는 true, 중복은 false")
    void addLikeIfAbsent_IdempotentLike() {
        boolean first = rankingRepository.addLikeIfAbsent(101L, 456L, TODAY);
        boolean duplicate = rankingRepository.addLikeIfAbsent(101L, 456L, TODAY);

        assertThat(first).isTrue();
        assertThat(duplicate).isFalse();
    }

    @Test
    @DisplayName("addLikeIfAbsent — 다른 유저는 독립적으로 좋아요 가능")
    void addLikeIfAbsent_DifferentUsersIndependent() {
        boolean userA = rankingRepository.addLikeIfAbsent(101L, 1L, TODAY);
        boolean userB = rankingRepository.addLikeIfAbsent(101L, 2L, TODAY);

        assertThat(userA).isTrue();
        assertThat(userB).isTrue();
    }

    @Test
    @DisplayName("removeLikeIfPresent — 존재하면 true, 없으면 false")
    void removeLikeIfPresent_IdempotentUnlike() {
        rankingRepository.addLikeIfAbsent(101L, 456L, TODAY);

        boolean removed = rankingRepository.removeLikeIfPresent(101L, 456L, TODAY);
        boolean removedAgain = rankingRepository.removeLikeIfPresent(101L, 456L, TODAY);

        assertThat(removed).isTrue();
        assertThat(removedAgain).isFalse();
    }

    @Test
    @DisplayName("addLikeIfAbsent — TTL 2일이 설정된다")
    void addLikeIfAbsent_SetsTTL() {
        rankingRepository.addLikeIfAbsent(101L, 456L, TODAY);

        Long ttl = redisTemplateMaster.getExpire(LIKED_KEY);
        assertThat(ttl).isGreaterThan(0);
        assertThat(ttl).isLessThanOrEqualTo(172800);
    }
}

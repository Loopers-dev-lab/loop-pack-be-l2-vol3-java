package com.loopers.interfaces.api.ranking;

import com.loopers.interfaces.api.ApiResponse;
import com.loopers.testcontainers.MySqlTestContainersConfig;
import com.loopers.testcontainers.RedisTestContainersConfig;
import com.loopers.utils.RedisCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import({MySqlTestContainersConfig.class, RedisTestContainersConfig.class})
@ActiveProfiles("test")
class RankingV1ApiE2ETest {

    private final TestRestTemplate testRestTemplate;
    private final RedisTemplate<String, String> redisTemplate;
    private final RedisCleanUp redisCleanUp;

    @Autowired
    public RankingV1ApiE2ETest(
        TestRestTemplate testRestTemplate,
        RedisTemplate<String, String> redisTemplate,
        RedisCleanUp redisCleanUp
    ) {
        this.testRestTemplate = testRestTemplate;
        this.redisTemplate = redisTemplate;
        this.redisCleanUp = redisCleanUp;
    }

    @AfterEach
    void tearDown() {
        redisCleanUp.truncateAll();
    }

    @DisplayName("GET /api/v1/rankings")
    @Nested
    class GetRankings {

        @DisplayName("ZSET에 데이터가 있으면 점수 내림차순으로 반환한다")
        @Test
        void returns_rankings_sorted_by_score() {
            // given
            String key = "ranking:all:20260410";
            redisTemplate.opsForZSet().add(key, "1", 100.0);
            redisTemplate.opsForZSet().add(key, "2", 200.0);
            redisTemplate.opsForZSet().add(key, "3", 50.0);

            // when
            ResponseEntity<ApiResponse<RankingV1Dto.RankingListResponse>> response = testRestTemplate.exchange(
                "/api/v1/rankings?date=20260410&size=20&page=1",
                HttpMethod.GET, null,
                new ParameterizedTypeReference<>() {}
            );

            // then
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().data().date()).isEqualTo("20260410"),
                () -> assertThat(response.getBody().data().rankings()).isNotNull()
            );
        }

        @DisplayName("ZSET에 데이터가 없으면 빈 목록을 반환한다")
        @Test
        void returns_empty_when_no_data() {
            // when
            ResponseEntity<ApiResponse<RankingV1Dto.RankingListResponse>> response = testRestTemplate.exchange(
                "/api/v1/rankings?date=20260410&size=20&page=1",
                HttpMethod.GET, null,
                new ParameterizedTypeReference<>() {}
            );

            // then
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().data().rankings()).isEmpty()
            );
        }

        @DisplayName("date 미지정 시 오늘 날짜로 조회한다")
        @Test
        void defaults_to_today_when_no_date() {
            // when
            ResponseEntity<ApiResponse<RankingV1Dto.RankingListResponse>> response = testRestTemplate.exchange(
                "/api/v1/rankings",
                HttpMethod.GET, null,
                new ParameterizedTypeReference<>() {}
            );

            // then
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().data().date()).isNotBlank(),
                () -> assertThat(response.getBody().data().rankings()).isEmpty()
            );
        }
    }
}

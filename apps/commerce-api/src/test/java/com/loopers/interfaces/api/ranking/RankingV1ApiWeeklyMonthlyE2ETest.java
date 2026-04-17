package com.loopers.interfaces.api.ranking;

import com.loopers.fixture.MonthlyRankTestFixture;
import com.loopers.fixture.WeeklyRankTestFixture;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RankingV1ApiWeeklyMonthlyE2ETest {

    private static final LocalDate SNAPSHOT = LocalDate.of(2026, 4, 16);
    private static final String WEEKLY_LATEST_KEY  = "rankings:weekly:latest_date";
    private static final String MONTHLY_LATEST_KEY = "rankings:monthly:latest_date";

    @Autowired private TestRestTemplate restTemplate;
    @Autowired private WeeklyRankTestFixture weeklyFixture;
    @Autowired private MonthlyRankTestFixture monthlyFixture;
    @Autowired private StringRedisTemplate stringRedisTemplate;
    @Autowired private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
        stringRedisTemplate.delete(WEEKLY_LATEST_KEY);
        stringRedisTemplate.delete(MONTHLY_LATEST_KEY);
        stringRedisTemplate.delete("rankings:weekly:%s:0:20".formatted(SNAPSHOT));
        stringRedisTemplate.delete("rankings:monthly:%s:0:20".formatted(SNAPSHOT));
    }

    @Nested
    @DisplayName("GET /api/v1/rankings/weekly")
    class WeeklyRanking {

        @Test
        @DisplayName("date 명시 → 200 + 해당 스냅샷 랭킹 반환")
        void withExplicitDate() {
            // arrange
            weeklyFixture.insertWithProduct(SNAPSHOT, 1L, 1);

            // act
            ResponseEntity<ApiResponse<RankingV1Dto.RankingListResponse>> response = restTemplate.exchange(
                "/api/v1/rankings/weekly?date=%s&page=0&size=20".formatted(SNAPSHOT),
                HttpMethod.GET, null,
                new ParameterizedTypeReference<>() {});

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().data().rankings()).hasSize(1);
            assertThat(response.getBody().data().rankings().get(0).rank()).isEqualTo(1);
            assertThat(response.getBody().data().totalElements()).isEqualTo(1);
        }

        @Test
        @DisplayName("date 미지정 + latest_date 캐시 hit → 200 + 해당 스냅샷 반환")
        void withoutDate_cacheHit() {
            // arrange
            weeklyFixture.insertWithProduct(SNAPSHOT, 1L, 1);
            stringRedisTemplate.opsForValue().set(WEEKLY_LATEST_KEY, SNAPSHOT.toString());

            // act
            ResponseEntity<ApiResponse<RankingV1Dto.RankingListResponse>> response = restTemplate.exchange(
                "/api/v1/rankings/weekly?page=0&size=20",
                HttpMethod.GET, null,
                new ParameterizedTypeReference<>() {});

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().data().rankings()).hasSize(1);
        }

        @Test
        @DisplayName("date 미지정 + latest_date 캐시 miss → DB MAX 폴백 후 200 반환")
        void withoutDate_cacheMiss_dbFallback() {
            // arrange
            weeklyFixture.insertWithProduct(SNAPSHOT, 1L, 1);

            // act
            ResponseEntity<ApiResponse<RankingV1Dto.RankingListResponse>> response = restTemplate.exchange(
                "/api/v1/rankings/weekly?page=0&size=20",
                HttpMethod.GET, null,
                new ParameterizedTypeReference<>() {});

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().data().rankings()).hasSize(1);
            assertThat(stringRedisTemplate.opsForValue().get(WEEKLY_LATEST_KEY)).isEqualTo(SNAPSHOT.toString());
        }

        @Test
        @DisplayName("스냅샷 데이터 없음 → 200 + 빈 리스트")
        void emptySnapshot() {
            // act
            ResponseEntity<ApiResponse<RankingV1Dto.RankingListResponse>> response = restTemplate.exchange(
                "/api/v1/rankings/weekly?date=%s&page=0&size=20".formatted(SNAPSHOT),
                HttpMethod.GET, null,
                new ParameterizedTypeReference<>() {});

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().data().rankings()).isEmpty();
            assertThat(response.getBody().data().totalElements()).isEqualTo(0);
        }

        @Test
        @DisplayName("응답 스키마 필드 일치 검증")
        void responseSchemaValidation() {
            // arrange
            weeklyFixture.insertWithProduct(SNAPSHOT, 1L, 1);

            // act
            ResponseEntity<ApiResponse<RankingV1Dto.RankingListResponse>> response = restTemplate.exchange(
                "/api/v1/rankings/weekly?date=%s&page=0&size=20".formatted(SNAPSHOT),
                HttpMethod.GET, null,
                new ParameterizedTypeReference<>() {});

            // assert
            RankingV1Dto.RankingListResponse body = response.getBody().data();
            assertThat(body.page()).isEqualTo(0);
            assertThat(body.size()).isEqualTo(20);
            RankingV1Dto.RankingItemResponse item = body.rankings().get(0);
            assertThat(item.rank()).isEqualTo(1);
            assertThat(item.productName()).isNotBlank();
            assertThat(item.brandName()).isNotBlank();
            assertThat(item.price()).isGreaterThan(0);
        }
    }

    @Nested
    @DisplayName("GET /api/v1/rankings/monthly")
    class MonthlyRanking {

        @Test
        @DisplayName("date 명시 → 200 + 해당 스냅샷 랭킹 반환")
        void withExplicitDate() {
            // arrange
            monthlyFixture.insertWithProduct(SNAPSHOT, 1L, 1);

            // act
            ResponseEntity<ApiResponse<RankingV1Dto.RankingListResponse>> response = restTemplate.exchange(
                "/api/v1/rankings/monthly?date=%s&page=0&size=20".formatted(SNAPSHOT),
                HttpMethod.GET, null,
                new ParameterizedTypeReference<>() {});

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().data().rankings()).hasSize(1);
            assertThat(response.getBody().data().rankings().get(0).rank()).isEqualTo(1);
        }

        @Test
        @DisplayName("date 미지정 + latest_date 캐시 miss → DB MAX 폴백 후 200 반환")
        void withoutDate_cacheMiss_dbFallback() {
            // arrange
            monthlyFixture.insertWithProduct(SNAPSHOT, 1L, 1);

            // act
            ResponseEntity<ApiResponse<RankingV1Dto.RankingListResponse>> response = restTemplate.exchange(
                "/api/v1/rankings/monthly?page=0&size=20",
                HttpMethod.GET, null,
                new ParameterizedTypeReference<>() {});

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().data().rankings()).hasSize(1);
            assertThat(stringRedisTemplate.opsForValue().get(MONTHLY_LATEST_KEY)).isEqualTo(SNAPSHOT.toString());
        }

        @Test
        @DisplayName("스냅샷 데이터 없음 → 200 + 빈 리스트")
        void emptySnapshot() {
            // act
            ResponseEntity<ApiResponse<RankingV1Dto.RankingListResponse>> response = restTemplate.exchange(
                "/api/v1/rankings/monthly?date=%s&page=0&size=20".formatted(SNAPSHOT),
                HttpMethod.GET, null,
                new ParameterizedTypeReference<>() {});

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().data().rankings()).isEmpty();
        }
    }
}

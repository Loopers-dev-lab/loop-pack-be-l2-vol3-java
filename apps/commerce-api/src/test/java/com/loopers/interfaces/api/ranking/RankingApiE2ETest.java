package com.loopers.interfaces.api.ranking;

import com.loopers.interfaces.api.ApiResponse;
import com.loopers.support.E2ETestFixture;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(E2ETestFixture.class)
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class RankingApiE2ETest {

    private static final String ENDPOINT = "/api/v1/rankings";

    @Autowired
    private TestRestTemplate testRestTemplate;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @Autowired
    private E2ETestFixture fixture;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @Autowired
    private Clock clock;

    @BeforeEach
    void setUp() {
        databaseCleanUp.truncateAllTables();
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushAll();
    }

    @Nested
    class 랭킹_조회 {

        @Test
        void 기본_파라미터로_조회하면_200_응답한다() {
            ResponseEntity<ApiResponse<RankingV1Dto.PageResponse>> response = getRankings("");

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody().data().period().name()).isEqualTo("DAILY"),
                    () -> assertThat(response.getBody().data().page()).isEqualTo(0),
                    () -> assertThat(response.getBody().data().size()).isEqualTo(20)
            );
        }

        @Test
        void ZSET에_데이터가_있으면_랭킹_목록을_반환한다() {
            Long brandId = fixture.registerBrand("나이키", "스포츠");
            Long productId1 = fixture.registerProduct(brandId, "상품A", BigDecimal.valueOf(10000), 100, "설명A");
            Long productId2 = fixture.registerProduct(brandId, "상품B", BigDecimal.valueOf(20000), 100, "설명B");

            // 캐시 충돌 방지: 다른 날짜 사용
            String targetDate = "20260401";
            String key = "ranking:daily:" + targetDate + ":control";
            redisTemplate.opsForZSet().add(key, String.valueOf(productId1), 52.4);
            redisTemplate.opsForZSet().add(key, String.valueOf(productId2), 41.0);

            ResponseEntity<ApiResponse<RankingV1Dto.PageResponse>> response = getRankings("?date=" + targetDate);

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody().data().items()).hasSize(2),
                    () -> assertThat(response.getBody().data().items().get(0).rank()).isEqualTo(1),
                    () -> assertThat(response.getBody().data().items().get(0).score()).isEqualTo(52.4),
                    () -> assertThat(response.getBody().data().items().get(0).productName()).isEqualTo("상품A"),
                    () -> assertThat(response.getBody().data().items().get(1).rank()).isEqualTo(2)
            );
        }

        @Test
        void 삭제된_상품은_필터링된다() {
            Long brandId = fixture.registerBrand("나이키", "스포츠");
            Long productId1 = fixture.registerProduct(brandId, "상품A", BigDecimal.valueOf(10000), 100, "설명A");
            Long productId2 = fixture.registerProduct(brandId, "상품B", BigDecimal.valueOf(20000), 100, "설명B");
            fixture.deleteProduct(productId2);

            String targetDate = "20260402";
            String key = "ranking:daily:" + targetDate + ":control";
            redisTemplate.opsForZSet().add(key, String.valueOf(productId1), 52.4);
            redisTemplate.opsForZSet().add(key, String.valueOf(productId2), 100.0);

            ResponseEntity<ApiResponse<RankingV1Dto.PageResponse>> response = getRankings("?date=" + targetDate);

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody().data().items()).hasSize(1),
                    () -> assertThat(response.getBody().data().items().get(0).productName()).isEqualTo("상품A")
            );
        }

        @Test
        void 페이지_파라미터로_페이징_조회한다() {
            ResponseEntity<ApiResponse<RankingV1Dto.PageResponse>> response = getRankings("?page=1&size=10");

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody().data().page()).isEqualTo(1),
                    () -> assertThat(response.getBody().data().size()).isEqualTo(10)
            );
        }
    }

    @Nested
    class 유효성_검증 {

        @Test
        void 잘못된_날짜_형식이면_400_응답한다() {
            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                    ENDPOINT + "?date=invalid",
                    HttpMethod.GET, new HttpEntity<>(new HttpHeaders()),
                    new ParameterizedTypeReference<>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @Test
        void page가_음수이면_400_응답한다() {
            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                    ENDPOINT + "?page=-1",
                    HttpMethod.GET, new HttpEntity<>(new HttpHeaders()),
                    new ParameterizedTypeReference<>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @Test
        void page가_49를_초과하면_400_응답한다() {
            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                    ENDPOINT + "?page=50",
                    HttpMethod.GET, new HttpEntity<>(new HttpHeaders()),
                    new ParameterizedTypeReference<>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @Test
        void size가_0이면_400_응답한다() {
            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                    ENDPOINT + "?size=0",
                    HttpMethod.GET, new HttpEntity<>(new HttpHeaders()),
                    new ParameterizedTypeReference<>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @Test
        void size가_100을_초과하면_400_응답한다() {
            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                    ENDPOINT + "?size=101",
                    HttpMethod.GET, new HttpEntity<>(new HttpHeaders()),
                    new ParameterizedTypeReference<>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }

    @Nested
    class 기간별_조회 {

        @Test
        void 실시간_기간으로_조회하면_200_응답한다() {
            ResponseEntity<ApiResponse<RankingV1Dto.PageResponse>> response = getRankings("?period=REALTIME");

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody().data().period().name()).isEqualTo("REALTIME")
            );
        }

        @Test
        void 주간_기간으로_조회하면_200_응답한다() {
            ResponseEntity<ApiResponse<RankingV1Dto.PageResponse>> response = getRankings("?period=WEEKLY");

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody().data().period().name()).isEqualTo("WEEKLY")
            );
        }

        @Test
        void 월간_기간으로_조회하면_200_응답한다() {
            ResponseEntity<ApiResponse<RankingV1Dto.PageResponse>> response = getRankings("?period=MONTHLY");

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody().data().period().name()).isEqualTo("MONTHLY")
            );
        }
    }

    // --- 헬퍼 메서드 ---

    private ResponseEntity<ApiResponse<RankingV1Dto.PageResponse>> getRankings(String queryString) {
        return testRestTemplate.exchange(
                ENDPOINT + queryString,
                HttpMethod.GET,
                new HttpEntity<>(new HttpHeaders()),
                new ParameterizedTypeReference<>() {}
        );
    }

    private String dailyKey() {
        String today = LocalDate.now(clock).format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        return "ranking:daily:" + today + ":control";
    }
}

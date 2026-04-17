package com.loopers.interfaces.api.ranking;

import com.loopers.config.redis.RankingKeys;
import com.loopers.domain.brand.Brand;
import com.loopers.domain.product.Money;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.Stock;
import com.loopers.infrastructure.brand.BrandJpaRepository;
import com.loopers.infrastructure.product.ProductJpaRepository;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.product.ProductV1Dto;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RankingV1ApiE2ETest {

    private static final LocalDate TODAY = LocalDate.now();
    private static final LocalDate YESTERDAY = TODAY.minusDays(1);
    private static final String TODAY_STR = TODAY.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
    private static final String YESTERDAY_STR = YESTERDAY.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
    private static final String ENDPOINT_RANKINGS = "/api/v1/rankings";
    private static final String ENDPOINT_HOURLY_RANKINGS = "/api/v1/rankings/hourly";
    private static final int TEST_HOUR = 10;
    private static final String BRAND_NAME = "나이키";
    private static final int PRICE = 10000;
    private static final int STOCK = 10;

    @Autowired
    private TestRestTemplate testRestTemplate;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @Autowired
    private BrandJpaRepository brandJpaRepository;

    @Autowired
    private ProductJpaRepository productJpaRepository;

    @Autowired
    @Qualifier("redisTemplateMaster")
    private RedisTemplate<String, String> redisTemplateMaster;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
        redisTemplateMaster.delete(RankingKeys.dailyKey(TODAY));
        redisTemplateMaster.delete(RankingKeys.dailyKey(YESTERDAY));
        redisTemplateMaster.delete(RankingKeys.hourlyKey(TODAY, TEST_HOUR));
        // Cache-Aside 캐시 키 전체 삭제 (loopers:ranking:* 패턴)
        redisTemplateMaster.execute((RedisCallback<Void>) connection -> {
            ScanOptions options = ScanOptions.scanOptions().match("loopers:ranking:*").count(100).build();
            try (var cursor = connection.scan(options)) {
                while (cursor.hasNext()) {
                    connection.del(cursor.next());
                }
            } catch (Exception ignored) {}
            return null;
        });
    }

    @DisplayName("GET /api/v1/rankings")
    @Nested
    class GetDailyRanking {

        @Test
        @DisplayName("데이터가 없으면 200 OK와 빈 rankings 배열을 반환한다")
        void returnsEmptyRankings_whenNoData() {
            // act
            ParameterizedTypeReference<ApiResponse<RankingV1Dto.RankingListResponse>> type =
                    new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<RankingV1Dto.RankingListResponse>> response =
                    testRestTemplate.exchange(ENDPOINT_RANKINGS, HttpMethod.GET, HttpEntity.EMPTY, type);

            // assert
            assertAll(
                    () -> assertTrue(response.getStatusCode().is2xxSuccessful()),
                    () -> assertThat(response.getBody().data().rankings()).isEmpty(),
                    () -> assertThat(response.getBody().data().totalElements()).isZero()
            );
        }

        @Test
        @DisplayName("Redis에 랭킹이 있으면 200 OK와 score 내림차순 정렬된 목록을 반환한다")
        void returnsRankings_orderedByScoreDesc() {
            // arrange
            Brand brand = brandJpaRepository.save(new Brand(BRAND_NAME));
            Product productA = productJpaRepository.save(new Product(brand.getId(), "에어맥스 A", new Money(PRICE), new Stock(STOCK)));
            Product productB = productJpaRepository.save(new Product(brand.getId(), "에어맥스 B", new Money(PRICE), new Stock(STOCK)));

            redisTemplateMaster.opsForZSet().add(RankingKeys.dailyKey(TODAY), String.valueOf(productA.getId()), 100.0);
            redisTemplateMaster.opsForZSet().add(RankingKeys.dailyKey(TODAY), String.valueOf(productB.getId()), 50.0);

            // act
            ParameterizedTypeReference<ApiResponse<RankingV1Dto.RankingListResponse>> type =
                    new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<RankingV1Dto.RankingListResponse>> response =
                    testRestTemplate.exchange(ENDPOINT_RANKINGS, HttpMethod.GET, HttpEntity.EMPTY, type);

            // assert
            var rankings = response.getBody().data().rankings();
            assertAll(
                    () -> assertTrue(response.getStatusCode().is2xxSuccessful()),
                    () -> assertThat(rankings).hasSize(2),
                    () -> assertThat(rankings.get(0).rank()).isEqualTo(1),
                    () -> assertThat(rankings.get(0).productName()).isEqualTo("에어맥스 A"),
                    () -> assertThat(rankings.get(0).score()).isEqualTo(100.0),
                    () -> assertThat(rankings.get(1).rank()).isEqualTo(2)
            );
        }

        @Test
        @DisplayName("date 파라미터로 특정 날짜의 랭킹을 조회한다")
        void returnsRanking_forSpecificDate() {
            // arrange
            Brand brand = brandJpaRepository.save(new Brand(BRAND_NAME));
            Product product = productJpaRepository.save(new Product(brand.getId(), "에어맥스", new Money(PRICE), new Stock(STOCK)));
            redisTemplateMaster.opsForZSet().add(RankingKeys.dailyKey(TODAY), String.valueOf(product.getId()), 100.0);

            // act
            String url = ENDPOINT_RANKINGS + "?date=" + TODAY_STR;
            ParameterizedTypeReference<ApiResponse<RankingV1Dto.RankingListResponse>> type =
                    new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<RankingV1Dto.RankingListResponse>> response =
                    testRestTemplate.exchange(url, HttpMethod.GET, HttpEntity.EMPTY, type);

            // assert
            assertAll(
                    () -> assertTrue(response.getStatusCode().is2xxSuccessful()),
                    () -> assertThat(response.getBody().data().rankings()).hasSize(1)
            );
        }

        @Test
        @DisplayName("어제 날짜 파라미터로 조회하면 어제 ZSET의 데이터가 반환되고 오늘 데이터와 독립적이다")
        void returnsYesterdayRanking_independentFromToday() {
            // arrange — 오늘과 어제에 각각 다른 상품 등록
            Brand brand = brandJpaRepository.save(new Brand(BRAND_NAME));
            Product yesterdayProduct = productJpaRepository.save(new Product(brand.getId(), "어제상품", new Money(PRICE), new Stock(STOCK)));
            Product todayProduct = productJpaRepository.save(new Product(brand.getId(), "오늘상품", new Money(PRICE), new Stock(STOCK)));

            redisTemplateMaster.opsForZSet().add(RankingKeys.dailyKey(YESTERDAY), String.valueOf(yesterdayProduct.getId()), 200.0);
            redisTemplateMaster.opsForZSet().add(RankingKeys.dailyKey(TODAY), String.valueOf(todayProduct.getId()), 100.0);

            // act
            String url = ENDPOINT_RANKINGS + "?date=" + YESTERDAY_STR;
            ParameterizedTypeReference<ApiResponse<RankingV1Dto.RankingListResponse>> type =
                    new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<RankingV1Dto.RankingListResponse>> response =
                    testRestTemplate.exchange(url, HttpMethod.GET, HttpEntity.EMPTY, type);

            // assert — 어제 랭킹에는 어제 상품만 있어야 한다
            var rankings = response.getBody().data().rankings();
            assertAll(
                    () -> assertTrue(response.getStatusCode().is2xxSuccessful()),
                    () -> assertThat(rankings).hasSize(1),
                    () -> assertThat(rankings.get(0).productName()).isEqualTo("어제상품"),
                    () -> assertThat(rankings.get(0).rank()).isEqualTo(1)
            );
        }
    }

    @DisplayName("GET /api/v1/rankings/hourly")
    @Nested
    class GetHourlyRanking {

        @Test
        @DisplayName("데이터가 없으면 200 OK와 빈 rankings 배열을 반환한다")
        void returnsEmptyRankings_whenNoData() {
            // act
            String url = ENDPOINT_HOURLY_RANKINGS + "?hour=" + TEST_HOUR;
            ParameterizedTypeReference<ApiResponse<RankingV1Dto.RankingListResponse>> type =
                    new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<RankingV1Dto.RankingListResponse>> response =
                    testRestTemplate.exchange(url, HttpMethod.GET, HttpEntity.EMPTY, type);

            // assert
            assertAll(
                    () -> assertTrue(response.getStatusCode().is2xxSuccessful()),
                    () -> assertThat(response.getBody().data().rankings()).isEmpty(),
                    () -> assertThat(response.getBody().data().totalElements()).isZero()
            );
        }

        @Test
        @DisplayName("Redis에 시간별 랭킹이 있으면 200 OK와 score 내림차순 정렬된 목록을 반환한다")
        void returnsHourlyRankings_orderedByScoreDesc() {
            // arrange
            Brand brand = brandJpaRepository.save(new Brand(BRAND_NAME));
            Product productA = productJpaRepository.save(new Product(brand.getId(), "에어맥스 A", new Money(PRICE), new Stock(STOCK)));
            Product productB = productJpaRepository.save(new Product(brand.getId(), "에어맥스 B", new Money(PRICE), new Stock(STOCK)));

            redisTemplateMaster.opsForZSet().add(RankingKeys.hourlyKey(TODAY, TEST_HOUR), String.valueOf(productA.getId()), 80.0);
            redisTemplateMaster.opsForZSet().add(RankingKeys.hourlyKey(TODAY, TEST_HOUR), String.valueOf(productB.getId()), 40.0);

            // act
            String url = ENDPOINT_HOURLY_RANKINGS + "?date=" + TODAY_STR + "&hour=" + TEST_HOUR;
            ParameterizedTypeReference<ApiResponse<RankingV1Dto.RankingListResponse>> type =
                    new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<RankingV1Dto.RankingListResponse>> response =
                    testRestTemplate.exchange(url, HttpMethod.GET, HttpEntity.EMPTY, type);

            // assert
            var rankings = response.getBody().data().rankings();
            assertAll(
                    () -> assertTrue(response.getStatusCode().is2xxSuccessful()),
                    () -> assertThat(rankings).hasSize(2),
                    () -> assertThat(rankings.get(0).rank()).isEqualTo(1),
                    () -> assertThat(rankings.get(0).productName()).isEqualTo("에어맥스 A"),
                    () -> assertThat(rankings.get(1).rank()).isEqualTo(2)
            );
        }
    }

    @DisplayName("GET /api/v1/products/{id} — rank 필드")
    @Nested
    class ProductRankField {

        @Test
        @DisplayName("오늘 랭킹에 있는 상품을 조회하면 rank 필드에 순위가 반환된다")
        void returnsRank_whenProductIsInRanking() {
            // arrange
            Brand brand = brandJpaRepository.save(new Brand(BRAND_NAME));
            Product product = productJpaRepository.save(new Product(brand.getId(), "에어맥스", new Money(PRICE), new Stock(STOCK)));

            // ZSET에 1위로 등록
            redisTemplateMaster.opsForZSet().add(RankingKeys.dailyKey(TODAY), String.valueOf(product.getId()), 100.0);

            // act
            String url = "/api/v1/products/" + product.getId();
            ParameterizedTypeReference<ApiResponse<ProductV1Dto.ProductResponse>> type =
                    new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<ProductV1Dto.ProductResponse>> response =
                    testRestTemplate.exchange(url, HttpMethod.GET, HttpEntity.EMPTY, type);

            // assert
            assertAll(
                    () -> assertTrue(response.getStatusCode().is2xxSuccessful()),
                    () -> assertThat(response.getBody().data().rank()).isEqualTo(1L)
            );
        }

        @Test
        @DisplayName("오늘 랭킹에 없는 상품을 조회하면 rank 필드가 null이다")
        void returnsNullRank_whenProductNotInRanking() {
            // arrange
            Brand brand = brandJpaRepository.save(new Brand(BRAND_NAME));
            Product product = productJpaRepository.save(new Product(brand.getId(), "에어맥스", new Money(PRICE), new Stock(STOCK)));
            // Redis에 등록하지 않음

            // act
            String url = "/api/v1/products/" + product.getId();
            ParameterizedTypeReference<ApiResponse<ProductV1Dto.ProductResponse>> type =
                    new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<ProductV1Dto.ProductResponse>> response =
                    testRestTemplate.exchange(url, HttpMethod.GET, HttpEntity.EMPTY, type);

            // assert
            assertAll(
                    () -> assertTrue(response.getStatusCode().is2xxSuccessful()),
                    () -> assertThat(response.getBody().data().rank()).isNull()
            );
        }
    }
}

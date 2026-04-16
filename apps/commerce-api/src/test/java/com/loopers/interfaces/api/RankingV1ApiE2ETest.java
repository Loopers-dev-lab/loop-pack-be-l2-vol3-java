package com.loopers.interfaces.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import com.loopers.config.redis.RedisConfig;
import com.loopers.infrastructure.brand.BrandEntity;
import com.loopers.infrastructure.brand.BrandJpaRepository;
import com.loopers.infrastructure.product.ProductEntity;
import com.loopers.infrastructure.product.ProductJpaRepository;
import com.loopers.interfaces.api.product.ProductV1Dto;
import com.loopers.interfaces.api.ranking.RankingV1Dto;
import com.loopers.utils.DatabaseCleanUp;
import com.loopers.utils.RedisCleanUp;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RankingV1ApiE2ETest {

    private static final String RANKING_KEY_PREFIX = "ranking:all:";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");

    @Autowired
    private TestRestTemplate testRestTemplate;

    @Autowired
    private BrandJpaRepository brandJpaRepository;

    @Autowired
    private ProductJpaRepository productJpaRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @Autowired
    private RedisCleanUp redisCleanUp;

    @Autowired
    @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER)
    private RedisTemplate<String, String> redisTemplate;

    private BrandEntity savedBrand;

    @BeforeEach
    void setUp() {
        savedBrand = brandJpaRepository.save(createBrandEntity("나이키", "스포츠 브랜드"));
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
        redisCleanUp.truncateAll();
    }

    @Nested
    @DisplayName("GET /api/v1/rankings")
    class GetRankings {

        @Test
        @DisplayName("ZSET에 점수가 반영된 상품들을 조회하면, 점수 내림차순으로 상품+브랜드 정보가 aggregation된 랭킹 목록을 반환한다")
        void success_returns_rankings_with_aggregated_product_and_brand_info() {
            // given
            ProductEntity product1 = productJpaRepository.save(
                createProductEntity("에어맥스", savedBrand.getId(), 150000, 100, 0)
            );
            ProductEntity product2 = productJpaRepository.save(
                createProductEntity("조던", savedBrand.getId(), 200000, 50, 0)
            );

            LocalDate today = LocalDate.now();
            String key = RANKING_KEY_PREFIX + today.format(DATE_FORMATTER);
            redisTemplate.opsForZSet().add(key, String.valueOf(product1.getId()), 0.5);
            redisTemplate.opsForZSet().add(key, String.valueOf(product2.getId()), 1.0);

            String url = "/api/v1/rankings?date=" + today.format(DATE_FORMATTER) + "&page=0&size=20";
            var responseType = new ParameterizedTypeReference<ApiResponse<List<RankingV1Dto.RankingResponse>>>() {};

            // when
            ResponseEntity<ApiResponse<List<RankingV1Dto.RankingResponse>>> response =
                testRestTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(null), responseType);

            // then
            List<RankingV1Dto.RankingResponse> data = response.getBody().data();
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(data).hasSize(2),
                // 점수 높은 순 (조던 1.0 > 에어맥스 0.5)
                () -> assertThat(data.get(0).rank()).isEqualTo(1L),
                () -> assertThat(data.get(0).productName()).isEqualTo("조던"),
                () -> assertThat(data.get(0).brandName()).isEqualTo("나이키"),
                () -> assertThat(data.get(0).score()).isEqualTo(1.0),
                () -> assertThat(data.get(1).rank()).isEqualTo(2L),
                () -> assertThat(data.get(1).productName()).isEqualTo("에어맥스")
            );
        }

        @Test
        @DisplayName("ZSET에 데이터가 없으면, 빈 목록을 반환한다")
        void success_returns_empty_list_when_no_ranking_data() {
            LocalDate today = LocalDate.now();
            String url = "/api/v1/rankings?date=" + today.format(DATE_FORMATTER) + "&page=0&size=20";
            var responseType = new ParameterizedTypeReference<ApiResponse<List<RankingV1Dto.RankingResponse>>>() {};

            ResponseEntity<ApiResponse<List<RankingV1Dto.RankingResponse>>> response =
                testRestTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(null), responseType);

            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().data()).isEmpty()
            );
        }

        @Test
        @DisplayName("일자가 변경되어도, 이전 날짜로 조회하면 해당 날짜의 랭킹을 정상 반환한다")
        void success_returns_past_date_ranking_independently_of_today() {
            // given
            ProductEntity product = productJpaRepository.save(
                createProductEntity("에어맥스", savedBrand.getId(), 150000, 100, 0)
            );

            LocalDate yesterday = LocalDate.now().minusDays(1);
            LocalDate today = LocalDate.now();

            // 어제 ZSET에만 점수 적재
            redisTemplate.opsForZSet().add(
                RANKING_KEY_PREFIX + yesterday.format(DATE_FORMATTER),
                String.valueOf(product.getId()),
                2.0
            );
            // 오늘 ZSET은 비어있음

            var responseType = new ParameterizedTypeReference<ApiResponse<List<RankingV1Dto.RankingResponse>>>() {};

            // when - 어제 날짜 조회
            ResponseEntity<ApiResponse<List<RankingV1Dto.RankingResponse>>> yesterdayResponse =
                testRestTemplate.exchange(
                    "/api/v1/rankings?date=" + yesterday.format(DATE_FORMATTER) + "&page=0&size=20",
                    HttpMethod.GET, new HttpEntity<>(null), responseType
                );

            // when - 오늘 날짜 조회
            ResponseEntity<ApiResponse<List<RankingV1Dto.RankingResponse>>> todayResponse =
                testRestTemplate.exchange(
                    "/api/v1/rankings?date=" + today.format(DATE_FORMATTER) + "&page=0&size=20",
                    HttpMethod.GET, new HttpEntity<>(null), responseType
                );

            // then - 어제 랭킹은 정상 반환, 오늘 랭킹은 빈 목록
            assertAll(
                () -> assertThat(yesterdayResponse.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(yesterdayResponse.getBody().data()).hasSize(1),
                () -> assertThat(yesterdayResponse.getBody().data().get(0).productName()).isEqualTo("에어맥스"),

                () -> assertThat(todayResponse.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(todayResponse.getBody().data()).isEmpty()
            );
        }

        @Test
        @DisplayName("페이지 파라미터에 따라 랭킹 목록이 분할되어 반환된다")
        void success_returns_paginated_rankings() {
            // given - 상품 3개 등록
            ProductEntity p1 = productJpaRepository.save(createProductEntity("상품A", savedBrand.getId(), 10000, 10, 0));
            ProductEntity p2 = productJpaRepository.save(createProductEntity("상품B", savedBrand.getId(), 20000, 10, 0));
            ProductEntity p3 = productJpaRepository.save(createProductEntity("상품C", savedBrand.getId(), 30000, 10, 0));

            LocalDate today = LocalDate.now();
            String key = RANKING_KEY_PREFIX + today.format(DATE_FORMATTER);
            redisTemplate.opsForZSet().add(key, String.valueOf(p1.getId()), 3.0);
            redisTemplate.opsForZSet().add(key, String.valueOf(p2.getId()), 2.0);
            redisTemplate.opsForZSet().add(key, String.valueOf(p3.getId()), 1.0);

            var responseType = new ParameterizedTypeReference<ApiResponse<List<RankingV1Dto.RankingResponse>>>() {};
            String dateParam = today.format(DATE_FORMATTER);

            // when - size=2, page=0
            ResponseEntity<ApiResponse<List<RankingV1Dto.RankingResponse>>> page0 =
                testRestTemplate.exchange(
                    "/api/v1/rankings?date=" + dateParam + "&page=0&size=2",
                    HttpMethod.GET, new HttpEntity<>(null), responseType
                );

            // when - size=2, page=1
            ResponseEntity<ApiResponse<List<RankingV1Dto.RankingResponse>>> page1 =
                testRestTemplate.exchange(
                    "/api/v1/rankings?date=" + dateParam + "&page=1&size=2",
                    HttpMethod.GET, new HttpEntity<>(null), responseType
                );

            // then
            assertAll(
                () -> assertThat(page0.getBody().data()).hasSize(2),
                () -> assertThat(page0.getBody().data().get(0).productName()).isEqualTo("상품A"),
                () -> assertThat(page0.getBody().data().get(0).rank()).isEqualTo(1L),
                () -> assertThat(page1.getBody().data()).hasSize(1),
                () -> assertThat(page1.getBody().data().get(0).productName()).isEqualTo("상품C"),
                () -> assertThat(page1.getBody().data().get(0).rank()).isEqualTo(3L)
            );
        }
    }

    @Nested
    @DisplayName("GET /api/v1/products/{productId} - 순위 반환")
    class GetProductWithRank {

        @Test
        @DisplayName("ZSET에 점수가 있는 상품 조회 시, 해당 상품의 순위가 함께 반환된다")
        void success_returns_product_detail_with_rank() {
            // given
            ProductEntity product1 = productJpaRepository.save(
                createProductEntity("에어맥스", savedBrand.getId(), 150000, 100, 0)
            );
            ProductEntity product2 = productJpaRepository.save(
                createProductEntity("조던", savedBrand.getId(), 200000, 50, 0)
            );

            LocalDate today = LocalDate.now();
            String key = RANKING_KEY_PREFIX + today.format(DATE_FORMATTER);
            redisTemplate.opsForZSet().add(key, String.valueOf(product1.getId()), 0.5); // 2위
            redisTemplate.opsForZSet().add(key, String.valueOf(product2.getId()), 1.0); // 1위

            var responseType = new ParameterizedTypeReference<ApiResponse<ProductV1Dto.ProductDetailResponse>>() {};

            // when
            ResponseEntity<ApiResponse<ProductV1Dto.ProductDetailResponse>> response =
                testRestTemplate.exchange(
                    "/api/v1/products/" + product1.getId(),
                    HttpMethod.GET, new HttpEntity<>(null), responseType
                );

            // then - 에어맥스는 2위
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().data().rank()).isEqualTo(2L)
            );
        }

        @Test
        @DisplayName("ZSET에 점수가 없는 상품 조회 시, 순위는 null로 반환된다")
        void success_returns_null_rank_when_product_not_in_ranking() {
            // given
            ProductEntity product = productJpaRepository.save(
                createProductEntity("에어맥스", savedBrand.getId(), 150000, 100, 0)
            );
            // Redis에 점수 없음

            var responseType = new ParameterizedTypeReference<ApiResponse<ProductV1Dto.ProductDetailResponse>>() {};

            // when
            ResponseEntity<ApiResponse<ProductV1Dto.ProductDetailResponse>> response =
                testRestTemplate.exchange(
                    "/api/v1/products/" + product.getId(),
                    HttpMethod.GET, new HttpEntity<>(null), responseType
                );

            // then
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().data().rank()).isNull()
            );
        }
    }

    private BrandEntity createBrandEntity(String name, String description) {
        try {
            BrandEntity entity = BrandEntity.class.getDeclaredConstructor().newInstance();

            var nameField = BrandEntity.class.getDeclaredField("name");
            nameField.setAccessible(true);
            nameField.set(entity, name);

            var descField = BrandEntity.class.getDeclaredField("description");
            descField.setAccessible(true);
            descField.set(entity, description);

            return entity;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private ProductEntity createProductEntity(String name, Long brandId, int price, int stock, int likeCount) {
        try {
            ProductEntity entity = ProductEntity.class.getDeclaredConstructor().newInstance();

            var nameField = ProductEntity.class.getDeclaredField("name");
            nameField.setAccessible(true);
            nameField.set(entity, name);

            var brandIdField = ProductEntity.class.getDeclaredField("refBrandId");
            brandIdField.setAccessible(true);
            brandIdField.set(entity, brandId);

            var priceField = ProductEntity.class.getDeclaredField("price");
            priceField.setAccessible(true);
            priceField.set(entity, price);

            var stockField = ProductEntity.class.getDeclaredField("stock");
            stockField.setAccessible(true);
            stockField.set(entity, stock);

            var likeCountField = ProductEntity.class.getDeclaredField("likeCount");
            likeCountField.setAccessible(true);
            likeCountField.set(entity, likeCount);

            return entity;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}

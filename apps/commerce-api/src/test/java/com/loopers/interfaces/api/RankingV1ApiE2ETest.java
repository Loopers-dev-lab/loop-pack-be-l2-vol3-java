package com.loopers.interfaces.api;

import com.loopers.domain.brand.Brand;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.SellingStatus;
import com.loopers.infrastructure.brand.BrandJpaRepository;
import com.loopers.infrastructure.product.ProductJpaRepository;
import com.loopers.interfaces.api.ranking.RankingV1Dto;
import com.loopers.utils.DatabaseCleanUp;
import com.loopers.utils.RedisCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RankingV1ApiE2ETest {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");

    @Autowired
    private TestRestTemplate testRestTemplate;

    @Autowired
    private BrandJpaRepository brandJpaRepository;

    @Autowired
    private ProductJpaRepository productJpaRepository;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @Autowired
    private RedisCleanUp redisCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
        redisCleanUp.truncateAll();
    }

    private Brand createBrand(String name) {
        return brandJpaRepository.save(new Brand(name, null));
    }

    private Product createProduct(Long brandId, String name, int price) {
        return productJpaRepository.save(new Product(brandId, name, null, price, 10, SellingStatus.SELLING));
    }

    private void addRankingScore(LocalDate date, Long productId, double score) {
        String key = "ranking:all:" + date.format(DATE_FORMATTER);
        redisTemplate.opsForZSet().incrementScore(key, String.valueOf(productId), score);
    }

    @DisplayName("GET /api/v1/rankings")
    @Nested
    class GetRankings {

        @DisplayName("ZSET에 데이터가 없으면 빈 목록을 반환한다.")
        @Test
        void returnsEmptyList_whenNoRankingData() {
            // arrange
            String today = LocalDate.now().format(DATE_FORMATTER);

            // act
            ResponseEntity<ApiResponse<RankingV1Dto.RankingPageResponse>> response = testRestTemplate.exchange(
                "/api/v1/rankings?date=" + today,
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<ApiResponse<RankingV1Dto.RankingPageResponse>>() {}
            );

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().data().content()).isEmpty()
            );
        }

        @DisplayName("ZSET에 점수가 높은 순서로 랭킹 목록을 반환한다.")
        @Test
        void returnsRankingList_orderedByScore() {
            // arrange
            Brand brand = createBrand("Nike");
            Product product1 = createProduct(brand.getId(), "상품A", 10000);
            Product product2 = createProduct(brand.getId(), "상품B", 20000);
            Product product3 = createProduct(brand.getId(), "상품C", 30000);

            LocalDate today = LocalDate.now();
            addRankingScore(today, product3.getId(), 0.7); // 1위
            addRankingScore(today, product1.getId(), 0.4); // 2위
            addRankingScore(today, product2.getId(), 0.2); // 3위

            // act
            ResponseEntity<ApiResponse<RankingV1Dto.RankingPageResponse>> response = testRestTemplate.exchange(
                "/api/v1/rankings?date=" + today.format(DATE_FORMATTER),
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<ApiResponse<RankingV1Dto.RankingPageResponse>>() {}
            );

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().data().content()).hasSize(3),
                () -> assertThat(response.getBody().data().content().get(0).rank()).isEqualTo(1),
                () -> assertThat(response.getBody().data().content().get(0).productId()).isEqualTo(product3.getId()),
                () -> assertThat(response.getBody().data().content().get(1).rank()).isEqualTo(2),
                () -> assertThat(response.getBody().data().content().get(1).productId()).isEqualTo(product1.getId()),
                () -> assertThat(response.getBody().data().content().get(2).rank()).isEqualTo(3),
                () -> assertThat(response.getBody().data().content().get(2).productId()).isEqualTo(product2.getId())
            );
        }

        @DisplayName("date 파라미터 없이 호출하면 오늘 날짜 기준으로 반환한다.")
        @Test
        void returnsRankingForToday_whenDateParamOmitted() {
            // arrange
            Brand brand = createBrand("Adidas");
            Product product = createProduct(brand.getId(), "에어맥스", 50000);
            addRankingScore(LocalDate.now(), product.getId(), 0.5);

            // act
            ResponseEntity<ApiResponse<RankingV1Dto.RankingPageResponse>> response = testRestTemplate.exchange(
                "/api/v1/rankings",
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<ApiResponse<RankingV1Dto.RankingPageResponse>>() {}
            );

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().data().content()).hasSize(1),
                () -> assertThat(response.getBody().data().content().get(0).productId()).isEqualTo(product.getId())
            );
        }

        @DisplayName("다른 날짜의 랭킹을 조회하면 해당 날짜 데이터만 반환한다.")
        @Test
        void returnsRankingByDate_whenDateParamGiven() {
            // arrange
            Brand brand = createBrand("Puma");
            Product product = createProduct(brand.getId(), "클래식", 30000);
            LocalDate yesterday = LocalDate.now().minusDays(1);
            addRankingScore(yesterday, product.getId(), 1.0);

            // act — 어제 날짜로 조회
            ResponseEntity<ApiResponse<RankingV1Dto.RankingPageResponse>> response = testRestTemplate.exchange(
                "/api/v1/rankings?date=" + yesterday.format(DATE_FORMATTER),
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<ApiResponse<RankingV1Dto.RankingPageResponse>>() {}
            );

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().data().content()).hasSize(1),
                () -> assertThat(response.getBody().data().date()).isEqualTo(yesterday.format(DATE_FORMATTER))
            );
        }

        @DisplayName("가중치가 의도대로 랭킹 순서에 반영된다 — 주문 1건이 좋아요 3건보다 높다.")
        @Test
        void rankingOrder_reflectsWeightCorrectly() {
            // arrange
            Brand brand = createBrand("New Balance");
            Product orderProduct = createProduct(brand.getId(), "주문상품", 50000);
            Product likeProduct = createProduct(brand.getId(), "좋아요상품", 10000);

            LocalDate today = LocalDate.now();
            // 주문 1건: 0.7 * log(1 + 50000) ≈ 7.6
            addRankingScore(today, orderProduct.getId(), 0.7 * Math.log1p(50000));
            // 좋아요 3건: 0.2 * 3 = 0.6
            addRankingScore(today, likeProduct.getId(), 0.2);
            addRankingScore(today, likeProduct.getId(), 0.2);
            addRankingScore(today, likeProduct.getId(), 0.2);

            // act
            ResponseEntity<ApiResponse<RankingV1Dto.RankingPageResponse>> response = testRestTemplate.exchange(
                "/api/v1/rankings?date=" + today.format(DATE_FORMATTER),
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<ApiResponse<RankingV1Dto.RankingPageResponse>>() {}
            );

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().data().content().get(0).productId()).isEqualTo(orderProduct.getId()),
                () -> assertThat(response.getBody().data().content().get(1).productId()).isEqualTo(likeProduct.getId())
            );
        }
    }
}

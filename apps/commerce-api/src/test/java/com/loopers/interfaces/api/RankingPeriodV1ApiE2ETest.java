package com.loopers.interfaces.api;

import com.loopers.domain.brand.Brand;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.SellingStatus;
import com.loopers.infrastructure.brand.BrandJpaRepository;
import com.loopers.infrastructure.product.ProductJpaRepository;
import com.loopers.infrastructure.ranking.MvProductRankMonthlyJpaRepository;
import com.loopers.infrastructure.ranking.MvProductRankWeeklyJpaRepository;
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
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RankingPeriodV1ApiE2ETest {

    @Autowired
    private TestRestTemplate testRestTemplate;

    @Autowired
    private BrandJpaRepository brandJpaRepository;

    @Autowired
    private ProductJpaRepository productJpaRepository;

    @Autowired
    private MvProductRankWeeklyJpaRepository weeklyJpaRepository;

    @Autowired
    private MvProductRankMonthlyJpaRepository monthlyJpaRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

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

    @DisplayName("GET /api/v1/rankings?period=weekly")
    @Nested
    class GetWeeklyRankings {

        @DisplayName("mv_product_rank_weekly 데이터가 없으면 빈 목록을 반환한다.")
        @Test
        void returnsEmptyList_whenNoWeeklyMvData() {
            // arrange (empty)

            // act
            ResponseEntity<ApiResponse<RankingV1Dto.RankingPageResponse>> response = testRestTemplate.exchange(
                "/api/v1/rankings?period=weekly",
                HttpMethod.GET, null,
                new ParameterizedTypeReference<ApiResponse<RankingV1Dto.RankingPageResponse>>() {}
            );

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().data().content()).isEmpty(),
                () -> assertThat(response.getBody().data().period()).isEqualTo("weekly"),
                () -> assertThat(response.getBody().data().date()).isNull()
            );
        }

        @DisplayName("mv_product_rank_weekly 데이터를 score 내림차순으로 반환한다.")
        @Test
        void returnsWeeklyRanking_orderedByScore() {
            // arrange
            Brand brand = createBrand("Nike");
            Product product1 = createProduct(brand.getId(), "상품A", 10000);
            Product product2 = createProduct(brand.getId(), "상품B", 20000);

            // product2가 더 높은 score
            jdbcTemplate.update(
                "INSERT INTO mv_product_rank_weekly (product_id, like_count, order_count, score, year_month_week, updated_at) " +
                "VALUES (?, ?, ?, ?, ?, NOW())",
                product1.getId(), 2, 1, 1.1, "2025-W15"
            );
            jdbcTemplate.update(
                "INSERT INTO mv_product_rank_weekly (product_id, like_count, order_count, score, year_month_week, updated_at) " +
                "VALUES (?, ?, ?, ?, ?, NOW())",
                product2.getId(), 5, 3, 3.1, "2025-W15"
            );

            // act
            ResponseEntity<ApiResponse<RankingV1Dto.RankingPageResponse>> response = testRestTemplate.exchange(
                "/api/v1/rankings?period=weekly",
                HttpMethod.GET, null,
                new ParameterizedTypeReference<ApiResponse<RankingV1Dto.RankingPageResponse>>() {}
            );

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().data().content()).hasSize(2),
                () -> assertThat(response.getBody().data().content().get(0).rank()).isEqualTo(1),
                () -> assertThat(response.getBody().data().content().get(0).productId()).isEqualTo(product2.getId()),
                () -> assertThat(response.getBody().data().content().get(1).rank()).isEqualTo(2),
                () -> assertThat(response.getBody().data().content().get(1).productId()).isEqualTo(product1.getId()),
                () -> assertThat(response.getBody().data().period()).isEqualTo("weekly")
            );
        }
    }

    @DisplayName("GET /api/v1/rankings?period=monthly")
    @Nested
    class GetMonthlyRankings {

        @DisplayName("mv_product_rank_monthly 데이터가 없으면 빈 목록을 반환한다.")
        @Test
        void returnsEmptyList_whenNoMonthlyMvData() {
            // arrange (empty)

            // act
            ResponseEntity<ApiResponse<RankingV1Dto.RankingPageResponse>> response = testRestTemplate.exchange(
                "/api/v1/rankings?period=monthly",
                HttpMethod.GET, null,
                new ParameterizedTypeReference<ApiResponse<RankingV1Dto.RankingPageResponse>>() {}
            );

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().data().content()).isEmpty(),
                () -> assertThat(response.getBody().data().period()).isEqualTo("monthly"),
                () -> assertThat(response.getBody().data().date()).isNull()
            );
        }

        @DisplayName("mv_product_rank_monthly 데이터를 score 내림차순으로 반환한다.")
        @Test
        void returnsMonthlyRanking_orderedByScore() {
            // arrange
            Brand brand = createBrand("Adidas");
            Product product1 = createProduct(brand.getId(), "상품C", 15000);
            Product product2 = createProduct(brand.getId(), "상품D", 25000);

            jdbcTemplate.update(
                "INSERT INTO mv_product_rank_monthly (product_id, like_count, order_count, score, ranking_period, updated_at) " +
                "VALUES (?, ?, ?, ?, ?, NOW())",
                product1.getId(), 3, 2, 2.0, "2025-04"
            );
            jdbcTemplate.update(
                "INSERT INTO mv_product_rank_monthly (product_id, like_count, order_count, score, ranking_period, updated_at) " +
                "VALUES (?, ?, ?, ?, ?, NOW())",
                product2.getId(), 10, 8, 7.6, "2025-04"
            );

            // act
            ResponseEntity<ApiResponse<RankingV1Dto.RankingPageResponse>> response = testRestTemplate.exchange(
                "/api/v1/rankings?period=monthly",
                HttpMethod.GET, null,
                new ParameterizedTypeReference<ApiResponse<RankingV1Dto.RankingPageResponse>>() {}
            );

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().data().content()).hasSize(2),
                () -> assertThat(response.getBody().data().content().get(0).productId()).isEqualTo(product2.getId()),
                () -> assertThat(response.getBody().data().content().get(1).productId()).isEqualTo(product1.getId()),
                () -> assertThat(response.getBody().data().period()).isEqualTo("monthly")
            );
        }
    }

    @DisplayName("GET /api/v1/rankings (period 미입력)")
    @Nested
    class GetDefaultPeriodRankings {

        @DisplayName("period 파라미터 없이 호출하면 daily로 처리한다.")
        @Test
        void defaultsToDailyRanking_whenPeriodParamOmitted() {
            // act
            ResponseEntity<ApiResponse<RankingV1Dto.RankingPageResponse>> response = testRestTemplate.exchange(
                "/api/v1/rankings",
                HttpMethod.GET, null,
                new ParameterizedTypeReference<ApiResponse<RankingV1Dto.RankingPageResponse>>() {}
            );

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().data().period()).isEqualTo("daily")
            );
        }
    }
}

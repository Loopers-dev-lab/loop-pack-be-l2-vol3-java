package com.loopers.interfaces.api;

import com.loopers.domain.product.Brand;
import com.loopers.domain.product.Product;
import com.loopers.infrastructure.product.BrandJpaRepository;
import com.loopers.infrastructure.product.ProductJpaRepository;
import com.loopers.interfaces.api.ranking.RankingV1Dto;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RankingV1ApiE2ETest {

    private static final String ENDPOINT = "/api/v1/rankings";

    @Autowired
    private TestRestTemplate testRestTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ProductJpaRepository productJpaRepository;

    @Autowired
    private BrandJpaRepository brandJpaRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("GET /api/v1/rankings?period=weekly")
    @Nested
    class WeeklyRanking {

        @DisplayName("주간 랭킹을 조회하면, MV 테이블에서 순위 데이터를 반환한다")
        @Test
        void returnsWeeklyRankings() {
            // arrange
            Brand brand = brandJpaRepository.save(new Brand("나이키"));
            Product product1 = productJpaRepository.save(new Product(brand.getId(), "에어맥스", 150000L, 100));
            Product product2 = productJpaRepository.save(new Product(brand.getId(), "조던", 200000L, 50));

            LocalDate weekStart = LocalDate.of(2026, 4, 13);
            insertWeeklyMv(weekStart, product1.getId(), 1, 565.0);
            insertWeeklyMv(weekStart, product2.getId(), 2, 210.0);

            // act
            String url = ENDPOINT + "?period=weekly&date=20260415&size=10&page=1";
            var response = exchange(url);

            // assert
            assertAll(
                () -> assertTrue(response.getStatusCode().is2xxSuccessful()),
                () -> assertThat(response.getBody().data()).hasSize(2),
                () -> assertThat(response.getBody().data().get(0).rank()).isEqualTo(1),
                () -> assertThat(response.getBody().data().get(0).productName()).isEqualTo("에어맥스"),
                () -> assertThat(response.getBody().data().get(0).score()).isEqualTo(565.0),
                () -> assertThat(response.getBody().data().get(1).rank()).isEqualTo(2),
                () -> assertThat(response.getBody().data().get(1).productName()).isEqualTo("조던")
            );
        }
    }

    @DisplayName("GET /api/v1/rankings?period=monthly")
    @Nested
    class MonthlyRanking {

        @DisplayName("월간 랭킹을 조회하면, MV 테이블에서 순위 데이터를 반환한다")
        @Test
        void returnsMonthlyRankings() {
            // arrange
            Brand brand = brandJpaRepository.save(new Brand("아디다스"));
            Product product1 = productJpaRepository.save(new Product(brand.getId(), "울트라부스트", 180000L, 80));

            LocalDate monthStart = LocalDate.of(2026, 4, 1);
            insertMonthlyMv(monthStart, product1.getId(), 1, 1200.0);

            // act
            String url = ENDPOINT + "?period=monthly&date=20260416&size=10&page=1";
            var response = exchange(url);

            // assert
            assertAll(
                () -> assertTrue(response.getStatusCode().is2xxSuccessful()),
                () -> assertThat(response.getBody().data()).hasSize(1),
                () -> assertThat(response.getBody().data().get(0).rank()).isEqualTo(1),
                () -> assertThat(response.getBody().data().get(0).productName()).isEqualTo("울트라부스트"),
                () -> assertThat(response.getBody().data().get(0).brandName()).isEqualTo("아디다스")
            );
        }
    }

    @DisplayName("GET /api/v1/rankings 파라미터 검증")
    @Nested
    class Validation {

        @DisplayName("잘못된 period를 주면 400을 반환한다")
        @Test
        void returnsBadRequest_whenInvalidPeriod() {
            String url = ENDPOINT + "?period=yearly";
            var response = exchange(url);

            assertTrue(response.getStatusCode().is4xxClientError());
        }

        @DisplayName("잘못된 date 형식을 주면 400을 반환한다")
        @Test
        void returnsBadRequest_whenInvalidDateFormat() {
            String url = ENDPOINT + "?period=weekly&date=2026-04-13";
            var response = exchange(url);

            assertTrue(response.getStatusCode().is4xxClientError());
        }
    }

    private ResponseEntity<ApiResponse<List<RankingV1Dto.RankingResponse>>> exchange(String url) {
        return testRestTemplate.exchange(
            url, HttpMethod.GET, new HttpEntity<>(null),
            new ParameterizedTypeReference<>() {}
        );
    }

    private void insertWeeklyMv(LocalDate weekStart, Long productId, int rankPosition, double totalScore) {
        jdbcTemplate.update(
            """
                INSERT INTO mv_product_rank_weekly
                    (week_start_date, product_id, rank_position, total_score, aggregated_at)
                VALUES (?, ?, ?, ?, NOW())
                """,
            weekStart, productId, rankPosition, totalScore
        );
    }

    private void insertMonthlyMv(LocalDate monthStart, Long productId, int rankPosition, double totalScore) {
        jdbcTemplate.update(
            """
                INSERT INTO mv_product_rank_monthly
                    (month_start_date, product_id, rank_position, total_score, aggregated_at)
                VALUES (?, ?, ?, ?, NOW())
                """,
            monthStart, productId, rankPosition, totalScore
        );
    }
}

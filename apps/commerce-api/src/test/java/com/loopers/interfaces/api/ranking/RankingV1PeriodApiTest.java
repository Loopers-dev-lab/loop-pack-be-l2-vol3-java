package com.loopers.interfaces.api.ranking;

import com.loopers.config.TestRedisConfiguration;
import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.domain.common.Money;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.product.Stock;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestRedisConfiguration.class)
@DisplayName("랭킹 API 기간별 조회 테스트")
class RankingV1PeriodApiTest {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.BASIC_ISO_DATE;

    @Autowired
    private TestRestTemplate testRestTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private BrandRepository brandRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    private Brand testBrand;
    private Product product1;
    private Product product2;
    private Product product3;

    @BeforeEach
    void setUp() {
        cleanUpRedisKeys();
        cleanUpRankingTables();

        testBrand = brandRepository.save(Brand.create("Test Brand", "Test", null));
        product1 = productRepository.save(Product.create(testBrand.getId(), "Product 1", "Desc", new Money(10000), new Stock(100), null));
        product2 = productRepository.save(Product.create(testBrand.getId(), "Product 2", "Desc", new Money(20000), new Stock(100), null));
        product3 = productRepository.save(Product.create(testBrand.getId(), "Product 3", "Desc", new Money(30000), new Stock(100), null));
    }

    @AfterEach
    void tearDown() {
        cleanUpRedisKeys();
        cleanUpRankingTables();
        databaseCleanUp.truncateAllTables();
    }

    private void cleanUpRedisKeys() {
        Set<String> keys = redisTemplate.keys("ranking:*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    private void cleanUpRankingTables() {
        jdbcTemplate.execute("DELETE FROM mv_product_rank_weekly");
        jdbcTemplate.execute("DELETE FROM mv_product_rank_monthly");
    }

    private String getTodayKey() {
        return "ranking:all:" + LocalDate.now().format(DATE_FORMATTER);
    }

    @Nested
    @DisplayName("GET /api/v1/rankings - period 파라미터")
    class PeriodRankings {

        @Test
        @DisplayName("period 미지정 시 기본값 DAILY로 동작한다")
        void defaultPeriodIsDaily() {
            // Arrange
            String today = LocalDate.now().format(DATE_FORMATTER);

            // Act
            ResponseEntity<ApiResponse<RankingV1Dto.PeriodRankingPageResponse>> response = testRestTemplate.exchange(
                "/api/v1/rankings?date=" + today,
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<>() {}
            );

            // Assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().data().period().name()).isEqualTo("DAILY");
            assertThat(response.getBody().data().date()).isEqualTo(today);
        }

        @Test
        @DisplayName("period=DAILY로 일간 랭킹을 조회한다")
        void getDailyRankings() {
            // Arrange
            String today = LocalDate.now().format(DATE_FORMATTER);
            String key = getTodayKey();
            redisTemplate.opsForZSet().add(key, String.valueOf(product1.getId()), 10.0);

            // Act
            ResponseEntity<ApiResponse<RankingV1Dto.PeriodRankingPageResponse>> response = testRestTemplate.exchange(
                "/api/v1/rankings?date=" + today + "&period=DAILY",
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<>() {}
            );

            // Assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().data().period().name()).isEqualTo("DAILY");
            assertThat(response.getBody().data().periodStart()).isEqualTo(today);
            assertThat(response.getBody().data().periodEnd()).isEqualTo(today);
            assertThat(response.getBody().data().rankings()).hasSize(1);
        }

        @Test
        @DisplayName("period=WEEKLY로 주간 랭킹을 조회한다")
        void getWeeklyRankings() {
            // Arrange - 주간 랭킹 데이터 삽입
            LocalDate weekStart = LocalDate.of(2025, 1, 13); // 월요일
            LocalDate weekEnd = LocalDate.of(2025, 1, 19);   // 일요일
            insertWeeklyRanking(product1.getId(), 1, weekStart, weekEnd, BigDecimal.valueOf(15.0), 50, 20, 10);
            insertWeeklyRanking(product2.getId(), 2, weekStart, weekEnd, BigDecimal.valueOf(12.5), 45, 18, 7);

            // Act
            ResponseEntity<ApiResponse<RankingV1Dto.PeriodRankingPageResponse>> response = testRestTemplate.exchange(
                "/api/v1/rankings?date=20250115&period=WEEKLY",
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<>() {}
            );

            // Assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().data().period().name()).isEqualTo("WEEKLY");
            assertThat(response.getBody().data().periodStart()).isEqualTo("20250113");
            assertThat(response.getBody().data().periodEnd()).isEqualTo("20250119");
            assertThat(response.getBody().data().rankings()).hasSize(2);

            // 1위
            assertThat(response.getBody().data().rankings().get(0).rank()).isEqualTo(1);
            assertThat(response.getBody().data().rankings().get(0).productId()).isEqualTo(product1.getId());
            assertThat(response.getBody().data().rankings().get(0).productName()).isEqualTo("Product 1");
            assertThat(response.getBody().data().rankings().get(0).viewCount()).isEqualTo(50);
            assertThat(response.getBody().data().rankings().get(0).likeCount()).isEqualTo(20);
            assertThat(response.getBody().data().rankings().get(0).orderCount()).isEqualTo(10);

            // 2위
            assertThat(response.getBody().data().rankings().get(1).rank()).isEqualTo(2);
            assertThat(response.getBody().data().rankings().get(1).productId()).isEqualTo(product2.getId());
        }

        @Test
        @DisplayName("period=MONTHLY로 월간 랭킹을 조회한다")
        void getMonthlyRankings() {
            // Arrange - 월간 랭킹 데이터 삽입
            LocalDate monthStart = LocalDate.of(2025, 1, 1);
            LocalDate monthEnd = LocalDate.of(2025, 1, 31);
            insertMonthlyRanking(product3.getId(), 1, monthStart, monthEnd, BigDecimal.valueOf(100.0), 500, 200, 50);

            // Act
            ResponseEntity<ApiResponse<RankingV1Dto.PeriodRankingPageResponse>> response = testRestTemplate.exchange(
                "/api/v1/rankings?date=20250115&period=MONTHLY",
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<>() {}
            );

            // Assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().data().period().name()).isEqualTo("MONTHLY");
            assertThat(response.getBody().data().periodStart()).isEqualTo("20250101");
            assertThat(response.getBody().data().periodEnd()).isEqualTo("20250131");
            assertThat(response.getBody().data().rankings()).hasSize(1);
            assertThat(response.getBody().data().rankings().get(0).rank()).isEqualTo(1);
            assertThat(response.getBody().data().rankings().get(0).productId()).isEqualTo(product3.getId());
            assertThat(response.getBody().data().rankings().get(0).productName()).isEqualTo("Product 3");
        }

        @Test
        @DisplayName("주간 랭킹 페이징이 정상 동작한다")
        void weeklyRankingPagination() {
            // Arrange - 3개의 주간 랭킹 데이터
            LocalDate weekStart = LocalDate.of(2025, 1, 13);
            LocalDate weekEnd = LocalDate.of(2025, 1, 19);
            insertWeeklyRanking(product1.getId(), 1, weekStart, weekEnd, BigDecimal.valueOf(15.0), 50, 20, 10);
            insertWeeklyRanking(product2.getId(), 2, weekStart, weekEnd, BigDecimal.valueOf(12.5), 45, 18, 7);
            insertWeeklyRanking(product3.getId(), 3, weekStart, weekEnd, BigDecimal.valueOf(10.0), 40, 15, 5);

            // Act & Assert - 페이지 1 (size=2)
            ResponseEntity<ApiResponse<RankingV1Dto.PeriodRankingPageResponse>> response1 = testRestTemplate.exchange(
                "/api/v1/rankings?date=20250115&period=WEEKLY&size=2&page=1",
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<>() {}
            );

            assertThat(response1.getBody()).isNotNull();
            assertThat(response1.getBody().data().rankings()).hasSize(2);
            assertThat(response1.getBody().data().rankings().get(0).rank()).isEqualTo(1);
            assertThat(response1.getBody().data().rankings().get(1).rank()).isEqualTo(2);
            assertThat(response1.getBody().data().page()).isEqualTo(1);
            assertThat(response1.getBody().data().size()).isEqualTo(2);
            assertThat(response1.getBody().data().totalCount()).isEqualTo(3);
            assertThat(response1.getBody().data().totalPages()).isEqualTo(2);

            // Act & Assert - 페이지 2 (size=2)
            ResponseEntity<ApiResponse<RankingV1Dto.PeriodRankingPageResponse>> response2 = testRestTemplate.exchange(
                "/api/v1/rankings?date=20250115&period=WEEKLY&size=2&page=2",
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<>() {}
            );

            assertThat(response2.getBody()).isNotNull();
            assertThat(response2.getBody().data().rankings()).hasSize(1);
            assertThat(response2.getBody().data().rankings().get(0).rank()).isEqualTo(3);
        }

        @Test
        @DisplayName("해당 기간에 데이터가 없으면 빈 목록을 반환한다")
        void emptyRankingsForPeriod() {
            // Act
            ResponseEntity<ApiResponse<RankingV1Dto.PeriodRankingPageResponse>> response = testRestTemplate.exchange(
                "/api/v1/rankings?date=20250101&period=WEEKLY",
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<>() {}
            );

            // Assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().data().rankings()).isEmpty();
            assertThat(response.getBody().data().totalCount()).isEqualTo(0);
        }
    }

    private void insertWeeklyRanking(Long productId, int rank, LocalDate periodStart, LocalDate periodEnd,
                                     BigDecimal score, long viewCount, long likeCount, long orderCount) {
        jdbcTemplate.update(
            """
            INSERT INTO mv_product_rank_weekly
            (product_id, rank_number, total_score, total_view_count, total_like_count, total_order_count, period_start_date, period_end_date, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, NOW())
            """,
            productId, rank, score, viewCount, likeCount, orderCount, periodStart, periodEnd
        );
    }

    private void insertMonthlyRanking(Long productId, int rank, LocalDate periodStart, LocalDate periodEnd,
                                      BigDecimal score, long viewCount, long likeCount, long orderCount) {
        jdbcTemplate.update(
            """
            INSERT INTO mv_product_rank_monthly
            (product_id, rank_number, total_score, total_view_count, total_like_count, total_order_count, period_start_date, period_end_date, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, NOW())
            """,
            productId, rank, score, viewCount, likeCount, orderCount, periodStart, periodEnd
        );
    }
}
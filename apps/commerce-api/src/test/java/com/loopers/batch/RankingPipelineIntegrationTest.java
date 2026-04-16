package com.loopers.batch;

import com.loopers.application.ranking.RankingPeriod;
import com.loopers.config.TestRedisConfiguration;
import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.domain.common.Money;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.product.Stock;
import com.loopers.infrastructure.persistence.jpa.rank.ProductRankMonthlyJpaRepository;
import com.loopers.infrastructure.persistence.jpa.rank.ProductRankWeeklyJpaRepository;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.batch.BatchAdminV1Dto;
import com.loopers.interfaces.api.ranking.RankingV1Dto;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
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
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 랭킹 파이프라인 전체 통합 테스트.
 *
 * <p>테스트 시나리오:
 * <ol>
 *   <li>테스트 데이터 준비 (product_metrics_daily)</li>
 *   <li>주간 배치 Job 실행 및 검증</li>
 *   <li>월간 배치 Job 실행 및 검증</li>
 *   <li>기간별 랭킹 API 조회 검증</li>
 *   <li>배치 재실행 시 멱등성 검증</li>
 * </ol>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestRedisConfiguration.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("랭킹 파이프라인 통합 테스트")
class RankingPipelineIntegrationTest {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.BASIC_ISO_DATE;
    private static final String TARGET_DATE = "20250414"; // 2025년 4월 14일 (월요일)

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
    private ProductRankWeeklyJpaRepository weeklyRankRepository;

    @Autowired
    private ProductRankMonthlyJpaRepository monthlyRankRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    private Brand testBrand;
    private List<Product> testProducts;

    @BeforeEach
    void setUp() {
        cleanUp();
        setupTestData();
    }

    @AfterEach
    void tearDown() {
        cleanUp();
        databaseCleanUp.truncateAllTables();
    }

    private void cleanUp() {
        Set<String> keys = redisTemplate.keys("ranking:*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
        jdbcTemplate.execute("DELETE FROM mv_product_rank_weekly");
        jdbcTemplate.execute("DELETE FROM mv_product_rank_monthly");
        jdbcTemplate.execute("DELETE FROM product_metrics_daily");
    }

    /**
     * 테스트 데이터 준비:
     * - 상품 10개 생성
     * - 2025년 4월 1일~30일 일별 메트릭 데이터
     * - Product 1이 최고점, Product 2가 2위, Product 3이 3위가 되도록 가중치 부여
     */
    private void setupTestData() {
        testBrand = brandRepository.save(Brand.create("Test Brand", "Test", null));

        // 10개 상품 생성
        testProducts = new java.util.ArrayList<>();
        for (int i = 1; i <= 10; i++) {
            Product product = productRepository.save(
                Product.create(testBrand.getId(), "Product " + i, "Desc", new Money(10000L * i), new Stock(100), null)
            );
            testProducts.add(product);
        }

        // 2025년 4월 1일~30일 일별 메트릭 데이터 삽입
        LocalDate startDate = LocalDate.of(2025, 4, 1);
        LocalDate endDate = LocalDate.of(2025, 4, 30);

        for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
            // Product 1: 최고 점수 (일평균 25~30)
            insertDailyMetrics(testProducts.get(0).getId(), date, 120, 60, 25, BigDecimal.valueOf(27.5));

            // Product 2: 2위 (일평균 20~24)
            insertDailyMetrics(testProducts.get(1).getId(), date, 100, 50, 20, BigDecimal.valueOf(22.0));

            // Product 3: 3위 (일평균 15~18)
            insertDailyMetrics(testProducts.get(2).getId(), date, 80, 40, 15, BigDecimal.valueOf(16.5));

            // Products 4~10: 낮은 점수 (일평균 5~12)
            for (int i = 3; i < testProducts.size(); i++) {
                int baseScore = 12 - i; // 4번째 상품: 8, 5번째: 7, ...
                insertDailyMetrics(
                    testProducts.get(i).getId(),
                    date,
                    20 + i * 5,
                    10 + i * 2,
                    2 + i,
                    BigDecimal.valueOf(baseScore)
                );
            }
        }
    }

    private void insertDailyMetrics(Long productId, LocalDate date, int viewCount, int likeCount, int orderCount, BigDecimal score) {
        jdbcTemplate.update(
            """
            INSERT INTO product_metrics_daily (product_id, metric_date, view_count, like_count, order_count, score, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, NOW(), NOW())
            """,
            productId, date, viewCount, likeCount, orderCount, score
        );
    }

    @Test
    @Order(1)
    @DisplayName("1. 테스트 데이터가 정상 생성되었는지 확인")
    void verifyTestDataCreated() {
        // 상품 수 확인
        assertThat(testProducts).hasSize(10);

        // 일별 메트릭 데이터 수 확인 (10 상품 x 30 일 = 300)
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM product_metrics_daily WHERE metric_date BETWEEN '2025-04-01' AND '2025-04-30'",
            Integer.class
        );
        assertThat(count).isEqualTo(300);

        // 월간 총점 상위 3개 확인
        List<Long> topProductIds = jdbcTemplate.query(
            """
            SELECT product_id FROM product_metrics_daily
            WHERE metric_date BETWEEN '2025-04-01' AND '2025-04-30'
            GROUP BY product_id
            ORDER BY SUM(score) DESC
            LIMIT 3
            """,
            (rs, rowNum) -> rs.getLong("product_id")
        );

        assertThat(topProductIds).containsExactly(
            testProducts.get(0).getId(),
            testProducts.get(1).getId(),
            testProducts.get(2).getId()
        );
    }

    @Test
    @Order(2)
    @DisplayName("2. 주간 배치 Job 실행 및 결과 검증")
    void runWeeklyBatchJobAndVerify() {
        // Act - 주간 배치 실행
        ResponseEntity<ApiResponse<BatchAdminV1Dto.JobExecutionResponse>> response = testRestTemplate.exchange(
            "/api-admin/v1/batch/weekly-ranking?targetDate=" + TARGET_DATE,
            HttpMethod.POST,
            null,
            new ParameterizedTypeReference<>() {}
        );

        // Assert - Job 실행 성공
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().data().status().name()).isEqualTo("COMPLETED");
        assertThat(response.getBody().data().jobName()).isEqualTo("weeklyRankingJob");

        // Assert - 주간 랭킹 테이블에 데이터 생성됨
        // 2025-04-14는 4월 14일(월) ~ 4월 20일(일) 주
        LocalDate weekStart = LocalDate.of(2025, 4, 14);
        var weeklyRankings = weeklyRankRepository.findByPeriodStartDateOrderByRankNumberAsc(weekStart);

        assertThat(weeklyRankings).isNotEmpty();
        assertThat(weeklyRankings.size()).isLessThanOrEqualTo(100); // TOP 100

        // 1위: Product 1
        assertThat(weeklyRankings.get(0).getProductId()).isEqualTo(testProducts.get(0).getId());
        assertThat(weeklyRankings.get(0).getRankNumber()).isEqualTo(1);

        // 2위: Product 2
        assertThat(weeklyRankings.get(1).getProductId()).isEqualTo(testProducts.get(1).getId());
        assertThat(weeklyRankings.get(1).getRankNumber()).isEqualTo(2);

        // 3위: Product 3
        assertThat(weeklyRankings.get(2).getProductId()).isEqualTo(testProducts.get(2).getId());
        assertThat(weeklyRankings.get(2).getRankNumber()).isEqualTo(3);
    }

    @Test
    @Order(3)
    @DisplayName("3. 월간 배치 Job 실행 및 결과 검증")
    void runMonthlyBatchJobAndVerify() {
        // Act - 월간 배치 실행
        ResponseEntity<ApiResponse<BatchAdminV1Dto.JobExecutionResponse>> response = testRestTemplate.exchange(
            "/api-admin/v1/batch/monthly-ranking?targetDate=" + TARGET_DATE,
            HttpMethod.POST,
            null,
            new ParameterizedTypeReference<>() {}
        );

        // Assert - Job 실행 성공
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().data().status().name()).isEqualTo("COMPLETED");
        assertThat(response.getBody().data().jobName()).isEqualTo("monthlyRankingJob");

        // Assert - 월간 랭킹 테이블에 데이터 생성됨
        LocalDate monthStart = LocalDate.of(2025, 4, 1);
        var monthlyRankings = monthlyRankRepository.findByPeriodStartDateOrderByRankNumberAsc(monthStart);

        assertThat(monthlyRankings).hasSize(10); // 10개 상품 모두

        // 1위: Product 1 (30일 * 27.5 = 825)
        assertThat(monthlyRankings.get(0).getProductId()).isEqualTo(testProducts.get(0).getId());
        assertThat(monthlyRankings.get(0).getRankNumber()).isEqualTo(1);
        assertThat(monthlyRankings.get(0).getTotalViewCount()).isEqualTo(120 * 30); // 3600
        assertThat(monthlyRankings.get(0).getTotalLikeCount()).isEqualTo(60 * 30);  // 1800
        assertThat(monthlyRankings.get(0).getTotalOrderCount()).isEqualTo(25 * 30); // 750

        // 2위: Product 2
        assertThat(monthlyRankings.get(1).getProductId()).isEqualTo(testProducts.get(1).getId());

        // 3위: Product 3
        assertThat(monthlyRankings.get(2).getProductId()).isEqualTo(testProducts.get(2).getId());
    }

    @Test
    @Order(4)
    @DisplayName("4. 주간 랭킹 API 조회 검증")
    void getWeeklyRankingsApi() {
        // Arrange - 먼저 배치 실행
        testRestTemplate.exchange(
            "/api-admin/v1/batch/weekly-ranking?targetDate=" + TARGET_DATE,
            HttpMethod.POST,
            null,
            new ParameterizedTypeReference<ApiResponse<BatchAdminV1Dto.JobExecutionResponse>>() {}
        );

        // Act - 주간 랭킹 API 조회
        ResponseEntity<ApiResponse<RankingV1Dto.PeriodRankingPageResponse>> response = testRestTemplate.exchange(
            "/api/v1/rankings?date=" + TARGET_DATE + "&period=WEEKLY&size=10&page=1",
            HttpMethod.GET,
            null,
            new ParameterizedTypeReference<>() {}
        );

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();

        var data = response.getBody().data();
        assertThat(data.period().name()).isEqualTo("WEEKLY");
        assertThat(data.periodStart()).isEqualTo("20250414"); // 월요일
        assertThat(data.periodEnd()).isEqualTo("20250420");   // 일요일
        assertThat(data.rankings()).isNotEmpty();

        // 1위 검증
        var firstRanking = data.rankings().get(0);
        assertThat(firstRanking.rank()).isEqualTo(1);
        assertThat(firstRanking.productId()).isEqualTo(testProducts.get(0).getId());
        assertThat(firstRanking.productName()).isEqualTo("Product 1");
        assertThat(firstRanking.viewCount()).isNotNull();
        assertThat(firstRanking.likeCount()).isNotNull();
        assertThat(firstRanking.orderCount()).isNotNull();
    }

    @Test
    @Order(5)
    @DisplayName("5. 월간 랭킹 API 조회 검증")
    void getMonthlyRankingsApi() {
        // Arrange - 먼저 배치 실행
        testRestTemplate.exchange(
            "/api-admin/v1/batch/monthly-ranking?targetDate=" + TARGET_DATE,
            HttpMethod.POST,
            null,
            new ParameterizedTypeReference<ApiResponse<BatchAdminV1Dto.JobExecutionResponse>>() {}
        );

        // Act - 월간 랭킹 API 조회
        ResponseEntity<ApiResponse<RankingV1Dto.PeriodRankingPageResponse>> response = testRestTemplate.exchange(
            "/api/v1/rankings?date=" + TARGET_DATE + "&period=MONTHLY&size=10&page=1",
            HttpMethod.GET,
            null,
            new ParameterizedTypeReference<>() {}
        );

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();

        var data = response.getBody().data();
        assertThat(data.period().name()).isEqualTo("MONTHLY");
        assertThat(data.periodStart()).isEqualTo("20250401"); // 4월 1일
        assertThat(data.periodEnd()).isEqualTo("20250430");   // 4월 30일
        assertThat(data.rankings()).hasSize(10);
        assertThat(data.totalCount()).isEqualTo(10);

        // 상위 3위 순서 검증
        assertThat(data.rankings().get(0).productId()).isEqualTo(testProducts.get(0).getId());
        assertThat(data.rankings().get(1).productId()).isEqualTo(testProducts.get(1).getId());
        assertThat(data.rankings().get(2).productId()).isEqualTo(testProducts.get(2).getId());
    }

    @Test
    @Order(6)
    @DisplayName("6. 배치 재실행 시 멱등성 검증 (데이터 중복 없음)")
    void batchIdempotencyTest() {
        // Arrange - 첫 번째 실행
        testRestTemplate.exchange(
            "/api-admin/v1/batch/weekly-ranking?targetDate=" + TARGET_DATE,
            HttpMethod.POST,
            null,
            new ParameterizedTypeReference<ApiResponse<BatchAdminV1Dto.JobExecutionResponse>>() {}
        );

        LocalDate weekStart = LocalDate.of(2025, 4, 14);
        int countAfterFirstRun = weeklyRankRepository.findByPeriodStartDateOrderByRankNumberAsc(weekStart).size();

        // Act - 두 번째 실행 (재실행)
        ResponseEntity<ApiResponse<BatchAdminV1Dto.JobExecutionResponse>> response = testRestTemplate.exchange(
            "/api-admin/v1/batch/weekly-ranking?targetDate=" + TARGET_DATE,
            HttpMethod.POST,
            null,
            new ParameterizedTypeReference<>() {}
        );

        // Assert - Job 성공
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().data().status().name()).isEqualTo("COMPLETED");

        // Assert - 데이터 중복 없음 (동일 개수)
        int countAfterSecondRun = weeklyRankRepository.findByPeriodStartDateOrderByRankNumberAsc(weekStart).size();
        assertThat(countAfterSecondRun).isEqualTo(countAfterFirstRun);
    }

    @Test
    @Order(7)
    @DisplayName("7. 일간 랭킹 API 조회 (Redis 기반)")
    void getDailyRankingsApi() {
        // Arrange - Redis에 일간 데이터 삽입
        String today = LocalDate.now().format(DATE_FORMATTER);
        String key = "ranking:all:" + today;

        redisTemplate.opsForZSet().add(key, String.valueOf(testProducts.get(0).getId()), 100.0);
        redisTemplate.opsForZSet().add(key, String.valueOf(testProducts.get(1).getId()), 80.0);
        redisTemplate.opsForZSet().add(key, String.valueOf(testProducts.get(2).getId()), 60.0);

        // Act - 일간 랭킹 API 조회
        ResponseEntity<ApiResponse<RankingV1Dto.PeriodRankingPageResponse>> response = testRestTemplate.exchange(
            "/api/v1/rankings?date=" + today + "&period=DAILY&size=10&page=1",
            HttpMethod.GET,
            null,
            new ParameterizedTypeReference<>() {}
        );

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();

        var data = response.getBody().data();
        assertThat(data.period().name()).isEqualTo("DAILY");
        assertThat(data.periodStart()).isEqualTo(today);
        assertThat(data.periodEnd()).isEqualTo(today);
        assertThat(data.rankings()).hasSize(3);

        // 일간 랭킹은 viewCount/likeCount/orderCount가 null
        assertThat(data.rankings().get(0).viewCount()).isNull();
        assertThat(data.rankings().get(0).likeCount()).isNull();
        assertThat(data.rankings().get(0).orderCount()).isNull();
    }
}
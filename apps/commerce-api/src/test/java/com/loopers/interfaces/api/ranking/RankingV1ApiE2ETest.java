package com.loopers.interfaces.api.ranking;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandService;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductService;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.testcontainers.MySqlTestContainersConfig;
import com.loopers.testcontainers.RedisTestContainersConfig;
import com.loopers.utils.DatabaseCleanUp;
import com.loopers.utils.RedisCleanUp;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.data.redis.core.RedisTemplate;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;

import static com.loopers.interfaces.api.ApiResponse.Metadata.Result;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import({MySqlTestContainersConfig.class, RedisTestContainersConfig.class})
class RankingV1ApiE2ETest {

    private static final String ENDPOINT = "/api/v1/rankings";
    private static final String RANKING_DATE = "20260408";

    @Autowired
    private TestRestTemplate testRestTemplate;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @Autowired
    private RedisCleanUp redisCleanUp;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @Autowired
    private BrandService brandService;

    @Autowired
    private ProductService productService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long highScoreProductId;
    private Long lowScoreProductId;
    private String brandName;

    @BeforeEach
    void clean() {
        databaseCleanUp.truncateAllTables();
        redisCleanUp.truncateAll();
    }

    private void seedTwoProductRanking() {
        BrandModel brand = brandService.registerBrand("랭킹E2E브랜드");
        brandName = brand.getName();
        ProductModel p1 = productService.registerProduct(brand.getId(), "높은점수", new BigDecimal("10000"), 5);
        ProductModel p2 = productService.registerProduct(brand.getId(), "낮은점수", new BigDecimal("5000"), 3);
        highScoreProductId = p1.getId();
        lowScoreProductId = p2.getId();

        String key = "ranking:all:" + RANKING_DATE;
        redisTemplate.opsForZSet().add(key, String.valueOf(highScoreProductId), 0.9);
        redisTemplate.opsForZSet().add(key, String.valueOf(lowScoreProductId), 0.3);
    }

    @Test
    @DisplayName("GET /api/v1/rankings - ZSET 순·상품 정보 aggregation")
    void getRankings_shouldReturnProductsOrderedByScore() {
        seedTwoProductRanking();
        ResponseEntity<ApiResponse<RankingV1Dto.ListResponse>> response = testRestTemplate.exchange(
                ENDPOINT + "?date=" + RANKING_DATE + "&page=1&size=20",
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<>() {});

        assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getHeaders().getFirst(RankingV1Controller.HEADER_RANKING_DATA_SOURCE))
                        .isEqualTo("REDIS"),
                () -> assertThat(response.getBody()).isNotNull(),
                () -> assertThat(response.getBody().meta().result()).isEqualTo(Result.SUCCESS),
                () -> assertThat(response.getBody().data().totalElements()).isEqualTo(2L),
                () -> assertThat(response.getBody().data().dataSource()).isEqualTo("REDIS"),
                () -> assertThat(response.getBody().data().content()).hasSize(2),
                () -> assertThat(response.getBody().data().content().get(0).rank()).isEqualTo(1),
                () -> assertThat(response.getBody().data().content().get(0).productId()).isEqualTo(highScoreProductId),
                () -> assertThat(response.getBody().data().content().get(0).score()).isEqualTo(0.9d),
                () -> assertThat(response.getBody().data().content().get(0).name()).isEqualTo("높은점수"),
                () -> assertThat(response.getBody().data().content().get(0).brandName()).isEqualTo(brandName),
                () -> assertThat(response.getBody().data().content().get(1).rank()).isEqualTo(2),
                () -> assertThat(response.getBody().data().content().get(1).productId()).isEqualTo(lowScoreProductId)
        );
    }

    @Test
    @DisplayName("GET /api/v1/rankings - 오프셋: page 1·2·3이 연속 구간을 반환하고 page 초과 시 빈 목록")
    void getRankings_offsetPagination_shouldSliceContinuouslyAndEmptyBeyondLastPage() {
        BrandModel brand = brandService.registerBrand("랭킹페이지E2E");
        ProductModel[] products = new ProductModel[5];
        for (int i = 0; i < 5; i++) {
            products[i] = productService.registerProduct(
                    brand.getId(), "p" + i, new BigDecimal(String.valueOf(1000 * (i + 1))), 5);
        }
        String key = "ranking:all:" + RANKING_DATE;
        double[] scores = {0.9, 0.8, 0.7, 0.6, 0.5};
        for (int i = 0; i < 5; i++) {
            redisTemplate.opsForZSet().add(key, String.valueOf(products[i].getId()), scores[i]);
        }

        ResponseEntity<ApiResponse<RankingV1Dto.ListResponse>> p1 = testRestTemplate.exchange(
                ENDPOINT + "?date=" + RANKING_DATE + "&page=1&size=2",
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<>() {});
        ResponseEntity<ApiResponse<RankingV1Dto.ListResponse>> p2 = testRestTemplate.exchange(
                ENDPOINT + "?date=" + RANKING_DATE + "&page=2&size=2",
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<>() {});
        ResponseEntity<ApiResponse<RankingV1Dto.ListResponse>> p3 = testRestTemplate.exchange(
                ENDPOINT + "?date=" + RANKING_DATE + "&page=3&size=2",
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<>() {});
        ResponseEntity<ApiResponse<RankingV1Dto.ListResponse>> p4 = testRestTemplate.exchange(
                ENDPOINT + "?date=" + RANKING_DATE + "&page=4&size=2",
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<>() {});

        assertAll(
                () -> assertThat(p1.getBody()).isNotNull(),
                () -> assertThat(p1.getBody().data().totalElements()).isEqualTo(5L),
                () -> assertThat(p1.getBody().data().totalPages()).isEqualTo(3),
                () -> assertThat(p1.getBody().data().content()).hasSize(2),
                () -> assertThat(p1.getBody().data().content().get(0).productId()).isEqualTo(products[0].getId()),
                () -> assertThat(p1.getBody().data().content().get(1).productId()).isEqualTo(products[1].getId()),
                () -> assertThat(p2.getBody().data().content().get(0).productId()).isEqualTo(products[2].getId()),
                () -> assertThat(p2.getBody().data().content().get(1).productId()).isEqualTo(products[3].getId()),
                () -> assertThat(p3.getBody().data().content()).hasSize(1),
                () -> assertThat(p3.getBody().data().content().get(0).productId()).isEqualTo(products[4].getId()),
                () -> assertThat(p4.getBody().data().content()).isEmpty(),
                () -> assertThat(p4.getBody().data().totalElements()).isEqualTo(5L),
                () -> assertThat(p4.getBody().data().totalPages()).isEqualTo(3)
        );
    }

    @Test
    @DisplayName("GET /api/v1/rankings - 동일 score는 Redis ZSET member 규칙(역사전순)을 따른다 (동점)")
    void getRankings_whenTieScore_shouldFollowRedisMemberOrder() {
        BrandModel brand = brandService.registerBrand("동점E2E");
        ProductModel a = productService.registerProduct(brand.getId(), "a", new BigDecimal("1000"), 5);
        ProductModel b = productService.registerProduct(brand.getId(), "b", new BigDecimal("2000"), 5);
        String key = "ranking:all:" + RANKING_DATE;
        double tie = 0.42d;
        redisTemplate.opsForZSet().add(key, String.valueOf(a.getId()), tie);
        redisTemplate.opsForZSet().add(key, String.valueOf(b.getId()), tie);

        ResponseEntity<ApiResponse<RankingV1Dto.ListResponse>> response = testRestTemplate.exchange(
                ENDPOINT + "?date=" + RANKING_DATE + "&page=1&size=10",
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<>() {});

        String sa = String.valueOf(a.getId());
        String sb = String.valueOf(b.getId());
        long expectedFirst = sa.compareTo(sb) > 0 ? a.getId() : b.getId();

        assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody()).isNotNull(),
                () -> assertThat(response.getBody().data().content()).hasSize(2),
                () -> assertThat(response.getBody().data().content().get(0).productId()).isEqualTo(expectedFirst),
                () -> assertThat(response.getBody().data().content().get(0).score()).isEqualTo(tie),
                () -> assertThat(response.getBody().data().content().get(1).score()).isEqualTo(tie)
        );
    }

    @Test
    @DisplayName("GET /api/v1/rankings - date 형식이 잘못되면 400")
    void getRankings_whenInvalidDate_shouldReturn400() {
        ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                ENDPOINT + "?date=not-a-date&page=1&size=20",
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().meta().result()).isEqualTo(Result.FAIL);
    }

    @Test
    @DisplayName("GET /api/v1/rankings - size가 100 초과면 400 (오프셋 페이징 크기 초과)")
    void getRankings_whenSizeExceedsMax_shouldReturn400() {
        ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                ENDPOINT + "?date=" + RANKING_DATE + "&page=1&size=101",
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().meta().result()).isEqualTo(Result.FAIL);
    }

    @Test
    @DisplayName("GET /api/v1/rankings - page가 0 이하면 400")
    void getRankings_whenPageLessThanOne_shouldReturn400() {
        ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                ENDPOINT + "?date=" + RANKING_DATE + "&page=0&size=20",
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("GET /api/v1/rankings - ZSET에만 있고 DB에 없는 member는 제외·totalElements는 ZCARD 유지 (R3)")
    void getRankings_whenZsetContainsUnknownProductId_shouldOmitRowAndKeepTotalFromZcard() {
        BrandModel brand = brandService.registerBrand("랭킹ZSET없음E2E");
        ProductModel existing = productService.registerProduct(
                brand.getId(), "실제상품", new BigDecimal("3000"), 5);
        long ghostId = existing.getId() + 1_000_000_000L;
        String key = "ranking:all:" + RANKING_DATE;
        redisTemplate.opsForZSet().add(key, String.valueOf(ghostId), 2.0d);
        redisTemplate.opsForZSet().add(key, String.valueOf(existing.getId()), 1.0d);

        ResponseEntity<ApiResponse<RankingV1Dto.ListResponse>> response = testRestTemplate.exchange(
                ENDPOINT + "?date=" + RANKING_DATE + "&page=1&size=10",
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<>() {});

        assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody()).isNotNull(),
                () -> assertThat(response.getBody().data().totalElements()).isEqualTo(2L),
                () -> assertThat(response.getBody().data().content()).hasSize(1),
                () -> assertThat(response.getBody().data().content().get(0).productId()).isEqualTo(existing.getId()),
                () -> assertThat(response.getBody().data().content().get(0).rank()).isEqualTo(2)
        );
    }

    @Test
    @DisplayName("GET /api/v1/rankings - 재고 0이어도 랭킹 목록에 포함된다 (E-STOCK-NOFILTER / R6)")
    void getRankings_whenProductStockZero_shouldStillReturnInRankingList() {
        BrandModel brand = brandService.registerBrand("품절랭킹E2E");
        ProductModel outOfStock = productService.registerProduct(
                brand.getId(), "품절랭킹상품", new BigDecimal("1000"), 0);
        String key = "ranking:all:" + RANKING_DATE;
        redisTemplate.opsForZSet().add(key, String.valueOf(outOfStock.getId()), 0.55d);

        ResponseEntity<ApiResponse<RankingV1Dto.ListResponse>> response = testRestTemplate.exchange(
                ENDPOINT + "?date=" + RANKING_DATE + "&page=1&size=10",
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<>() {});

        assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody()).isNotNull(),
                () -> assertThat(response.getBody().data().content()).hasSize(1),
                () -> assertThat(response.getBody().data().content().get(0).productId()).isEqualTo(outOfStock.getId()),
                () -> assertThat(response.getBody().data().content().get(0).name()).isEqualTo("품절랭킹상품"),
                () -> assertThat(response.getBody().data().content().get(0).stockQuantity()).isEqualTo(0)
        );
    }

    @Test
    @DisplayName("GET /api/v1/rankings - 동일 page 재요청 사이 ZSET 갱신 시 결과가 달라질 수 있다 (E-OFFSET-SHIFT)")
    void getRankings_whenZsetChangesBetweenRequests_shouldAllowOffsetShift() {
        BrandModel brand = brandService.registerBrand("오프셋시프트E2E");
        ProductModel p1 = productService.registerProduct(brand.getId(), "p1", new BigDecimal("1000"), 5);
        ProductModel p2 = productService.registerProduct(brand.getId(), "p2", new BigDecimal("2000"), 5);
        ProductModel p3 = productService.registerProduct(brand.getId(), "p3", new BigDecimal("3000"), 5);
        ProductModel p4 = productService.registerProduct(brand.getId(), "p4", new BigDecimal("4000"), 5);
        ProductModel p5 = productService.registerProduct(brand.getId(), "p5", new BigDecimal("5000"), 5);
        String key = "ranking:all:" + RANKING_DATE;
        redisTemplate.opsForZSet().add(key, String.valueOf(p1.getId()), 0.9d);
        redisTemplate.opsForZSet().add(key, String.valueOf(p2.getId()), 0.8d);
        redisTemplate.opsForZSet().add(key, String.valueOf(p3.getId()), 0.7d);
        redisTemplate.opsForZSet().add(key, String.valueOf(p4.getId()), 0.6d);
        redisTemplate.opsForZSet().add(key, String.valueOf(p5.getId()), 0.5d);

        ResponseEntity<ApiResponse<RankingV1Dto.ListResponse>> before = testRestTemplate.exchange(
                ENDPOINT + "?date=" + RANKING_DATE + "&page=2&size=2",
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<>() {});

        redisTemplate.opsForZSet().add(key, String.valueOf(p5.getId()), 1.1d);

        ResponseEntity<ApiResponse<RankingV1Dto.ListResponse>> after = testRestTemplate.exchange(
                ENDPOINT + "?date=" + RANKING_DATE + "&page=2&size=2",
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<>() {});

        assertAll(
                () -> assertThat(before.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(after.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(before.getBody()).isNotNull(),
                () -> assertThat(after.getBody()).isNotNull(),
                () -> assertThat(before.getBody().data().content()).hasSize(2),
                () -> assertThat(after.getBody().data().content()).hasSize(2),
                () -> assertThat(before.getBody().data().content().get(0).productId())
                        .isNotEqualTo(after.getBody().data().content().get(0).productId())
        );
    }

    @Test
    @DisplayName("POST snapshots 후 rankingSnapshotId로 page2 조회 시 라이브 ZSET이 바뀌어도 스냅샷 순서 유지")
    void getRankings_withSnapshot_shouldKeepOrderWhenLiveZsetChanges() {
        BrandModel brand = brandService.registerBrand("스냅샷E2E");
        ProductModel p1 = productService.registerProduct(brand.getId(), "s1", new BigDecimal("1000"), 5);
        ProductModel p2 = productService.registerProduct(brand.getId(), "s2", new BigDecimal("2000"), 5);
        ProductModel p3 = productService.registerProduct(brand.getId(), "s3", new BigDecimal("3000"), 5);
        ProductModel p4 = productService.registerProduct(brand.getId(), "s4", new BigDecimal("4000"), 5);
        ProductModel p5 = productService.registerProduct(brand.getId(), "s5", new BigDecimal("5000"), 5);
        String key = "ranking:all:" + RANKING_DATE;
        redisTemplate.opsForZSet().add(key, String.valueOf(p1.getId()), 0.9d);
        redisTemplate.opsForZSet().add(key, String.valueOf(p2.getId()), 0.8d);
        redisTemplate.opsForZSet().add(key, String.valueOf(p3.getId()), 0.7d);
        redisTemplate.opsForZSet().add(key, String.valueOf(p4.getId()), 0.6d);
        redisTemplate.opsForZSet().add(key, String.valueOf(p5.getId()), 0.5d);

        ResponseEntity<ApiResponse<RankingV1Dto.SnapshotCreateResponse>> snap = testRestTemplate.exchange(
                ENDPOINT + "/snapshots?date=" + RANKING_DATE,
                HttpMethod.POST,
                new HttpEntity<>(null),
                new ParameterizedTypeReference<>() {});

        assertThat(snap.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(snap.getBody()).isNotNull();
        assertThat(snap.getBody().data().totalElements()).isEqualTo(5L);
        String rankingSnapshotId = snap.getBody().data().rankingSnapshotId();

        ResponseEntity<ApiResponse<RankingV1Dto.ListResponse>> p2snap = testRestTemplate.exchange(
                ENDPOINT + "?date=" + RANKING_DATE + "&page=2&size=2&rankingSnapshotId=" + rankingSnapshotId,
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<>() {});

        redisTemplate.opsForZSet().add(key, String.valueOf(p5.getId()), 1.1d);

        ResponseEntity<ApiResponse<RankingV1Dto.ListResponse>> p2again = testRestTemplate.exchange(
                ENDPOINT + "?date=" + RANKING_DATE + "&page=2&size=2&rankingSnapshotId=" + rankingSnapshotId,
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<>() {});

        assertAll(
                () -> assertThat(p2snap.getBody()).isNotNull(),
                () -> assertThat(p2snap.getBody().data().dataSource()).isEqualTo("REDIS_SNAPSHOT"),
                () -> assertThat(p2snap.getBody().data().rankingSnapshotId()).isEqualTo(rankingSnapshotId),
                () -> assertThat(p2snap.getBody().data().content().get(0).productId())
                        .isEqualTo(p3.getId()),
                () -> assertThat(p2again.getBody().data().content().get(0).productId())
                        .isEqualTo(p3.getId())
        );
    }

    @Test
    @DisplayName("GET /api/v1/rankings - 주간 MV: DB 행 순·Hydration·MV_WEEKLY")
    void getRankings_weeklyMv_shouldReturnFromMaterializedView() {
        seedTwoProductRanking();
        String periodKey = "2026W15";
        Instant at = Instant.now();
        jdbcTemplate.update(
                """
                INSERT INTO mv_product_rank_weekly
                (period_key, product_id, `rank`, score, version, updated_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """,
                periodKey,
                highScoreProductId,
                1,
                new BigDecimal("0.90"),
                1,
                Timestamp.from(at));
        jdbcTemplate.update(
                """
                INSERT INTO mv_product_rank_weekly
                (period_key, product_id, `rank`, score, version, updated_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """,
                periodKey,
                lowScoreProductId,
                2,
                new BigDecimal("0.30"),
                1,
                Timestamp.from(at));

        ResponseEntity<ApiResponse<RankingV1Dto.ListResponse>> response = testRestTemplate.exchange(
                ENDPOINT + "?period=WEEKLY&periodKey=" + periodKey + "&page=1&size=20",
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<>() {});

        assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getHeaders().getFirst(RankingV1Controller.HEADER_RANKING_DATA_SOURCE))
                        .isEqualTo("MV_WEEKLY"),
                () -> assertThat(response.getBody()).isNotNull(),
                () -> assertThat(response.getBody().data().dataSource()).isEqualTo("MV_WEEKLY"),
                () -> assertThat(response.getBody().data().totalElements()).isEqualTo(2L),
                () -> assertThat(response.getBody().data().content()).hasSize(2),
                () -> assertThat(response.getBody().data().content().get(0).rank()).isEqualTo(1),
                () -> assertThat(response.getBody().data().content().get(0).productId())
                        .isEqualTo(highScoreProductId),
                () -> assertThat(response.getBody().data().content().get(0).score()).isEqualTo(0.9d),
                () -> assertThat(response.getBody().data().content().get(1).productId())
                        .isEqualTo(lowScoreProductId),
                () -> assertThat(response.getBody().data().mvPublishVersion()).isEqualTo(1)
        );
    }

    @Test
    @DisplayName("GET /api/v1/rankings - 월간 MV·MV_MONTHLY")
    void getRankings_monthlyMv_shouldReturnFromMaterializedView() {
        seedTwoProductRanking();
        String periodKey = "202604";
        Instant at = Instant.now();
        jdbcTemplate.update(
                """
                INSERT INTO mv_product_rank_monthly
                (period_key, product_id, `rank`, score, version, updated_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """,
                periodKey,
                lowScoreProductId,
                1,
                new BigDecimal("0.50"),
                1,
                Timestamp.from(at));

        ResponseEntity<ApiResponse<RankingV1Dto.ListResponse>> response = testRestTemplate.exchange(
                ENDPOINT + "?period=MONTHLY&periodKey=" + periodKey + "&page=1&size=10",
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<>() {});

        assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody()).isNotNull(),
                () -> assertThat(response.getBody().data().dataSource()).isEqualTo("MV_MONTHLY"),
                () -> assertThat(response.getBody().data().totalElements()).isEqualTo(1L),
                () -> assertThat(response.getBody().data().content()).hasSize(1),
                () -> assertThat(response.getBody().data().content().get(0).productId())
                        .isEqualTo(lowScoreProductId),
                () -> assertThat(response.getBody().data().mvPublishVersion()).isEqualTo(1)
        );
    }

    @Test
    @DisplayName("GET /api/v1/rankings - MV는 요청당 MAX(version)만 조회(혼합 버전 시 상위 버전만)")
    void getRankings_weeklyMv_whenMixedVersions_shouldUseMaxVersionOnly() {
        seedTwoProductRanking();
        String periodKey = "2026W20";
        Instant at = Instant.now();
        jdbcTemplate.update(
                """
                INSERT INTO mv_product_rank_weekly
                (period_key, product_id, `rank`, score, version, updated_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """,
                periodKey,
                highScoreProductId,
                1,
                new BigDecimal("0.10"),
                1,
                Timestamp.from(at));
        jdbcTemplate.update(
                """
                INSERT INTO mv_product_rank_weekly
                (period_key, product_id, `rank`, score, version, updated_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """,
                periodKey,
                lowScoreProductId,
                1,
                new BigDecimal("0.99"),
                2,
                Timestamp.from(at));

        ResponseEntity<ApiResponse<RankingV1Dto.ListResponse>> response = testRestTemplate.exchange(
                ENDPOINT + "?period=WEEKLY&periodKey=" + periodKey + "&page=1&size=20",
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<>() {});

        assertAll(
                () -> assertThat(response.getBody()).isNotNull(),
                () -> assertThat(response.getBody().data().mvPublishVersion()).isEqualTo(2),
                () -> assertThat(response.getBody().data().totalElements()).isEqualTo(1L),
                () -> assertThat(response.getBody().data().content()).hasSize(1),
                () -> assertThat(response.getBody().data().content().get(0).productId())
                        .isEqualTo(lowScoreProductId),
                () -> assertThat(response.getBody().data().content().get(0).score()).isEqualTo(0.99d)
        );
    }

    @Test
    @DisplayName("GET /api/v1/rankings - MV에서 마지막 페이지 초과 시 빈 content·total 유지")
    void getRankings_weeklyMv_whenPageBeyond_shouldKeepTotal() {
        seedTwoProductRanking();
        String periodKey = "2026W16";
        Instant at = Instant.now();
        jdbcTemplate.update(
                """
                INSERT INTO mv_product_rank_weekly
                (period_key, product_id, `rank`, score, version, updated_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """,
                periodKey,
                highScoreProductId,
                1,
                BigDecimal.ONE,
                1,
                Timestamp.from(at));

        ResponseEntity<ApiResponse<RankingV1Dto.ListResponse>> response = testRestTemplate.exchange(
                ENDPOINT + "?period=WEEKLY&periodKey=" + periodKey + "&page=5&size=1",
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<>() {});

        assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody()).isNotNull(),
                () -> assertThat(response.getBody().data().content()).isEmpty(),
                () -> assertThat(response.getBody().data().totalElements()).isEqualTo(1L),
                () -> assertThat(response.getBody().data().totalPages()).isEqualTo(1),
                () -> assertThat(response.getBody().data().mvPublishVersion()).isEqualTo(1)
        );
    }

    @Test
    @DisplayName("GET /api/v1/rankings - period만 주면 400")
    void getRankings_whenPeriodWithoutKey_shouldReturn400() {
        ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                ENDPOINT + "?period=WEEKLY&page=1&size=20",
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().meta().result()).isEqualTo(Result.FAIL);
    }

    @Test
    @DisplayName("GET /api/v1/rankings - date와 period 동시 지정 시 400")
    void getRankings_whenDateWithMvPeriod_shouldReturn400() {
        ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                ENDPOINT + "?date=20260408&period=WEEKLY&periodKey=2026W15&page=1&size=20",
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
}

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
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.data.redis.core.RedisTemplate;

import java.math.BigDecimal;

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
                () -> assertThat(response.getBody()).isNotNull(),
                () -> assertThat(response.getBody().meta().result()).isEqualTo(Result.SUCCESS),
                () -> assertThat(response.getBody().data().totalElements()).isEqualTo(2L),
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
}

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
import org.junit.jupiter.api.AfterEach;
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
    void setUp() {
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

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
        redisCleanUp.truncateAll();
    }

    @Test
    @DisplayName("GET /api/v1/rankings - ZSET 순·상품 정보 aggregation")
    void getRankings_shouldReturnProductsOrderedByScore() {
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
}

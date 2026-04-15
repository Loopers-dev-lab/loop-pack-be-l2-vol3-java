package com.loopers.interfaces.api.ranking;

import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductRepository;
import com.loopers.utils.DatabaseCleanUp;
import com.loopers.utils.RedisCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("Ranking V1 API E2E 테스트")
class RankingV1ApiE2ETest {

    private static final String RANKING_ENDPOINT = "/api/v1/rankings";
    private static final String PRODUCT_ENDPOINT = "/api/v1/products";

    @Autowired
    private TestRestTemplate testRestTemplate;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private BrandRepository brandRepository;

    @Autowired
    @Qualifier("redisTemplateMaster")
    private RedisTemplate<String, String> masterRedisTemplate;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @Autowired
    private RedisCleanUp redisCleanUp;

    private Brand savedBrand;
    private Product productA;
    private Product productB;
    private Product productC;
    private String todayKey;
    private String redisKey;

    @BeforeEach
    void setUp() {
        savedBrand = brandRepository.save(new Brand("Apple", "애플", "https://example.com/apple.png"));
        productA = productRepository.save(new Product("아이폰 15", savedBrand.getId(), 1L, 1500000L));
        productB = productRepository.save(new Product("맥북 프로", savedBrand.getId(), 1L, 3000000L));
        productC = productRepository.save(new Product("에어팟", savedBrand.getId(), 1L, 300000L));

        todayKey = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        redisKey = "ranking:all:" + todayKey;

        // 가중치 적용된 점수: A(조회 10회)=1.0, B(좋아요 3회)=0.6, C(주문 1건 50,000원)=30,000
        masterRedisTemplate.opsForZSet().add(redisKey, String.valueOf(productA.getId()), 1.0);
        masterRedisTemplate.opsForZSet().add(redisKey, String.valueOf(productB.getId()), 0.6);
        masterRedisTemplate.opsForZSet().add(redisKey, String.valueOf(productC.getId()), 30000.0);
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
        @DisplayName("랭킹 조회 API가 상품 정보와 함께 정상 응답한다")
        void returnsRankingsWithProductInfo() {
            // Act
            ResponseEntity<Map> response = testRestTemplate.getForEntity(
                RANKING_ENDPOINT + "?date=" + todayKey + "&size=10&page=0", Map.class);

            // Assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            Map body = response.getBody();
            assertThat(body).isNotNull();
            Map meta = (Map) body.get("meta");
            assertThat(meta.get("result")).isEqualTo("SUCCESS");

            Map data = (Map) body.get("data");
            List<Map> content = (List<Map>) data.get("content");
            assertThat(content).hasSize(3);

            // 1위: productC (주문 30,000점)
            Map first = content.get(0);
            assertAll(
                () -> assertThat(((Number) first.get("rank")).longValue()).isEqualTo(1L),
                () -> assertThat(((Number) first.get("score")).doubleValue()).isEqualTo(30000.0),
                () -> assertThat(first.get("productName")).isEqualTo("에어팟"),
                () -> assertThat(first.get("brandName")).isEqualTo("Apple")
            );
        }

        @Test
        @DisplayName("가중치가 의도대로 랭킹 순서에 반영된다 (주문 1건 > 좋아요 3건)")
        void weightIsReflectedInRankingOrder() {
            // Act
            ResponseEntity<Map> response = testRestTemplate.getForEntity(
                RANKING_ENDPOINT + "?date=" + todayKey + "&size=10&page=0", Map.class);

            // Assert
            Map data = (Map) response.getBody().get("data");
            List<Map> content = (List<Map>) data.get("content");

            // C(30,000) > A(1.0) > B(0.6)
            assertAll(
                () -> assertThat(((Number) content.get(0).get("productId")).longValue()).isEqualTo(productC.getId()),
                () -> assertThat(((Number) content.get(1).get("productId")).longValue()).isEqualTo(productA.getId()),
                () -> assertThat(((Number) content.get(2).get("productId")).longValue()).isEqualTo(productB.getId())
            );
        }
    }

    @Nested
    @DisplayName("GET /api/v1/products/{id}")
    class GetProductWithRanking {

        @Test
        @DisplayName("상품 상세 조회 시 ranking 필드가 포함된다")
        void returnsProductDetailWithRanking() {
            // Act
            ResponseEntity<Map> response = testRestTemplate.getForEntity(
                PRODUCT_ENDPOINT + "/" + productC.getId(), Map.class);

            // Assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            Map data = (Map) response.getBody().get("data");
            Map ranking = (Map) data.get("ranking");

            assertAll(
                () -> assertThat(ranking).isNotNull(),
                () -> assertThat(((Number) ranking.get("rank")).longValue()).isEqualTo(1L),
                () -> assertThat(((Number) ranking.get("score")).doubleValue()).isEqualTo(30000.0)
            );
        }

        @Test
        @DisplayName("랭킹에 없는 상품은 ranking이 null이다")
        void returnsNullRankingForUnrankedProduct() {
            // Arrange
            Product unranked = productRepository.save(new Product("새 상품", savedBrand.getId(), 1L, 500000L));

            // Act
            ResponseEntity<Map> response = testRestTemplate.getForEntity(
                PRODUCT_ENDPOINT + "/" + unranked.getId(), Map.class);

            // Assert
            Map data = (Map) response.getBody().get("data");
            assertThat(data.get("ranking")).isNull();
        }
    }
}

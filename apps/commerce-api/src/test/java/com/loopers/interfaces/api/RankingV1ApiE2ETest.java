package com.loopers.interfaces.api;

import com.loopers.domain.brand.Brand;
import com.loopers.domain.product.Product;
import com.loopers.event.ranking.RankingKeyGenerator;
import com.loopers.infrastructure.brand.BrandJpaRepository;
import com.loopers.infrastructure.product.ProductJpaRepository;
import com.loopers.interfaces.api.product.dto.ProductV1Dto;
import com.loopers.interfaces.api.ranking.dto.RankingV1Dto;
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

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RankingV1ApiE2ETest {

    private static final String RANKING_ENDPOINT = "/api/v1/rankings";
    private static final String PRODUCT_ENDPOINT = "/api/v1/products";

    @Autowired
    private TestRestTemplate testRestTemplate;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @Autowired
    private BrandJpaRepository brandJpaRepository;

    @Autowired
    private ProductJpaRepository productJpaRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @Autowired
    private RedisCleanUp redisCleanUp;

    @Autowired
    private Clock clock;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
        redisCleanUp.truncateAll();
    }

    private Brand saveBrand(String name) {
        return brandJpaRepository.save(Brand.create(name, null));
    }

    private Product saveProduct(Long brandId, String name, int price, int stock) {
        return productJpaRepository.save(Product.create(brandId, name, null, price, stock));
    }

    private void seedRankingScore(String key, Long productId, double score) {
        redisTemplate.opsForZSet().add(key, String.valueOf(productId), score);
    }

    @DisplayName("랭킹 페이지 조회시, ")
    @Nested
    class GetRankings {

        @DisplayName("랭킹에 등록된 상품들을 점수 높은 순으로 반환한다.")
        @Test
        void returnsRankedProducts() {
            // arrange
            LocalDate today = LocalDate.now(clock);
            String key = RankingKeyGenerator.keyOf(today);
            Brand brand = saveBrand("나이키");
            Product top = saveProduct(brand.getId(), "에어맥스", 200000, 10);
            Product mid = saveProduct(brand.getId(), "조던", 300000, 5);
            Product low = saveProduct(brand.getId(), "덩크", 120000, 20);

            seedRankingScore(key, top.getId(), 500.0);
            seedRankingScore(key, mid.getId(), 300.0);
            seedRankingScore(key, low.getId(), 100.0);

            // act
            ResponseEntity<ApiResponse<List<RankingV1Dto.RankingResponse>>> response =
                    testRestTemplate.exchange(
                            RANKING_ENDPOINT + "?size=3",
                            HttpMethod.GET, null,
                            new ParameterizedTypeReference<>() {}
                    );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            List<RankingV1Dto.RankingResponse> items = response.getBody().data();
            assertThat(items).hasSize(3);
            assertThat(items.get(0).productId()).isEqualTo(top.getId());
            assertThat(items.get(0).rank()).isEqualTo(1L);
            assertThat(items.get(0).productName()).isEqualTo("에어맥스");
            assertThat(items.get(1).productId()).isEqualTo(mid.getId());
            assertThat(items.get(1).rank()).isEqualTo(2L);
            assertThat(items.get(2).productId()).isEqualTo(low.getId());
            assertThat(items.get(2).rank()).isEqualTo(3L);
        }

        @DisplayName("점수가 높은 상품이 낮은 상품보다 상위에 노출된다.")
        @Test
        void higherScoreRanksFirst() {
            // arrange — 주문 가중치(0.7) × 10000 × 1 = 7000, 좋아요 가중치(0.2) × 30건 = 6.0
            LocalDate today = LocalDate.now(clock);
            String key = RankingKeyGenerator.keyOf(today);
            Brand brand = saveBrand("아디다스");
            Product orderedProduct = saveProduct(brand.getId(), "주문많은상품", 100000, 10);
            Product likedProduct = saveProduct(brand.getId(), "좋아요많은상품", 50000, 10);

            double orderScore = 0.7 * 10000 * 1;  // 7000
            double likeScore = 0.2 * 30;           // 6.0

            seedRankingScore(key, orderedProduct.getId(), orderScore);
            seedRankingScore(key, likedProduct.getId(), likeScore);

            // act
            ResponseEntity<ApiResponse<List<RankingV1Dto.RankingResponse>>> response =
                    testRestTemplate.exchange(
                            RANKING_ENDPOINT + "?size=10",
                            HttpMethod.GET, null,
                            new ParameterizedTypeReference<>() {}
                    );

            // assert
            List<RankingV1Dto.RankingResponse> items = response.getBody().data();
            assertThat(items.get(0).productId()).isEqualTo(orderedProduct.getId());
            assertThat(items.get(0).score()).isEqualTo(orderScore);
            assertThat(items.get(1).productId()).isEqualTo(likedProduct.getId());
            assertThat(items.get(1).score()).isEqualTo(likeScore);
        }

        @DisplayName("이전 날짜의 랭킹을 date 파라미터로 조회할 수 있다.")
        @Test
        void returnsRankingsForPastDate() {
            // arrange
            LocalDate yesterday = LocalDate.now(clock).minusDays(1);
            String yesterdayKey = RankingKeyGenerator.keyOf(yesterday);
            Brand brand = saveBrand("푸마");
            Product product = saveProduct(brand.getId(), "스웨이드", 90000, 15);

            seedRankingScore(yesterdayKey, product.getId(), 250.0);

            String dateParam = yesterday.format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE);

            // act
            ResponseEntity<ApiResponse<List<RankingV1Dto.RankingResponse>>> response =
                    testRestTemplate.exchange(
                            RANKING_ENDPOINT + "?date=" + dateParam,
                            HttpMethod.GET, null,
                            new ParameterizedTypeReference<>() {}
                    );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            List<RankingV1Dto.RankingResponse> items = response.getBody().data();
            assertThat(items).hasSize(1);
            assertThat(items.get(0).productId()).isEqualTo(product.getId());
            assertThat(items.get(0).score()).isEqualTo(250.0);
        }

        @DisplayName("랭킹 데이터가 없으면 빈 리스트를 반환한다.")
        @Test
        void returnsEmptyWhenNoRankingData() {
            // act
            ResponseEntity<ApiResponse<List<RankingV1Dto.RankingResponse>>> response =
                    testRestTemplate.exchange(
                            RANKING_ENDPOINT,
                            HttpMethod.GET, null,
                            new ParameterizedTypeReference<>() {}
                    );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().data()).isEmpty();
        }

        @DisplayName("페이지네이션이 정상 동작한다.")
        @Test
        void paginationWorks() {
            // arrange
            LocalDate today = LocalDate.now(clock);
            String key = RankingKeyGenerator.keyOf(today);
            Brand brand = saveBrand("뉴발란스");
            Product p1 = saveProduct(brand.getId(), "990", 250000, 5);
            Product p2 = saveProduct(brand.getId(), "993", 230000, 5);
            Product p3 = saveProduct(brand.getId(), "2002R", 180000, 10);

            seedRankingScore(key, p1.getId(), 300.0);
            seedRankingScore(key, p2.getId(), 200.0);
            seedRankingScore(key, p3.getId(), 100.0);

            // act — page 2, size 2 → 3위만 반환
            ResponseEntity<ApiResponse<List<RankingV1Dto.RankingResponse>>> response =
                    testRestTemplate.exchange(
                            RANKING_ENDPOINT + "?page=2&size=2",
                            HttpMethod.GET, null,
                            new ParameterizedTypeReference<>() {}
                    );

            // assert
            List<RankingV1Dto.RankingResponse> items = response.getBody().data();
            assertThat(items).hasSize(1);
            assertThat(items.get(0).productId()).isEqualTo(p3.getId());
            assertThat(items.get(0).rank()).isEqualTo(3L);
        }

        @DisplayName("삭제된 상품이 랭킹에 남아있으면 해당 항목을 건너뛰고 나머지를 반환한다.")
        @Test
        void skipsDeletedProductInRanking() {
            // arrange
            LocalDate today = LocalDate.now(clock);
            String key = RankingKeyGenerator.keyOf(today);
            Brand brand = saveBrand("나이키");
            Product active = saveProduct(brand.getId(), "활성상품", 150000, 10);
            Product deleted = saveProduct(brand.getId(), "삭제상품", 100000, 5);
            deleted.delete();
            productJpaRepository.save(deleted);

            seedRankingScore(key, deleted.getId(), 500.0);
            seedRankingScore(key, active.getId(), 300.0);

            // act
            ResponseEntity<ApiResponse<List<RankingV1Dto.RankingResponse>>> response =
                    testRestTemplate.exchange(
                            RANKING_ENDPOINT + "?size=10",
                            HttpMethod.GET, null,
                            new ParameterizedTypeReference<>() {}
                    );

            // assert — 삭제 상품은 skip, 활성 상품만 반환
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            List<RankingV1Dto.RankingResponse> items = response.getBody().data();
            assertThat(items).hasSize(1);
            assertThat(items.get(0).productId()).isEqualTo(active.getId());
        }
    }

    @DisplayName("상품 상세 조회시, ")
    @Nested
    class GetProductWithRanking {

        @DisplayName("랭킹에 등록된 상품은 순위와 점수가 함께 반환된다.")
        @Test
        void returnsProductWithRankingInfo() {
            // arrange
            LocalDate today = LocalDate.now(clock);
            String key = RankingKeyGenerator.keyOf(today);
            Brand brand = saveBrand("나이키");
            Product top = saveProduct(brand.getId(), "에어맥스", 200000, 10);
            Product target = saveProduct(brand.getId(), "조던", 300000, 5);

            seedRankingScore(key, top.getId(), 500.0);
            seedRankingScore(key, target.getId(), 300.0);

            // act
            ResponseEntity<ApiResponse<ProductV1Dto.ProductResponse>> response =
                    testRestTemplate.exchange(
                            PRODUCT_ENDPOINT + "/" + target.getId(),
                            HttpMethod.GET, null,
                            new ParameterizedTypeReference<>() {}
                    );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            ProductV1Dto.ProductResponse data = response.getBody().data();
            assertThat(data.ranking()).isNotNull();
            assertThat(data.ranking().rank()).isEqualTo(2L);
            assertThat(data.ranking().score()).isEqualTo(300.0);
        }

        @DisplayName("랭킹에 없는 상품은 ranking이 null로 반환된다.")
        @Test
        void returnsProductWithNullRanking() {
            // arrange
            Brand brand = saveBrand("아디다스");
            Product product = saveProduct(brand.getId(), "슈퍼스타", 120000, 8);

            // act
            ResponseEntity<ApiResponse<ProductV1Dto.ProductResponse>> response =
                    testRestTemplate.exchange(
                            PRODUCT_ENDPOINT + "/" + product.getId(),
                            HttpMethod.GET, null,
                            new ParameterizedTypeReference<>() {}
                    );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().data().ranking()).isNull();
        }
    }
}

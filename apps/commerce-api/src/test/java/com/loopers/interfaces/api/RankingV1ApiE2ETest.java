package com.loopers.interfaces.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import com.loopers.domain.brand.Brand;
import com.loopers.domain.common.Money;
import com.loopers.domain.product.Product;
import com.loopers.infrastructure.brand.BrandJpaRepository;
import com.loopers.infrastructure.product.ProductJpaRepository;
import com.loopers.utils.DatabaseCleanUp;
import com.loopers.utils.RedisCleanUp;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RankingV1ApiE2ETest {

    private static final String ENDPOINT = "/api/v1/rankings";
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

    @Autowired
    private TestRestTemplate testRestTemplate;

    @Autowired
    private BrandJpaRepository brandJpaRepository;

    @Autowired
    private ProductJpaRepository productJpaRepository;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @Autowired
    private RedisCleanUp redisCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
        redisCleanUp.truncateAll();
    }

    private void seedRankingZset(Long productId, double score) {
        String key = "ranking:all:" + LocalDate.now().format(DATE_FORMAT);
        redisTemplate.opsForZSet().add(key, productId.toString(), score);
    }

    @DisplayName("GET /api/v1/rankings")
    @Nested
    class GetRankings {

        @DisplayName("랭킹 데이터가 있을 때, 상품정보가 포함된 순위 목록을 반환한다.")
        @Test
        void returnsRankingWithProductInfo() {
            // arrange
            Brand brand = brandJpaRepository.save(new Brand("나이키"));
            Product product1 = productJpaRepository.save(new Product(brand.getId(), "신발A", new Money(50000L), "설명"));
            Product product2 = productJpaRepository.save(new Product(brand.getId(), "신발B", new Money(30000L), "설명"));

            seedRankingZset(product1.getId(), 5.0);
            seedRankingZset(product2.getId(), 3.0);

            // act
            ResponseEntity<ApiResponse<Map<String, Object>>> response = testRestTemplate.exchange(
                ENDPOINT + "?date=" + LocalDate.now().format(DATE_FORMAT) + "&size=20&page=1",
                HttpMethod.GET,
                new HttpEntity<>(null),
                new ParameterizedTypeReference<>() {}
            );

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> {
                    List<Map<String, Object>> items = (List<Map<String, Object>>) response.getBody().data().get("items");
                    assertThat(items).hasSize(2);
                    assertThat(((Number) items.get(0).get("rank")).intValue()).isEqualTo(1);
                    assertThat(items.get(0).get("productName")).isEqualTo("신발A");
                    assertThat(((Number) items.get(1).get("rank")).intValue()).isEqualTo(2);
                    assertThat(items.get(1).get("productName")).isEqualTo("신발B");
                }
            );
        }

        @DisplayName("랭킹 데이터가 없으면 빈 목록을 반환한다.")
        @Test
        void returnsEmptyList_whenNoRankingData() {
            // arrange — ZSET에 아무것도 없음

            // act
            ResponseEntity<ApiResponse<Map<String, Object>>> response = testRestTemplate.exchange(
                ENDPOINT + "?date=" + LocalDate.now().format(DATE_FORMAT),
                HttpMethod.GET,
                new HttpEntity<>(null),
                new ParameterizedTypeReference<>() {}
            );

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> {
                    List<Object> items = (List<Object>) response.getBody().data().get("items");
                    assertThat(items).isEmpty();
                }
            );
        }

        @DisplayName("이전 날짜 파라미터로 조회하면 해당 날짜의 랭킹을 반환한다.")
        @Test
        void returnsPreviousDayRanking() {
            // arrange
            Brand brand = brandJpaRepository.save(new Brand("아디다스"));
            Product product = productJpaRepository.save(new Product(brand.getId(), "운동화", new Money(40000L), "설명"));

            LocalDate yesterday = LocalDate.now().minusDays(1);
            String yesterdayKey = "ranking:all:" + yesterday.format(DATE_FORMAT);
            redisTemplate.opsForZSet().add(yesterdayKey, product.getId().toString(), 10.0);

            // act
            ResponseEntity<ApiResponse<Map<String, Object>>> response = testRestTemplate.exchange(
                ENDPOINT + "?date=" + yesterday.format(DATE_FORMAT),
                HttpMethod.GET,
                new HttpEntity<>(null),
                new ParameterizedTypeReference<>() {}
            );

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> {
                    List<Map<String, Object>> items = (List<Map<String, Object>>) response.getBody().data().get("items");
                    assertThat(items).hasSize(1);
                    assertThat(items.get(0).get("productName")).isEqualTo("운동화");
                }
            );
        }

        @DisplayName("주문 1건의 점수가 좋아요 3건보다 높으면 주문 상품이 1위로 반환된다.")
        @Test
        void orderedProductRanksHigherThanThreeLikes() {
            // arrange
            Brand brand = brandJpaRepository.save(new Brand("테스트브랜드"));
            Product likedProduct = productJpaRepository.save(new Product(brand.getId(), "좋아요상품", new Money(10000L), "설명"));
            Product soldProduct = productJpaRepository.save(new Product(brand.getId(), "주문상품", new Money(10000L), "설명"));

            // 좋아요 3건 = 0.6점
            seedRankingZset(likedProduct.getId(), 0.6);
            // 주문 1건 (10,000원) = 0.6 * log1p(10000) ≈ 5.53점
            seedRankingZset(soldProduct.getId(), 0.6 * Math.log1p(10000));

            // act
            ResponseEntity<ApiResponse<Map<String, Object>>> response = testRestTemplate.exchange(
                ENDPOINT + "?date=" + LocalDate.now().format(DATE_FORMAT) + "&size=10&page=1",
                HttpMethod.GET,
                new HttpEntity<>(null),
                new ParameterizedTypeReference<>() {}
            );

            // assert
            List<Map<String, Object>> items = (List<Map<String, Object>>) response.getBody().data().get("items");
            assertThat(items.get(0).get("productName")).isEqualTo("주문상품");
            assertThat(items.get(1).get("productName")).isEqualTo("좋아요상품");
        }

        @DisplayName("페이지 조회 시 size, page 파라미터가 반영된다.")
        @Test
        void respectsPageAndSizeParams() {
            // arrange
            Brand brand = brandJpaRepository.save(new Brand("페이지테스트"));
            for (int i = 1; i <= 5; i++) {
                Product p = productJpaRepository.save(new Product(brand.getId(), "상품" + i, new Money(10000L), "설명"));
                seedRankingZset(p.getId(), i * 1.0);
            }

            // act — page=2, size=2 → 3위, 4위 반환
            ResponseEntity<ApiResponse<Map<String, Object>>> response = testRestTemplate.exchange(
                ENDPOINT + "?date=" + LocalDate.now().format(DATE_FORMAT) + "&size=2&page=2",
                HttpMethod.GET,
                new HttpEntity<>(null),
                new ParameterizedTypeReference<>() {}
            );

            // assert
            List<Map<String, Object>> items = (List<Map<String, Object>>) response.getBody().data().get("items");
            assertThat(items).hasSize(2);
            assertThat(((Number) items.get(0).get("rank")).intValue()).isEqualTo(3);
            assertThat(((Number) items.get(1).get("rank")).intValue()).isEqualTo(4);
        }
    }
}

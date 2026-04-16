package com.loopers.interfaces.api;

import com.loopers.config.TestRedisConfiguration;
import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.domain.common.Money;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.product.Stock;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.ranking.RankingV1Dto;
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

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestRedisConfiguration.class)
@DisplayName("Ranking API E2E 테스트")
class RankingV1ApiE2ETest {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.BASIC_ISO_DATE;

    @Autowired
    private TestRestTemplate testRestTemplate;

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

        testBrand = brandRepository.save(Brand.create("Test Brand", "Test", null));
        product1 = productRepository.save(Product.create(testBrand.getId(), "Product 1", "Desc", new Money(10000), new Stock(100), null));
        product2 = productRepository.save(Product.create(testBrand.getId(), "Product 2", "Desc", new Money(20000), new Stock(100), null));
        product3 = productRepository.save(Product.create(testBrand.getId(), "Product 3", "Desc", new Money(30000), new Stock(100), null));
    }

    @AfterEach
    void tearDown() {
        cleanUpRedisKeys();
        databaseCleanUp.truncateAllTables();
    }

    private void cleanUpRedisKeys() {
        Set<String> keys = redisTemplate.keys("ranking:*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    private String getTodayKey() {
        return "ranking:all:" + LocalDate.now().format(DATE_FORMATTER);
    }

    @Nested
    @DisplayName("랭킹 조회 API")
    class GetRankings {

        @Test
        @DisplayName("랭킹을 점수 내림차순으로 조회한다")
        void 랭킹_조회_성공() {
            // Arrange
            String key = getTodayKey();
            redisTemplate.opsForZSet().add(key, String.valueOf(product1.getId()), 10.0);
            redisTemplate.opsForZSet().add(key, String.valueOf(product2.getId()), 5.0);
            redisTemplate.opsForZSet().add(key, String.valueOf(product3.getId()), 20.0);

            // Act
            ResponseEntity<ApiResponse<RankingV1Dto.RankingPageResponse>> response = testRestTemplate.exchange(
                "/api/v1/rankings?size=10&page=1",
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<>() {}
            );

            // Assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().data().rankings()).hasSize(3);
            assertThat(response.getBody().data().totalCount()).isEqualTo(3);
            assertThat(response.getBody().data().totalPages()).isEqualTo(1);

            // 1위: product3 (20.0)
            assertThat(response.getBody().data().rankings().get(0).productId()).isEqualTo(product3.getId());
            assertThat(response.getBody().data().rankings().get(0).rank()).isEqualTo(1);
            assertThat(response.getBody().data().rankings().get(0).productName()).isEqualTo("Product 3");

            // 2위: product1 (10.0)
            assertThat(response.getBody().data().rankings().get(1).productId()).isEqualTo(product1.getId());
            assertThat(response.getBody().data().rankings().get(1).rank()).isEqualTo(2);

            // 3위: product2 (5.0)
            assertThat(response.getBody().data().rankings().get(2).productId()).isEqualTo(product2.getId());
            assertThat(response.getBody().data().rankings().get(2).rank()).isEqualTo(3);
        }

        @Test
        @DisplayName("totalCount와 totalPages를 올바르게 계산한다")
        void totalCount_totalPages_계산() {
            // Arrange - 5개 상품 중 2개씩 페이징
            String key = getTodayKey();
            redisTemplate.opsForZSet().add(key, String.valueOf(product1.getId()), 50.0);
            redisTemplate.opsForZSet().add(key, String.valueOf(product2.getId()), 40.0);
            redisTemplate.opsForZSet().add(key, String.valueOf(product3.getId()), 30.0);
            redisTemplate.opsForZSet().add(key, "100", 20.0);  // 존재하지 않는 상품
            redisTemplate.opsForZSet().add(key, "101", 10.0);  // 존재하지 않는 상품

            // Act - size=2, page=1
            ResponseEntity<ApiResponse<RankingV1Dto.RankingPageResponse>> response = testRestTemplate.exchange(
                "/api/v1/rankings?size=2&page=1",
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<>() {}
            );

            // Assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            // totalCount는 ZCARD 결과 (5개)
            assertThat(response.getBody().data().totalCount()).isEqualTo(5);
            // totalPages = ceil(5 / 2) = 3
            assertThat(response.getBody().data().totalPages()).isEqualTo(3);
            // 현재 페이지 결과는 존재하는 상품 2개
            assertThat(response.getBody().data().rankings()).hasSize(2);
        }

        @Test
        @DisplayName("date 파라미터가 없으면 오늘 날짜를 기본값으로 사용한다")
        void 오늘_날짜_기본값() {
            // Arrange
            String key = getTodayKey();
            redisTemplate.opsForZSet().add(key, String.valueOf(product1.getId()), 10.0);

            // Act
            ResponseEntity<ApiResponse<RankingV1Dto.RankingPageResponse>> response = testRestTemplate.exchange(
                "/api/v1/rankings",
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<>() {}
            );

            // Assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().data().date()).isEqualTo(LocalDate.now().format(DATE_FORMATTER));
            assertThat(response.getBody().data().rankings()).hasSize(1);
        }

        @Test
        @DisplayName("특정 날짜의 랭킹을 조회한다")
        void 특정_날짜_조회() {
            // Arrange
            LocalDate yesterday = LocalDate.now().minusDays(1);
            String yesterdayKey = "ranking:all:" + yesterday.format(DATE_FORMATTER);
            redisTemplate.opsForZSet().add(yesterdayKey, String.valueOf(product1.getId()), 10.0);

            // Act
            ResponseEntity<ApiResponse<RankingV1Dto.RankingPageResponse>> response = testRestTemplate.exchange(
                "/api/v1/rankings?date=" + yesterday.format(DATE_FORMATTER),
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<>() {}
            );

            // Assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().data().date()).isEqualTo(yesterday.format(DATE_FORMATTER));
            assertThat(response.getBody().data().rankings()).hasSize(1);
        }
    }

    @Nested
    @DisplayName("엣지 케이스")
    class EdgeCases {

        @Test
        @DisplayName("랭킹 데이터가 없는 날짜를 조회하면 빈 리스트를 반환한다")
        void 데이터_없는_날짜_빈_리스트() {
            // Act
            ResponseEntity<ApiResponse<RankingV1Dto.RankingPageResponse>> response = testRestTemplate.exchange(
                "/api/v1/rankings?date=20200101",
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<>() {}
            );

            // Assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().data().rankings()).isEmpty();
        }

        @Test
        @DisplayName("page가 전체 데이터보다 큰 경우 빈 리스트를 반환한다")
        void 페이지_초과_빈_리스트() {
            // Arrange
            String key = getTodayKey();
            redisTemplate.opsForZSet().add(key, String.valueOf(product1.getId()), 10.0);

            // Act - 100페이지 요청 (데이터는 1개)
            ResponseEntity<ApiResponse<RankingV1Dto.RankingPageResponse>> response = testRestTemplate.exchange(
                "/api/v1/rankings?page=100&size=10",
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<>() {}
            );

            // Assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().data().rankings()).isEmpty();
        }

        @Test
        @DisplayName("삭제된 상품은 랭킹에서 필터링된다")
        void 삭제된_상품_필터링() {
            // Arrange
            String key = getTodayKey();
            redisTemplate.opsForZSet().add(key, String.valueOf(product1.getId()), 10.0);
            redisTemplate.opsForZSet().add(key, "99999", 20.0); // 존재하지 않는 상품

            // Act
            ResponseEntity<ApiResponse<RankingV1Dto.RankingPageResponse>> response = testRestTemplate.exchange(
                "/api/v1/rankings",
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<>() {}
            );

            // Assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().data().rankings()).hasSize(1);
            assertThat(response.getBody().data().rankings().get(0).productId()).isEqualTo(product1.getId());
        }
    }

    @Nested
    @DisplayName("상품 상세 조회 시 랭킹 정보")
    class ProductDetailWithRankInfo {

        @Test
        @DisplayName("상품 상세 조회 시 랭킹 정보가 포함된다")
        void 상품_상세_랭킹_정보_포함() {
            // Arrange
            String key = getTodayKey();
            redisTemplate.opsForZSet().add(key, String.valueOf(product1.getId()), 30.0);
            redisTemplate.opsForZSet().add(key, String.valueOf(product2.getId()), 20.0);
            redisTemplate.opsForZSet().add(key, String.valueOf(product3.getId()), 10.0);

            // Act
            ResponseEntity<ApiResponse<com.loopers.interfaces.api.product.ProductV1Dto.ProductDetailResponse>> response =
                testRestTemplate.exchange(
                    "/api/v1/products/" + product2.getId(),
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<>() {}
                );

            // Assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().data().id()).isEqualTo(product2.getId());
            assertThat(response.getBody().data().rankInfo()).isNotNull();
            assertThat(response.getBody().data().rankInfo().rank()).isEqualTo(2);
            assertThat(response.getBody().data().rankInfo().score()).isEqualTo(20.0);
        }

        @Test
        @DisplayName("랭킹에 없는 상품은 rankInfo가 null이다")
        void 랭킹_없는_상품_rankInfo_null() {
            // Act - 랭킹 데이터 없이 상품 조회
            ResponseEntity<ApiResponse<com.loopers.interfaces.api.product.ProductV1Dto.ProductDetailResponse>> response =
                testRestTemplate.exchange(
                    "/api/v1/products/" + product1.getId(),
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<>() {}
                );

            // Assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().data().id()).isEqualTo(product1.getId());
            assertThat(response.getBody().data().rankInfo()).isNull();
        }
    }

    @Nested
    @DisplayName("가중치 검증")
    class WeightVerification {

        @Test
        @DisplayName("주문 1건(0.7)이 좋아요 3건(0.6)보다 높다")
        void 주문이_좋아요보다_높음() {
            // Arrange
            String key = getTodayKey();
            // 주문 1건: 0.7 * 1 = 0.7
            double orderScore = 0.7;
            // 좋아요 3건: 0.2 * 3 = 0.6
            double likeScore = 0.6;

            redisTemplate.opsForZSet().add(key, String.valueOf(product1.getId()), orderScore); // 주문 1건
            redisTemplate.opsForZSet().add(key, String.valueOf(product2.getId()), likeScore);   // 좋아요 3건

            // Act
            ResponseEntity<ApiResponse<RankingV1Dto.RankingPageResponse>> response = testRestTemplate.exchange(
                "/api/v1/rankings",
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<>() {}
            );

            // Assert - 주문이 더 높은 순위
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().data().rankings().get(0).productId()).isEqualTo(product1.getId());
            assertThat(response.getBody().data().rankings().get(0).score()).isEqualTo(0.7);
            assertThat(response.getBody().data().rankings().get(1).productId()).isEqualTo(product2.getId());
            assertThat(response.getBody().data().rankings().get(1).score()).isEqualTo(0.6);
        }

        @Test
        @DisplayName("조회 10건(1.0) + 좋아요 2건(0.4) > 주문 1건(0.7)")
        void 복합_이벤트_점수_계산() {
            // Arrange
            String key = getTodayKey();
            // 상품A: 조회 10건(0.1*10=1.0) + 좋아요 2건(0.2*2=0.4) = 1.4
            double productAScore = 0.1 * 10 + 0.2 * 2;
            // 상품B: 주문 1건 = 0.7
            double productBScore = 0.7;

            redisTemplate.opsForZSet().add(key, String.valueOf(product1.getId()), productAScore);
            redisTemplate.opsForZSet().add(key, String.valueOf(product2.getId()), productBScore);

            // Act
            ResponseEntity<ApiResponse<RankingV1Dto.RankingPageResponse>> response = testRestTemplate.exchange(
                "/api/v1/rankings",
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<>() {}
            );

            // Assert - 복합 이벤트가 더 높은 순위
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().data().rankings().get(0).productId()).isEqualTo(product1.getId());
            assertThat(response.getBody().data().rankings().get(0).score()).isCloseTo(1.4, within(0.0001));
        }
    }

    @Nested
    @DisplayName("엣지 케이스")
    class AdditionalEdgeCases {

        @Test
        @DisplayName("빈 날짜 조회 시 totalCount=0, totalPages=0")
        void 빈_날짜_totalCount_0() {
            // Act
            ResponseEntity<ApiResponse<RankingV1Dto.RankingPageResponse>> response = testRestTemplate.exchange(
                "/api/v1/rankings?date=20200101",
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<>() {}
            );

            // Assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().data().rankings()).isEmpty();
            assertThat(response.getBody().data().totalCount()).isEqualTo(0);
            assertThat(response.getBody().data().totalPages()).isEqualTo(0);
        }

        @Test
        @DisplayName("좋아요 취소로 점수가 감소한다")
        void 좋아요_취소_점수_감소() {
            // Arrange
            String key = getTodayKey();
            // 좋아요 3건 후 1건 취소: 0.2*3 - 0.2 = 0.4
            double score = 0.2 * 3 - 0.2;
            redisTemplate.opsForZSet().add(key, String.valueOf(product1.getId()), score);

            // Act
            ResponseEntity<ApiResponse<RankingV1Dto.RankingPageResponse>> response = testRestTemplate.exchange(
                "/api/v1/rankings",
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<>() {}
            );

            // Assert
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().data().rankings().get(0).score()).isCloseTo(0.4, within(0.0001));
        }

        @Test
        @DisplayName("score가 0 이하인 상품도 랭킹에 포함된다")
        void 음수_점수_상품_포함() {
            // Arrange
            String key = getTodayKey();
            // 좋아요 1건 후 2건 취소: 0.2 - 0.4 = -0.2
            redisTemplate.opsForZSet().add(key, String.valueOf(product1.getId()), -0.2);
            redisTemplate.opsForZSet().add(key, String.valueOf(product2.getId()), 0.5);

            // Act
            ResponseEntity<ApiResponse<RankingV1Dto.RankingPageResponse>> response = testRestTemplate.exchange(
                "/api/v1/rankings",
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<>() {}
            );

            // Assert
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().data().rankings()).hasSize(2);
            // product2(0.5) > product1(-0.2)
            assertThat(response.getBody().data().rankings().get(0).productId()).isEqualTo(product2.getId());
            assertThat(response.getBody().data().rankings().get(1).productId()).isEqualTo(product1.getId());
            assertThat(response.getBody().data().rankings().get(1).score()).isEqualTo(-0.2);
        }
    }
}

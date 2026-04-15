package com.loopers.interfaces.api.ranking.v1;

import static com.loopers.interfaces.api.ranking.v1.RankingSteps.getDailyRankings;
import static com.loopers.interfaces.api.ranking.v1.RankingSteps.getHourlyRankings;
import static com.loopers.interfaces.api.ranking.v1.RankingSteps.getMonthlyRankings;
import static com.loopers.interfaces.api.ranking.v1.RankingSteps.getWeeklyRankings;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;

import com.loopers.domain.ranking.ProductRankingMonthly;
import com.loopers.domain.ranking.ProductRankingWeekly;
import com.loopers.infrastructure.ranking.persistence.MonthlyRankingJpaRepository;
import com.loopers.infrastructure.ranking.persistence.WeeklyRankingJpaRepository;
import com.loopers.interfaces.api.brand.v1.BrandDto;
import com.loopers.interfaces.api.brand.v1.BrandSteps;
import com.loopers.interfaces.api.product.v1.ProductDto;
import com.loopers.interfaces.api.product.v1.ProductSteps;
import com.loopers.support.BaseE2ETest;

@DisplayName("Ranking V1 API")
class RankingV1ApiE2ETest extends BaseE2ETest {

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @Autowired
    private WeeklyRankingJpaRepository weeklyRankingJpaRepository;

    @Autowired
    private MonthlyRankingJpaRepository monthlyRankingJpaRepository;

    @DisplayName("GET /api/v1/rankings/daily")
    @Nested
    class DailyRankings {

        private String rankingKey;

        @BeforeEach
        void setUp() {
            String today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
            rankingKey = "ranking:v1:daily:" + today;
        }

        @DisplayName("랭킹 데이터가 있으면,")
        @Nested
        class WhenRankingDataExists {

            private Long productId1;
            private Long productId2;
            private Long productId3;

            @BeforeEach
            void setUp() {
                Long brandId = BrandSteps.createBrand(
                        testRestTemplate,
                        new BrandDto.CreateBrandRequest("테스트 브랜드", "https://example.com/logo.png", null)
                );
                productId1 = ProductSteps.createProduct(testRestTemplate,
                        new ProductDto.CreateProductRequest(brandId, "상품1", "https://example.com/1.png", 50000L, 100L, null));
                productId2 = ProductSteps.createProduct(testRestTemplate,
                        new ProductDto.CreateProductRequest(brandId, "상품2", "https://example.com/2.png", 30000L, 100L, null));
                productId3 = ProductSteps.createProduct(testRestTemplate,
                        new ProductDto.CreateProductRequest(brandId, "상품3", "https://example.com/3.png", 10000L, 100L, null));

                redisTemplate.opsForZSet().add(rankingKey, String.valueOf(productId1), 70.0);
                redisTemplate.opsForZSet().add(rankingKey, String.valueOf(productId2), 58.4);
                redisTemplate.opsForZSet().add(rankingKey, String.valueOf(productId3), 45.2);
            }

            @DisplayName("순위순으로 상품 정보(brandId, liked 포함)를 반환한다.")
            @Test
            void returnsRankedProducts() {
                var response = getDailyRankings(testRestTemplate, "");
                var first = response.getBody().data().rankings().get(0);
                assertAll(
                        () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                        () -> assertThat(response.getBody().data().rankings()).hasSize(3),
                        () -> assertThat(first.rank()).isEqualTo(1),
                        () -> assertThat(first.productId()).isEqualTo(productId1),
                        () -> assertThat(first.productName()).isEqualTo("상품1"),
                        () -> assertThat(first.brandId()).isNotNull(),
                        () -> assertThat(first.liked()).isFalse()
                );
            }

            @DisplayName("페이지네이션이 적용된다.")
            @Test
            void supportsPagination() {
                var response = getDailyRankings(testRestTemplate, "page=1&size=2");
                List<RankingDto.RankedProductResponse> rankings = response.getBody().data().rankings();
                assertAll(
                        () -> assertThat(rankings).hasSize(1),
                        () -> assertThat(rankings.get(0).rank()).isEqualTo(3),
                        () -> assertThat(rankings.get(0).productId()).isEqualTo(productId3),
                        () -> assertThat(response.getBody().data().page()).isEqualTo(1),
                        () -> assertThat(response.getBody().data().size()).isEqualTo(2)
                );
            }
        }

        @DisplayName("랭킹 데이터가 없으면, 빈 배열을 반환한다.")
        @Test
        void returnsEmptyRankings_whenNoData() {
            var response = getDailyRankings(testRestTemplate, "");
            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody().data().rankings()).isEmpty()
            );
        }

        @DisplayName("특정 날짜를 지정하면, 해당 날짜의 랭킹을 조회한다.")
        @Test
        void returnsRankingsForSpecificDate() {
            String specificDate = "20250101";
            String specificKey = "ranking:v1:daily:" + specificDate;
            Long brandId = BrandSteps.createBrand(testRestTemplate,
                    new BrandDto.CreateBrandRequest("브랜드", "https://example.com/logo.png", null));
            Long productId = ProductSteps.createProduct(testRestTemplate,
                    new ProductDto.CreateProductRequest(brandId, "상품", "https://example.com/1.png", 10000L, 100L, null));
            redisTemplate.opsForZSet().add(specificKey, String.valueOf(productId), 99.0);

            var response = getDailyRankings(testRestTemplate, "date=" + specificDate);
            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody().data().rankings()).hasSize(1),
                    () -> assertThat(response.getBody().data().rankings().get(0).productId()).isEqualTo(productId)
            );
        }
    }

    @DisplayName("GET /api/v1/rankings/hourly")
    @Nested
    class HourlyRankings {

        private String rankingKey;

        @BeforeEach
        void setUp() {
            String currentHour = LocalDateTime.now().truncatedTo(ChronoUnit.HOURS)
                    .format(DateTimeFormatter.ofPattern("yyyyMMddHH"));
            rankingKey = "ranking:v1:hourly:" + currentHour;
        }

        @DisplayName("시간 단위 랭킹 데이터가 있으면,")
        @Nested
        class WhenRankingDataExists {

            private Long productId1;
            private Long productId2;
            private Long productId3;

            @BeforeEach
            void setUp() {
                Long brandId = BrandSteps.createBrand(
                        testRestTemplate,
                        new BrandDto.CreateBrandRequest("테스트 브랜드", "https://example.com/logo.png", null)
                );
                productId1 = ProductSteps.createProduct(testRestTemplate,
                        new ProductDto.CreateProductRequest(brandId, "상품1", "https://example.com/1.png", 50000L, 100L, null));
                productId2 = ProductSteps.createProduct(testRestTemplate,
                        new ProductDto.CreateProductRequest(brandId, "상품2", "https://example.com/2.png", 30000L, 100L, null));
                productId3 = ProductSteps.createProduct(testRestTemplate,
                        new ProductDto.CreateProductRequest(brandId, "상품3", "https://example.com/3.png", 10000L, 100L, null));

                redisTemplate.opsForZSet().add(rankingKey, String.valueOf(productId1), 70.0);
                redisTemplate.opsForZSet().add(rankingKey, String.valueOf(productId2), 58.4);
                redisTemplate.opsForZSet().add(rankingKey, String.valueOf(productId3), 45.2);
            }

            @DisplayName("순위순으로 상품 정보(brandId, liked 포함)를 반환한다.")
            @Test
            void returnsRankedProducts() {
                var response = getHourlyRankings(testRestTemplate, "");
                var first = response.getBody().data().rankings().get(0);
                assertAll(
                        () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                        () -> assertThat(response.getBody().data().rankings()).hasSize(3),
                        () -> assertThat(first.rank()).isEqualTo(1),
                        () -> assertThat(first.productId()).isEqualTo(productId1),
                        () -> assertThat(first.productName()).isEqualTo("상품1"),
                        () -> assertThat(first.brandId()).isNotNull(),
                        () -> assertThat(first.liked()).isFalse()
                );
            }

            @DisplayName("페이지네이션이 적용된다.")
            @Test
            void supportsPagination() {
                var response = getHourlyRankings(testRestTemplate, "page=1&size=2");
                List<RankingDto.RankedProductResponse> rankings = response.getBody().data().rankings();
                assertAll(
                        () -> assertThat(rankings).hasSize(1),
                        () -> assertThat(rankings.get(0).rank()).isEqualTo(3),
                        () -> assertThat(rankings.get(0).productId()).isEqualTo(productId3),
                        () -> assertThat(response.getBody().data().page()).isEqualTo(1),
                        () -> assertThat(response.getBody().data().size()).isEqualTo(2)
                );
            }
        }

        @DisplayName("랭킹 데이터가 없으면, 빈 배열을 반환한다.")
        @Test
        void returnsEmptyRankings_whenNoData() {
            var response = getHourlyRankings(testRestTemplate, "");
            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody().data().rankings()).isEmpty()
            );
        }

        @DisplayName("특정 시간을 지정하면, 해당 시간의 랭킹을 조회한다.")
        @Test
        void returnsRankingsForSpecificDatetime() {
            String specificDatetime = "2025010113";
            String specificKey = "ranking:v1:hourly:" + specificDatetime;
            Long brandId = BrandSteps.createBrand(testRestTemplate,
                    new BrandDto.CreateBrandRequest("브랜드", "https://example.com/logo.png", null));
            Long productId = ProductSteps.createProduct(testRestTemplate,
                    new ProductDto.CreateProductRequest(brandId, "상품", "https://example.com/1.png", 10000L, 100L, null));
            redisTemplate.opsForZSet().add(specificKey, String.valueOf(productId), 99.0);

            var response = getHourlyRankings(testRestTemplate, "datetime=" + specificDatetime);
            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody().data().rankings()).hasSize(1),
                    () -> assertThat(response.getBody().data().rankings().get(0).productId()).isEqualTo(productId)
            );
        }
    }

    @DisplayName("GET /api/v1/rankings/weekly")
    @Nested
    class WeeklyRankings {

        private static final String DATE = "20260414";

        @DisplayName("주간 랭킹 데이터가 있으면,")
        @Nested
        class WhenRankingDataExists {

            private Long productId1;
            private Long productId2;
            private Long productId3;

            @BeforeEach
            void setUp() {
                Long brandId = BrandSteps.createBrand(
                        testRestTemplate,
                        new BrandDto.CreateBrandRequest("테스트 브랜드", "https://example.com/logo.png", null)
                );
                productId1 = ProductSteps.createProduct(testRestTemplate,
                        new ProductDto.CreateProductRequest(brandId, "상품1", "https://example.com/1.png", 50000L, 100L, null));
                productId2 = ProductSteps.createProduct(testRestTemplate,
                        new ProductDto.CreateProductRequest(brandId, "상품2", "https://example.com/2.png", 30000L, 100L, null));
                productId3 = ProductSteps.createProduct(testRestTemplate,
                        new ProductDto.CreateProductRequest(brandId, "상품3", "https://example.com/3.png", 10000L, 100L, null));

                LocalDate scoreDate = LocalDate.of(2026, 4, 14);
                weeklyRankingJpaRepository.save(ProductRankingWeekly.create(productId1, scoreDate, 70.0));
                weeklyRankingJpaRepository.save(ProductRankingWeekly.create(productId2, scoreDate, 58.4));
                weeklyRankingJpaRepository.save(ProductRankingWeekly.create(productId3, scoreDate, 45.2));
            }

            @DisplayName("순위순으로 상품 정보(brandId, liked 포함)를 반환한다.")
            @Test
            void returnsRankedProducts() {
                var response = getWeeklyRankings(testRestTemplate, "date=" + DATE);
                var first = response.getBody().data().rankings().get(0);
                assertAll(
                        () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                        () -> assertThat(response.getBody().data().rankings()).hasSize(3),
                        () -> assertThat(first.rank()).isEqualTo(1),
                        () -> assertThat(first.productId()).isEqualTo(productId1),
                        () -> assertThat(first.productName()).isEqualTo("상품1"),
                        () -> assertThat(first.brandId()).isNotNull(),
                        () -> assertThat(first.liked()).isFalse()
                );
            }

            @DisplayName("페이지네이션이 적용된다.")
            @Test
            void supportsPagination() {
                var response = getWeeklyRankings(testRestTemplate, "date=" + DATE + "&page=1&size=2");
                List<RankingDto.RankedProductResponse> rankings = response.getBody().data().rankings();
                assertAll(
                        () -> assertThat(rankings).hasSize(1),
                        () -> assertThat(rankings.get(0).rank()).isEqualTo(3),
                        () -> assertThat(rankings.get(0).productId()).isEqualTo(productId3),
                        () -> assertThat(response.getBody().data().page()).isEqualTo(1),
                        () -> assertThat(response.getBody().data().size()).isEqualTo(2)
                );
            }
        }

        @DisplayName("해당 date에 데이터가 없으면, 빈 배열을 반환한다.")
        @Test
        void returnsEmptyRankings_whenNoData() {
            var response = getWeeklyRankings(testRestTemplate, "date=" + DATE);
            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody().data().rankings()).isEmpty()
            );
        }
    }

    @DisplayName("GET /api/v1/rankings/monthly")
    @Nested
    class MonthlyRankings {

        private static final String DATE = "20260414";

        @DisplayName("월간 랭킹 데이터가 있으면,")
        @Nested
        class WhenRankingDataExists {

            private Long productId1;
            private Long productId2;
            private Long productId3;

            @BeforeEach
            void setUp() {
                Long brandId = BrandSteps.createBrand(
                        testRestTemplate,
                        new BrandDto.CreateBrandRequest("테스트 브랜드", "https://example.com/logo.png", null)
                );
                productId1 = ProductSteps.createProduct(testRestTemplate,
                        new ProductDto.CreateProductRequest(brandId, "상품1", "https://example.com/1.png", 50000L, 100L, null));
                productId2 = ProductSteps.createProduct(testRestTemplate,
                        new ProductDto.CreateProductRequest(brandId, "상품2", "https://example.com/2.png", 30000L, 100L, null));
                productId3 = ProductSteps.createProduct(testRestTemplate,
                        new ProductDto.CreateProductRequest(brandId, "상품3", "https://example.com/3.png", 10000L, 100L, null));

                LocalDate scoreDate = LocalDate.of(2026, 4, 14);
                monthlyRankingJpaRepository.save(ProductRankingMonthly.create(productId1, scoreDate, 70.0));
                monthlyRankingJpaRepository.save(ProductRankingMonthly.create(productId2, scoreDate, 58.4));
                monthlyRankingJpaRepository.save(ProductRankingMonthly.create(productId3, scoreDate, 45.2));
            }

            @DisplayName("순위순으로 상품 정보(brandId, liked 포함)를 반환한다.")
            @Test
            void returnsRankedProducts() {
                // act
                var response = getMonthlyRankings(testRestTemplate, "date=" + DATE);

                // assert
                var first = response.getBody().data().rankings().get(0);
                assertAll(
                        () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                        () -> assertThat(response.getBody().data().rankings()).hasSize(3),
                        () -> assertThat(first.rank()).isEqualTo(1),
                        () -> assertThat(first.productId()).isEqualTo(productId1),
                        () -> assertThat(first.productName()).isEqualTo("상품1"),
                        () -> assertThat(first.brandId()).isNotNull(),
                        () -> assertThat(first.liked()).isFalse()
                );
            }

            @DisplayName("페이지네이션이 적용된다.")
            @Test
            void supportsPagination() {
                // act
                var response = getMonthlyRankings(testRestTemplate, "date=" + DATE + "&page=1&size=2");

                // assert
                List<RankingDto.RankedProductResponse> rankings = response.getBody().data().rankings();
                assertAll(
                        () -> assertThat(rankings).hasSize(1),
                        () -> assertThat(rankings.get(0).rank()).isEqualTo(3),
                        () -> assertThat(rankings.get(0).productId()).isEqualTo(productId3),
                        () -> assertThat(response.getBody().data().page()).isEqualTo(1),
                        () -> assertThat(response.getBody().data().size()).isEqualTo(2)
                );
            }
        }

        @DisplayName("해당 date에 데이터가 없으면, 빈 배열을 반환한다.")
        @Test
        void returnsEmptyRankings_whenNoData() {
            // act
            var response = getMonthlyRankings(testRestTemplate, "date=" + DATE);

            // assert
            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody().data().rankings()).isEmpty()
            );
        }
    }
}

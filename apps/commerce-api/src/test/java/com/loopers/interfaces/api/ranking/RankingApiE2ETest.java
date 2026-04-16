package com.loopers.interfaces.api.ranking;

import com.loopers.domain.brand.Brand;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.vo.Price;
import com.loopers.domain.product.vo.Stock;
import com.loopers.domain.ranking.MvProductRankMonthly;
import com.loopers.domain.ranking.MvProductRankWeekly;
import com.loopers.infrastructure.brand.BrandJpaRepository;
import com.loopers.infrastructure.product.ProductJpaRepository;
import com.loopers.infrastructure.ranking.MonthlyRankingJpaRepository;
import com.loopers.infrastructure.ranking.WeeklyRankingJpaRepository;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.support.page.PageResponse;
import com.loopers.testcontainers.RedisTestContainersConfig;
import com.loopers.utils.DatabaseCleanUp;
import com.loopers.utils.RedisCleanUp;
import org.junit.jupiter.api.AfterEach;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@Import(RedisTestContainersConfig.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RankingApiE2ETest {

    private static final String ENDPOINT = "/api/v1/rankings";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");

    @Autowired
    private TestRestTemplate testRestTemplate;

    @Autowired
    private BrandJpaRepository brandJpaRepository;

    @Autowired
    private ProductJpaRepository productJpaRepository;

    @Autowired
    private WeeklyRankingJpaRepository weeklyRankingJpaRepository;

    @Autowired
    private MonthlyRankingJpaRepository monthlyRankingJpaRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @Autowired
    private RedisCleanUp redisCleanUp;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
        redisCleanUp.truncateAll();
    }

    @DisplayName("GET /api/v1/rankings")
    @Nested
    class GetRankings {

        @DisplayName("ZSET과 DB에 데이터가 있으면, 랭킹 순서(점수 높은 순)로 상품 정보를 반환한다.")
        @Test
        void returnsRankingsInOrder_whenDataExists() {
            // arrange
            LocalDate date = LocalDate.now();
            String key = "ranking:all:" + date.format(DATE_FORMATTER);

            Brand brand = brandJpaRepository.save(Brand.of("나이키", null));
            Product product1 = productJpaRepository.save(Product.of("상품 A", "설명 A", Stock.from(10), Price.from(1000), brand.getId()));
            Product product2 = productJpaRepository.save(Product.of("상품 B", "설명 B", Stock.from(5), Price.from(2000), brand.getId()));

            // product2 가 더 높은 점수 → rank 1
            redisTemplate.opsForZSet().add(key, product1.getId().toString(), 1.0);
            redisTemplate.opsForZSet().add(key, product2.getId().toString(), 5.0);

            // act
            ResponseEntity<ApiResponse<PageResponse<RankingDto.Response>>> response = testRestTemplate.exchange(
                    ENDPOINT + "?date=" + date.format(DATE_FORMATTER),
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<>() {}
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            PageResponse<RankingDto.Response> data = response.getBody().data();
            assertAll(
                    () -> assertThat(data.content()).hasSize(2),
                    () -> assertThat(data.content().get(0).rank()).isEqualTo(1L),
                    () -> assertThat(data.content().get(0).productId()).isEqualTo(product2.getId()),
                    () -> assertThat(data.content().get(0).name()).isEqualTo("상품 B"),
                    () -> assertThat(data.content().get(1).rank()).isEqualTo(2L),
                    () -> assertThat(data.content().get(1).productId()).isEqualTo(product1.getId()),
                    () -> assertThat(data.totalPages()).isEqualTo(1)
            );
        }

        @DisplayName("ZSET 이 비어있으면, 빈 content 와 totalPages=0 을 반환한다.")
        @Test
        void returnsEmptyPage_whenZSetIsEmpty() {
            // arrange
            LocalDate date = LocalDate.now();

            // act
            ResponseEntity<ApiResponse<PageResponse<RankingDto.Response>>> response = testRestTemplate.exchange(
                    ENDPOINT + "?date=" + date.format(DATE_FORMATTER),
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<>() {}
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            PageResponse<RankingDto.Response> data = response.getBody().data();
            assertAll(
                    () -> assertThat(data.content()).isEmpty(),
                    () -> assertThat(data.totalPages()).isEqualTo(0)
            );
        }

        @DisplayName("date 파라미터를 생략하면, 오늘 날짜 기준으로 랭킹을 반환한다.")
        @Test
        void usesTodayDate_whenDateParamOmitted() {
            // arrange
            LocalDate today = LocalDate.now();
            String key = "ranking:all:" + today.format(DATE_FORMATTER);

            Brand brand = brandJpaRepository.save(Brand.of("아디다스", null));
            Product product = productJpaRepository.save(Product.of("상품 C", "설명 C", Stock.from(3), Price.from(5000), brand.getId()));
            redisTemplate.opsForZSet().add(key, product.getId().toString(), 2.0);

            // act
            ResponseEntity<ApiResponse<PageResponse<RankingDto.Response>>> response = testRestTemplate.exchange(
                    ENDPOINT,
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<>() {}
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            PageResponse<RankingDto.Response> data = response.getBody().data();
            assertAll(
                    () -> assertThat(data.content()).hasSize(1),
                    () -> assertThat(data.content().get(0).productId()).isEqualTo(product.getId())
            );
        }

        @DisplayName("period 파라미터가 잘못된 값이면 400을 반환한다.")
        @Test
        void period_invalidValue_returns400() {
            // act
            ResponseEntity<ApiResponse<PageResponse<RankingDto.Response>>> response = testRestTemplate.exchange(
                    ENDPOINT + "?period=INVALID",
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<>() {}
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }

    @DisplayName("GET /api/v1/rankings?period=WEEKLY")
    @Nested
    class GetWeeklyRankings {

        @DisplayName("mv_product_rank_weekly 에 데이터가 있으면 rank 순서대로 상품 정보를 반환한다.")
        @Test
        void returnsRankingsInOrder_whenDataExists() {
            // arrange
            LocalDate monday = LocalDate.of(2026, 4, 13);
            Brand brand = brandJpaRepository.save(Brand.of("나이키", null));
            Product product1 = productJpaRepository.save(Product.of("주간 상품 A", null, Stock.from(10), Price.from(1000), brand.getId()));
            Product product2 = productJpaRepository.save(Product.of("주간 상품 B", null, Stock.from(5), Price.from(2000), brand.getId()));

            weeklyRankingJpaRepository.save(MvProductRankWeekly.of(product2.getId(), 1, 200.0, 5, 3, 50, 2000, monday));
            weeklyRankingJpaRepository.save(MvProductRankWeekly.of(product1.getId(), 2, 100.0, 3, 1, 30, 1000, monday));

            // act
            ResponseEntity<ApiResponse<PageResponse<RankingDto.Response>>> response = testRestTemplate.exchange(
                    ENDPOINT + "?date=" + monday.format(DATE_FORMATTER) + "&period=WEEKLY",
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<>() {}
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            PageResponse<RankingDto.Response> data = response.getBody().data();
            assertAll(
                    () -> assertThat(data.content()).hasSize(2),
                    () -> assertThat(data.content().get(0).rank()).isEqualTo(1L),
                    () -> assertThat(data.content().get(0).productId()).isEqualTo(product2.getId()),
                    () -> assertThat(data.content().get(1).rank()).isEqualTo(2L),
                    () -> assertThat(data.content().get(1).productId()).isEqualTo(product1.getId())
            );
        }

        @DisplayName("date 가 수요일이면 해당 주 월요일 기준 데이터를 반환한다.")
        @Test
        void usesMondayAsBaseDate_whenDateIsWednesday() {
            // arrange
            LocalDate wednesday = LocalDate.of(2026, 4, 15);
            LocalDate monday = LocalDate.of(2026, 4, 13);
            Brand brand = brandJpaRepository.save(Brand.of("나이키", null));
            Product product = productJpaRepository.save(Product.of("주간 상품", null, Stock.from(10), Price.from(1000), brand.getId()));

            weeklyRankingJpaRepository.save(MvProductRankWeekly.of(product.getId(), 1, 100.0, 1, 1, 10, 1000, monday));

            // act
            ResponseEntity<ApiResponse<PageResponse<RankingDto.Response>>> response = testRestTemplate.exchange(
                    ENDPOINT + "?date=" + wednesday.format(DATE_FORMATTER) + "&period=WEEKLY",
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<>() {}
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            PageResponse<RankingDto.Response> data = response.getBody().data();
            assertAll(
                    () -> assertThat(data.content()).hasSize(1),
                    () -> assertThat(data.content().get(0).productId()).isEqualTo(product.getId())
            );
        }

        @DisplayName("데이터가 없으면 빈 content 와 totalPages=0 을 반환한다.")
        @Test
        void returnsEmpty_whenNoWeeklyData() {
            // arrange
            LocalDate monday = LocalDate.of(2026, 4, 13);

            // act
            ResponseEntity<ApiResponse<PageResponse<RankingDto.Response>>> response = testRestTemplate.exchange(
                    ENDPOINT + "?date=" + monday.format(DATE_FORMATTER) + "&period=WEEKLY",
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<>() {}
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            PageResponse<RankingDto.Response> data = response.getBody().data();
            assertAll(
                    () -> assertThat(data.content()).isEmpty(),
                    () -> assertThat(data.totalPages()).isEqualTo(0)
            );
        }
    }

    @DisplayName("GET /api/v1/rankings?period=MONTHLY")
    @Nested
    class GetMonthlyRankings {

        @DisplayName("mv_product_rank_monthly 에 데이터가 있으면 rank 순서대로 상품 정보를 반환한다.")
        @Test
        void returnsRankingsInOrder_whenDataExists() {
            // arrange
            LocalDate firstDay = LocalDate.of(2026, 4, 1);
            Brand brand = brandJpaRepository.save(Brand.of("아디다스", null));
            Product product1 = productJpaRepository.save(Product.of("월간 상품 A", null, Stock.from(10), Price.from(1000), brand.getId()));
            Product product2 = productJpaRepository.save(Product.of("월간 상품 B", null, Stock.from(5), Price.from(2000), brand.getId()));

            monthlyRankingJpaRepository.save(MvProductRankMonthly.of(product2.getId(), 1, 200.0, 5, 3, 50, 2000, firstDay));
            monthlyRankingJpaRepository.save(MvProductRankMonthly.of(product1.getId(), 2, 100.0, 3, 1, 30, 1000, firstDay));

            // act
            ResponseEntity<ApiResponse<PageResponse<RankingDto.Response>>> response = testRestTemplate.exchange(
                    ENDPOINT + "?date=" + firstDay.format(DATE_FORMATTER) + "&period=MONTHLY",
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<>() {}
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            PageResponse<RankingDto.Response> data = response.getBody().data();
            assertAll(
                    () -> assertThat(data.content()).hasSize(2),
                    () -> assertThat(data.content().get(0).rank()).isEqualTo(1L),
                    () -> assertThat(data.content().get(0).productId()).isEqualTo(product2.getId()),
                    () -> assertThat(data.content().get(1).rank()).isEqualTo(2L),
                    () -> assertThat(data.content().get(1).productId()).isEqualTo(product1.getId())
            );
        }

        @DisplayName("date 가 월 중간이면 해당 월 1일 기준 데이터를 반환한다.")
        @Test
        void usesFirstDayAsBaseDate_whenDateIsMiddleOfMonth() {
            // arrange
            LocalDate midMonth = LocalDate.of(2026, 4, 15);
            LocalDate firstDay = LocalDate.of(2026, 4, 1);
            Brand brand = brandJpaRepository.save(Brand.of("아디다스", null));
            Product product = productJpaRepository.save(Product.of("월간 상품", null, Stock.from(10), Price.from(1000), brand.getId()));

            monthlyRankingJpaRepository.save(MvProductRankMonthly.of(product.getId(), 1, 100.0, 1, 1, 10, 1000, firstDay));

            // act
            ResponseEntity<ApiResponse<PageResponse<RankingDto.Response>>> response = testRestTemplate.exchange(
                    ENDPOINT + "?date=" + midMonth.format(DATE_FORMATTER) + "&period=MONTHLY",
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<>() {}
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            PageResponse<RankingDto.Response> data = response.getBody().data();
            assertAll(
                    () -> assertThat(data.content()).hasSize(1),
                    () -> assertThat(data.content().get(0).productId()).isEqualTo(product.getId())
            );
        }

        @DisplayName("데이터가 없으면 빈 content 와 totalPages=0 을 반환한다.")
        @Test
        void returnsEmpty_whenNoMonthlyData() {
            // arrange
            LocalDate firstDay = LocalDate.of(2026, 4, 1);

            // act
            ResponseEntity<ApiResponse<PageResponse<RankingDto.Response>>> response = testRestTemplate.exchange(
                    ENDPOINT + "?date=" + firstDay.format(DATE_FORMATTER) + "&period=MONTHLY",
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<>() {}
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            PageResponse<RankingDto.Response> data = response.getBody().data();
            assertAll(
                    () -> assertThat(data.content()).isEmpty(),
                    () -> assertThat(data.totalPages()).isEqualTo(0)
            );
        }
    }
}

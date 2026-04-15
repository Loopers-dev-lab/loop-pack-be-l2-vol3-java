package com.loopers.interfaces.api;

import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.domain.common.Money;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.ranking.MvProductRankMonthly;
import com.loopers.domain.ranking.MvProductRankWeekly;
import com.loopers.domain.ranking.ProductRankMvRepository;
import com.loopers.support.error.ErrorType;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RankingApiE2ETest {

    private static final String RANKINGS_ENDPOINT = "/api/v1/rankings";
    private static final String HOURLY_RANKINGS_ENDPOINT = "/api/v1/rankings/hourly";

    @Autowired
    private TestRestTemplate testRestTemplate;

    @Autowired
    private BrandRepository brandRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductRankMvRepository productRankMvRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    private Long productId1;
    private Long productId2;

    @BeforeEach
    void setUp() {
        Brand brand = brandRepository.save(Brand.create("테스트 브랜드"));
        Product product1 = Product.create(brand.getId(), "상품A", Money.of(10000L));
        Product product2 = Product.create(brand.getId(), "상품B", Money.of(20000L));
        productId1 = productRepository.save(product1).getId();
        productId2 = productRepository.save(product2).getId();
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @Nested
    @DisplayName("GET /api/v1/rankings — 입력 검증")
    class GetRankingsValidation {

        @Test
        @DisplayName("page가 0이면 400 응답을 받는다")
        void getRankings_fail_whenPageIsZero() {
            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                    RANKINGS_ENDPOINT + "?page=0&size=20",
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<>() {}
            );

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST),
                    () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.FAIL),
                    () -> assertThat(response.getBody().meta().errorCode()).isEqualTo(ErrorType.BAD_REQUEST.getCode())
            );
        }

        @Test
        @DisplayName("size가 너무 크면 400 응답을 받는다")
        void getRankings_fail_whenSizeExceedsMax() {
            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                    RANKINGS_ENDPOINT + "?page=1&size=101",
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<>() {}
            );

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST),
                    () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.FAIL),
                    () -> assertThat(response.getBody().meta().errorCode()).isEqualTo(ErrorType.BAD_REQUEST.getCode())
            );
        }

        @Test
        @DisplayName("date 형식이 잘못되면 400 응답을 받는다")
        void getRankings_fail_whenDateIsMalformed() {
            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                    RANKINGS_ENDPOINT + "?date=2026-04-09&page=1&size=20",
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<>() {}
            );

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST),
                    () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.FAIL),
                    () -> assertThat(response.getBody().meta().errorCode()).isEqualTo(ErrorType.BAD_REQUEST.getCode())
            );
        }

        @Test
        @DisplayName("잘못된 period 값이면 400 응답을 받는다")
        void getRankings_fail_whenPeriodIsInvalid() {
            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                    RANKINGS_ENDPOINT + "?period=yearly&page=1&size=20",
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<>() {}
            );

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST),
                    () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.FAIL)
            );
        }
    }

    @Nested
    @DisplayName("GET /api/v1/rankings?period=weekly")
    class GetWeeklyRankings {

        @Test
        @DisplayName("주간 랭킹을 MV 테이블에서 조회하여 반환한다")
        void getWeeklyRankings_success() {
            // arrange — MV 데이터 직접 적재
            productRankMvRepository.saveAllWeekly(List.of(
                    MvProductRankWeekly.create(productId1, "2026-W15", 100, 50, 10000, 85.5, 1),
                    MvProductRankWeekly.create(productId2, "2026-W15", 50, 20, 5000, 42.3, 2)
            ));

            // act
            ResponseEntity<ApiResponse<Map>> response = testRestTemplate.exchange(
                    RANKINGS_ENDPOINT + "?period=weekly&date=20260408&page=1&size=20",
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<>() {}
            );

            // assert
            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.SUCCESS),
                    () -> {
                        Map body = response.getBody().data();
                        List rankings = (List) body.get("rankings");
                        assertThat(rankings).hasSize(2);
                    }
            );
        }

        @Test
        @DisplayName("데이터가 없으면 빈 목록을 반환한다")
        void getWeeklyRankings_empty() {
            ResponseEntity<ApiResponse<Map>> response = testRestTemplate.exchange(
                    RANKINGS_ENDPOINT + "?period=weekly&date=20260101&page=1&size=20",
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<>() {}
            );

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> {
                        Map body = response.getBody().data();
                        List rankings = (List) body.get("rankings");
                        assertThat(rankings).isEmpty();
                    }
            );
        }
    }

    @Nested
    @DisplayName("GET /api/v1/rankings?period=monthly")
    class GetMonthlyRankings {

        @Test
        @DisplayName("월간 랭킹을 MV 테이블에서 조회하여 반환한다")
        void getMonthlyRankings_success() {
            // arrange
            productRankMvRepository.saveAllMonthly(List.of(
                    MvProductRankMonthly.create(productId1, "2026-04", 200, 100, 50000, 120.7, 1),
                    MvProductRankMonthly.create(productId2, "2026-04", 80, 30, 8000, 55.2, 2)
            ));

            // act
            ResponseEntity<ApiResponse<Map>> response = testRestTemplate.exchange(
                    RANKINGS_ENDPOINT + "?period=monthly&date=20260415&page=1&size=20",
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<>() {}
            );

            // assert
            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.SUCCESS),
                    () -> {
                        Map body = response.getBody().data();
                        List rankings = (List) body.get("rankings");
                        assertThat(rankings).hasSize(2);
                    }
            );
        }

        @Test
        @DisplayName("데이터가 없으면 빈 목록을 반환한다")
        void getMonthlyRankings_empty() {
            ResponseEntity<ApiResponse<Map>> response = testRestTemplate.exchange(
                    RANKINGS_ENDPOINT + "?period=monthly&date=20260101&page=1&size=20",
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<>() {}
            );

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> {
                        Map body = response.getBody().data();
                        List rankings = (List) body.get("rankings");
                        assertThat(rankings).isEmpty();
                    }
            );
        }
    }

    @Nested
    @DisplayName("GET /api/v1/rankings/hourly")
    class GetHourlyRankings {

        @Test
        @DisplayName("page가 0이면 400 응답을 받는다")
        void getHourlyRankings_fail_whenPageIsZero() {
            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                    HOURLY_RANKINGS_ENDPOINT + "?page=0&size=20",
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<>() {}
            );

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST),
                    () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.FAIL),
                    () -> assertThat(response.getBody().meta().errorCode()).isEqualTo(ErrorType.BAD_REQUEST.getCode())
            );
        }

        @Test
        @DisplayName("hour 형식이 잘못되면 400 응답을 받는다")
        void getHourlyRankings_fail_whenHourIsMalformed() {
            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                    HOURLY_RANKINGS_ENDPOINT + "?hour=2026-04-0912&page=1&size=20",
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<>() {}
            );

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST),
                    () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.FAIL),
                    () -> assertThat(response.getBody().meta().errorCode()).isEqualTo(ErrorType.BAD_REQUEST.getCode())
            );
        }
    }
}

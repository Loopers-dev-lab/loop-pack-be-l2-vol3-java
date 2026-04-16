package com.loopers.interfaces.api;

import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandStatus;
import com.loopers.domain.product.MarginType;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductStatus;
import com.loopers.infrastructure.brand.BrandJpaRepository;
import com.loopers.infrastructure.product.ProductJpaRepository;
import com.loopers.interfaces.api.ranking.dto.RankingV1Dto;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
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
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 주간 랭킹 API E2E — HTTP → Controller → Facade → DB (MV 테이블).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("WeeklyRankingV1 API E2E")
class WeeklyRankingV1ApiE2ETest {

    private static final String ENDPOINT = "/api/v1/rankings/weekly";
    private static final DateTimeFormatter YYYYMMDD = DateTimeFormatter.ofPattern("yyyyMMdd");

    @Autowired
    private Clock clock;

    @Autowired
    private TestRestTemplate testRestTemplate;

    @Autowired
    private BrandJpaRepository brandJpaRepository;

    @Autowired
    private ProductJpaRepository productJpaRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private Brand saveBrand() {
        Brand brand = new Brand("브랜드", "설명");
        brand.changeStatus(BrandStatus.ACTIVE);
        return brandJpaRepository.save(brand);
    }

    private Product saveProduct(Brand brand, String name, String displayYn) {
        Product product = new Product(brand, name, 10000, 9000, 1000, 2500,
                "설명", MarginType.AMOUNT, ProductStatus.ON_SALE, displayYn, List.of());
        return productJpaRepository.save(product);
    }

    private void seedWeeklyRanking(Long productId, LocalDate baseDate, int rank, double score) {
        jdbcTemplate.update(
                "INSERT INTO mv_product_rank_weekly (product_id, base_date, `rank`, score, updated_at) VALUES (?, ?, ?, ?, ?)",
                productId, baseDate, rank, score, LocalDateTime.now()
        );
    }

    @Nested
    @DisplayName("GET /api/v1/rankings/weekly")
    class GetWeeklyRanking {

        @Test
        @DisplayName("200 — 상품 정보가 Aggregation 된 주간 랭킹 Page 를 반환한다.")
        void happyPath() {
            // given
            Brand brand = saveBrand();
            Product p1 = saveProduct(brand, "상품1", "Y");
            Product p2 = saveProduct(brand, "상품2", "Y");
            LocalDate baseDate = LocalDate.now(clock).minusDays(1);
            seedWeeklyRanking(p1.getId(), baseDate, 1, 10.0);
            seedWeeklyRanking(p2.getId(), baseDate, 2, 5.0);

            // when
            ResponseEntity<ApiResponse<RankingV1Dto.RankingPageResponse>> response = testRestTemplate.exchange(
                    ENDPOINT + "?date=" + baseDate.format(YYYYMMDD) + "&page=1&size=20",
                    HttpMethod.GET, null,
                    new ParameterizedTypeReference<>() {}
            );

            // then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            RankingV1Dto.RankingPageResponse body = response.getBody().data();
            assertThat(body.items()).hasSize(2);
            assertThat(body.items().get(0).rank()).isEqualTo(1L);
            assertThat(body.items().get(0).productId()).isEqualTo(p1.getId());
            assertThat(body.items().get(0).name()).isEqualTo("상품1");
            assertThat(body.items().get(1).rank()).isEqualTo(2L);
            assertThat(body.items().get(1).productId()).isEqualTo(p2.getId());
            assertThat(body.totalElements()).isEqualTo(2L);
            assertThat(body.date()).isEqualTo(baseDate.format(YYYYMMDD));
        }

        @Test
        @DisplayName("숨김/삭제 상품은 응답에서 제외된다.")
        void visibilityFilter() {
            // given
            Brand brand = saveBrand();
            Product visible = saveProduct(brand, "visible", "Y");
            Product hidden = saveProduct(brand, "hidden", "N");
            LocalDate baseDate = LocalDate.now(clock).minusDays(1);
            seedWeeklyRanking(hidden.getId(), baseDate, 1, 20.0);
            seedWeeklyRanking(visible.getId(), baseDate, 2, 10.0);

            // when
            ResponseEntity<ApiResponse<RankingV1Dto.RankingPageResponse>> response = testRestTemplate.exchange(
                    ENDPOINT + "?date=" + baseDate.format(YYYYMMDD),
                    HttpMethod.GET, null,
                    new ParameterizedTypeReference<>() {}
            );

            // then
            RankingV1Dto.RankingPageResponse body = response.getBody().data();
            assertThat(body.items()).hasSize(1);
            assertThat(body.items().get(0).productId()).isEqualTo(visible.getId());
        }

        @Test
        @DisplayName("date 파라미터 생략 시 어제 날짜 기준 주간 랭킹을 조회한다.")
        void defaultToYesterday() {
            // given
            Brand brand = saveBrand();
            Product product = saveProduct(brand, "상품", "Y");
            LocalDate yesterday = LocalDate.now(clock).minusDays(1);
            seedWeeklyRanking(product.getId(), yesterday, 1, 10.0);

            // when
            ResponseEntity<ApiResponse<RankingV1Dto.RankingPageResponse>> response = testRestTemplate.exchange(
                    ENDPOINT,
                    HttpMethod.GET, null,
                    new ParameterizedTypeReference<>() {}
            );

            // then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            RankingV1Dto.RankingPageResponse body = response.getBody().data();
            assertThat(body.items()).hasSize(1);
            assertThat(body.date()).isEqualTo(yesterday.format(YYYYMMDD));
        }

        @Test
        @DisplayName("잘못된 date 포맷은 400을 반환한다.")
        void badDateFormat() {
            // when
            ResponseEntity<ApiResponse<RankingV1Dto.RankingPageResponse>> response = testRestTemplate.exchange(
                    ENDPOINT + "?date=2026-04-09",
                    HttpMethod.GET, null,
                    new ParameterizedTypeReference<>() {}
            );

            // then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @Test
        @DisplayName("page=2, size=2 요청 시 2페이지 데이터를 반환한다.")
        void pagination() {
            // given
            Brand brand = saveBrand();
            Product p1 = saveProduct(brand, "상품1", "Y");
            Product p2 = saveProduct(brand, "상품2", "Y");
            Product p3 = saveProduct(brand, "상품3", "Y");
            LocalDate baseDate = LocalDate.now(clock).minusDays(1);
            seedWeeklyRanking(p1.getId(), baseDate, 1, 30.0);
            seedWeeklyRanking(p2.getId(), baseDate, 2, 20.0);
            seedWeeklyRanking(p3.getId(), baseDate, 3, 10.0);

            // when
            ResponseEntity<ApiResponse<RankingV1Dto.RankingPageResponse>> response = testRestTemplate.exchange(
                    ENDPOINT + "?date=" + baseDate.format(YYYYMMDD) + "&page=2&size=2",
                    HttpMethod.GET, null,
                    new ParameterizedTypeReference<>() {}
            );

            // then — 3번째 상품(rank=3)만 반환, totalElements는 전체 3건
            RankingV1Dto.RankingPageResponse body = response.getBody().data();
            assertThat(body.items()).hasSize(1);
            assertThat(body.items().get(0).productId()).isEqualTo(p3.getId());
            assertThat(body.items().get(0).rank()).isEqualTo(3L);
            assertThat(body.totalElements()).isEqualTo(3L);
        }

        @Test
        @DisplayName("size=200 요청 시 100으로 보정된 size 가 응답에 반영된다.")
        void sizeClampedToMax() {
            // when
            ResponseEntity<ApiResponse<RankingV1Dto.RankingPageResponse>> response = testRestTemplate.exchange(
                    ENDPOINT + "?date=" + LocalDate.now(clock).minusDays(1).format(YYYYMMDD) + "&size=200",
                    HttpMethod.GET, null,
                    new ParameterizedTypeReference<>() {}
            );

            // then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().data().size()).isEqualTo(100);
        }

        @Test
        @DisplayName("랭킹 데이터가 없으면 빈 items 와 totalElements=0 을 반환한다.")
        void emptyRanking() {
            // when
            ResponseEntity<ApiResponse<RankingV1Dto.RankingPageResponse>> response = testRestTemplate.exchange(
                    ENDPOINT + "?date=" + LocalDate.now(clock).minusDays(1).format(YYYYMMDD),
                    HttpMethod.GET, null,
                    new ParameterizedTypeReference<>() {}
            );

            // then
            RankingV1Dto.RankingPageResponse body = response.getBody().data();
            assertThat(body.items()).isEmpty();
            assertThat(body.totalElements()).isZero();
        }
    }
}

package com.loopers.interfaces.api;

import com.loopers.config.redis.RedisConfig;
import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandStatus;
import com.loopers.domain.product.MarginType;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductStatus;
import com.loopers.infrastructure.brand.BrandJpaRepository;
import com.loopers.infrastructure.product.ProductJpaRepository;
import com.loopers.domain.ranking.RankingKey;
import com.loopers.interfaces.api.ranking.dto.RankingV1Dto;
import com.loopers.utils.DatabaseCleanUp;
import com.loopers.utils.RedisCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 랭킹 API E2E — HTTP → Controller → Facade → Redis + DB.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("RankingV1 API E2E")
class RankingV1ApiE2ETest {

    private static final String ENDPOINT = "/api/v1/rankings";
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
    @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER)
    private RedisTemplate<String, String> masterRedisTemplate;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @Autowired
    private RedisCleanUp redisCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
        redisCleanUp.truncateAll();
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

    private void seedRanking(LocalDate date, Long productId, double score) {
        masterRedisTemplate.opsForZSet().add(RankingKey.daily(date), productId.toString(), score);
    }

    @Nested
    @DisplayName("GET /api/v1/rankings")
    class GetDailyRanking {

        @Test
        @DisplayName("200 — 상품 정보 Aggregation 된 랭킹 Page 를 반환")
        void happyPath() {
            // given
            Brand brand = saveBrand();
            Product p1 = saveProduct(brand, "상품1", "Y");
            Product p2 = saveProduct(brand, "상품2", "Y");
            LocalDate today = LocalDate.now(clock);
            seedRanking(today, p1.getId(), 10.0);
            seedRanking(today, p2.getId(), 5.0);

            // when
            ResponseEntity<ApiResponse<RankingV1Dto.RankingPageResponse>> response = testRestTemplate.exchange(
                    ENDPOINT + "?date=" + today.format(YYYYMMDD) + "&page=1&size=20",
                    org.springframework.http.HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<>() {
                    }
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
            assertThat(body.date()).isEqualTo(today.format(YYYYMMDD));
        }

        @Test
        @DisplayName("숨김/삭제 상품은 응답에서 제외되어 size 가 축소된다")
        void visibilityFilter() {
            // given
            Brand brand = saveBrand();
            Product visible = saveProduct(brand, "visible", "Y");
            Product hidden = saveProduct(brand, "hidden", "N");
            LocalDate today = LocalDate.now(clock);
            seedRanking(today, visible.getId(), 10.0);
            seedRanking(today, hidden.getId(), 20.0);

            // when
            ResponseEntity<ApiResponse<RankingV1Dto.RankingPageResponse>> response = testRestTemplate.exchange(
                    ENDPOINT + "?date=" + today.format(YYYYMMDD) + "&page=1&size=20",
                    org.springframework.http.HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<>() {
                    }
            );

            // then — hidden 제외, visible 만 남음
            RankingV1Dto.RankingPageResponse body = response.getBody().data();
            assertThat(body.items()).hasSize(1);
            assertThat(body.items().get(0).productId()).isEqualTo(visible.getId());
        }

        @Test
        @DisplayName("과거 날짜의 랭킹도 ZSET 에 남아 있으면 조회 가능 (retention)")
        void pastDate() {
            // given
            Brand brand = saveBrand();
            Product product = saveProduct(brand, "상품", "Y");
            LocalDate yesterday = LocalDate.now(clock).minusDays(1);
            seedRanking(yesterday, product.getId(), 7.0);

            // when
            ResponseEntity<ApiResponse<RankingV1Dto.RankingPageResponse>> response = testRestTemplate.exchange(
                    ENDPOINT + "?date=" + yesterday.format(YYYYMMDD),
                    org.springframework.http.HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<>() {
                    }
            );

            // then
            RankingV1Dto.RankingPageResponse body = response.getBody().data();
            assertThat(body.items()).hasSize(1);
            assertThat(body.items().get(0).productId()).isEqualTo(product.getId());
        }

        @Test
        @DisplayName("date 파라미터 생략 시 오늘 랭킹을 조회")
        void defaultToday() {
            // given
            Brand brand = saveBrand();
            Product product = saveProduct(brand, "today", "Y");
            LocalDate today = LocalDate.now(clock);
            seedRanking(today, product.getId(), 10.0);

            // when
            ResponseEntity<ApiResponse<RankingV1Dto.RankingPageResponse>> response = testRestTemplate.exchange(
                    ENDPOINT,
                    org.springframework.http.HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<>() {
                    }
            );

            // then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            RankingV1Dto.RankingPageResponse body = response.getBody().data();
            assertThat(body.items()).hasSize(1);
            assertThat(body.date()).isEqualTo(today.format(YYYYMMDD));
        }

        @Test
        @DisplayName("잘못된 date 포맷은 400")
        void badDateFormat() {
            ResponseEntity<ApiResponse<RankingV1Dto.RankingPageResponse>> response = testRestTemplate.exchange(
                    ENDPOINT + "?date=2026-04-09",
                    org.springframework.http.HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<>() {
                    }
            );
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @Test
        @DisplayName("빈 랭킹은 items 빈 배열 + totalElements=0")
        void emptyRanking() {
            ResponseEntity<ApiResponse<RankingV1Dto.RankingPageResponse>> response = testRestTemplate.exchange(
                    ENDPOINT + "?date=" + LocalDate.now(clock).format(YYYYMMDD),
                    org.springframework.http.HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<>() {
                    }
            );

            RankingV1Dto.RankingPageResponse body = response.getBody().data();
            assertThat(body.items()).isEmpty();
            assertThat(body.totalElements()).isZero();
        }
    }
}

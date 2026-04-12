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
import com.loopers.interfaces.api.product.dto.ProductV1Dto;
import com.loopers.utils.DatabaseCleanUp;
import com.loopers.utils.RedisCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
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
 * 상품 상세 API 의 `dailyRank` 필드 E2E 검증 (week9.md §9 / 체크리스트).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("GET /api/v1/products/{id} — dailyRank 포함 E2E")
class ProductDetailDailyRankE2ETest {

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

    private Product saveProduct(Brand brand) {
        Product product = new Product(brand, "상품", 10000, 9000, 1000, 2500,
                "설명", MarginType.AMOUNT, ProductStatus.ON_SALE, "Y", List.of());
        return productJpaRepository.save(product);
    }

    private void seedRankingToday(Long productId, double score) {
        masterRedisTemplate.opsForZSet().add(RankingKey.daily(LocalDate.now(clock)), productId.toString(), score);
    }

    @Test
    @DisplayName("ZSET 에 존재하면 1-based dailyRank 가 응답에 포함된다")
    void withRank() {
        // given
        Brand brand = saveBrand();
        Product top = saveProduct(brand);
        Product other = saveProduct(brand);
        seedRankingToday(top.getId(), 10.0);
        seedRankingToday(other.getId(), 1.0);

        // when
        ResponseEntity<ApiResponse<ProductV1Dto.ProductDetailResponse>> response = testRestTemplate.exchange(
                "/api/v1/products/" + top.getId(),
                org.springframework.http.HttpMethod.GET,
                null,
                new ParameterizedTypeReference<>() {
                }
        );

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        ProductV1Dto.ProductDetailResponse body = response.getBody().data();
        assertThat(body.id()).isEqualTo(top.getId());
        assertThat(body.dailyRank()).isEqualTo(1L);
    }

    @Test
    @DisplayName("순위권 밖이면 dailyRank 는 null")
    void absentRank() {
        // given
        Brand brand = saveBrand();
        Product product = saveProduct(brand);
        // ZSET 에 시드하지 않음

        // when
        ResponseEntity<ApiResponse<ProductV1Dto.ProductDetailResponse>> response = testRestTemplate.exchange(
                "/api/v1/products/" + product.getId(),
                org.springframework.http.HttpMethod.GET,
                null,
                new ParameterizedTypeReference<>() {
                }
        );

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        ProductV1Dto.ProductDetailResponse body = response.getBody().data();
        assertThat(body.dailyRank()).isNull();
    }
}

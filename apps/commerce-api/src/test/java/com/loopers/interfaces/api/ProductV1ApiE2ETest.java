package com.loopers.interfaces.api;

import com.loopers.domain.brand.Brand;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.SellingStatus;
import com.loopers.infrastructure.brand.BrandJpaRepository;
import com.loopers.infrastructure.product.ProductJpaRepository;
import com.loopers.interfaces.api.product.ProductAdminV1Dto;
import com.loopers.interfaces.api.product.ProductV1Dto;
import com.loopers.utils.DatabaseCleanUp;
import com.loopers.utils.RedisCleanUp;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ProductV1ApiE2ETest {

    private static final String ADMIN_HEADER = "X-Loopers-Ldap";
    private static final String ADMIN_VALUE = "loopers.admin";

    @Autowired
    private TestRestTemplate testRestTemplate;

    @Autowired
    private BrandJpaRepository brandJpaRepository;

    @Autowired
    private ProductJpaRepository productJpaRepository;

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

    private HttpHeaders createAdminHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(ADMIN_HEADER, ADMIN_VALUE);
        return headers;
    }

    private Brand createBrand(String name) {
        return brandJpaRepository.save(new Brand(name, null));
    }

    private Product createProduct(Long brandId, String name, int price, int likeCount) {
        Product product = new Product(brandId, name, null, price, 10, SellingStatus.SELLING);
        for (int i = 0; i < likeCount; i++) {
            product.increaseLikes();
        }
        return productJpaRepository.save(product);
    }

    @DisplayName("GET /api/v1/products")
    @Nested
    class GetProducts {

        @DisplayName("인증 없이 요청해도 200 OK와 페이징된 상품 목록을 반환한다.")
        @Test
        void returns200WithPagedProducts_withoutAuth() {
            // arrange
            Brand brand = createBrand("Nike");
            createProduct(brand.getId(), "에어맥스", 100000, 0);
            createProduct(brand.getId(), "조던", 200000, 0);

            // act
            ResponseEntity<ApiResponse<ProductV1Dto.ProductPageResponse>> response = testRestTemplate.exchange(
                "/api/v1/products?page=0&size=10",
                HttpMethod.GET,
                new HttpEntity<>(null),
                new ParameterizedTypeReference<>() {}
            );

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody()).isNotNull(),
                () -> assertThat(response.getBody().data().content()).hasSize(2),
                () -> assertThat(response.getBody().data().totalElements()).isEqualTo(2)
            );
        }

        @DisplayName("sort=latest 파라미터로 조회하면, 최신 등록 순으로 정렬된다.")
        @Test
        void returns200WithLatestSort() {
            // arrange
            Brand brand = createBrand("Nike");
            Product older = createProduct(brand.getId(), "에어맥스", 100000, 0);
            Product newer = createProduct(brand.getId(), "조던", 200000, 0);

            // act
            ResponseEntity<ApiResponse<ProductV1Dto.ProductPageResponse>> response = testRestTemplate.exchange(
                "/api/v1/products?sort=latest&page=0&size=10",
                HttpMethod.GET,
                new HttpEntity<>(null),
                new ParameterizedTypeReference<>() {}
            );

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().data().content()).hasSize(2),
                () -> assertThat(response.getBody().data().content().get(0).id()).isEqualTo(newer.getId()),
                () -> assertThat(response.getBody().data().content().get(1).id()).isEqualTo(older.getId())
            );
        }

        @DisplayName("sort=price_asc 파라미터로 조회하면, 가격 오름차순으로 정렬된다.")
        @Test
        void returns200WithPriceAscSort() {
            // arrange
            Brand brand = createBrand("Nike");
            Product expensive = createProduct(brand.getId(), "조던", 200000, 0);
            Product cheap = createProduct(brand.getId(), "에어맥스", 50000, 0);

            // act
            ResponseEntity<ApiResponse<ProductV1Dto.ProductPageResponse>> response = testRestTemplate.exchange(
                "/api/v1/products?sort=price_asc&page=0&size=10",
                HttpMethod.GET,
                new HttpEntity<>(null),
                new ParameterizedTypeReference<>() {}
            );

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().data().content()).hasSize(2),
                () -> assertThat(response.getBody().data().content().get(0).id()).isEqualTo(cheap.getId()),
                () -> assertThat(response.getBody().data().content().get(1).id()).isEqualTo(expensive.getId())
            );
        }

        @DisplayName("sort=likes_desc 파라미터로 조회하면, 좋아요 내림차순으로 정렬된다.")
        @Test
        void returns200WithLikesDescSort() {
            // arrange
            Brand brand = createBrand("Nike");
            Product fewLikes = createProduct(brand.getId(), "에어맥스", 100000, 1);
            Product manyLikes = createProduct(brand.getId(), "조던", 200000, 5);

            // act
            ResponseEntity<ApiResponse<ProductV1Dto.ProductPageResponse>> response = testRestTemplate.exchange(
                "/api/v1/products?sort=likes_desc&page=0&size=10",
                HttpMethod.GET,
                new HttpEntity<>(null),
                new ParameterizedTypeReference<>() {}
            );

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().data().content()).hasSize(2),
                () -> assertThat(response.getBody().data().content().get(0).id()).isEqualTo(manyLikes.getId()),
                () -> assertThat(response.getBody().data().content().get(1).id()).isEqualTo(fewLikes.getId())
            );
        }

        @DisplayName("brandId 필터를 적용하면, 해당 브랜드의 상품만 반환한다.")
        @Test
        void returns200WithBrandIdFilter() {
            // arrange
            Brand nike = createBrand("Nike");
            Brand adidas = createBrand("Adidas");
            createProduct(nike.getId(), "에어맥스", 100000, 0);
            createProduct(adidas.getId(), "슈퍼스타", 80000, 0);

            // act
            ResponseEntity<ApiResponse<ProductV1Dto.ProductPageResponse>> response = testRestTemplate.exchange(
                "/api/v1/products?brandId=" + nike.getId() + "&page=0&size=10",
                HttpMethod.GET,
                new HttpEntity<>(null),
                new ParameterizedTypeReference<>() {}
            );

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().data().content()).hasSize(1),
                () -> assertThat(response.getBody().data().content().get(0).brandId()).isEqualTo(nike.getId())
            );
        }
    }

    @DisplayName("Cache-Aside")
    @Nested
    class ProductCacheE2E {

        @DisplayName("첫 페이지 조회 결과가 Redis에 캐시된다.")
        @Test
        void 첫_페이지_조회_결과가_Redis에_캐시된다() {
            // arrange
            Brand brand = createBrand("Nike");
            createProduct(brand.getId(), "에어맥스", 100000, 0);

            // act
            testRestTemplate.exchange(
                "/api/v1/products?page=0&size=20&sort=latest",
                HttpMethod.GET,
                new HttpEntity<>(null),
                new ParameterizedTypeReference<ApiResponse<ProductV1Dto.ProductPageResponse>>() {}
            );

            // assert
            String cacheKey = "product:list:all:latest:0:20";
            assertThat(redisTemplate.hasKey(cacheKey)).isTrue();
        }

        @DisplayName("캐시 적중 시 DB 변경 후에도 이전 결과를 반환한다.")
        @Test
        void 캐시_적중_시_DB_변경_후에도_이전_결과를_반환한다() {
            // arrange
            Brand brand = createBrand("Nike");
            createProduct(brand.getId(), "에어맥스", 100000, 0);

            ResponseEntity<ApiResponse<ProductV1Dto.ProductPageResponse>> first = testRestTemplate.exchange(
                "/api/v1/products?page=0&size=20&sort=latest",
                HttpMethod.GET,
                new HttpEntity<>(null),
                new ParameterizedTypeReference<>() {}
            );
            int originalSize = first.getBody().data().content().size();

            // DB에 새 상품 추가
            createProduct(brand.getId(), "조던", 200000, 0);

            // act: 두 번째 조회 (캐시 적중)
            ResponseEntity<ApiResponse<ProductV1Dto.ProductPageResponse>> second = testRestTemplate.exchange(
                "/api/v1/products?page=0&size=20&sort=latest",
                HttpMethod.GET,
                new HttpEntity<>(null),
                new ParameterizedTypeReference<>() {}
            );

            // assert: 캐시된 결과(이전 상품 수) 반환
            assertThat(second.getBody().data().content()).hasSize(originalSize);
        }

        @DisplayName("2페이지 이상은 캐시하지 않는다.")
        @Test
        void 두번째_페이지_이상은_캐시하지_않는다() {
            // arrange
            Brand brand = createBrand("Nike");
            createProduct(brand.getId(), "에어맥스", 100000, 0);

            // act
            testRestTemplate.exchange(
                "/api/v1/products?page=1&size=20&sort=latest",
                HttpMethod.GET,
                new HttpEntity<>(null),
                new ParameterizedTypeReference<ApiResponse<ProductV1Dto.ProductPageResponse>>() {}
            );

            // assert: page=1 은 캐시 키 없음
            String cacheKey = "product:list:all:latest:1:20";
            assertThat(redisTemplate.hasKey(cacheKey)).isFalse();
            assertThat(redisTemplate.keys("product:list:*")).isEmpty();
        }

        @DisplayName("어드민 상품 삭제 시 캐시가 무효화된다.")
        @Test
        void 어드민_상품_삭제_시_캐시가_무효화된다() {
            // arrange: 상품 생성 후 첫 페이지 조회로 캐시 적재
            Brand brand = createBrand("Nike");
            Product product = createProduct(brand.getId(), "에어맥스", 100000, 0);

            testRestTemplate.exchange(
                "/api/v1/products?page=0&size=20&sort=latest",
                HttpMethod.GET,
                new HttpEntity<>(null),
                new ParameterizedTypeReference<ApiResponse<ProductV1Dto.ProductPageResponse>>() {}
            );
            assertThat(redisTemplate.keys("product:list:*")).isNotEmpty();

            // act: 어드민 삭제
            testRestTemplate.exchange(
                "/api-admin/v1/products/" + product.getId(),
                HttpMethod.DELETE,
                new HttpEntity<>(createAdminHeaders()),
                Void.class
            );

            // assert: 캐시 무효화 확인
            assertThat(redisTemplate.keys("product:list:*")).isEmpty();

            // 재조회 시 삭제된 상품 반영
            ResponseEntity<ApiResponse<ProductV1Dto.ProductPageResponse>> response = testRestTemplate.exchange(
                "/api/v1/products?page=0&size=20&sort=latest",
                HttpMethod.GET,
                new HttpEntity<>(null),
                new ParameterizedTypeReference<>() {}
            );
            assertThat(response.getBody().data().content()).isEmpty();
        }
    }

    @DisplayName("GET /api/v1/products/{productId}")
    @Nested
    class GetProduct {

        @DisplayName("존재하는 productId로 조회하면, 200 OK와 상품 정보를 반환한다.")
        @Test
        void returns200_whenProductExists() {
            // arrange
            Brand brand = createBrand("Nike");
            Product product = createProduct(brand.getId(), "에어맥스", 100000, 0);

            // act
            ResponseEntity<ApiResponse<ProductV1Dto.ProductResponse>> response = testRestTemplate.exchange(
                "/api/v1/products/" + product.getId(),
                HttpMethod.GET,
                new HttpEntity<>(null),
                new ParameterizedTypeReference<>() {}
            );

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody()).isNotNull(),
                () -> assertThat(response.getBody().data().id()).isEqualTo(product.getId()),
                () -> assertThat(response.getBody().data().name()).isEqualTo("에어맥스"),
                () -> assertThat(response.getBody().data().price()).isEqualTo(100000)
            );
        }

        @DisplayName("존재하지 않는 productId로 조회하면, 404 Not Found를 반환한다.")
        @Test
        void returns404_whenProductNotFound() {
            // arrange & act
            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                "/api/v1/products/99999",
                HttpMethod.GET,
                new HttpEntity<>(null),
                new ParameterizedTypeReference<>() {}
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @DisplayName("상품 상세 조회 결과가 Redis에 캐시된다.")
        @Test
        void 상품_상세_조회_결과가_Redis에_캐시된다() {
            // arrange
            Brand brand = createBrand("Nike");
            Product product = createProduct(brand.getId(), "에어맥스", 100000, 0);

            // act
            testRestTemplate.exchange(
                "/api/v1/products/" + product.getId(),
                HttpMethod.GET,
                new HttpEntity<>(null),
                new ParameterizedTypeReference<ApiResponse<ProductV1Dto.ProductResponse>>() {}
            );

            // assert
            String cacheKey = "product:detail:" + product.getId();
            assertThat(redisTemplate.hasKey(cacheKey)).isTrue();
        }

        @DisplayName("캐시 적중 시 DB 변경 후에도 이전 결과를 반환한다.")
        @Test
        void 캐시_적중_시_DB_변경_후에도_이전_결과를_반환한다() {
            // arrange
            Brand brand = createBrand("Nike");
            Product product = createProduct(brand.getId(), "에어맥스", 100000, 0);

            testRestTemplate.exchange(
                "/api/v1/products/" + product.getId(),
                HttpMethod.GET,
                new HttpEntity<>(null),
                new ParameterizedTypeReference<ApiResponse<ProductV1Dto.ProductResponse>>() {}
            );

            // DB 직접 수정 (캐시 우회)
            Product saved = productJpaRepository.findById(product.getId()).orElseThrow();
            saved.changeProductInfo("조던", null, 200000, SellingStatus.SELLING);
            productJpaRepository.save(saved);

            // act: 두 번째 조회 (캐시 적중)
            ResponseEntity<ApiResponse<ProductV1Dto.ProductResponse>> response = testRestTemplate.exchange(
                "/api/v1/products/" + product.getId(),
                HttpMethod.GET,
                new HttpEntity<>(null),
                new ParameterizedTypeReference<>() {}
            );

            // assert: 캐시된 이전 이름이 반환됨
            assertThat(response.getBody().data().name()).isEqualTo("에어맥스");
        }

        @DisplayName("어드민 상품 수정 시 상세 캐시가 무효화된다.")
        @Test
        void 어드민_상품_수정_시_상세_캐시가_무효화된다() {
            // arrange: 조회로 캐시 적재
            Brand brand = createBrand("Nike");
            Product product = createProduct(brand.getId(), "에어맥스", 100000, 0);

            testRestTemplate.exchange(
                "/api/v1/products/" + product.getId(),
                HttpMethod.GET,
                new HttpEntity<>(null),
                new ParameterizedTypeReference<ApiResponse<ProductV1Dto.ProductResponse>>() {}
            );
            assertThat(redisTemplate.hasKey("product:detail:" + product.getId())).isTrue();

            // act: 어드민 상품 수정
            ProductAdminV1Dto.UpdateRequest updateRequest = new ProductAdminV1Dto.UpdateRequest(
                "조던", null, 200000, SellingStatus.SELLING
            );
            HttpHeaders headers = createAdminHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            testRestTemplate.exchange(
                "/api-admin/v1/products/" + product.getId(),
                HttpMethod.PATCH,
                new HttpEntity<>(updateRequest, headers),
                Void.class
            );

            // assert: 캐시 무효화 후 재조회 시 변경된 데이터 반영
            ResponseEntity<ApiResponse<ProductV1Dto.ProductResponse>> response = testRestTemplate.exchange(
                "/api/v1/products/" + product.getId(),
                HttpMethod.GET,
                new HttpEntity<>(null),
                new ParameterizedTypeReference<>() {}
            );
            assertThat(response.getBody().data().name()).isEqualTo("조던");
        }

        @DisplayName("랭킹 ZSET에 점수가 있으면 상품 상세 조회 시 rank가 반환된다.")
        @Test
        void returnsRank_whenProductHasRankingScore() {
            // arrange
            Brand brand = createBrand("Nike");
            Product product1 = createProduct(brand.getId(), "에어맥스", 100000, 0);
            Product product2 = createProduct(brand.getId(), "조던", 200000, 0);

            String rankingKey = "ranking:all:" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
            redisTemplate.opsForZSet().add(rankingKey, String.valueOf(product1.getId()), 1.0);
            redisTemplate.opsForZSet().add(rankingKey, String.valueOf(product2.getId()), 0.5);

            // act
            ResponseEntity<ApiResponse<ProductV1Dto.ProductResponse>> response = testRestTemplate.exchange(
                "/api/v1/products/" + product1.getId(),
                HttpMethod.GET,
                new HttpEntity<>(null),
                new ParameterizedTypeReference<>() {}
            );

            // assert: product1은 점수가 높아 1위
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().data().rank()).isEqualTo(1)
            );
        }

        @DisplayName("랭킹 ZSET에 점수가 없으면 상품 상세 조회 시 rank가 null로 반환된다.")
        @Test
        void returnsNullRank_whenProductHasNoRankingScore() {
            // arrange
            Brand brand = createBrand("Nike");
            Product product = createProduct(brand.getId(), "에어맥스", 100000, 0);
            // ZSET에 데이터 없음

            // act
            ResponseEntity<ApiResponse<ProductV1Dto.ProductResponse>> response = testRestTemplate.exchange(
                "/api/v1/products/" + product.getId(),
                HttpMethod.GET,
                new HttpEntity<>(null),
                new ParameterizedTypeReference<>() {}
            );

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().data().rank()).isNull()
            );
        }

        @DisplayName("어드민 상품 삭제 시 상세 캐시가 무효화된다.")
        @Test
        void 어드민_상품_삭제_시_상세_캐시가_무효화된다() {
            // arrange: 조회로 캐시 적재
            Brand brand = createBrand("Nike");
            Product product = createProduct(brand.getId(), "에어맥스", 100000, 0);

            testRestTemplate.exchange(
                "/api/v1/products/" + product.getId(),
                HttpMethod.GET,
                new HttpEntity<>(null),
                new ParameterizedTypeReference<ApiResponse<ProductV1Dto.ProductResponse>>() {}
            );
            assertThat(redisTemplate.hasKey("product:detail:" + product.getId())).isTrue();

            // act: 어드민 상품 삭제
            testRestTemplate.exchange(
                "/api-admin/v1/products/" + product.getId(),
                HttpMethod.DELETE,
                new HttpEntity<>(createAdminHeaders()),
                Void.class
            );

            // assert: 상세 캐시 키 삭제 확인
            assertThat(redisTemplate.hasKey("product:detail:" + product.getId())).isFalse();
        }
    }
}

package com.loopers.interfaces.api;

import com.loopers.domain.brand.Brand;
import com.loopers.domain.product.Product;
import com.loopers.infrastructure.brand.BrandJpaRepository;
import com.loopers.infrastructure.product.ProductJpaRepository;
import com.loopers.interfaces.api.product.dto.ProductV1Dto;
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
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assumptions.assumeThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ProductCacheE2ETest {

    private static final String PRODUCT_ENDPOINT = "/api/v1/products";
    private static final String ADMIN_PRODUCT_ENDPOINT = "/api-admin/v1/products";
    private static final String LDAP_HEADER = "X-Loopers-Ldap";
    private static final String LDAP_VALUE = "loopers.admin";

    private final TestRestTemplate testRestTemplate;
    private final BrandJpaRepository brandJpaRepository;
    private final ProductJpaRepository productJpaRepository;
    private final DatabaseCleanUp databaseCleanUp;
    private final RedisCleanUp redisCleanUp;

    @Autowired
    public ProductCacheE2ETest(
            TestRestTemplate testRestTemplate,
            BrandJpaRepository brandJpaRepository,
            ProductJpaRepository productJpaRepository,
            DatabaseCleanUp databaseCleanUp,
            RedisCleanUp redisCleanUp
    ) {
        this.testRestTemplate = testRestTemplate;
        this.brandJpaRepository = brandJpaRepository;
        this.productJpaRepository = productJpaRepository;
        this.databaseCleanUp = databaseCleanUp;
        this.redisCleanUp = redisCleanUp;
    }

    @AfterEach
    void tearDown() {
        try {
            databaseCleanUp.truncateAllTables();
        } finally {
            redisCleanUp.truncateAll();
        }
    }

    private HttpHeaders adminHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(LDAP_HEADER, LDAP_VALUE);
        return headers;
    }

    private Brand saveBrand(String name) {
        return brandJpaRepository.save(Brand.create(name, null));
    }

    private Product saveProduct(Long brandId, String name, int price, int stock) {
        return productJpaRepository.save(Product.create(brandId, name, null, price, stock));
    }

    @DisplayName("상품 상세 캐시")
    @Nested
    class ProductDetailCache {

        @DisplayName("캐시 히트: 캐시된 상품은 DB가 변경되어도 캐시된 데이터를 반환한다.")
        @Test
        void returnsCachedProduct_evenAfterDbSoftDelete() {
            // arrange
            Brand brand = saveBrand("나이키");
            Product saved = saveProduct(brand.getId(), "에어맥스", 150000, 10);

            // 첫 번째 조회 - 캐시 미스 → Redis에 저장
            testRestTemplate.exchange(
                    PRODUCT_ENDPOINT + "/" + saved.getId(),
                    HttpMethod.GET, null,
                    new ParameterizedTypeReference<ApiResponse<ProductV1Dto.ProductResponse>>() {}
            );

            // DB에서 직접 soft-delete (캐시 eviction 없이 DB 상태 변경)
            saved.delete();
            productJpaRepository.save(saved);

            // act - 두 번째 조회: 캐시 히트 → Redis에서 반환
            ResponseEntity<ApiResponse<ProductV1Dto.ProductResponse>> response =
                    testRestTemplate.exchange(
                            PRODUCT_ENDPOINT + "/" + saved.getId(),
                            HttpMethod.GET, null,
                            new ParameterizedTypeReference<>() {}
                    );

            // assert
            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody().data().id()).isEqualTo(saved.getId()),
                    () -> assertThat(response.getBody().data().name()).isEqualTo("에어맥스")
            );
        }

        @DisplayName("캐시 무효화: 상품 삭제 API 호출 후, 동일 상품 조회 시 404를 반환한다.")
        @Test
        void returnsNotFound_afterCacheEvictedByDeleteApi() {
            // arrange
            Brand brand = saveBrand("나이키");
            Product saved = saveProduct(brand.getId(), "에어맥스", 150000, 10);

            // 첫 번째 조회 - 캐시 미스 → Redis에 저장
            testRestTemplate.exchange(
                    PRODUCT_ENDPOINT + "/" + saved.getId(),
                    HttpMethod.GET, null,
                    new ParameterizedTypeReference<ApiResponse<ProductV1Dto.ProductResponse>>() {}
            );

            // act - Admin API로 삭제 → @CacheEvict 발동
            testRestTemplate.exchange(
                    ADMIN_PRODUCT_ENDPOINT + "/" + saved.getId(),
                    HttpMethod.DELETE,
                    new HttpEntity<>(adminHeaders()),
                    new ParameterizedTypeReference<ApiResponse<Void>>() {}
            );

            // 두 번째 조회: 캐시 무효화 → DB 재조회 → 삭제된 상품 → 404
            ResponseEntity<ApiResponse<Void>> response =
                    testRestTemplate.exchange(
                            PRODUCT_ENDPOINT + "/" + saved.getId(),
                            HttpMethod.GET, null,
                            new ParameterizedTypeReference<>() {}
                    );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    @DisplayName("상품 목록 캐시")
    @Nested
    class ProductListCache {

        @DisplayName("캐시 히트: 캐시된 목록은 DB가 변경되어도 캐시된 데이터를 반환한다.")
        @Test
        void returnsCachedProducts_evenAfterDbSoftDelete() {
            // arrange
            Brand brand = saveBrand("나이키");
            Product saved = saveProduct(brand.getId(), "에어맥스", 150000, 10);

            // 첫 번째 조회 - 캐시 미스 → Redis에 저장
            testRestTemplate.exchange(
                    PRODUCT_ENDPOINT,
                    HttpMethod.GET, null,
                    new ParameterizedTypeReference<ApiResponse<PageResponse<ProductV1Dto.ProductResponse>>>() {}
            );

            // DB에서 직접 soft-delete (캐시 eviction 없이 DB 상태 변경)
            saved.delete();
            productJpaRepository.save(saved);

            // act - 두 번째 조회: 캐시 히트 → Redis에서 반환
            ResponseEntity<ApiResponse<PageResponse<ProductV1Dto.ProductResponse>>> response =
                    testRestTemplate.exchange(
                            PRODUCT_ENDPOINT,
                            HttpMethod.GET, null,
                            new ParameterizedTypeReference<>() {}
                    );

            // assert
            List<Long> ids = response.getBody().data().content().stream()
                                     .map(ProductV1Dto.ProductResponse::id)
                                     .toList();
            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(ids).contains(saved.getId())
            );
        }

        @DisplayName("캐시 무효화: 상품 등록 API 호출 후, 목록 재조회 시 신규 상품이 즉시 포함된다.")
        @Test
        void includesNewProduct_afterCacheEvictedByRegisterApi() {
            // arrange
            Brand brand = saveBrand("나이키");
            Product productA = saveProduct(brand.getId(), "에어맥스", 150000, 10);

            // 첫 번째 조회 - 캐시 미스 → Redis에 저장 (productA만 포함)
            testRestTemplate.exchange(
                    PRODUCT_ENDPOINT,
                    HttpMethod.GET, null,
                    new ParameterizedTypeReference<ApiResponse<PageResponse<ProductV1Dto.ProductResponse>>>() {}
            );

            // DB에 직접 productB 저장 (캐시 eviction 없이 DB 상태 변경)
            Product productB = saveProduct(brand.getId(), "조던", 200000, 5);

            // 캐시 히트 전제 확인: productB는 DB에 있지만 캐시에는 없으므로 목록에 나타나지 않아야 함
            ResponseEntity<ApiResponse<PageResponse<ProductV1Dto.ProductResponse>>> cachedResponse =
                    testRestTemplate.exchange(
                            PRODUCT_ENDPOINT,
                            HttpMethod.GET, null,
                            new ParameterizedTypeReference<>() {}
                    );
            List<Long> cachedIds = cachedResponse.getBody().data().content().stream()
                                                 .map(ProductV1Dto.ProductResponse::id)
                                                 .toList();
            assumeThat(cachedIds).doesNotContain(productB.getId());

            // act - Admin API로 productC 등록 → @CacheEvict(allEntries=true) 발동
            Map<String, Object> createRequest = Map.of(
                    "brandId", brand.getId(),
                    "name", "나이키 SB",
                    "price", 120000,
                    "stockQuantity", 3
            );
            ResponseEntity<ApiResponse<ProductV1Dto.AdminProductResponse>> registerResponse =
                    testRestTemplate.exchange(
                            ADMIN_PRODUCT_ENDPOINT,
                            HttpMethod.POST,
                            new HttpEntity<>(createRequest, adminHeaders()),
                            new ParameterizedTypeReference<>() {}
                    );
            Long productCId = registerResponse.getBody().data().id();

            // 캐시 무효화 후 재조회: DB 재조회 → productA, productB, productC 모두 포함
            ResponseEntity<ApiResponse<PageResponse<ProductV1Dto.ProductResponse>>> response =
                    testRestTemplate.exchange(
                            PRODUCT_ENDPOINT,
                            HttpMethod.GET, null,
                            new ParameterizedTypeReference<>() {}
                    );

            // assert
            List<Long> ids = response.getBody().data().content().stream()
                                     .map(ProductV1Dto.ProductResponse::id)
                                     .toList();
            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(ids).contains(productA.getId()),
                    () -> assertThat(ids).contains(productB.getId()), // evict 전엔 안 보였던 상품이 이제 보임
                    () -> assertThat(ids).contains(productCId)        // 방금 등록한 상품도 포함
            );
        }

        @DisplayName("캐시 무효화: 상품 삭제 API 호출 후, 목록 재조회 시 해당 상품이 제외된다.")
        @Test
        void excludesDeletedProduct_afterCacheEvictedByDeleteApi() {
            // arrange
            Brand brand = saveBrand("나이키");
            Product toDelete = saveProduct(brand.getId(), "에어맥스", 150000, 10);
            Product remaining = saveProduct(brand.getId(), "조던", 200000, 5);

            // 첫 번째 조회 - 캐시 미스 → Redis에 저장
            testRestTemplate.exchange(
                    PRODUCT_ENDPOINT,
                    HttpMethod.GET, null,
                    new ParameterizedTypeReference<ApiResponse<PageResponse<ProductV1Dto.ProductResponse>>>() {}
            );

            // act - Admin API로 삭제 → @CacheEvict(allEntries=true) 발동
            testRestTemplate.exchange(
                    ADMIN_PRODUCT_ENDPOINT + "/" + toDelete.getId(),
                    HttpMethod.DELETE,
                    new HttpEntity<>(adminHeaders()),
                    new ParameterizedTypeReference<ApiResponse<Void>>() {}
            );

            // 두 번째 조회: 캐시 무효화 → DB 재조회
            ResponseEntity<ApiResponse<PageResponse<ProductV1Dto.ProductResponse>>> response =
                    testRestTemplate.exchange(
                            PRODUCT_ENDPOINT,
                            HttpMethod.GET, null,
                            new ParameterizedTypeReference<>() {}
                    );

            // assert
            List<Long> ids = response.getBody().data().content().stream()
                                     .map(ProductV1Dto.ProductResponse::id)
                                     .toList();
            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(ids).doesNotContain(toDelete.getId()),
                    () -> assertThat(ids).contains(remaining.getId())
            );
        }
    }
}

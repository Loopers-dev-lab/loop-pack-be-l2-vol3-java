package com.loopers.application.product;

import com.loopers.application.like.LikeApp;
import com.loopers.domain.brand.BrandService;
import com.loopers.domain.product.ProductService;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@DisplayName("상품 캐시 히트/미스/무효화 통합 테스트")
class ProductCacheIntegrationTest {

    @Autowired
    private ProductApp productApp;

    @Autowired
    private LikeApp likeApp;

    @Autowired
    private ProductService productService;

    @Autowired
    private BrandService brandService;

    @Autowired
    private CacheManager cacheManager;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private Cache productCache() {
        return cacheManager.getCache("product");
    }

    private Cache productsCache() {
        return cacheManager.getCache("products");
    }

    @Test
    @DisplayName("상품 상세 첫 조회 시 캐시에 저장된다")
    void getProduct_firstCall_storesInCache() {
        brandService.createBrand("nike", "Nike");
        productService.createProduct("prod1", "nike", "Nike Air", new BigDecimal("100000"), 10);

        assertThat(productCache().get("prod1")).isNull();

        productApp.getProduct("prod1", null);

        assertThat(productCache().get("prod1")).isNotNull();
    }

    @Test
    @DisplayName("상품 상세 재조회 시 캐시에서 반환된다 (캐시 히트)")
    void getProduct_secondCall_returnsFromCache() {
        brandService.createBrand("nike", "Nike");
        productService.createProduct("prod1", "nike", "Nike Air", new BigDecimal("100000"), 10);

        ProductInfo first = productApp.getProduct("prod1", null);
        ProductInfo second = productApp.getProduct("prod1", null);

        assertThat(second).isEqualTo(first);
    }

    @Test
    @DisplayName("상품 수정 시 상품 상세 캐시가 무효화된다")
    void updateProduct_evictsProductCache() {
        brandService.createBrand("nike", "Nike");
        productService.createProduct("prod1", "nike", "Nike Air", new BigDecimal("100000"), 10);
        productApp.getProduct("prod1", null);
        assertThat(productCache().get("prod1")).isNotNull();

        productApp.updateProduct("prod1", "Nike Air Updated", new BigDecimal("120000"), 10);

        assertThat(productCache().get("prod1")).isNull();
    }

    @Test
    @DisplayName("상품 삭제 시 상품 상세 캐시가 무효화된다")
    void deleteProduct_evictsProductCache() {
        brandService.createBrand("nike", "Nike");
        productService.createProduct("prod1", "nike", "Nike Air", new BigDecimal("100000"), 10);
        productApp.getProduct("prod1", null);
        assertThat(productCache().get("prod1")).isNotNull();

        productApp.deleteProduct("prod1");

        assertThat(productCache().get("prod1")).isNull();
    }

    @Test
    @DisplayName("상품 목록 첫 조회 시 캐시에 저장된다")
    void getProducts_firstCall_storesInCache() {
        brandService.createBrand("nike", "Nike");
        productService.createProduct("prod1", "nike", "Nike Air", new BigDecimal("100000"), 10);

        String cacheKey = "null:latest:0:10";
        assertThat(productsCache().get(cacheKey)).isNull();

        productApp.getProducts(null, "latest", PageRequest.of(0, 10));

        assertThat(productsCache().get(cacheKey)).isNotNull();
    }

    @Test
    @DisplayName("좋아요 추가 시 상품 상세와 목록 캐시가 무효화된다")
    void addLike_evictsProductAndProductsCache() {
        brandService.createBrand("nike", "Nike");
        productService.createProduct("prod1", "nike", "Nike Air", new BigDecimal("100000"), 10);
        productApp.getProduct("prod1", null);
        productApp.getProducts(null, "latest", PageRequest.of(0, 10));
        assertThat(productCache().get("prod1")).isNotNull();
        assertThat(productsCache().get("null:latest:0:10")).isNotNull();

        likeApp.addLike(1L, "prod1");

        assertThat(productCache().get("prod1")).isNull();
        assertThat(productsCache().get("null:latest:0:10")).isNull();
    }

    @Test
    @DisplayName("좋아요 취소 시 상품 상세와 목록 캐시가 무효화된다")
    void removeLike_evictsProductAndProductsCache() {
        brandService.createBrand("nike", "Nike");
        productService.createProduct("prod1", "nike", "Nike Air", new BigDecimal("100000"), 10);
        likeApp.addLike(1L, "prod1");
        productApp.getProduct("prod1", null);
        productApp.getProducts(null, "latest", PageRequest.of(0, 10));
        assertThat(productCache().get("prod1")).isNotNull();
        assertThat(productsCache().get("null:latest:0:10")).isNotNull();

        likeApp.removeLike(1L, "prod1");

        assertThat(productCache().get("prod1")).isNull();
        assertThat(productsCache().get("null:latest:0:10")).isNull();
    }
}

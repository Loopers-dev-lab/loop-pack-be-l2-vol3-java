package com.loopers.application.cache;

import com.loopers.application.product.ProductFacade;
import com.loopers.application.product.ProductAdminFacade;
import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.domain.brand.BrandStatus;
import com.loopers.domain.product.ProductSortType;
import com.loopers.utils.DatabaseCleanUp;
import com.loopers.utils.RedisCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class ProductCacheIntegrationTest {

    @Autowired
    private ProductFacade productFacade;

    @Autowired
    private ProductAdminFacade productAdminFacade;

    @Autowired
    private BrandRepository brandRepository;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @Autowired
    private RedisCleanUp redisCleanUp;

    private Brand activeBrand;

    @BeforeEach
    void setUp() {
        activeBrand = brandRepository.save(Brand.register("테스트브랜드", "설명"));
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
        redisCleanUp.truncateAll();
    }

    @Nested
    @DisplayName("상품 상세 캐시")
    class ProductDetailCache {

        @Test
        void 상품_상세_조회_시_캐시에_저장된다() {
            // arrange
            var created = productAdminFacade.createProduct(
                    activeBrand.getId(), "에어맥스", "설명", 150000, 100);
            Long productId = created.product().id();

            // 생성 시 afterCommit으로 캐시가 삭제되므로, 잠시 대기 후 조회
            waitForCacheEviction();

            // act
            productFacade.getProductDetail(productId);

            // assert
            String key = "products:detail:" + productId;
            String cached = redisTemplate.opsForValue().get(key);
            assertThat(cached).isNotNull();
            assertThat(cached).contains("에어맥스");
            assertThat(cached).contains("150000");
        }

        @Test
        void 캐시_히트_시_동일한_결과를_반환한다() {
            // arrange
            var created = productAdminFacade.createProduct(
                    activeBrand.getId(), "에어맥스", "설명", 150000, 100);
            Long productId = created.product().id();
            waitForCacheEviction();

            // act — 첫 조회 (cache miss → DB → cache put)
            var firstResult = productFacade.getProductDetail(productId);
            // act — 두 번째 조회 (cache hit)
            var secondResult = productFacade.getProductDetail(productId);

            // assert
            assertThat(secondResult.product().id()).isEqualTo(firstResult.product().id());
            assertThat(secondResult.product().name()).isEqualTo(firstResult.product().name());
            assertThat(secondResult.brand().name()).isEqualTo(firstResult.brand().name());
        }

        @Test
        void 상품_수정_시_캐시가_삭제된다() {
            // arrange
            var created = productAdminFacade.createProduct(
                    activeBrand.getId(), "에어맥스", "설명", 150000, 100);
            Long productId = created.product().id();
            waitForCacheEviction();

            productFacade.getProductDetail(productId); // 캐시 저장
            String key = "products:detail:" + productId;
            assertThat(redisTemplate.opsForValue().get(key)).isNotNull();

            // act — 상품 수정 (afterCommit 캐시 삭제 트리거)
            productAdminFacade.updateProduct(productId, "에어포스", null, null);
            waitForCacheEviction();

            // assert — 캐시 삭제됨
            assertThat(redisTemplate.opsForValue().get(key)).isNull();
        }

        @Test
        void 상품_삭제_시_캐시가_삭제된다() {
            // arrange
            var created = productAdminFacade.createProduct(
                    activeBrand.getId(), "에어맥스", "설명", 150000, 100);
            Long productId = created.product().id();
            waitForCacheEviction();

            productFacade.getProductDetail(productId); // 캐시 저장
            String key = "products:detail:" + productId;
            assertThat(redisTemplate.opsForValue().get(key)).isNotNull();

            // act
            productAdminFacade.deleteProduct(productId);
            waitForCacheEviction();

            // assert
            assertThat(redisTemplate.opsForValue().get(key)).isNull();
        }
    }

    @Nested
    @DisplayName("상품 목록 캐시")
    class ProductListCache {

        @Test
        void 첫_페이지_조회_시_캐시에_저장된다() {
            // arrange
            productAdminFacade.createProduct(activeBrand.getId(), "상품1", "설명", 10000, 100);
            productAdminFacade.createProduct(activeBrand.getId(), "상품2", "설명", 20000, 100);
            waitForCacheEviction();

            // act
            productFacade.getDisplayableProductsWithCursor(null, ProductSortType.LATEST, null, 20);

            // assert
            String key = "products:list:LATEST:all";
            String cached = redisTemplate.opsForValue().get(key);
            assertThat(cached).isNotNull();
            assertThat(cached).contains("상품1");
            assertThat(cached).contains("상품2");
        }

        @Test
        void 브랜드_필터_조회_시_브랜드별_키로_캐싱된다() {
            // arrange
            productAdminFacade.createProduct(activeBrand.getId(), "상품1", "설명", 10000, 100);
            waitForCacheEviction();

            // act
            productFacade.getDisplayableProductsWithCursor(
                    activeBrand.getId(), ProductSortType.PRICE_ASC, null, 20);

            // assert
            String key = "products:list:PRICE_ASC:" + activeBrand.getId();
            assertThat(redisTemplate.opsForValue().get(key)).isNotNull();
        }

        @Test
        void 상품_생성_시_목록_캐시가_삭제된다() {
            // arrange
            productAdminFacade.createProduct(activeBrand.getId(), "상품1", "설명", 10000, 100);
            waitForCacheEviction();

            productFacade.getDisplayableProductsWithCursor(null, ProductSortType.LATEST, null, 20);
            assertThat(redisTemplate.opsForValue().get("products:list:LATEST:all")).isNotNull();

            // act — 새 상품 생성 (목록 캐시 무효화)
            productAdminFacade.createProduct(activeBrand.getId(), "상품2", "설명", 20000, 100);
            waitForCacheEviction();

            // assert — 목록 캐시 삭제됨
            assertThat(redisTemplate.opsForValue().get("products:list:LATEST:all")).isNull();
        }

        @Test
        void 정렬_타입별로_별도_캐시가_생성된다() {
            // arrange
            productAdminFacade.createProduct(activeBrand.getId(), "상품1", "설명", 10000, 100);
            waitForCacheEviction();

            // act
            productFacade.getDisplayableProductsWithCursor(null, ProductSortType.LATEST, null, 20);
            productFacade.getDisplayableProductsWithCursor(null, ProductSortType.PRICE_ASC, null, 20);

            // assert
            assertThat(redisTemplate.opsForValue().get("products:list:LATEST:all")).isNotNull();
            assertThat(redisTemplate.opsForValue().get("products:list:PRICE_ASC:all")).isNotNull();
            assertThat(redisTemplate.opsForValue().get("products:list:LIKES_DESC:all")).isNull();
        }
    }

    /**
     * afterCommit 캐시 삭제가 완료될 때까지 대기.
     * 트랜잭션 커밋 후 비동기 처리 여유를 위해 짧은 대기.
     */
    private void waitForCacheEviction() {
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}

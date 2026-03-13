package com.loopers.application.product;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.like.LikeService;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductSortType;
import com.loopers.domain.product.ProductStatus;
import com.loopers.infrastructure.brand.BrandJpaRepository;
import com.loopers.infrastructure.product.ProductJpaRepository;
import com.loopers.utils.DatabaseCleanUp;
import com.loopers.utils.RedisCleanUp;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.data.domain.PageRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest
@DisplayName("상품 캐시 전략 검증")
class ProductCacheTest {

    @Autowired
    private ProductFacade productFacade;

    @Autowired
    private LikeService likeService;

    @Autowired
    private ProductJpaRepository productJpaRepository;

    @Autowired
    private BrandJpaRepository brandJpaRepository;

    @Autowired
    private CacheManager cacheManager;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @Autowired
    private RedisCleanUp redisCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
        redisCleanUp.truncateAll();
    }

    @DisplayName("상품 상세 캐시")
    @Nested
    class ProductDetailCache {

        @DisplayName("첫 번째 조회 시 캐시 미스가 발생하고, 두 번째 조회 시 캐시 히트로 동일한 결과를 반환한다.")
        @Test
        void returnsCachedResult_onSecondCall() {
            // arrange
            BrandModel brand = brandJpaRepository.save(new BrandModel("나이키", "스포츠 브랜드"));
            ProductModel product = productJpaRepository.save(
                new ProductModel(brand, "에어맥스", 150000L, "설명", 100, ProductStatus.ON_SALE)
            );

            // act - 첫 번째 조회 (캐시 미스)
            ProductInfo firstCall = productFacade.getProduct(product.getId());

            // act - 두 번째 조회 (캐시 히트)
            ProductInfo secondCall = productFacade.getProduct(product.getId());

            // assert
            assertAll(
                () -> assertThat(firstCall.id()).isEqualTo(secondCall.id()),
                () -> assertThat(firstCall.name()).isEqualTo(secondCall.name()),
                () -> assertThat(firstCall.likeCount()).isEqualTo(secondCall.likeCount()),
                () -> assertThat(cacheManager.getCache(ProductFacade.PRODUCT_DETAIL_CACHE)).isNotNull()
            );
        }

        @DisplayName("상품 수정 후 캐시가 무효화되어 갱신된 데이터를 반환한다.")
        @Test
        void returnsFreshData_afterProductUpdate() {
            // arrange
            BrandModel brand = brandJpaRepository.save(new BrandModel("나이키", "스포츠 브랜드"));
            ProductModel product = productJpaRepository.save(
                new ProductModel(brand, "에어맥스", 150000L, "설명", 100, ProductStatus.ON_SALE)
            );
            productFacade.getProduct(product.getId()); // 캐시 적재

            // act - 상품 수정 (캐시 무효화 발생)
            productFacade.update(product.getId(), brand.getId(), "에어포스", 120000L, "새 설명", 50, ProductStatus.ON_SALE);

            // act - 수정 후 조회
            ProductInfo afterUpdate = productFacade.getProduct(product.getId());

            // assert
            assertAll(
                () -> assertThat(afterUpdate.name()).isEqualTo("에어포스"),
                () -> assertThat(afterUpdate.price()).isEqualTo(120000L)
            );
        }

        @DisplayName("좋아요 등록 후 캐시가 무효화되어 갱신된 likeCount를 반환한다.")
        @Test
        void returnsFreshLikeCount_afterLike() {
            // arrange
            BrandModel brand = brandJpaRepository.save(new BrandModel("나이키", "스포츠 브랜드"));
            ProductModel product = productJpaRepository.save(
                new ProductModel(brand, "에어맥스", 150000L, "설명", 100, ProductStatus.ON_SALE)
            );
            ProductInfo beforeLike = productFacade.getProduct(product.getId());
            assertThat(beforeLike.likeCount()).isEqualTo(0L);

            // act - 좋아요 등록 (캐시 무효화)
            likeService.like(1L, product.getId());

            // act - 캐시 무효화 후 조회
            ProductInfo afterLike = productFacade.getProduct(product.getId());

            // assert
            assertThat(afterLike.likeCount()).isEqualTo(1L);
        }
    }

    @DisplayName("상품 목록 캐시")
    @Nested
    class ProductListCache {

        @DisplayName("동일한 조건으로 두 번 조회하면 캐시 히트로 동일한 결과를 반환한다.")
        @Test
        void returnsCachedList_onSecondCall() {
            // arrange
            BrandModel brand = brandJpaRepository.save(new BrandModel("나이키", "스포츠 브랜드"));
            productJpaRepository.save(new ProductModel(brand, "에어맥스", 150000L, "설명", 100, ProductStatus.ON_SALE));
            productJpaRepository.save(new ProductModel(brand, "에어포스", 120000L, "설명", 50, ProductStatus.ON_SALE));

            PageRequest pageable = PageRequest.of(0, 20);

            // act
            ProductPageInfo firstCall = productFacade.getAll(pageable, ProductSortType.LATEST, null);
            ProductPageInfo secondCall = productFacade.getAll(pageable, ProductSortType.LATEST, null);

            // assert
            assertAll(
                () -> assertThat(firstCall.totalElements()).isEqualTo(secondCall.totalElements()),
                () -> assertThat(firstCall.content()).hasSameSizeAs(secondCall.content())
            );
        }

        @DisplayName("상품 등록 후 목록 캐시가 무효화되어 새 상품이 포함된다.")
        @Test
        void includesNewProduct_afterRegistration() {
            // arrange
            BrandModel brand = brandJpaRepository.save(new BrandModel("나이키", "스포츠 브랜드"));
            productJpaRepository.save(new ProductModel(brand, "에어맥스", 150000L, "설명", 100, ProductStatus.ON_SALE));

            PageRequest pageable = PageRequest.of(0, 20);
            ProductPageInfo beforeRegister = productFacade.getAll(pageable, ProductSortType.LATEST, null);
            assertThat(beforeRegister.totalElements()).isEqualTo(1);

            // act - 상품 등록 (목록 캐시 무효화)
            productFacade.register(brand.getId(), "에어포스", 120000L, "설명", 50, ProductStatus.ON_SALE);

            // act - 무효화 후 목록 조회
            ProductPageInfo afterRegister = productFacade.getAll(pageable, ProductSortType.LATEST, null);

            // assert
            assertThat(afterRegister.totalElements()).isEqualTo(2);
        }

        @DisplayName("브랜드 필터 + 좋아요순 정렬 캐시가 정상 동작한다.")
        @Test
        void cacheWorksWithBrandFilterAndLikesSort() {
            // arrange
            BrandModel nike = brandJpaRepository.save(new BrandModel("나이키", "스포츠 브랜드"));
            BrandModel adidas = brandJpaRepository.save(new BrandModel("아디다스", "스포츠 브랜드"));
            ProductModel nikeProduct = productJpaRepository.save(
                new ProductModel(nike, "나이키-1", 100000L, "설명", 100, ProductStatus.ON_SALE)
            );
            productJpaRepository.save(
                new ProductModel(adidas, "아디다스-1", 110000L, "설명", 100, ProductStatus.ON_SALE)
            );
            likeService.like(1L, nikeProduct.getId());

            PageRequest pageable = PageRequest.of(0, 20);

            // act
            ProductPageInfo nikeProducts = productFacade.getAll(pageable, ProductSortType.LIKES_DESC, nike.getId());
            ProductPageInfo allProducts = productFacade.getAll(pageable, ProductSortType.LIKES_DESC, null);

            // assert
            assertAll(
                () -> assertThat(nikeProducts.totalElements()).isEqualTo(1),
                () -> assertThat(allProducts.totalElements()).isEqualTo(2),
                () -> assertThat(nikeProducts.content().get(0).likeCount()).isEqualTo(1L)
            );
        }
    }

    @DisplayName("캐시 미스 시에도 서비스가 정상 동작한다")
    @Nested
    class CacheMiss {

        @DisplayName("Redis 캐시가 비어 있어도 상품 상세 조회가 DB fallback으로 정상 동작한다.")
        @Test
        void detailQuery_worksWithoutCache() {
            // arrange
            redisCleanUp.truncateAll();
            BrandModel brand = brandJpaRepository.save(new BrandModel("나이키", "스포츠 브랜드"));
            ProductModel product = productJpaRepository.save(
                new ProductModel(brand, "에어맥스", 150000L, "설명", 100, ProductStatus.ON_SALE)
            );

            // act
            ProductInfo result = productFacade.getProduct(product.getId());

            // assert
            assertAll(
                () -> assertThat(result).isNotNull(),
                () -> assertThat(result.name()).isEqualTo("에어맥스")
            );
        }

        @DisplayName("Redis 캐시가 비어 있어도 상품 목록 조회가 DB fallback으로 정상 동작한다.")
        @Test
        void listQuery_worksWithoutCache() {
            // arrange
            redisCleanUp.truncateAll();
            BrandModel brand = brandJpaRepository.save(new BrandModel("나이키", "스포츠 브랜드"));
            productJpaRepository.save(new ProductModel(brand, "에어맥스", 150000L, "설명", 100, ProductStatus.ON_SALE));

            PageRequest pageable = PageRequest.of(0, 20);

            // act
            ProductPageInfo result = productFacade.getAll(pageable, ProductSortType.LATEST, null);

            // assert
            assertAll(
                () -> assertThat(result).isNotNull(),
                () -> assertThat(result.totalElements()).isEqualTo(1)
            );
        }
    }
}
package com.loopers.application.product;

import com.loopers.domain.brand.Brand;
import com.loopers.domain.like.Like;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductWithBrand;
import com.loopers.domain.product.vo.Price;
import com.loopers.domain.product.vo.Stock;
import com.loopers.fake.FakeBrandRepository;
import com.loopers.fake.FakeLikeRepository;
import com.loopers.fake.FakeProductCachePort;
import com.loopers.fake.FakeProductRepository;
import com.loopers.fake.FakeStockReservationRedisRepository;
import com.loopers.interfaces.api.product.ProductDto;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductFacadeTest {

    private ProductFacade productFacade;
    private FakeProductRepository productRepository;
    private FakeBrandRepository brandRepository;
    private FakeLikeRepository likeRepository;

    @BeforeEach
    void setUp() {
        productRepository = new FakeProductRepository();
        brandRepository = new FakeBrandRepository();
        likeRepository = new FakeLikeRepository();
        productRepository.setBrandRepository(brandRepository);
        productFacade = new ProductFacade(productRepository, brandRepository, likeRepository, new FakeProductCachePort(), event -> {}, new FakeStockReservationRedisRepository(), null);
    }

    @Nested
    @DisplayName("상품 상세 조회")
    class GetProductDetail {

        @DisplayName("상품을 조회하면 브랜드 정보와 likeCount가 함께 반환된다")
        @Test
        void getProductDetail_returnsProductWithBrand() {
            // arrange
            Brand brand = brandRepository.save(new Brand("나이키", "스포츠 브랜드"));
            Product product = productRepository.save(
                    new Product(brand.getId(), "에어맥스", new Price(150000), new Stock(10)));

            // act
            ProductWithBrand result = productFacade.getProductDetail(product.getId());

            // assert
            assertThat(result.product().getId()).isEqualTo(product.getId());
            assertThat(result.product().getName()).isEqualTo("에어맥스");
            assertThat(result.brandName()).isEqualTo("나이키");
            assertThat(result.likeCount()).isEqualTo(0);
        }

        @DisplayName("존재하지 않는 상품을 조회하면 예외가 발생한다")
        @Test
        void getProductDetail_whenProductNotExists_throwsCoreException() {
            assertThatThrownBy(() -> productFacade.getProductDetail(999L))
                    .isInstanceOf(CoreException.class)
                    .extracting(e -> ((CoreException) e).getErrorType())
                    .isEqualTo(ErrorType.NOT_FOUND);
        }

        @DisplayName("브랜드가 삭제된 상품은 브랜드 이름이 null로 반환된다")
        @Test
        void getProductDetail_whenBrandDeleted_returnsNullBrandName() {
            // arrange
            Product product = productRepository.save(
                    new Product(999L, "에어맥스", new Price(150000), new Stock(10)));

            // act
            ProductWithBrand result = productFacade.getProductDetail(product.getId());

            // assert
            assertThat(result.brandName()).isNull();
        }

        @DisplayName("likeCount가 반영된 상품 상세가 반환된다")
        @Test
        void getProductDetail_returnsLikeCount() {
            // arrange
            Brand brand = brandRepository.save(new Brand("나이키", "스포츠 브랜드"));
            Product product = productRepository.save(
                    new Product(brand.getId(), "에어맥스", new Price(150000), new Stock(10)));
            productRepository.incrementLikeCount(product.getId());
            productRepository.incrementLikeCount(product.getId());
            productRepository.incrementLikeCount(product.getId());

            // act
            ProductWithBrand result = productFacade.getProductDetail(product.getId());

            // assert
            assertThat(result.likeCount()).isEqualTo(3);
        }
    }

    @Nested
    @DisplayName("상품 전체 조회 (페이지네이션)")
    class GetAllProducts {

        @DisplayName("모든 상품이 브랜드 정보와 함께 반환된다")
        @Test
        void getAllProducts_returnsAllWithBrandInfo() {
            // arrange
            Brand brand = brandRepository.save(new Brand("나이키", "스포츠 브랜드"));
            productRepository.save(new Product(brand.getId(), "에어맥스", new Price(150000), new Stock(10)));
            productRepository.save(new Product(brand.getId(), "에어포스", new Price(120000), new Stock(20)));

            // act
            Page<ProductWithBrand> result = productFacade.getAllProducts("latest", PageRequest.of(0, 20));

            // assert
            assertThat(result.getContent()).hasSize(2);
            assertThat(result.getContent()).allSatisfy(info ->
                    assertThat(info.brandName()).isEqualTo("나이키")
            );
        }

        @DisplayName("상품이 없으면 빈 페이지가 반환된다")
        @Test
        void getAllProducts_whenEmpty_returnsEmptyPage() {
            // act
            Page<ProductWithBrand> result = productFacade.getAllProducts("latest", PageRequest.of(0, 20));

            // assert
            assertThat(result.getContent()).isEmpty();
            assertThat(result.getTotalElements()).isZero();
        }

        @DisplayName("좋아요순 정렬이 DB에서 처리된다")
        @Test
        void getAllProducts_likesDesc_sortedByLikeCount() {
            // arrange
            Brand brand = brandRepository.save(new Brand("나이키", "스포츠 브랜드"));
            Product p1 = productRepository.save(new Product(brand.getId(), "에어맥스", new Price(150000), new Stock(10)));
            Product p2 = productRepository.save(new Product(brand.getId(), "에어포스", new Price(120000), new Stock(20)));
            Product p3 = productRepository.save(new Product(brand.getId(), "덩크", new Price(130000), new Stock(15)));

            // p2에 좋아요 3개, p3에 1개, p1에 0개
            productRepository.incrementLikeCount(p2.getId());
            productRepository.incrementLikeCount(p2.getId());
            productRepository.incrementLikeCount(p2.getId());
            productRepository.incrementLikeCount(p3.getId());

            // act
            Page<ProductWithBrand> result = productFacade.getAllProducts("likes_desc", PageRequest.of(0, 20));

            // assert
            List<ProductWithBrand> content = result.getContent();
            assertThat(content).hasSize(3);
            assertThat(content.get(0).product().getName()).isEqualTo("에어포스");
            assertThat(content.get(0).likeCount()).isEqualTo(3);
            assertThat(content.get(1).product().getName()).isEqualTo("덩크");
            assertThat(content.get(1).likeCount()).isEqualTo(1);
            assertThat(content.get(2).product().getName()).isEqualTo("에어맥스");
            assertThat(content.get(2).likeCount()).isEqualTo(0);
        }

        @DisplayName("페이지네이션이 올바르게 동작한다")
        @Test
        void getAllProducts_pagination_worksCorrectly() {
            // arrange
            Brand brand = brandRepository.save(new Brand("나이키", "스포츠 브랜드"));
            for (int i = 0; i < 25; i++) {
                productRepository.save(new Product(brand.getId(), "상품" + i, new Price(10000 + i), new Stock(10)));
            }

            // act
            Page<ProductWithBrand> page0 = productFacade.getAllProducts("latest", PageRequest.of(0, 10));
            Page<ProductWithBrand> page1 = productFacade.getAllProducts("latest", PageRequest.of(1, 10));
            Page<ProductWithBrand> page2 = productFacade.getAllProducts("latest", PageRequest.of(2, 10));

            // assert
            assertThat(page0.getContent()).hasSize(10);
            assertThat(page1.getContent()).hasSize(10);
            assertThat(page2.getContent()).hasSize(5);
            assertThat(page0.getTotalElements()).isEqualTo(25);
            assertThat(page0.getTotalPages()).isEqualTo(3);
        }
    }

    @Nested
    @DisplayName("캐시 통합 조회")
    class CachedQueries {

        @DisplayName("캐시 미스 시 DB에서 조회하여 반환한다")
        @Test
        void getAllProductsCached_onCacheMiss_returnsFromDb() {
            // arrange
            Brand brand = brandRepository.save(new Brand("나이키", "스포츠 브랜드"));
            productRepository.save(new Product(brand.getId(), "에어맥스", new Price(150000), new Stock(10)));

            // act
            ProductDto.PagedProductResponse response = productFacade.getAllProductsCached(null, "latest", 0, 20);

            // assert
            assertThat(response.data()).hasSize(1);
            assertThat(response.totalElements()).isEqualTo(1);
        }

        @DisplayName("상품 상세 캐시 미스 시 DB에서 조회하여 반환한다")
        @Test
        void getProductDetailCached_onCacheMiss_returnsFromDb() {
            // arrange
            Brand brand = brandRepository.save(new Brand("나이키", "스포츠 브랜드"));
            Product product = productRepository.save(
                    new Product(brand.getId(), "에어맥스", new Price(150000), new Stock(10)));

            // act
            ProductDto.ProductResponse response = productFacade.getProductDetailCached(product.getId());

            // assert
            assertThat(response.name()).isEqualTo("에어맥스");
            assertThat(response.brandName()).isEqualTo("나이키");
        }
    }

    @Nested
    @DisplayName("상품 생성")
    class CreateProduct {

        @DisplayName("유효한 브랜드로 상품을 생성하면 ID가 부여되어 반환된다")
        @Test
        void createProduct_withValidBrand_returnsWithId() {
            // arrange
            Brand brand = brandRepository.save(new Brand("나이키", "스포츠 브랜드"));

            // act
            Product result = productFacade.createProduct(brand.getId(), "에어맥스", 150000, 10);

            // assert
            assertThat(result.getId()).isNotNull();
            assertThat(result.getId()).isGreaterThan(0L);
            assertThat(result.getName()).isEqualTo("에어맥스");
            assertThat(result.getPrice().getValue()).isEqualTo(150000);
            assertThat(result.getStock().getQuantity()).isEqualTo(10);
            assertThat(result.getBrandId()).isEqualTo(brand.getId());
        }

        @DisplayName("존재하지 않는 브랜드로 상품을 생성하면 예외가 발생한다")
        @Test
        void createProduct_withInvalidBrand_throwsCoreException() {
            assertThatThrownBy(() -> productFacade.createProduct(999L, "에어맥스", 150000, 10))
                    .isInstanceOf(CoreException.class)
                    .extracting(e -> ((CoreException) e).getErrorType())
                    .isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("상품 수정")
    class UpdateProduct {

        @DisplayName("존재하는 상품을 수정하면 변경된 정보가 반환된다")
        @Test
        void updateProduct_whenExists_returnsUpdated() {
            // arrange
            Brand brand = brandRepository.save(new Brand("나이키", "스포츠 브랜드"));
            Product product = productRepository.save(
                    new Product(brand.getId(), "에어맥스", new Price(150000), new Stock(10)));

            // act
            Product result = productFacade.updateProduct(product.getId(), "에어맥스 97", 180000, 5);

            // assert
            assertThat(result.getName()).isEqualTo("에어맥스 97");
            assertThat(result.getPrice().getValue()).isEqualTo(180000);
            assertThat(result.getStock().getQuantity()).isEqualTo(5);
        }

        @DisplayName("존재하지 않는 상품을 수정하면 예외가 발생한다")
        @Test
        void updateProduct_whenNotExists_throwsCoreException() {
            assertThatThrownBy(() -> productFacade.updateProduct(999L, "에어맥스", 150000, 10))
                    .isInstanceOf(CoreException.class)
                    .extracting(e -> ((CoreException) e).getErrorType())
                    .isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("상품 삭제")
    class DeleteProduct {

        @DisplayName("상품을 삭제하면 소프트 삭제된다")
        @Test
        void deleteProduct_softDeletesProduct() {
            // arrange
            Brand brand = brandRepository.save(new Brand("나이키", "스포츠 브랜드"));
            Product product = productRepository.save(
                    new Product(brand.getId(), "에어맥스", new Price(150000), new Stock(10)));

            // act
            productFacade.deleteProduct(product.getId());

            // assert
            assertThat(productRepository.findById(product.getId())).isEmpty();
        }

        @DisplayName("존재하지 않는 상품을 삭제하면 예외가 발생한다")
        @Test
        void deleteProduct_whenNotExists_throwsCoreException() {
            assertThatThrownBy(() -> productFacade.deleteProduct(999L))
                    .isInstanceOf(CoreException.class)
                    .extracting(e -> ((CoreException) e).getErrorType())
                    .isEqualTo(ErrorType.NOT_FOUND);
        }

        @DisplayName("상품 삭제 시 해당 상품의 좋아요가 hard delete 된다")
        @Test
        void deleteProduct_hardDeletesLikes() {
            // arrange
            Brand brand = brandRepository.save(new Brand("나이키", "스포츠 브랜드"));
            Product product = productRepository.save(
                    new Product(brand.getId(), "에어맥스", new Price(150000), new Stock(10)));
            likeRepository.save(new Like(1L, product.getId()));
            likeRepository.save(new Like(2L, product.getId()));

            // act
            productFacade.deleteProduct(product.getId());

            // assert
            assertThat(likeRepository.findByMemberIdAndProductId(1L, product.getId())).isEmpty();
            assertThat(likeRepository.findByMemberIdAndProductId(2L, product.getId())).isEmpty();
        }
    }

    @Nested
    @DisplayName("벤치마크 전용 AS-IS 재현")
    class NoOptimization {

        @DisplayName("enrichWithLikeCount + in-memory sort가 동작한다")
        @Test
        void getAllProductsNoOptimization_usesLegacyPath() {
            // arrange
            Brand brand = brandRepository.save(new Brand("나이키", "스포츠 브랜드"));
            Product p1 = productRepository.save(new Product(brand.getId(), "에어맥스", new Price(150000), new Stock(10)));
            Product p2 = productRepository.save(new Product(brand.getId(), "에어포스", new Price(120000), new Stock(20)));

            // p2에 좋아요 2개 (likeRepository를 통해)
            likeRepository.save(new Like(1L, p2.getId()));
            likeRepository.save(new Like(2L, p2.getId()));

            // act
            List<ProductWithBrand> result = productFacade.getAllProductsNoOptimization("likes_desc");

            // assert
            assertThat(result).hasSize(2);
            assertThat(result.get(0).likeCount()).isEqualTo(2);
            assertThat(result.get(0).product().getName()).isEqualTo("에어포스");
        }
    }
}

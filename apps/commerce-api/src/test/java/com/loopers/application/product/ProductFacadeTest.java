package com.loopers.application.product;

import com.loopers.domain.brand.Brand;
import com.loopers.domain.like.Like;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductWithBrand;
import com.loopers.domain.product.vo.Price;
import com.loopers.domain.product.vo.Stock;
import com.loopers.fake.FakeBrandRepository;
import com.loopers.fake.FakeLikeRepository;
import com.loopers.fake.FakeProductRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

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
        productFacade = new ProductFacade(productRepository, brandRepository, likeRepository);
    }

    @Nested
    @DisplayName("상품 상세 조회")
    class GetProductDetail {

        @DisplayName("상품을 조회하면 브랜드 정보가 함께 반환된다")
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
    }

    @Nested
    @DisplayName("상품 전체 조회")
    class GetAllProducts {

        @DisplayName("모든 상품이 브랜드 정보와 함께 반환된다")
        @Test
        void getAllProducts_returnsAllWithBrandInfo() {
            // arrange
            Brand brand = brandRepository.save(new Brand("나이키", "스포츠 브랜드"));
            productRepository.save(new Product(brand.getId(), "에어맥스", new Price(150000), new Stock(10)));
            productRepository.save(new Product(brand.getId(), "에어포스", new Price(120000), new Stock(20)));

            // act
            List<ProductWithBrand> result = productFacade.getAllProducts();

            // assert
            assertThat(result).hasSize(2);
            assertThat(result).allSatisfy(info ->
                    assertThat(info.brandName()).isEqualTo("나이키")
            );
        }

        @DisplayName("상품이 없으면 빈 리스트가 반환된다")
        @Test
        void getAllProducts_whenEmpty_returnsEmptyList() {
            // act
            List<ProductWithBrand> result = productFacade.getAllProducts();

            // assert
            assertThat(result).isEmpty();
        }

        @DisplayName("브랜드가 삭제된 상품은 브랜드 이름이 null로 반환된다")
        @Test
        void getAllProducts_whenBrandDeleted_returnsNullBrandName() {
            // arrange
            productRepository.save(new Product(999L, "에어맥스", new Price(150000), new Stock(10)));

            // act
            List<ProductWithBrand> result = productFacade.getAllProducts();

            // assert
            assertThat(result).hasSize(1);
            assertThat(result.get(0).brandName()).isNull();
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
}

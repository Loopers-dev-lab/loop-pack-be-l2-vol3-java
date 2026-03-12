package com.loopers.domain.product;

import com.loopers.application.product.ProductFacade;
import com.loopers.domain.brand.BrandService;
import com.loopers.domain.stock.StockModel;
import com.loopers.domain.stock.StockService;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
class ProductServiceIntegrationTest {

    @Autowired
    private ProductService productService;

    @Autowired
    private ProductFacade productFacade;

    @Autowired
    private BrandService brandService;

    @Autowired
    private StockService stockService;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private Long createBrand(String name) {
        return brandService.register(name, "설명").getId();
    }

    private ProductModel createProduct(String name, String description, Money price, Long brandId, int initialStock) {
        return productFacade.register(name, description, price, brandId, initialStock);
    }

    @DisplayName("상품 등록")
    @Nested
    class Register {

        @DisplayName("유효한 정보로 등록하면 상품과 재고가 생성된다")
        @Test
        void createsProductAndStock() {
            // given
            Long brandId = createBrand("나이키");

            // when
            ProductModel result = createProduct("에어맥스 90", "러닝화", new Money(129000), brandId, 100);

            // then
            assertAll(
                () -> assertThat(result.getId()).isNotNull(),
                () -> assertThat(result.name()).isEqualTo("에어맥스 90"),
                () -> assertThat(result.price()).isEqualTo(new Money(129000)),
                () -> assertThat(result.brandId()).isEqualTo(brandId)
            );

            StockModel stock = stockService.getByProductId(result.getId());
            assertThat(stock.quantity()).isEqualTo(100);
        }

        @DisplayName("삭제된 브랜드에 등록하면 NOT_FOUND 예외가 발생한다")
        @Test
        void throwsWhenBrandDeleted() {
            // given
            Long brandId = createBrand("나이키");
            brandService.delete(brandId);

            // when
            CoreException result = assertThrows(CoreException.class,
                () -> createProduct("에어맥스", "러닝화", new Money(129000), brandId, 100));

            // then
            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @DisplayName("상품 조회")
    @Nested
    class GetProduct {

        @DisplayName("존재하고 미삭제 상태면 상품을 반환한다")
        @Test
        void returnsProduct() {
            // given
            Long brandId = createBrand("나이키");
            ProductModel saved = createProduct("에어맥스 90", "러닝화", new Money(129000), brandId, 100);

            // when
            ProductModel result = productService.getProduct(saved.getId());

            // then
            assertThat(result.name()).isEqualTo("에어맥스 90");
        }

        @DisplayName("삭제된 상품이면 NOT_FOUND 예외가 발생한다")
        @Test
        void throwsWhenDeleted() {
            // given
            Long brandId = createBrand("나이키");
            ProductModel saved = createProduct("에어맥스 90", "러닝화", new Money(129000), brandId, 100);
            productService.delete(saved.getId());

            // when
            CoreException result = assertThrows(CoreException.class,
                () -> productService.getProduct(saved.getId()));

            // then
            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @DisplayName("상품 목록 조회")
    @Nested
    class GetProducts {

        @DisplayName("미삭제 상품만 페이징하여 반환한다")
        @Test
        void returnsNotDeletedProducts() {
            // given
            Long brandId = createBrand("나이키");
            createProduct("에어맥스 90", "러닝화", new Money(129000), brandId, 100);
            createProduct("에어맥스 95", "러닝화", new Money(159000), brandId, 50);
            ProductModel deleted = createProduct("삭제될 상품", "설명", new Money(99000), brandId, 10);
            productService.delete(deleted.getId());

            // when
            Page<ProductModel> result = productService.getProducts(null, ProductSortType.LATEST, PageRequest.of(0, 10));

            // then
            assertThat(result.getTotalElements()).isEqualTo(2);
        }

        @DisplayName("brandId로 필터링하여 조회한다")
        @Test
        void filtersByBrandId() {
            // given
            Long nikeId = createBrand("나이키");
            Long adidasId = createBrand("아디다스");
            createProduct("에어맥스 90", "러닝화", new Money(129000), nikeId, 100);
            createProduct("슈퍼스타", "캐주얼", new Money(99000), adidasId, 50);

            // when
            Page<ProductModel> result = productService.getProducts(nikeId, ProductSortType.LATEST, PageRequest.of(0, 10));

            // then
            assertThat(result.getTotalElements()).isEqualTo(1);
            assertThat(result.getContent().get(0).name()).isEqualTo("에어맥스 90");
        }
    }

    @DisplayName("상품 수정")
    @Nested
    class Update {

        @DisplayName("name, description, price를 변경할 수 있다")
        @Test
        void updatesSuccessfully() {
            // given
            Long brandId = createBrand("나이키");
            ProductModel saved = createProduct("에어맥스 90", "러닝화", new Money(129000), brandId, 100);

            // when
            ProductModel result = productService.update(saved.getId(), "에어맥스 95", "뉴 러닝화", new Money(159000));

            // then
            assertAll(
                () -> assertThat(result.name()).isEqualTo("에어맥스 95"),
                () -> assertThat(result.description()).isEqualTo("뉴 러닝화"),
                () -> assertThat(result.price()).isEqualTo(new Money(159000))
            );
        }
    }

    @DisplayName("상품 삭제")
    @Nested
    class Delete {

        @DisplayName("soft delete 후 customer 조회에서 제외된다")
        @Test
        void excludedFromCustomerQueryAfterDelete() {
            // given
            Long brandId = createBrand("나이키");
            ProductModel saved = createProduct("에어맥스 90", "러닝화", new Money(129000), brandId, 100);

            // when
            productService.delete(saved.getId());

            // then
            CoreException result = assertThrows(CoreException.class,
                () -> productService.getProduct(saved.getId()));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }

        @DisplayName("soft delete 후 admin 조회에서는 포함된다")
        @Test
        void includedInAdminQueryAfterDelete() {
            // given
            Long brandId = createBrand("나이키");
            ProductModel saved = createProduct("에어맥스 90", "러닝화", new Money(129000), brandId, 100);

            // when
            productService.delete(saved.getId());

            // then
            ProductModel result = productService.getProductForAdmin(saved.getId());
            assertThat(result.getDeletedAt()).isNotNull();
        }
    }

    @DisplayName("브랜드별 상품 전체 삭제")
    @Nested
    class DeleteAllByBrandId {

        @DisplayName("해당 브랜드의 모든 상품을 soft delete 한다")
        @Test
        void softDeletesAllProducts() {
            // given
            Long brandId = createBrand("나이키");
            createProduct("에어맥스 90", "러닝화", new Money(129000), brandId, 100);
            createProduct("에어맥스 95", "러닝화", new Money(159000), brandId, 50);

            // when
            productService.deleteAllByBrandId(brandId);

            // then
            Page<ProductModel> result = productService.getProducts(brandId, ProductSortType.LATEST, PageRequest.of(0, 10));
            assertThat(result.getTotalElements()).isEqualTo(0);
        }
    }
}

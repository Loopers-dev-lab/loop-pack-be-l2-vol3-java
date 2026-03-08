package com.loopers.domain.product;

import com.loopers.domain.PageResult;
import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandDomainService;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
class ProductDomainServiceIntegrationTest {

    @Autowired
    private ProductDomainService productService;

    @Autowired
    private BrandDomainService brandService;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @Autowired
    private TransactionTemplate transactionTemplate;

    private Long brandId;

    @BeforeEach
    void setUp() {
        Brand brand = brandService.register("나이키");
        brandId = brand.getId();
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("상품을 등록할 때, ")
    @Nested
    class Register {

        @DisplayName("올바른 정보이면, 상품이 저장되고 반환된다.")
        @Test
        void savesAndReturnsProduct_whenValidInfo() {
            Product result = productService.register(brandId, "에어맥스", 129000);

            assertAll(
                () -> assertThat(result.getId()).isNotNull(),
                () -> assertThat(result.getBrandId()).isEqualTo(brandId),
                () -> assertThat(result.getName()).isEqualTo("에어맥스"),
                () -> assertThat(result.getPrice()).isEqualTo(new Money(129000)),
                () -> assertThat(result.getLikeCount()).isEqualTo(0)
            );
        }
    }

    @DisplayName("상품을 조회할 때, ")
    @Nested
    class GetById {

        @DisplayName("존재하는 상품이면, 상품을 반환한다.")
        @Test
        void returnsProduct_whenProductExists() {
            Product product = productService.register(brandId, "에어맥스", 129000);

            Product result = productService.getById(product.getId());

            assertThat(result.getName()).isEqualTo("에어맥스");
        }

        @DisplayName("존재하지 않는 상품이면, NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenProductDoesNotExist() {
            CoreException result = assertThrows(CoreException.class, () -> productService.getById(999L));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }

        @DisplayName("삭제된 상품이면, NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenProductIsDeleted() {
            Product product = productService.register(brandId, "에어맥스", 129000);
            productService.delete(product.getId());

            CoreException result = assertThrows(CoreException.class, () -> productService.getById(product.getId()));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @DisplayName("상품 목록을 조회할 때, ")
    @Nested
    class GetAll {

        @DisplayName("상품이 존재하면, 페이지 결과를 반환한다.")
        @Test
        void returnsPageResult_whenProductsExist() {
            productService.register(brandId, "에어맥스", 129000);
            productService.register(brandId, "울트라부스트", 159000);
            productService.register(brandId, "뉴발란스 990", 199000);

            PageResult<Product> result = productService.getAll(null, ProductSortType.LATEST, 0, 2);

            assertAll(
                () -> assertThat(result.items()).hasSize(2),
                () -> assertThat(result.totalElements()).isEqualTo(3),
                () -> assertThat(result.totalPages()).isEqualTo(2)
            );
        }

        @DisplayName("브랜드별 필터링이 동작한다.")
        @Test
        void filtersByBrandId() {
            Brand brand2 = brandService.register("아디다스");
            productService.register(brandId, "에어맥스", 129000);
            productService.register(brand2.getId(), "울트라부스트", 159000);

            PageResult<Product> result = productService.getAll(brandId, ProductSortType.LATEST, 0, 20);

            assertAll(
                () -> assertThat(result.items()).hasSize(1),
                () -> assertThat(result.items().get(0).getName()).isEqualTo("에어맥스")
            );
        }

        @DisplayName("가격 오름차순으로 정렬된다.")
        @Test
        void sortsByPriceAsc() {
            productService.register(brandId, "비싼상품", 199000);
            productService.register(brandId, "싼상품", 99000);
            productService.register(brandId, "중간상품", 149000);

            PageResult<Product> result = productService.getAll(null, ProductSortType.PRICE_ASC, 0, 20);

            assertAll(
                () -> assertThat(result.items()).hasSize(3),
                () -> assertThat(result.items().get(0).getName()).isEqualTo("싼상품"),
                () -> assertThat(result.items().get(1).getName()).isEqualTo("중간상품"),
                () -> assertThat(result.items().get(2).getName()).isEqualTo("비싼상품")
            );
        }

        @DisplayName("좋아요 내림차순으로 정렬된다.")
        @Test
        void sortsByLikesDesc() {
            Product p1 = productService.register(brandId, "인기없는상품", 129000);
            Product p2 = productService.register(brandId, "인기상품", 159000);
            transactionTemplate.executeWithoutResult(status -> {
                productService.incrementLikeCount(p2.getId());
                productService.incrementLikeCount(p2.getId());
                productService.incrementLikeCount(p1.getId());
            });

            PageResult<Product> result = productService.getAll(null, ProductSortType.LIKES_DESC, 0, 20);

            assertAll(
                () -> assertThat(result.items()).hasSize(2),
                () -> assertThat(result.items().get(0).getName()).isEqualTo("인기상품"),
                () -> assertThat(result.items().get(1).getName()).isEqualTo("인기없는상품")
            );
        }

        @DisplayName("삭제된 상품은 목록에 포함되지 않는다.")
        @Test
        void excludesDeletedProducts() {
            Product product = productService.register(brandId, "에어맥스", 129000);
            productService.register(brandId, "울트라부스트", 159000);
            productService.delete(product.getId());

            PageResult<Product> result = productService.getAll(null, ProductSortType.LATEST, 0, 20);

            assertAll(
                () -> assertThat(result.items()).hasSize(1),
                () -> assertThat(result.items().get(0).getName()).isEqualTo("울트라부스트")
            );
        }
    }

    @DisplayName("상품을 수정할 때, ")
    @Nested
    class Update {

        @DisplayName("올바른 정보이면, 상품이 수정된다.")
        @Test
        void updatesProduct_whenValidInfo() {
            Product product = productService.register(brandId, "에어맥스", 129000);

            Product result = productService.update(product.getId(), "에어포스1", 109000);

            assertAll(
                () -> assertThat(result.getName()).isEqualTo("에어포스1"),
                () -> assertThat(result.getPrice()).isEqualTo(new Money(109000))
            );
        }

        @DisplayName("존재하지 않는 상품이면, NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenProductDoesNotExist() {
            CoreException result = assertThrows(CoreException.class,
                () -> productService.update(999L, "에어맥스", 129000));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @DisplayName("상품을 삭제할 때, ")
    @Nested
    class Delete {

        @DisplayName("존재하는 상품이면, 논리 삭제된다.")
        @Test
        void softDeletesProduct_whenProductExists() {
            Product product = productService.register(brandId, "에어맥스", 129000);

            productService.delete(product.getId());

            CoreException result = assertThrows(CoreException.class, () -> productService.getById(product.getId()));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }

        @DisplayName("존재하지 않는 상품이면, NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenProductDoesNotExist() {
            CoreException result = assertThrows(CoreException.class, () -> productService.delete(999L));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @DisplayName("브랜드 삭제 시, ")
    @Nested
    class DeleteAllByBrand {

        @DisplayName("해당 브랜드의 모든 상품이 삭제된다.")
        @Test
        void deletesAllProductsOfBrand() {
            productService.register(brandId, "에어맥스", 129000);
            productService.register(brandId, "에어포스1", 109000);

            transactionTemplate.executeWithoutResult(status ->
                productService.deleteAllByBrandId(brandId)
            );

            PageResult<Product> result = productService.getAll(brandId, ProductSortType.LATEST, 0, 20);
            assertThat(result.items()).isEmpty();
        }
    }

    @DisplayName("좋아요 수를 증가할 때, ")
    @Nested
    class IncrementLikeCount {

        @DisplayName("좋아요 수가 1 증가한다.")
        @Test
        void incrementsLikeCount() {
            Product product = productService.register(brandId, "에어맥스", 129000);

            transactionTemplate.executeWithoutResult(status ->
                productService.incrementLikeCount(product.getId())
            );

            Product result = productService.getById(product.getId());
            assertThat(result.getLikeCount()).isEqualTo(1);
        }
    }

    @DisplayName("좋아요 수를 감소할 때, ")
    @Nested
    class DecrementLikeCount {

        @DisplayName("좋아요 수가 1 감소한다.")
        @Test
        void decrementsLikeCount() {
            Product product = productService.register(brandId, "에어맥스", 129000);
            transactionTemplate.executeWithoutResult(status ->
                productService.incrementLikeCount(product.getId())
            );

            transactionTemplate.executeWithoutResult(status ->
                productService.decrementLikeCount(product.getId())
            );

            Product result = productService.getById(product.getId());
            assertThat(result.getLikeCount()).isEqualTo(0);
        }

        @DisplayName("좋아요 수가 0이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenLikeCountIsZero() {
            Product product = productService.register(brandId, "에어맥스", 129000);

            CoreException result = assertThrows(CoreException.class,
                () -> transactionTemplate.executeWithoutResult(status ->
                    productService.decrementLikeCount(product.getId())
                ));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }
}

package com.loopers.domain.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

import com.loopers.domain.product.dto.ProductCommand;
import com.loopers.domain.product.dto.ProductInfo;
import com.loopers.support.error.CoreException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

class ProductServiceTest {

    private ProductService productService;
    private FakeProductRepository productRepository;

    private static final Long BRAND_ID = 1L;
    private static final Long BRAND_ID_2 = 2L;

    @BeforeEach
    void setUp() {
        productRepository = new FakeProductRepository();
        productService = new ProductService(productRepository);
    }

    @DisplayName("상품을 등록할 때, ")
    @Nested
    class Register {

        @DisplayName("유효한 값이 주어지면, 정상적으로 등록된다.")
        @Test
        void register_whenValidValues() {
            // act
            productService.register(BRAND_ID, "에어맥스", 150000, 100);

            // assert
            Page<ProductModel> all = productRepository.findAll(PageRequest.of(0, 20));
            assertThat(all.getTotalElements()).isEqualTo(1);
            assertThat(all.getContent().get(0).getName()).isEqualTo("에어맥스");
        }
    }

    @DisplayName("상품을 조회할 때, ")
    @Nested
    class GetById {

        @DisplayName("존재하는 상품 ID가 주어지면, 정상적으로 조회된다.")
        @Test
        void getById_whenExists() {
            // arrange
            productService.register(BRAND_ID, "에어맥스", 150000, 100);
            Long savedId = productRepository.findAll(PageRequest.of(0, 20)).getContent().get(0).getId();

            // act
            ProductModel found = productService.getById(savedId);

            // assert
            assertThat(found.getName()).isEqualTo("에어맥스");
        }

        @DisplayName("존재하지 않는 상품 ID이면 NOT_FOUND 예외가 발생한다.")
        @Test
        void getById_whenNotExists() {
            assertThatThrownBy(() -> productService.getById(999L))
                .isInstanceOf(CoreException.class)
                .satisfies(e -> assertThat(((CoreException) e).getErrorCode()).isEqualTo(ProductErrorCode.NOT_FOUND));
        }

        @DisplayName("삭제된 상품 ID이면 NOT_FOUND 예외가 발생한다.")
        @Test
        void getById_whenDeleted() {
            // arrange
            productService.register(BRAND_ID, "에어맥스", 150000, 100);
            Long savedId = productRepository.findAll(PageRequest.of(0, 20)).getContent().get(0).getId();
            productService.delete(savedId);

            // act & assert
            assertThatThrownBy(() -> productService.getById(savedId))
                .isInstanceOf(CoreException.class)
                .satisfies(e -> assertThat(((CoreException) e).getErrorCode()).isEqualTo(ProductErrorCode.NOT_FOUND));
        }
    }

    @DisplayName("상품을 수정할 때, ")
    @Nested
    class Update {

        @DisplayName("유효한 값이 주어지면, 정상적으로 수정된다.")
        @Test
        void update_whenValidValues() {
            // arrange
            productService.register(BRAND_ID, "에어맥스", 150000, 100);
            Long savedId = productRepository.findAll(PageRequest.of(0, 20)).getContent().get(0).getId();

            // act
            productService.update(savedId, "에어포스", 120000, 50);

            // assert
            ProductModel updated = productService.getById(savedId);
            assertThat(updated.getName()).isEqualTo("에어포스");
            assertThat(updated.getPrice()).isEqualTo(120000);
            assertThat(updated.getStock()).isEqualTo(50);
        }

        @DisplayName("존재하지 않는 상품이면 NOT_FOUND 예외가 발생한다.")
        @Test
        void update_whenNotExists() {
            assertThatThrownBy(() -> productService.update(999L, "에어포스", 120000, 50))
                .isInstanceOf(CoreException.class)
                .satisfies(e -> assertThat(((CoreException) e).getErrorCode()).isEqualTo(ProductErrorCode.NOT_FOUND));
        }
    }

    @DisplayName("상품을 삭제할 때, ")
    @Nested
    class Delete {

        @DisplayName("존재하는 상품 ID가 주어지면, 정상적으로 삭제된다.")
        @Test
        void delete_whenExists() {
            // arrange
            productService.register(BRAND_ID, "에어맥스", 150000, 100);
            Long savedId = productRepository.findAll(PageRequest.of(0, 20)).getContent().get(0).getId();

            // act
            productService.delete(savedId);

            // assert
            assertThatThrownBy(() -> productService.getById(savedId))
                .isInstanceOf(CoreException.class)
                .satisfies(e -> assertThat(((CoreException) e).getErrorCode()).isEqualTo(ProductErrorCode.NOT_FOUND));
        }
    }

    @DisplayName("상품 목록을 조회할 때, ")
    @Nested
    class GetAll {

        @DisplayName("등록된 상품이 있으면, 페이지네이션된 목록을 반환한다.")
        @Test
        void getAll_whenProductsExist() {
            // arrange
            productService.register(BRAND_ID, "에어맥스", 150000, 100);
            productService.register(BRAND_ID, "에어포스", 120000, 50);
            productService.register(BRAND_ID, "조던1", 200000, 30);

            // act
            Page<ProductModel> result = productService.getAll(PageRequest.of(0, 2));

            // assert
            assertThat(result.getContent()).hasSize(2);
            assertThat(result.getTotalElements()).isEqualTo(3);
            assertThat(result.getTotalPages()).isEqualTo(2);
        }

        @DisplayName("삭제된 상품은 목록에 포함되지 않는다.")
        @Test
        void getAll_excludesDeletedProducts() {
            // arrange
            productService.register(BRAND_ID, "에어맥스", 150000, 100);
            productService.register(BRAND_ID, "에어포스", 120000, 50);
            Long firstId = productRepository.findAll(PageRequest.of(0, 20)).getContent().get(0).getId();
            productService.delete(firstId);

            // act
            Page<ProductModel> result = productService.getAll(PageRequest.of(0, 20));

            // assert
            assertThat(result.getTotalElements()).isEqualTo(1);
        }
    }

    @DisplayName("브랜드별 상품 목록을 조회할 때, ")
    @Nested
    class GetAllByBrandId {

        @DisplayName("해당 브랜드의 상품만 반환한다.")
        @Test
        void getAllByBrandId_whenExists() {
            // arrange
            productService.register(BRAND_ID, "에어맥스", 150000, 100);
            productService.register(BRAND_ID_2, "울트라부스트", 180000, 80);

            // act
            Page<ProductModel> result = productService.getAllByBrandId(BRAND_ID, PageRequest.of(0, 20));

            // assert
            assertThat(result.getTotalElements()).isEqualTo(1);
            assertThat(result.getContent().get(0).getName()).isEqualTo("에어맥스");
        }
    }

    @DisplayName("브랜드별 상품을 일괄 삭제할 때, ")
    @Nested
    class DeleteAllByBrandId {

        @DisplayName("해당 브랜드의 모든 상품이 삭제된다.")
        @Test
        void deleteAllByBrandId_whenProductsExist() {
            // arrange
            productService.register(BRAND_ID, "에어맥스", 150000, 100);
            productService.register(BRAND_ID, "에어포스", 120000, 50);

            // act
            productService.deleteAllByBrandId(BRAND_ID);

            // assert
            Page<ProductModel> result = productService.getAll(PageRequest.of(0, 20));
            assertThat(result.getTotalElements()).isEqualTo(0);
        }
    }

    @DisplayName("상품 검증 및 재고 차감을 할 때, ")
    @Nested
    class ValidateAndDeductStock {

        @DisplayName("유효한 커맨드가 주어지면, 스냅샷을 반환하고 재고가 차감된다.")
        @Test
        void validateAndDeductStock_success() {
            // arrange
            productService.register(BRAND_ID, "에어맥스", 150000, 100);
            Long productId = productRepository.findAll(PageRequest.of(0, 20))
                    .getContent().get(0).getId();

            List<ProductCommand.StockDeduction> commands = List.of(
                    new ProductCommand.StockDeduction(productId, 2, 150000));

            // act
            List<ProductInfo.StockDeduction> snapshots = productService.validateAndDeductStock(commands);

            // assert
            assertAll(
                    () -> assertThat(snapshots).hasSize(1),
                    () -> assertThat(snapshots.get(0).productId()).isEqualTo(productId),
                    () -> assertThat(snapshots.get(0).name()).isEqualTo("에어맥스"),
                    () -> assertThat(snapshots.get(0).price()).isEqualTo(150000),
                    () -> assertThat(snapshots.get(0).quantity()).isEqualTo(2),
                    () -> assertThat(snapshots.get(0).brandId()).isEqualTo(BRAND_ID),
                    () -> assertThat(productService.getById(productId).getStock()).isEqualTo(98));
        }

        @DisplayName("존재하지 않는 상품 ID가 포함되면, NOT_FOUND 예외가 발생한다.")
        @Test
        void validateAndDeductStock_whenProductNotFound() {
            assertThatThrownBy(() -> productService.validateAndDeductStock(List.of(
                    new ProductCommand.StockDeduction(999L, 1, 50000))))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorCode())
                            .isEqualTo(ProductErrorCode.NOT_FOUND));
        }

        @DisplayName("expectedPrice와 현재 가격이 불일치하면, PRICE_MISMATCH 예외가 발생한다.")
        @Test
        void validateAndDeductStock_whenPriceMismatch() {
            // arrange
            productService.register(BRAND_ID, "에어맥스", 150000, 100);
            Long productId = productRepository.findAll(PageRequest.of(0, 20))
                    .getContent().get(0).getId();

            // act & assert
            assertThatThrownBy(() -> productService.validateAndDeductStock(List.of(
                    new ProductCommand.StockDeduction(productId, 1, 200000))))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorCode())
                            .isEqualTo(ProductErrorCode.PRICE_MISMATCH));
        }

        @DisplayName("재고가 부족하면, 예외가 발생한다.")
        @Test
        void validateAndDeductStock_whenInsufficientStock() {
            // arrange
            productService.register(BRAND_ID, "에어맥스", 150000, 5);
            Long productId = productRepository.findAll(PageRequest.of(0, 20))
                    .getContent().get(0).getId();

            // act & assert
            assertThatThrownBy(() -> productService.validateAndDeductStock(List.of(
                    new ProductCommand.StockDeduction(productId, 10, 150000))))
                    .isInstanceOf(CoreException.class);
        }
    }
}

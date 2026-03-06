package com.loopers.domain.stock;

import com.loopers.domain.brand.BrandDomainService;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductDomainService;
import com.loopers.domain.product.Stock;
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

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
class ProductStockDomainServiceIntegrationTest {

    @Autowired
    private ProductStockDomainService productStockService;

    @Autowired
    private ProductDomainService productService;

    @Autowired
    private BrandDomainService brandService;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @Autowired
    private TransactionTemplate transactionTemplate;

    private Long brandId;
    private Long productId;

    @BeforeEach
    void setUp() {
        brandId = brandService.register("나이키").getId();
        Product product = productService.register(brandId, "에어맥스", 129000);
        productId = product.getId();
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("재고를 생성할 때, ")
    @Nested
    class Create {

        @DisplayName("올바른 정보이면, 재고가 저장되고 반환된다.")
        @Test
        void savesAndReturnsStock_whenValidInfo() {
            ProductStock result = productStockService.create(productId, 100);

            assertAll(
                () -> assertThat(result.getId()).isNotNull(),
                () -> assertThat(result.getProductId()).isEqualTo(productId),
                () -> assertThat(result.getStock()).isEqualTo(new Stock(100))
            );
        }
    }

    @DisplayName("재고를 조회할 때, ")
    @Nested
    class GetByProductId {

        @DisplayName("존재하는 재고이면, 반환한다.")
        @Test
        void returnsStock_whenExists() {
            productStockService.create(productId, 100);

            ProductStock result = productStockService.getByProductId(productId);

            assertThat(result.getStock()).isEqualTo(new Stock(100));
        }

        @DisplayName("존재하지 않는 재고이면, NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenNotExists() {
            CoreException result = assertThrows(CoreException.class,
                () -> productStockService.getByProductId(999L));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @DisplayName("재고를 일괄 조회할 때, ")
    @Nested
    class GetByProductIds {

        @DisplayName("여러 상품의 재고를 Map으로 반환한다.")
        @Test
        void returnsStockMap_whenProductsExist() {
            Product product2 = productService.register(brandId, "에어포스1", 109000);
            productStockService.create(productId, 100);
            productStockService.create(product2.getId(), 50);

            Map<Long, ProductStock> result = productStockService.getByProductIds(Set.of(productId, product2.getId()));

            assertAll(
                () -> assertThat(result).hasSize(2),
                () -> assertThat(result.get(productId).getStock()).isEqualTo(new Stock(100)),
                () -> assertThat(result.get(product2.getId()).getStock()).isEqualTo(new Stock(50))
            );
        }
    }

    @DisplayName("재고를 차감할 때, ")
    @Nested
    class DeductWithLock {

        @DisplayName("충분한 재고가 있으면, 차감된다.")
        @Test
        void deductsStock_whenSufficient() {
            productStockService.create(productId, 10);

            transactionTemplate.executeWithoutResult(status -> {
                ProductStock result = productStockService.deductWithLock(productId, 3);
                assertThat(result.getStock()).isEqualTo(new Stock(7));
            });
        }

        @DisplayName("재고가 부족하면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenInsufficient() {
            productStockService.create(productId, 2);

            CoreException result = assertThrows(CoreException.class,
                () -> transactionTemplate.executeWithoutResult(status ->
                    productStockService.deductWithLock(productId, 3)));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("재고를 복원할 때, ")
    @Nested
    class RestoreWithLock {

        @DisplayName("유효한 수량이면, 복원된다.")
        @Test
        void restoresStock_whenValidQuantity() {
            productStockService.create(productId, 97);

            transactionTemplate.executeWithoutResult(status -> {
                ProductStock result = productStockService.restoreWithLock(productId, 3);
                assertThat(result.getStock()).isEqualTo(new Stock(100));
            });
        }
    }

    @DisplayName("재고를 변경할 때, ")
    @Nested
    class ChangeQuantity {

        @DisplayName("올바른 수량이면, 변경된다.")
        @Test
        void changesQuantity_whenValid() {
            productStockService.create(productId, 100);

            ProductStock result = productStockService.changeQuantity(productId, 50);

            assertThat(result.getStock()).isEqualTo(new Stock(50));
        }
    }

    @DisplayName("재고를 삭제할 때, ")
    @Nested
    class Delete {

        @DisplayName("상품 ID로 삭제하면, 조회되지 않는다.")
        @Test
        void deletesStock_whenProductIdGiven() {
            productStockService.create(productId, 100);

            transactionTemplate.executeWithoutResult(status ->
                productStockService.deleteByProductId(productId));

            CoreException result = assertThrows(CoreException.class,
                () -> productStockService.getByProductId(productId));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }
}

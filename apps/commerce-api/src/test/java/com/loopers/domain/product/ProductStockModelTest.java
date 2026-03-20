package com.loopers.domain.product;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

@DisplayName("ProductStockModel 도메인 모델 테스트")
class ProductStockModelTest {

    @Nested
    @DisplayName("생성 검증")
    class CreateTests {

        @Test
        @DisplayName("유효한 입력으로 생성 성공")
        void create_WithValidInputs_ShouldSuccess() {
            ProductStockModel stock = ProductStockModel.create(1L, 100);

            assertThat(stock.getProductId()).isEqualTo(1L);
            assertThat(stock.getOnHand()).isEqualTo(100);
        }

        @Test
        @DisplayName("음수 onHand로 생성 시 CoreException 발생")
        void create_WithNegativeOnHand_ShouldThrow() {
            assertThatThrownBy(() -> ProductStockModel.create(1L, -1))
                    .isInstanceOf(CoreException.class);
        }

        @Test
        @DisplayName("생성 시 reserved 기본값은 0이다")
        void create_InitialReserved_ShouldBeZero() {
            ProductStockModel stock = ProductStockModel.create(1L, 100);
            assertThat(stock.getReserved()).isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("재고 계산")
    class StockCalculationTests {

        @Test
        @DisplayName("가용 재고 = onHand - reserved")
        void getAvailableQty_ShouldReturnOnHandMinusReserved() {
            ProductStockModel stock = ProductStockModel.create(1L, 100);
            assertThat(stock.getAvailableQty()).isEqualTo(100);
        }

        @Test
        @DisplayName("충분한 재고가 있으면 canHold true")
        void canHold_WhenSufficient_ShouldReturnTrue() {
            ProductStockModel stock = ProductStockModel.create(1L, 10);
            assertThat(stock.canHold(10)).isTrue();
        }

        @Test
        @DisplayName("재고가 부족하면 canHold false")
        void canHold_WhenInsufficient_ShouldReturnFalse() {
            ProductStockModel stock = ProductStockModel.create(1L, 10);
            assertThat(stock.canHold(11)).isFalse();
        }
    }

    @Nested
    @DisplayName("재고 검증")
    class ValidateCanHoldTests {

        @Test
        @DisplayName("충분한 재고가 있으면 예외 없이 통과한다")
        void validateCanHold_WhenSufficient_ShouldPass() {
            ProductStockModel stock = ProductStockModel.create(1L, 10);

            assertThatCode(() -> stock.validateCanHold(10)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("재고가 부족하면 CART_STOCK_EXCEEDED 예외가 발생한다")
        void validateCanHold_WhenInsufficient_ShouldThrowCartStockExceeded() {
            ProductStockModel stock = ProductStockModel.create(1L, 10);

            assertThatThrownBy(() -> stock.validateCanHold(11))
                    .isInstanceOf(CoreException.class)
                    .satisfies(ex -> assertThat(((CoreException) ex).getErrorType())
                            .isEqualTo(ErrorType.CART_STOCK_EXCEEDED));
        }
    }

    @Nested
    @DisplayName("재고 수정")
    class UpdateOnHandTests {

        @Test
        @DisplayName("유효한 수량으로 onHand 수정 성공")
        void updateOnHand_WithValidQty_ShouldUpdate() {
            ProductStockModel stock = ProductStockModel.create(1L, 100);
            stock.updateOnHand(200);
            assertThat(stock.getOnHand()).isEqualTo(200);
        }

        @Test
        @DisplayName("reserved보다 작은 onHand로 수정 시 CoreException 발생")
        void updateOnHand_WhenNewOnHandLessThanReserved_ShouldThrow() {
            ProductStockModel stock = ProductStockModel.createWithReserved(1L, 100, 50);
            assertThatThrownBy(() -> stock.updateOnHand(30))
                    .isInstanceOf(CoreException.class);
        }
    }
}

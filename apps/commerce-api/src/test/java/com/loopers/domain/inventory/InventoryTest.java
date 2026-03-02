package com.loopers.domain.inventory;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.InventoryErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class InventoryTest {

    @DisplayName("생성할 때,")
    @Nested
    class 생성 {

        @Test
        void 유효한_정보면_reservedQty가_0으로_생성된다() {
            // act
            Inventory inventory = Inventory.create(1L, 100);

            // assert
            assertThat(inventory)
                    .extracting(Inventory::getProductId, Inventory::getQuantity, Inventory::getReservedQty)
                    .containsExactly(1L, 100, 0);
        }
    }

    @DisplayName("가용 재고를 조회할 때,")
    @Nested
    class 가용재고조회 {

        @Test
        void quantity에서_reservedQty를_뺀_값을_반환한다() {
            // arrange
            Inventory inventory = Inventory.create(1L, 100);
            inventory.reserve(30);

            // act & assert
            assertThat(inventory.getAvailableQuantity()).isEqualTo(70);
        }
    }

    @DisplayName("예약할 때,")
    @Nested
    class 예약 {

        @Test
        void 요청_수량이_0_이하이면_예외가_발생한다() {
            // arrange
            Inventory inventory = Inventory.create(1L, 100);

            // act & assert
            assertThatThrownBy(() -> inventory.reserve(0))
                    .isInstanceOf(CoreException.class)
                    .extracting(e -> ((CoreException) e).getErrorType())
                    .isEqualTo(InventoryErrorType.INVALID_QUANTITY);
        }

        @Test
        void 가용재고가_부족하면_예외가_발생한다() {
            // arrange
            Inventory inventory = Inventory.create(1L, 10);

            // act & assert
            assertThatThrownBy(() -> inventory.reserve(11))
                    .isInstanceOf(CoreException.class)
                    .extracting(e -> ((CoreException) e).getErrorType())
                    .isEqualTo(InventoryErrorType.INSUFFICIENT_STOCK);
        }

        @Test
        void 가용재고가_충분하면_reservedQty가_증가한다() {
            // arrange
            Inventory inventory = Inventory.create(1L, 100);

            // act
            inventory.reserve(30);

            // assert
            assertThat(inventory.getReservedQty()).isEqualTo(30);
        }
    }

    @DisplayName("확정할 때,")
    @Nested
    class 확정 {

        @Test
        void quantity와_reservedQty가_모두_감소한다() {
            // arrange
            Inventory inventory = Inventory.create(1L, 100);
            inventory.reserve(30);

            // act
            inventory.commit(30);

            // assert
            assertThat(inventory)
                    .extracting(Inventory::getQuantity, Inventory::getReservedQty)
                    .containsExactly(70, 0);
        }
    }

    @DisplayName("예약 해제할 때,")
    @Nested
    class 예약해제 {

        @Test
        void reservedQty만_감소한다() {
            // arrange
            Inventory inventory = Inventory.create(1L, 100);
            inventory.reserve(30);

            // act
            inventory.release(30);

            // assert
            assertThat(inventory)
                    .extracting(Inventory::getQuantity, Inventory::getReservedQty)
                    .containsExactly(100, 0);
        }
    }
}

package com.loopers.domain.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

import com.loopers.support.error.CoreException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("OrderItemModel 단위 테스트")
class OrderItemModelTest {

    @DisplayName("생성할 때, ")
    @Nested
    class Create {

        @DisplayName("유효한 값이면 생성에 성공한다 (orderId 없이, 스냅샷 포함)")
        @Test
        void create_withValidValues() {
            // act
            OrderItemModel orderItem = OrderItemModel.create(
                    10L, 25000, 2, "상품A", ("브랜드A"));

            // assert
            assertAll(
                    () -> assertThat(orderItem.getProductId()).isEqualTo(10L),
                    () -> assertThat(orderItem.getOrderPrice()).isEqualTo(25000),
                    () -> assertThat(orderItem.getQuantity()).isEqualTo(2),
                    () -> assertThat(orderItem.getProductName()).isEqualTo("상품A"),
                    () -> assertThat(orderItem.getBrandName()).isEqualTo("브랜드A"));
        }

        @DisplayName("productId가 null이면 예외가 발생한다")
        @Test
        void create_withNullProductId_throwsException() {
            assertThatThrownBy(() -> OrderItemModel.create(
                    null, 25000, 2, "상품A", "브랜드A"))
                    .isInstanceOf(CoreException.class);
        }

        @DisplayName("orderPrice가 음수이면 예외가 발생한다")
        @Test
        void create_withNegativeOrderPrice_throwsException() {
            assertThatThrownBy(() -> OrderItemModel.create(
                    10L, -1, 2, "상품A", "브랜드A"))
                    .isInstanceOf(CoreException.class);
        }

        @DisplayName("quantity가 0이면 예외가 발생한다")
        @Test
        void create_withZeroQuantity_throwsException() {
            assertThatThrownBy(() -> OrderItemModel.create(
                    10L, 25000, 0, "상품A", "브랜드A"))
                    .isInstanceOf(CoreException.class);
        }

        @DisplayName("productName이 빈 문자열이면 예외가 발생한다")
        @Test
        void create_withBlankProductName_throwsException() {
            assertThatThrownBy(() -> OrderItemModel.create(
                    10L, 25000, 2, "  ", "브랜드A"))
                    .isInstanceOf(CoreException.class);
        }

        @DisplayName("brandName이 null이면 예외가 발생한다")
        @Test
        void create_withNullBrandName_throwsException() {
            assertThatThrownBy(() -> OrderItemModel.create(
                    10L, 25000, 2, "상품A", null))
                    .isInstanceOf(CoreException.class);
        }

        @DisplayName("orderPrice가 0이면 정상 생성된다")
        @Test
        void create_withZeroOrderPrice_succeeds() {
            // act
            OrderItemModel item = OrderItemModel.create(
                    10L, 0, 1, "상품A", "브랜드A");

            // assert
            assertThat(item.getOrderPrice()).isZero();
        }
    }

    @DisplayName("취소할 때, ")
    @Nested
    class Cancel {

        @DisplayName("ORDERED 상태의 아이템이 CANCELLED로 변경된다")
        @Test
        void cancel_success() {
            // arrange
            OrderItemModel orderItem = OrderItemModel.create(
                    10L, 25000, 2, "상품A", ("브랜드A"));

            // act
            orderItem.cancel();

            // assert
            assertThat(orderItem.getStatus()).isEqualTo(OrderItemStatus.CANCELLED);
        }

        @DisplayName("이미 CANCELLED인 아이템을 취소하면 예외가 발생한다")
        @Test
        void cancel_alreadyCancelled_throwsException() {
            // arrange
            OrderItemModel orderItem = OrderItemModel.create(
                    10L, 25000, 2, "상품A", ("브랜드A"));
            orderItem.cancel();

            // act & assert
            assertThatThrownBy(() -> orderItem.cancel())
                    .isInstanceOf(CoreException.class);
        }
    }
}

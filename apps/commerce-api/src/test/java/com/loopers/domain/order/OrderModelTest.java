package com.loopers.domain.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

import com.loopers.support.error.CoreException;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("OrderModel 단위 테스트")
class OrderModelTest {

    private static final AtomicLong ID_GENERATOR = new AtomicLong(1);

    private static void setId(Object entity, long id) {
        try {
            var idField = entity.getClass().getSuperclass().getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(entity, id);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    private static OrderItemModel createItemWithId(Long productId, int orderPrice, int quantity,
                                                   String productName, String brandName) {
        OrderItemModel item = OrderItemModel.create(
                productId, orderPrice, quantity, productName, brandName);
        setId(item, ID_GENERATOR.getAndIncrement());
        return item;
    }

    @DisplayName("생성할 때, ")
    @Nested
    class Create {

        @DisplayName("유효한 값이면 ORDERED 상태로 생성되고 totalPrice가 items로부터 계산된다")
        @Test
        void create_withValidValues() {
            // arrange
            List<OrderItemModel> items = List.of(
                    OrderItemModel.create(10L, 25000, 2, "상품A", ("브랜드A")));

            // act
            OrderModel order = OrderModel.create(1L, items);

            // assert
            assertAll(
                    () -> assertThat(order.getUserId()).isEqualTo(1L),
                    () -> assertThat(order.getTotalPrice()).isEqualTo(50000),
                    () -> assertThat(order.getStatus()).isEqualTo(OrderStatus.ORDERED),
                    () -> assertThat(order.getItems()).hasSize(1),
                    () -> assertThat(order.getItems().get(0).getOrder()).isSameAs(order));
        }

        @DisplayName("items가 비어있으면 예외가 발생한다")
        @Test
        void create_withEmptyItems_throwsException() {
            assertThatThrownBy(() -> OrderModel.create(1L, List.of()))
                    .isInstanceOf(CoreException.class);
        }

        @DisplayName("userId가 null이면 예외가 발생한다")
        @Test
        void create_withNullUserId_throwsException() {
            // arrange
            List<OrderItemModel> items = List.of(
                    OrderItemModel.create(10L, 25000, 1, "상품A", ("브랜드A")));

            // act & assert
            assertThatThrownBy(() -> OrderModel.create(null, items))
                    .isInstanceOf(CoreException.class);
        }

        @DisplayName("items가 null이면 예외가 발생한다")
        @Test
        void create_withNullItems_throwsException() {
            assertThatThrownBy(() -> OrderModel.create(1L, null))
                    .isInstanceOf(CoreException.class);
        }
    }

    @DisplayName("소유자 검증할 때, ")
    @Nested
    class ValidateOwner {

        @DisplayName("본인 주문이 아니면 예외가 발생한다")
        @Test
        void validateOwner_notOwner_throwsException() {
            // arrange
            OrderModel order = OrderModel.create(1L, List.of(
                    OrderItemModel.create(10L, 25000, 1, "상품A", ("브랜드A"))));

            // act & assert
            assertThatThrownBy(() -> order.validateOwner(999L))
                    .isInstanceOf(CoreException.class);
        }
    }

    @DisplayName("아이템을 취소할 때, ")
    @Nested
    class CancelItem {

        @DisplayName("취소된 아이템을 제외하고 totalPrice가 재계산된다")
        @Test
        void cancelItem_recalculatesTotalPrice() {
            // arrange
            OrderItemModel item1 = createItemWithId(10L, 25000, 2, "상품A", "브랜드A");
            OrderItemModel item2 = createItemWithId(20L, 30000, 1, "상품B", "브랜드B");
            OrderModel order = OrderModel.create(1L, List.of(item1, item2));

            // act
            order.cancelItem(item1.getId());

            // assert
            assertAll(
                    () -> assertThat(order.getTotalPrice()).isEqualTo(30000),
                    () -> assertThat(order.getOriginalTotalPrice()).isEqualTo(80000),
                    () -> assertThat(item1.getStatus()).isEqualTo(OrderItemStatus.CANCELLED));
        }

        @DisplayName("모든 아이템이 취소되면 주문 상태가 CANCELLED로 변경된다")
        @Test
        void cancelItem_allItemsCancelled_orderCancelled() {
            // arrange
            OrderItemModel item1 = createItemWithId(10L, 25000, 2, "상품A", "브랜드A");
            OrderItemModel item2 = createItemWithId(20L, 30000, 1, "상품B", "브랜드B");
            OrderModel order = OrderModel.create(1L, List.of(item1, item2));

            // act
            order.cancelItem(item1.getId());
            order.cancelItem(item2.getId());

            // assert
            assertAll(
                    () -> assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED),
                    () -> assertThat(order.getTotalPrice()).isEqualTo(0));
        }

        @DisplayName("존재하지 않는 아이템 ID로 취소하면 예외가 발생한다")
        @Test
        void cancelItem_itemNotFound_throwsException() {
            // arrange
            OrderItemModel item = createItemWithId(10L, 25000, 1, "상품A", "브랜드A");
            OrderModel order = OrderModel.create(1L, List.of(item));

            // act & assert
            assertThatThrownBy(() -> order.cancelItem(999L))
                    .isInstanceOf(CoreException.class);
        }

        @DisplayName("이미 CANCELLED인 주문에서 취소하면 예외가 발생한다")
        @Test
        void cancelItem_orderAlreadyCancelled_throwsException() {
            // arrange
            OrderItemModel item = createItemWithId(10L, 25000, 1, "상품A", "브랜드A");
            OrderModel order = OrderModel.create(1L, List.of(item));
            order.cancelItem(item.getId());

            // act & assert
            assertThatThrownBy(() -> order.cancelItem(item.getId()))
                    .isInstanceOf(CoreException.class);
        }

        @DisplayName("할인 적용 주문에서 부분 취소 시 할인이 유지된다")
        @Test
        void cancelItem_withDiscount_maintainsDiscount() {
            // arrange
            OrderItemModel item1 = createItemWithId(10L, 30000, 1, "상품A", "브랜드A");
            OrderItemModel item2 = createItemWithId(20L, 20000, 1, "상품B", "브랜드B");
            OrderModel order = OrderModel.create(1L, List.of(item1, item2), 5000);
            // originalTotalPrice=50000, totalPrice=45000

            // act
            order.cancelItem(item1.getId());

            // assert — 남은 아이템 20000 - 할인 5000 = 15000
            assertAll(
                    () -> assertThat(order.getTotalPrice()).isEqualTo(15000),
                    () -> assertThat(order.getOriginalTotalPrice()).isEqualTo(50000),
                    () -> assertThat(order.getStatus()).isEqualTo(OrderStatus.ORDERED));
        }

        @DisplayName("할인이 남은 아이템 합보다 클 때 totalPrice는 0이다")
        @Test
        void cancelItem_withDiscountExceedingRemaining_totalPriceIsZero() {
            // arrange
            OrderItemModel item1 = createItemWithId(10L, 8000, 1, "상품A", "브랜드A");
            OrderItemModel item2 = createItemWithId(20L, 2000, 1, "상품B", "브랜드B");
            OrderModel order = OrderModel.create(1L, List.of(item1, item2), 5000);

            // act — 8000짜리 취소 → 남은 2000 - 할인 5000 = -3000 → 0
            order.cancelItem(item1.getId());

            // assert
            assertThat(order.getTotalPrice()).isZero();
        }

        @DisplayName("전체 취소 후 originalTotalPrice는 변하지 않는다")
        @Test
        void cancelItem_allCancelled_originalTotalPriceUnchanged() {
            // arrange
            OrderItemModel item1 = createItemWithId(10L, 25000, 2, "상품A", "브랜드A");
            OrderItemModel item2 = createItemWithId(20L, 30000, 1, "상품B", "브랜드B");
            OrderModel order = OrderModel.create(1L, List.of(item1, item2));

            // act
            order.cancelItem(item1.getId());
            order.cancelItem(item2.getId());

            // assert
            assertAll(
                    () -> assertThat(order.getTotalPrice()).isZero(),
                    () -> assertThat(order.getOriginalTotalPrice()).isEqualTo(80000));
        }
    }

    @DisplayName("할인을 적용할 때, ")
    @Nested
    class ApplyDiscount {

        @DisplayName("totalPrice가 originalTotalPrice - discountAmount로 계산된다")
        @Test
        void applyDiscount_calculatesTotalPrice() {
            // arrange
            OrderModel order = OrderModel.create(1L, List.of(
                    OrderItemModel.create(10L, 50000, 1, "상품A", "브랜드A")));

            // act
            order.applyDiscount(5000);

            // assert
            assertAll(
                    () -> assertThat(order.getTotalPrice()).isEqualTo(45000),
                    () -> assertThat(order.getOriginalTotalPrice()).isEqualTo(50000),
                    () -> assertThat(order.getDiscountAmount()).isEqualTo(5000));
        }

        @DisplayName("할인이 총액보다 커도 totalPrice는 0이다")
        @Test
        void applyDiscount_exceedsTotalPrice_totalPriceIsZero() {
            // arrange
            OrderModel order = OrderModel.create(1L, List.of(
                    OrderItemModel.create(10L, 3000, 1, "상품A", "브랜드A")));

            // act
            order.applyDiscount(5000);

            // assert
            assertThat(order.getTotalPrice()).isZero();
        }
    }
}

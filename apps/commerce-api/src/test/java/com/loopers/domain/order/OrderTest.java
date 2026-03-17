package com.loopers.domain.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.loopers.domain.shared.Money;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

class OrderTest {

    @DisplayName("주문을 생성할 때,")
    @Nested
    class Create {

        @DisplayName("상품 1건이면, 주문 이름은 상품명이다.")
        @Test
        void setsOrderName_whenSingleItem() {
            // arrange
            var cart = new Cart(1L, List.of(
                    new Cart.CartItem(1L, "테스트 상품", "https://thumb.png", Money.wons(10000L), 2L)
            ));

            // act
            var order = Order.create(cart, Money.ZERO, null);

            // assert
            assertThat(order.getName()).isEqualTo("테스트 상품");
        }

        @DisplayName("상품 2건 이상이면, 주문 이름은 '첫 상품명 외 N건'이다.")
        @Test
        void setsOrderName_whenMultipleItems() {
            // arrange
            var cart = new Cart(1L, List.of(
                    new Cart.CartItem(1L, "첫 번째 상품", "https://thumb1.png", Money.wons(10000L), 1L),
                    new Cart.CartItem(2L, "두 번째 상품", "https://thumb2.png", Money.wons(20000L), 1L),
                    new Cart.CartItem(3L, "세 번째 상품", "https://thumb3.png", Money.wons(30000L), 1L)
            ));

            // act
            var order = Order.create(cart, Money.ZERO, null);

            // assert
            assertThat(order.getName()).isEqualTo("첫 번째 상품 외 2건");
        }

        @DisplayName("주문 총액은 각 항목의 소계 합산이다.")
        @Test
        void calculatesTotalPrice() {
            // arrange
            var cart = new Cart(1L, List.of(
                    new Cart.CartItem(1L, "상품A", "https://a.png", Money.wons(10000L), 2L),
                    new Cart.CartItem(2L, "상품B", "https://b.png", Money.wons(5000L), 3L)
            ));

            // act
            var order = Order.create(cart, Money.ZERO, null);

            // assert
            assertThat(order.getTotalPrice()).isEqualTo(Money.wons(35000L));
        }

        @DisplayName("주문 상태는 CREATED이다.")
        @Test
        void setsStatusToCreated() {
            // arrange
            var cart = new Cart(1L, List.of(
                    new Cart.CartItem(1L, "상품", "https://thumb.png", Money.wons(10000L), 1L)
            ));

            // act
            var order = Order.create(cart, Money.ZERO, null);

            // assert
            assertThat(order.getStatus()).isEqualTo(OrderStatus.CREATED);
        }

        @DisplayName("주문 시각이 설정된다.")
        @Test
        void setsOrderedAt() {
            // arrange
            var cart = new Cart(1L, List.of(
                    new Cart.CartItem(1L, "상품", "https://thumb.png", Money.wons(10000L), 1L)
            ));

            // act
            var order = Order.create(cart, Money.ZERO, null);

            // assert
            assertThat(order.getOrderedAt()).isNotNull();
        }

        @DisplayName("주문 항목이 Order에 연결된다.")
        @Test
        void associatesItemsWithOrder() {
            // arrange
            var cart = new Cart(1L, List.of(
                    new Cart.CartItem(1L, "상품A", "https://a.png", Money.wons(10000L), 2L),
                    new Cart.CartItem(2L, "상품B", "https://b.png", Money.wons(5000L), 1L)
            ));

            // act
            var order = Order.create(cart, Money.ZERO, null);

            // assert
            assertAll(
                    () -> assertThat(order.getOrderItems()).hasSize(2),
                    () -> assertThat(order.getOrderItems()).allSatisfy(item ->
                            assertThat(item.getOrder()).isSameAs(order)
                    )
            );
        }

        @DisplayName("주문 항목이 없으면, REQUIRED_ORDER_ITEM 예외가 발생한다.")
        @Test
        void throwsException_whenNoOrderItems() {
            // arrange
            var cart = new Cart(1L, Collections.emptyList());

            // act & assert
            assertThatThrownBy(() -> Order.create(cart, Money.ZERO, null))
                    .isInstanceOf(CoreException.class)
                    .hasMessageContaining(ErrorType.REQUIRED_ORDER_ITEM.getMessage());
        }

        @DisplayName("동일 상품이 중복되면, DUPLICATE_ORDER_PRODUCT 예외가 발생한다.")
        @Test
        void throwsException_whenDuplicateProduct() {
            // arrange
            var cart = new Cart(1L, List.of(
                    new Cart.CartItem(1L, "상품A", "https://a.png", Money.wons(10000L), 1L),
                    new Cart.CartItem(1L, "상품A", "https://a.png", Money.wons(10000L), 2L)
            ));

            // act & assert
            assertThatThrownBy(() -> Order.create(cart, Money.ZERO, null))
                    .isInstanceOf(CoreException.class)
                    .hasMessageContaining(ErrorType.DUPLICATE_ORDER_PRODUCT.getMessage());
        }

        @DisplayName("쿠폰 할인을 적용하면, 세 가격 필드가 올바르게 설정된다.")
        @Test
        void setsPriceFields_whenCouponApplied() {
            // arrange
            var cart = new Cart(1L, List.of(
                    new Cart.CartItem(1L, "상품A", "https://a.png", Money.wons(10000L), 2L)
            ));

            // act
            var order = Order.create(cart, Money.wons(5000L), 100L);

            // assert
            assertAll(
                    () -> assertThat(order.getOriginalTotalPrice()).isEqualTo(Money.wons(20000L)),
                    () -> assertThat(order.getDiscountAmount()).isEqualTo(Money.wons(5000L)),
                    () -> assertThat(order.getTotalPrice()).isEqualTo(Money.wons(15000L)),
                    () -> assertThat(order.getOwnedCouponId()).isEqualTo(100L)
            );
        }

        @DisplayName("할인 금액이 주문 총액을 초과하면, 결제 금액은 0원이고 할인 금액은 주문 총액으로 보정된다.")
        @Test
        void capsDiscountToOriginalTotal_whenDiscountExceedsTotal() {
            // arrange
            var cart = new Cart(1L, List.of(
                    new Cart.CartItem(1L, "상품A", "https://a.png", Money.wons(8000L), 1L)
            ));

            // act
            var order = Order.create(cart, Money.wons(10000L), 100L);

            // assert
            assertAll(
                    () -> assertThat(order.getOriginalTotalPrice()).isEqualTo(Money.wons(8000L)),
                    () -> assertThat(order.getDiscountAmount()).isEqualTo(Money.wons(8000L)),
                    () -> assertThat(order.getTotalPrice()).isEqualTo(Money.ZERO)
            );
        }

        @DisplayName("쿠폰 미적용이면, originalTotalPrice = totalPrice이고 discountAmount = 0이다.")
        @Test
        void setsPriceFieldsWithoutDiscount_whenNoCoupon() {
            // arrange
            var cart = new Cart(1L, List.of(
                    new Cart.CartItem(1L, "상품A", "https://a.png", Money.wons(10000L), 2L)
            ));

            // act
            var order = Order.create(cart, Money.ZERO, null);

            // assert
            assertAll(
                    () -> assertThat(order.getOriginalTotalPrice()).isEqualTo(Money.wons(20000L)),
                    () -> assertThat(order.getDiscountAmount()).isEqualTo(Money.ZERO),
                    () -> assertThat(order.getTotalPrice()).isEqualTo(Money.wons(20000L)),
                    () -> assertThat(order.getOwnedCouponId()).isNull()
            );
        }
    }

    @DisplayName("결제 가능 여부를 검증할 때,")
    @Nested
    class ValidatePayable {

        @DisplayName("CREATED 상태이면, 예외가 발생하지 않는다.")
        @Test
        void doesNotThrow_whenStatusIsCreated() {
            // arrange
            var cart = new Cart(1L, List.of(
                    new Cart.CartItem(1L, "상품", "https://thumb.png", Money.wons(10000L), 1L)
            ));
            var order = Order.create(cart, Money.ZERO, null);

            // act & assert
            assertThatCode(() -> order.validatePayable()).doesNotThrowAnyException();
        }

        @DisplayName("PAID 상태이면, ORDER_NOT_PAYABLE 예외가 발생한다.")
        @Test
        void throwsException_whenStatusIsPaid() {
            // arrange
            var cart = new Cart(1L, List.of(
                    new Cart.CartItem(1L, "상품", "https://thumb.png", Money.wons(10000L), 1L)
            ));
            var order = Order.create(cart, Money.ZERO, null);
            order.pay();

            // act & assert
            assertThatThrownBy(() -> order.validatePayable())
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.ORDER_NOT_PAYABLE));
        }
    }

    @DisplayName("주문을 결제 완료할 때,")
    @Nested
    class Pay {

        @DisplayName("CREATED 상태이면, PAID로 변경된다.")
        @Test
        void changesStatusToPaid_whenCreated() {
            // arrange
            var cart = new Cart(1L, List.of(
                    new Cart.CartItem(1L, "상품", "https://thumb.png", Money.wons(10000L), 1L)
            ));
            var order = Order.create(cart, Money.ZERO, null);

            // act
            order.pay();

            // assert
            assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        }

        @DisplayName("PAID 상태이면, ORDER_NOT_PAYABLE 예외가 발생한다.")
        @Test
        void throwsException_whenAlreadyPaid() {
            // arrange
            var cart = new Cart(1L, List.of(
                    new Cart.CartItem(1L, "상품", "https://thumb.png", Money.wons(10000L), 1L)
            ));
            var order = Order.create(cart, Money.ZERO, null);
            order.pay();

            // act & assert
            assertThatThrownBy(() -> order.pay())
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.ORDER_NOT_PAYABLE));
        }
    }

    @DisplayName("주문 소유자를 검증할 때,")
    @Nested
    class ValidateOwner {

        @DisplayName("본인의 주문이면, 예외가 발생하지 않는다.")
        @Test
        void doesNotThrow_whenOwnerMatches() {
            // arrange
            var cart = new Cart(1L, List.of(
                    new Cart.CartItem(1L, "상품", "https://thumb.png", Money.wons(10000L), 1L)
            ));
            var order = Order.create(cart, Money.ZERO, null);

            // act & assert
            assertThatCode(() -> order.validateOwner(1L)).doesNotThrowAnyException();
        }

        @DisplayName("다른 사용자의 주문이면, FORBIDDEN_ORDER_ACCESS 예외가 발생한다.")
        @Test
        void throwsException_whenOwnerDoesNotMatch() {
            // arrange
            var cart = new Cart(1L, List.of(
                    new Cart.CartItem(1L, "상품", "https://thumb.png", Money.wons(10000L), 1L)
            ));
            var order = Order.create(cart, Money.ZERO, null);

            // act & assert
            assertThatThrownBy(() -> order.validateOwner(999L))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.FORBIDDEN_ORDER_ACCESS));
        }
    }
}

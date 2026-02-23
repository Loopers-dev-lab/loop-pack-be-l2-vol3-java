package com.loopers.domain.order;

import static org.assertj.core.api.Assertions.assertThat;
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
            var items = List.of(
                    OrderItem.create(1L, "테스트 상품", "https://thumb.png", Money.wons(10000L), 2L)
            );

            // act
            var order = Order.create(1L, items);

            // assert
            assertThat(order.getName()).isEqualTo("테스트 상품");
        }

        @DisplayName("상품 2건 이상이면, 주문 이름은 '첫 상품명 외 N건'이다.")
        @Test
        void setsOrderName_whenMultipleItems() {
            // arrange
            var items = List.of(
                    OrderItem.create(1L, "첫 번째 상품", "https://thumb1.png", Money.wons(10000L), 1L),
                    OrderItem.create(2L, "두 번째 상품", "https://thumb2.png", Money.wons(20000L), 1L),
                    OrderItem.create(3L, "세 번째 상품", "https://thumb3.png", Money.wons(30000L), 1L)
            );

            // act
            var order = Order.create(1L, items);

            // assert
            assertThat(order.getName()).isEqualTo("첫 번째 상품 외 2건");
        }

        @DisplayName("주문 총액은 각 항목의 소계 합산이다.")
        @Test
        void calculatesTotalPrice() {
            // arrange
            var items = List.of(
                    OrderItem.create(1L, "상품A", "https://a.png", Money.wons(10000L), 2L),
                    OrderItem.create(2L, "상품B", "https://b.png", Money.wons(5000L), 3L)
            );

            // act
            var order = Order.create(1L, items);

            // assert
            assertThat(order.getTotalPrice()).isEqualTo(Money.wons(35000L));
        }

        @DisplayName("주문 상태는 CREATED이다.")
        @Test
        void setsStatusToCreated() {
            // arrange
            var items = List.of(
                    OrderItem.create(1L, "상품", "https://thumb.png", Money.wons(10000L), 1L)
            );

            // act
            var order = Order.create(1L, items);

            // assert
            assertThat(order.getStatus()).isEqualTo(OrderStatus.CREATED);
        }

        @DisplayName("주문 시각이 설정된다.")
        @Test
        void setsOrderedAt() {
            // arrange
            var items = List.of(
                    OrderItem.create(1L, "상품", "https://thumb.png", Money.wons(10000L), 1L)
            );

            // act
            var order = Order.create(1L, items);

            // assert
            assertThat(order.getOrderedAt()).isNotNull();
        }

        @DisplayName("주문 항목이 Order에 연결된다.")
        @Test
        void associatesItemsWithOrder() {
            // arrange
            var items = List.of(
                    OrderItem.create(1L, "상품A", "https://a.png", Money.wons(10000L), 2L),
                    OrderItem.create(2L, "상품B", "https://b.png", Money.wons(5000L), 1L)
            );

            // act
            var order = Order.create(1L, items);

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
            assertThatThrownBy(() -> Order.create(1L, Collections.emptyList()))
                    .isInstanceOf(CoreException.class)
                    .hasMessageContaining(ErrorType.REQUIRED_ORDER_ITEM.getMessage());
        }

        @DisplayName("동일 상품이 중복되면, DUPLICATE_ORDER_PRODUCT 예외가 발생한다.")
        @Test
        void throwsException_whenDuplicateProduct() {
            // arrange
            var items = List.of(
                    OrderItem.create(1L, "상품A", "https://a.png", Money.wons(10000L), 1L),
                    OrderItem.create(1L, "상품A", "https://a.png", Money.wons(10000L), 2L)
            );

            // act & assert
            assertThatThrownBy(() -> Order.create(1L, items))
                    .isInstanceOf(CoreException.class)
                    .hasMessageContaining(ErrorType.DUPLICATE_ORDER_PRODUCT.getMessage());
        }
    }
}

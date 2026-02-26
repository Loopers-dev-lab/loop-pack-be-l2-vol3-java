package com.loopers.domain.order;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderItemTest {

    @Nested
    @DisplayName("주문 항목 생성")
    class Create {

        @Test
        @DisplayName("유효한 값으로 OrderItem 생성 성공")
        void createSuccess() {
            OrderItem item = new OrderItem(1L, 2, "강아지 사료", 10000, "퍼피박스");

            assertThat(item.productId()).isEqualTo(1L);
            assertThat(item.quantity()).isEqualTo(2);
            assertThat(item.snapshotProductName()).isEqualTo("강아지 사료");
            assertThat(item.snapshotPrice()).isEqualTo(10000);
            assertThat(item.snapshotBrandName()).isEqualTo("퍼피박스");
        }

        @Test
        @DisplayName("수량이 0이면 예외가 발생한다")
        void zeroQuantityFails() {
            assertThatThrownBy(() -> new OrderItem(1L, 0, "강아지 사료", 10000, "퍼피박스"))
                    .isInstanceOf(CoreException.class)
                    .satisfies(ex -> assertThat(((CoreException) ex).getErrorType()).isEqualTo(ErrorType.BAD_REQUEST));
        }

        @Test
        @DisplayName("수량이 음수이면 예외가 발생한다")
        void negativeQuantityFails() {
            assertThatThrownBy(() -> new OrderItem(1L, -1, "강아지 사료", 10000, "퍼피박스"))
                    .isInstanceOf(CoreException.class)
                    .satisfies(ex -> assertThat(((CoreException) ex).getErrorType()).isEqualTo(ErrorType.BAD_REQUEST));
        }

        @Test
        @DisplayName("상품명 스냅샷이 blank이면 예외가 발생한다")
        void blankProductNameFails() {
            assertThatThrownBy(() -> new OrderItem(1L, 1, "  ", 10000, "퍼피박스"))
                    .isInstanceOf(CoreException.class)
                    .satisfies(ex -> assertThat(((CoreException) ex).getErrorType()).isEqualTo(ErrorType.BAD_REQUEST));
        }

        @Test
        @DisplayName("브랜드명 스냅샷이 blank이면 예외가 발생한다")
        void blankBrandNameFails() {
            assertThatThrownBy(() -> new OrderItem(1L, 1, "강아지 사료", 10000, "  "))
                    .isInstanceOf(CoreException.class)
                    .satisfies(ex -> assertThat(((CoreException) ex).getErrorType()).isEqualTo(ErrorType.BAD_REQUEST));
        }

        @Test
        @DisplayName("가격 스냅샷이 음수이면 예외가 발생한다")
        void negativePriceFails() {
            assertThatThrownBy(() -> new OrderItem(1L, 1, "강아지 사료", -1, "퍼피박스"))
                    .isInstanceOf(CoreException.class)
                    .satisfies(ex -> assertThat(((CoreException) ex).getErrorType()).isEqualTo(ErrorType.BAD_REQUEST));
        }
    }

    @Nested
    @DisplayName("총 금액 계산")
    class TotalPrice {

        @Test
        @DisplayName("수량 * 단가를 반환한다")
        void calculateTotalPrice() {
            OrderItem item = new OrderItem(1L, 3, "강아지 사료", 5000, "퍼피박스");

            assertThat(item.totalPrice()).isEqualTo(15000);
        }
    }
}

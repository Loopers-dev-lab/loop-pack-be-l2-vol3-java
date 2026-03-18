package com.loopers.domain.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.loopers.domain.order.Cart;
import com.loopers.domain.order.Order;
import com.loopers.domain.shared.Money;
import com.loopers.infrastructure.order.persistence.OrderJpaRepository;
import com.loopers.support.BaseIntegrationTest;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

class PaymentServiceIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private OrderJpaRepository orderJpaRepository;

    private Order savedOrder;

    @BeforeEach
    void setUp() {
        Cart cart = new Cart(1L, List.of(
                new Cart.CartItem(1L, "테스트 상품", "https://thumb.png", Money.wons(50000L), 1L)
        ));
        savedOrder = orderJpaRepository.save(Order.create("test-order-key", cart, Money.ZERO, null));
    }

    @DisplayName("결제를 생성할 때,")
    @Nested
    class Create {

        @DisplayName("유효한 결제 정보이면, READY 상태로 DB에 저장된다.")
        @Test
        void savesPaymentWithReadyStatus_whenValidPayment() {
            // arrange
            PaymentMethod paymentMethod = new PaymentMethod(CardType.SHINHAN, "1234-5678-9012-3456");

            // act
            Payment result = paymentService.create(savedOrder, paymentMethod);

            // assert
            assertAll(
                    () -> assertThat(result.getId()).isNotNull(),
                    () -> assertThat(result.getUserId()).isEqualTo(savedOrder.getUserId()),
                    () -> assertThat(result.getOrderId()).isEqualTo(savedOrder.getId()),
                    () -> assertThat(result.getTransactionKey()).isNull(),
                    () -> assertThat(result.getCardType()).isEqualTo(CardType.SHINHAN),
                    () -> assertThat(result.getCardNo()).isEqualTo("1234-5678-9012-3456"),
                    () -> assertThat(result.getAmount()).isEqualTo(savedOrder.getTotalPrice()),
                    () -> assertThat(result.getStatus()).isEqualTo(PaymentStatus.READY)
            );
        }
    }

    @DisplayName("결제를 시작할 때,")
    @Nested
    class StartPaymentTest {

        @DisplayName("READY 상태이면, PENDING으로 전이되고 transactionKey가 할당된다.")
        @Test
        void transitionsToPendingWithTransactionKey() {
            // arrange
            PaymentMethod paymentMethod = new PaymentMethod(CardType.SHINHAN, "1234-5678-9012-3456");
            Payment payment = paymentService.create(savedOrder, paymentMethod);

            // act
            Payment result = paymentService.confirmPayment(payment.getId(), "txn-start-test");

            // assert
            assertAll(
                    () -> assertThat(result.getStatus()).isEqualTo(PaymentStatus.PENDING),
                    () -> assertThat(result.getTransactionKey()).isEqualTo("txn-start-test")
            );
        }
    }

    @DisplayName("거래 키로 결제를 조회할 때,")
    @Nested
    class GetByTransactionKey {

        @DisplayName("존재하는 거래 키이면, 결제를 반환한다.")
        @Test
        void returnsPayment_whenTransactionKeyExists() {
            // arrange
            PaymentMethod paymentMethod = new PaymentMethod(CardType.SHINHAN, "1234-5678-9012-3456");
            Payment payment = paymentService.create(savedOrder, paymentMethod);
            paymentService.confirmPayment(payment.getId(), "txn-find-test");

            // act
            Payment result = paymentService.getByTransactionKey("txn-find-test");

            // assert
            assertThat(result.getTransactionKey()).isEqualTo("txn-find-test");
        }

        @DisplayName("존재하지 않는 거래 키이면, PAYMENT_NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsException_whenTransactionKeyNotFound() {
            assertThatThrownBy(() -> paymentService.getByTransactionKey("non-existent-key"))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.PAYMENT_NOT_FOUND));
        }
    }
}

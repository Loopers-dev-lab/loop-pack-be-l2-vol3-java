package com.loopers.domain.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.time.ZonedDateTime;
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
import com.loopers.infrastructure.payment.persistence.PaymentJpaRepository;
import com.loopers.support.BaseIntegrationTest;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

class PaymentServiceIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private OrderJpaRepository orderJpaRepository;

    @Autowired
    private PaymentJpaRepository paymentJpaRepository;

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

        @DisplayName("존재하지 않는 결제 ID이면, PAYMENT_NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsException_whenPaymentNotFound() {
            assertThatThrownBy(() -> paymentService.confirmPayment(999L, "txn-not-found"))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.PAYMENT_NOT_FOUND));
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

    @DisplayName("PENDING 경과 결제를 조회할 때,")
    @Nested
    class GetPendingPaymentsBefore {

        @DisplayName("기준 시각 이전에 PENDING 상태가 된 결제만 반환한다.")
        @Test
        void returnsPendingPayments_whenUpdatedBeforeThreshold() {
            // arrange
            PaymentMethod paymentMethod = new PaymentMethod(CardType.SHINHAN, "1234-5678-9012-3456");
            Payment payment = paymentService.create(savedOrder, paymentMethod);
            paymentService.confirmPayment(payment.getId(), "txn-pending-test");

            ZonedDateTime threshold = ZonedDateTime.now().plusMinutes(1);

            // act
            List<Payment> result = paymentService.getPendingPaymentsBefore(threshold);

            // assert
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getTransactionKey()).isEqualTo("txn-pending-test");
        }

        @DisplayName("기준 시각 이후에 PENDING 상태가 된 결제는 반환하지 않는다.")
        @Test
        void excludesPendingPayments_whenUpdatedAfterThreshold() {
            // arrange
            PaymentMethod paymentMethod = new PaymentMethod(CardType.SHINHAN, "1234-5678-9012-3456");
            Payment payment = paymentService.create(savedOrder, paymentMethod);
            paymentService.confirmPayment(payment.getId(), "txn-recent-test");

            ZonedDateTime threshold = ZonedDateTime.now().minusMinutes(1);

            // act
            List<Payment> result = paymentService.getPendingPaymentsBefore(threshold);

            // assert
            assertThat(result).isEmpty();
        }

        @DisplayName("SUCCESS 상태의 결제는 반환하지 않는다.")
        @Test
        void excludesSuccessPayments() {
            // arrange
            PaymentMethod paymentMethod = new PaymentMethod(CardType.SHINHAN, "1234-5678-9012-3456");
            Payment payment = paymentService.create(savedOrder, paymentMethod);
            Payment confirmed = paymentService.confirmPayment(payment.getId(), "txn-success-test");
            confirmed.update(PaymentStatus.SUCCESS, null);
            paymentJpaRepository.save(confirmed);

            ZonedDateTime threshold = ZonedDateTime.now().plusMinutes(1);

            // act
            List<Payment> result = paymentService.getPendingPaymentsBefore(threshold);

            // assert
            assertThat(result).isEmpty();
        }
    }

    @DisplayName("READY 경과 결제를 조회할 때,")
    @Nested
    class GetReadyPaymentsBefore {

        @DisplayName("기준 시각 이전에 READY 상태인 결제만 반환한다.")
        @Test
        void returnsReadyPayments_whenUpdatedBeforeThreshold() {
            // arrange
            PaymentMethod paymentMethod = new PaymentMethod(CardType.SHINHAN, "1234-5678-9012-3456");
            paymentService.create(savedOrder, paymentMethod);

            ZonedDateTime threshold = ZonedDateTime.now().plusMinutes(1);

            // act
            List<Payment> result = paymentService.getReadyPaymentsBefore(threshold);

            // assert
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getStatus()).isEqualTo(PaymentStatus.READY);
        }

        @DisplayName("기준 시각 이후에 READY 상태인 결제는 반환하지 않는다.")
        @Test
        void excludesReadyPayments_whenUpdatedAfterThreshold() {
            // arrange
            PaymentMethod paymentMethod = new PaymentMethod(CardType.SHINHAN, "1234-5678-9012-3456");
            paymentService.create(savedOrder, paymentMethod);

            ZonedDateTime threshold = ZonedDateTime.now().minusMinutes(1);

            // act
            List<Payment> result = paymentService.getReadyPaymentsBefore(threshold);

            // assert
            assertThat(result).isEmpty();
        }

        @DisplayName("PENDING/SUCCESS 상태는 반환하지 않는다.")
        @Test
        void excludesPendingAndSuccessPayments() {
            // arrange
            PaymentMethod paymentMethod = new PaymentMethod(CardType.SHINHAN, "1234-5678-9012-3456");
            Payment payment = paymentService.create(savedOrder, paymentMethod);
            paymentService.confirmPayment(payment.getId(), "txn-not-ready");

            ZonedDateTime threshold = ZonedDateTime.now().plusMinutes(1);

            // act
            List<Payment> result = paymentService.getReadyPaymentsBefore(threshold);

            // assert
            assertThat(result).isEmpty();
        }
    }

    @DisplayName("결제를 실패 처리할 때,")
    @Nested
    class Fail {

        @DisplayName("READY 상태의 결제이면, FAILED로 전이되고 사유가 저장된다.")
        @Test
        void transitionsToFailed_whenReadyPayment() {
            // arrange
            PaymentMethod paymentMethod = new PaymentMethod(CardType.SHINHAN, "1234-5678-9012-3456");
            Payment payment = paymentService.create(savedOrder, paymentMethod);

            // act
            Payment result = paymentService.fail(payment.getId(), "PG 요청 타임아웃");

            // assert
            assertAll(
                    () -> assertThat(result.getStatus()).isEqualTo(PaymentStatus.FAILED),
                    () -> assertThat(result.getReason()).isEqualTo("PG 요청 타임아웃")
            );
        }

        @DisplayName("PENDING 상태의 결제이면, FAILED로 전이되고 사유가 저장된다.")
        @Test
        void transitionsToFailed_whenPendingPayment() {
            // arrange
            PaymentMethod paymentMethod = new PaymentMethod(CardType.SHINHAN, "1234-5678-9012-3456");
            Payment payment = paymentService.create(savedOrder, paymentMethod);
            paymentService.confirmPayment(payment.getId(), "txn-fail-test");

            // act
            Payment result = paymentService.fail(payment.getId(), "PG 승인 실패");

            // assert
            assertAll(
                    () -> assertThat(result.getStatus()).isEqualTo(PaymentStatus.FAILED),
                    () -> assertThat(result.getReason()).isEqualTo("PG 승인 실패")
            );
        }

        @DisplayName("존재하지 않는 결제 ID이면, PAYMENT_NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsException_whenPaymentNotFound() {
            assertThatThrownBy(() -> paymentService.fail(999L, "실패 사유"))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.PAYMENT_NOT_FOUND));
        }
    }
}

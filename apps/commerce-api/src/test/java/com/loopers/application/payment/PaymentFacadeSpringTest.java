package com.loopers.application.payment;

import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderItemSnapshot;
import com.loopers.domain.payment.CardType;
import com.loopers.domain.payment.Payment;
import com.loopers.domain.payment.PaymentStatus;
import com.loopers.infrastructure.client.PgPaymentDto;
import com.loopers.infrastructure.client.PgPaymentGateway;
import com.loopers.infrastructure.order.OrderJpaRepository;
import com.loopers.infrastructure.payment.PaymentJpaRepository;
import com.loopers.infrastructure.user.UserJpaRepository;
import com.loopers.domain.user.UserFixture;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@SpringBootTest
class PaymentFacadeSpringTest {

    @Autowired
    private PaymentFacade paymentFacade;

    @Autowired
    private PaymentJpaRepository paymentJpaRepository;

    @Autowired
    private OrderJpaRepository orderJpaRepository;

    @Autowired
    private UserJpaRepository userJpaRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @MockitoBean
    private PgPaymentGateway pgPaymentGateway;

    private Long userId;
    private Order order;

    @BeforeEach
    void setUp() {
        userId = userJpaRepository.save(UserFixture.builder().build()).getId();
        order = orderJpaRepository.save(Order.create(userId, List.of(new OrderItemSnapshot(1L, "상품", 10000L, 1))));
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("결제 요청 시, ")
    @Nested
    class RequestPayment {

        @DisplayName("PG 호출 성공 시 Payment가 DB에 저장되고 transactionKey가 할당된다.")
        @Test
        void savesPaymentWithTransactionKey_whenPgSucceeds() {
            // arrange
            given(pgPaymentGateway.requestPayment(anyString(), any()))
                    .willReturn(Optional.of(new PgPaymentDto.TransactionResponse("TXN-001", "PENDING", null)));

            // act
            PaymentInfo result = paymentFacade.requestPayment(userId, new PaymentCommand(order.getId(), CardType.SAMSUNG, "1234-5678-9012-3456"));

            // assert
            Payment saved = paymentJpaRepository.findById(result.id()).orElseThrow();
            assertAll(
                    () -> assertThat(saved.getStatus()).isEqualTo(PaymentStatus.PENDING),
                    () -> assertThat(saved.getPgTransactionKey()).isEqualTo("TXN-001")
            );
        }

        @DisplayName("PG 호출 실패(fallback) 시 Payment는 PENDING으로 저장되고 transactionKey는 null이다.")
        @Test
        void savesPaymentAsPending_whenPgFallback() {
            // arrange
            given(pgPaymentGateway.requestPayment(anyString(), any())).willReturn(Optional.empty());

            // act
            PaymentInfo result = paymentFacade.requestPayment(userId, new PaymentCommand(order.getId(), CardType.KB, "1234-5678-9012-3456"));

            // assert
            Payment saved = paymentJpaRepository.findById(result.id()).orElseThrow();
            assertAll(
                    () -> assertThat(saved.getStatus()).isEqualTo(PaymentStatus.PENDING),
                    () -> assertThat(saved.getPgTransactionKey()).isNull()
            );
        }

        @DisplayName("ORDERED 상태가 아닌 주문에 결제 요청 시 예외가 발생한다.")
        @Test
        void throwsException_whenOrderNotOrdered() {
            // arrange
            order.markPaid();
            orderJpaRepository.save(order);

            // act
            CoreException result = assertThrows(CoreException.class, () ->
                    paymentFacade.requestPayment(userId, new PaymentCommand(order.getId(), CardType.SAMSUNG, "1234-5678-9012-3456"))
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("PG 콜백 수신 시, ")
    @Nested
    class HandleCallback {

        @DisplayName("SUCCESS 콜백 수신 시 Payment는 COMPLETED, Order는 PAID로 전환된다.")
        @Test
        void completesPaymentAndPaysOrder_whenSuccessCallback() {
            // arrange
            Payment payment = paymentJpaRepository.save(
                    Payment.create(order.getId(), "pgOrderCode-001", CardType.SAMSUNG, "1234-5678-9012-3456", 10000L)
            );
            payment.assignPgTransaction("TXN-001");
            paymentJpaRepository.save(payment);

            // act
            paymentFacade.handleCallback(new PgCallbackCommand("TXN-001", "SUCCESS", null));

            // assert
            Payment updatedPayment = paymentJpaRepository.findById(payment.getId()).orElseThrow();
            Order updatedOrder = orderJpaRepository.findById(order.getId()).orElseThrow();
            assertAll(
                    () -> assertThat(updatedPayment.getStatus()).isEqualTo(PaymentStatus.COMPLETED),
                    () -> assertThat(updatedOrder.getStatus()).isEqualTo(Order.Status.PAID)
            );
        }

        @DisplayName("FAILED 콜백 수신 시 Payment는 FAILED, Order는 FAILED로 전환된다.")
        @Test
        void failsPaymentAndFailsOrder_whenFailedCallback() {
            // arrange
            Payment payment = paymentJpaRepository.save(
                    Payment.create(order.getId(), "pgOrderCode-002", CardType.KB, "1234-5678-9012-3456", 10000L)
            );
            payment.assignPgTransaction("TXN-002");
            paymentJpaRepository.save(payment);

            // act
            paymentFacade.handleCallback(new PgCallbackCommand("TXN-002", "FAILED", "한도초과입니다."));

            // assert
            Payment updatedPayment = paymentJpaRepository.findById(payment.getId()).orElseThrow();
            Order updatedOrder = orderJpaRepository.findById(order.getId()).orElseThrow();
            assertAll(
                    () -> assertThat(updatedPayment.getStatus()).isEqualTo(PaymentStatus.FAILED),
                    () -> assertThat(updatedPayment.getFailReason()).isEqualTo("한도초과입니다."),
                    () -> assertThat(updatedOrder.getStatus()).isEqualTo(Order.Status.FAILED)
            );
        }
    }

    @DisplayName("PG 결제 상태 조회 시, ")
    @Nested
    class SyncPayment {

        @DisplayName("PENDING 결제건을 PG에 조회해 SUCCESS면 COMPLETED로 업데이트된다.")
        @Test
        void completesPayment_whenPgReturnsSuccess() {
            // arrange
            Payment payment = paymentJpaRepository.save(
                    Payment.create(order.getId(), "pgOrderCode-003", CardType.HYUNDAI, "1234-5678-9012-3456", 10000L)
            );
            payment.assignPgTransaction("TXN-003");
            paymentJpaRepository.save(payment);

            given(pgPaymentGateway.getTransactionsByOrder(anyString(), anyString()))
                    .willReturn(Optional.of(new PgPaymentDto.OrderTransactionResponse(
                            "pgOrderCode-003",
                            List.of(new PgPaymentDto.TransactionSummary("TXN-003", "SUCCESS", null))
                    )));

            // act
            PaymentInfo result = paymentFacade.syncPayment(userId, payment.getId());

            // assert
            assertThat(result.status()).isEqualTo(PaymentStatus.COMPLETED);
            assertThat(orderJpaRepository.findById(order.getId()).orElseThrow().getStatus()).isEqualTo(Order.Status.PAID);
        }

        @DisplayName("이미 COMPLETED인 결제건은 PG 조회 없이 현재 상태를 반환한다.")
        @Test
        void returnsCurrentStatus_whenAlreadyCompleted() {
            // arrange
            Payment payment = paymentJpaRepository.save(
                    Payment.create(order.getId(), "pgOrderCode-004", CardType.SAMSUNG, "1234-5678-9012-3456", 10000L)
            );
            payment.assignPgTransaction("TXN-004");
            payment.complete();
            paymentJpaRepository.save(payment);

            // act
            PaymentInfo result = paymentFacade.syncPayment(userId, payment.getId());

            // assert
            assertThat(result.status()).isEqualTo(PaymentStatus.COMPLETED);
            then(pgPaymentGateway).should(never()).getTransactionsByOrder(anyString(), anyString());
        }

        @DisplayName("pgOrderCode로 PG 조회하여 SUCCESS면 COMPLETED로 전환되고 transactionKey가 저장된다.")
        @Test
        void completesPaymentWithTransactionKey_whenPgOrderCodeQueryReturnsSuccess() {
            // arrange
            Payment payment = paymentJpaRepository.save(
                    Payment.create(order.getId(), "pgOrderCode-005", CardType.SAMSUNG, "1234-5678-9012-3456", 10000L)
            );
            // pgTransactionKey 미할당(타임아웃 등의 케이스)

            given(pgPaymentGateway.getTransactionsByOrder(anyString(), anyString()))
                    .willReturn(Optional.of(new PgPaymentDto.OrderTransactionResponse(
                            "pgOrderCode-005",
                            List.of(new PgPaymentDto.TransactionSummary("TXN-005", "SUCCESS", null))
                    )));

            // act
            PaymentInfo result = paymentFacade.syncPayment(userId, payment.getId());

            // assert
            Payment updated = paymentJpaRepository.findById(payment.getId()).orElseThrow();
            assertAll(
                    () -> assertThat(result.status()).isEqualTo(PaymentStatus.COMPLETED),
                    () -> assertThat(updated.getPgTransactionKey()).isEqualTo("TXN-005"),
                    () -> assertThat(orderJpaRepository.findById(order.getId()).orElseThrow().getStatus()).isEqualTo(Order.Status.PAID)
            );
        }

        @DisplayName("pgOrderCode로 PG 조회하여 확정된 거래가 없으면 PENDING 유지된다.")
        @Test
        void keepsPending_whenPgOrderCodeQueryReturnsEmpty() {
            // arrange
            Payment payment = paymentJpaRepository.save(
                    Payment.create(order.getId(), "pgOrderCode-006", CardType.KB, "1234-5678-9012-3456", 10000L)
            );
            // pgTransactionKey 미할당(타임아웃 등의 케이스)

            given(pgPaymentGateway.getTransactionsByOrder(anyString(), anyString()))
                    .willReturn(Optional.empty());

            // act
            PaymentInfo result = paymentFacade.syncPayment(userId, payment.getId());

            // assert
            assertThat(result.status()).isEqualTo(PaymentStatus.PENDING);
        }
    }
}

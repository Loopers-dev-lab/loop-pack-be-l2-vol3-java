package com.loopers.application.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import com.loopers.domain.payment.CardType;
import com.loopers.domain.payment.PaymentModel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class PaymentStatusResultTest {

    @DisplayName("결제 상태를 변환할 때, ")
    @Nested
    class From {

        @DisplayName("PENDING 상태면 실패 정보 없이 반환한다.")
        @Test
        void from_whenPending_returnsNoFailure() {
            // arrange
            PaymentModel payment = PaymentModel.create(
                    1L, 50000, CardType.SAMSUNG, "****-****-****-1451");

            // act
            PaymentStatusResult result = PaymentStatusResult.from(payment);

            // assert
            assertAll(
                    () -> assertThat(result.paymentStatus()).isEqualTo("PENDING"),
                    () -> assertThat(result.failureCode()).isNull(),
                    () -> assertThat(result.failureMessage()).isNull());
        }

        @DisplayName("한도 초과로 실패하면 고객 친화적 메시지를 반환한다.")
        @Test
        void from_whenLimitExceeded_returnsCustomerMessage() {
            // arrange
            PaymentModel payment = createFailedPayment(
                    "LIMIT_EXCEEDED", "한도초과입니다.");

            // act
            PaymentStatusResult result = PaymentStatusResult.from(payment);

            // assert
            assertAll(
                    () -> assertThat(result.paymentStatus()).isEqualTo("FAILED"),
                    () -> assertThat(result.failureCode()).isEqualTo("LIMIT_EXCEEDED"),
                    () -> assertThat(result.failureMessage())
                            .isEqualTo("카드 한도가 초과되었습니다. 다른 카드로 결제해주세요."));
        }

        @DisplayName("잘못된 카드로 실패하면 고객 친화적 메시지를 반환한다.")
        @Test
        void from_whenInvalidCard_returnsCustomerMessage() {
            // arrange
            PaymentModel payment = createFailedPayment(
                    "INVALID_CARD", "잘못된 카드입니다.");

            // act
            PaymentStatusResult result = PaymentStatusResult.from(payment);

            // assert
            assertAll(
                    () -> assertThat(result.paymentStatus()).isEqualTo("FAILED"),
                    () -> assertThat(result.failureCode()).isEqualTo("INVALID_CARD"),
                    () -> assertThat(result.failureMessage())
                            .isEqualTo("카드 정보가 올바르지 않습니다. 카드 번호를 확인해주세요."));
        }

        @DisplayName("PG 요청 실패로 실패하면 고객 친화적 메시지를 반환한다.")
        @Test
        void from_whenPgRequestFailed_returnsCustomerMessage() {
            // arrange
            PaymentModel payment = createFailedPayment(
                    "PG_REQUEST_FAILED", "PG 요청 실패");

            // act
            PaymentStatusResult result = PaymentStatusResult.from(payment);

            // assert
            assertAll(
                    () -> assertThat(result.paymentStatus()).isEqualTo("FAILED"),
                    () -> assertThat(result.failureCode()).isEqualTo("PG_REQUEST_FAILED"),
                    () -> assertThat(result.failureMessage())
                            .isEqualTo("결제 요청에 실패했습니다. 다시 시도해주세요."));
        }

        @DisplayName("알 수 없는 에러 코드로 실패하면 기본 메시지를 반환한다.")
        @Test
        void from_whenUnknownError_returnsDefaultMessage() {
            // arrange
            PaymentModel payment = createFailedPayment(
                    "PG_ERROR", "알 수 없는 PG 오류");

            // act
            PaymentStatusResult result = PaymentStatusResult.from(payment);

            // assert
            assertAll(
                    () -> assertThat(result.paymentStatus()).isEqualTo("FAILED"),
                    () -> assertThat(result.failureCode()).isEqualTo("PG_ERROR"),
                    () -> assertThat(result.failureMessage())
                            .isEqualTo("결제 처리 중 오류가 발생했습니다. 다시 시도해주세요."));
        }

        @DisplayName("COMPLETED 상태면 실패 정보 없이 반환한다.")
        @Test
        void from_whenCompleted_returnsNoFailure() {
            // arrange
            PaymentModel payment = PaymentModel.create(
                    1L, 50000, CardType.SAMSUNG, "****-****-****-1451");
            payment.requested("TX_001");
            payment.complete();

            // act
            PaymentStatusResult result = PaymentStatusResult.from(payment);

            // assert
            assertAll(
                    () -> assertThat(result.paymentStatus()).isEqualTo("COMPLETED"),
                    () -> assertThat(result.failureCode()).isNull(),
                    () -> assertThat(result.failureMessage()).isNull());
        }
    }

    private PaymentModel createFailedPayment(String failureCode, String failureMessage) {
        PaymentModel payment = PaymentModel.create(
                1L, 50000, CardType.SAMSUNG, "****-****-****-1451");
        payment.fail(failureCode, failureMessage);
        return payment;
    }
}

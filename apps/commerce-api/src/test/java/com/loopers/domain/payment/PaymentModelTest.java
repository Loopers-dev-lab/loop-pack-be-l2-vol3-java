package com.loopers.domain.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.loopers.support.error.CoreException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class PaymentModelTest {

    @DisplayName("결제를 생성할 때, ")
    @Nested
    class Create {

        @DisplayName("PENDING 상태로 생성된다.")
        @Test
        void create_pendingStatus() {
            // act
            PaymentModel payment = PaymentModel.create(
                    1L, 50000, CardType.SAMSUNG, "****-****-****-1451");

            // assert
            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
            assertThat(payment.getPgTransactionId()).isNull();
            assertThat(payment.getFailureCode()).isNull();
        }
    }

    @DisplayName("PG 요청이 수락되면, ")
    @Nested
    class Requested {

        @DisplayName("REQUESTED 상태로 전이되고 pgTransactionId가 저장된다.")
        @Test
        void requested_transitionAndSaveKey() {
            // arrange
            PaymentModel payment = PaymentModel.create(
                    1L, 50000, CardType.SAMSUNG, "****-****-****-1451");

            // act
            payment.requested("TX_001");

            // assert
            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REQUESTED);
            assertThat(payment.getPgTransactionId()).isEqualTo("TX_001");
        }

        @DisplayName("REQUESTED 상태에서 다시 requested 호출 시 예외가 발생한다.")
        @Test
        void requested_fromRequested_throwsException() {
            // arrange
            PaymentModel payment = PaymentModel.create(
                    1L, 50000, CardType.SAMSUNG, "****-****-****-1451");
            payment.requested("TX_001");

            // act & assert
            assertThatThrownBy(() -> payment.requested("TX_002"))
                    .isInstanceOf(CoreException.class);
        }
    }

    @DisplayName("결제가 완료되면, ")
    @Nested
    class Complete {

        @DisplayName("COMPLETED 상태로 전이된다.")
        @Test
        void complete_fromRequested() {
            // arrange
            PaymentModel payment = PaymentModel.create(
                    1L, 50000, CardType.SAMSUNG, "****-****-****-1451");
            payment.requested("TX_001");

            // act
            payment.complete();

            // assert
            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
            assertThat(payment.getApprovedAt()).isNotNull();
        }

        @DisplayName("PENDING에서 complete 호출 시 예외가 발생한다.")
        @Test
        void complete_fromPending_throwsException() {
            // arrange
            PaymentModel payment = PaymentModel.create(
                    1L, 50000, CardType.SAMSUNG, "****-****-****-1451");

            // act & assert
            assertThatThrownBy(() -> payment.complete())
                    .isInstanceOf(CoreException.class);
        }
    }

    @DisplayName("결제가 실패하면, ")
    @Nested
    class Fail {

        @DisplayName("PENDING에서 FAILED로 전이된다.")
        @Test
        void fail_fromPending() {
            // arrange
            PaymentModel payment = PaymentModel.create(
                    1L, 50000, CardType.SAMSUNG, "****-****-****-1451");

            // act
            payment.fail();

            // assert
            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        }

        @DisplayName("REQUESTED에서 FAILED로 전이되고 실패 사유가 저장된다.")
        @Test
        void fail_fromRequested_withReason() {
            // arrange
            PaymentModel payment = PaymentModel.create(
                    1L, 50000, CardType.SAMSUNG, "****-****-****-1451");
            payment.requested("TX_001");

            // act
            payment.fail("LIMIT_EXCEEDED", "한도초과");

            // assert
            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
            assertThat(payment.getFailureCode()).isEqualTo("LIMIT_EXCEEDED");
            assertThat(payment.getFailureMessage()).isEqualTo("한도초과");
        }

        @DisplayName("COMPLETED에서 fail 호출 시 예외가 발생한다.")
        @Test
        void fail_fromCompleted_throwsException() {
            // arrange
            PaymentModel payment = PaymentModel.create(
                    1L, 50000, CardType.SAMSUNG, "****-****-****-1451");
            payment.requested("TX_001");
            payment.complete();

            // act & assert
            assertThatThrownBy(() -> payment.fail())
                    .isInstanceOf(CoreException.class);
        }
    }

    @DisplayName("터미널 상태를 확인할 때, ")
    @Nested
    class Terminal {

        @DisplayName("COMPLETED는 터미널 상태이다.")
        @Test
        void isTerminal_completed() {
            // arrange
            PaymentModel payment = PaymentModel.create(
                    1L, 50000, CardType.SAMSUNG, "****-****-****-1451");
            payment.requested("TX_001");
            payment.complete();

            // assert
            assertThat(payment.isTerminal()).isTrue();
        }

        @DisplayName("PENDING은 터미널 상태가 아니다.")
        @Test
        void isTerminal_pending() {
            // arrange
            PaymentModel payment = PaymentModel.create(
                    1L, 50000, CardType.SAMSUNG, "****-****-****-1451");

            // assert
            assertThat(payment.isTerminal()).isFalse();
        }
    }
}

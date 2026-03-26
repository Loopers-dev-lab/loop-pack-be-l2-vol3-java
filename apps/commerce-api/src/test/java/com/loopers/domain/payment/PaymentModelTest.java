package com.loopers.domain.payment;

import com.loopers.domain.BaseStringIdEntity;
import com.loopers.support.enums.CardType;
import com.loopers.support.enums.PaymentStatus;
import com.loopers.support.error.CoreException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("PaymentModel 도메인 모델 테스트")
class PaymentModelTest {

    private static final Long ORDER_ID = 1L;
    private static final Long USER_ID = 100L;
    private static final CardType CARD_TYPE = CardType.SAMSUNG;
    private static final String CARD_NO = "1234-5678-9012-3456";
    private static final BigDecimal AMOUNT = BigDecimal.valueOf(50000);

    private PaymentModel createTestPayment() {
        return PaymentModel.create(ORDER_ID, USER_ID, CARD_TYPE, CARD_NO, AMOUNT);
    }

    @Nested
    @DisplayName("생성 검증")
    class CreateTests {

        @Test
        @DisplayName("정상 입력으로 생성 시 REQUESTED 상태이다")
        void create_WithValidInputs_ShouldBeREQUESTED() {
            PaymentModel payment = createTestPayment();

            assertThat(payment.getOrderId()).isEqualTo(ORDER_ID);
            assertThat(payment.getUserId()).isEqualTo(USER_ID);
            assertThat(payment.getCardType()).isEqualTo(CARD_TYPE);
            assertThat(payment.getAmount()).isEqualByComparingTo(AMOUNT);
            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REQUESTED);
            assertThat(payment.getTransactionKey()).isNull();
            assertThat(payment.getPaidAt()).isNull();
        }

        @Test
        @DisplayName("카드번호가 마스킹 처리된다")
        void create_ShouldMaskCardNumber() {
            PaymentModel payment = createTestPayment();

            // 1234-5678-9012-3456 → 1234-56**-****-3456
            assertThat(payment.getCardNoMasked()).isEqualTo("1234-56**-****-3456");
        }

        @Test
        @DisplayName("BaseStringIdEntity를 상속한다")
        void create_ShouldExtendBaseStringIdEntity() {
            PaymentModel payment = createTestPayment();
            assertThat(payment).isInstanceOf(BaseStringIdEntity.class);
        }
    }

    @Nested
    @DisplayName("상태 전이 검증")
    class TransitToTests {

        @Test
        @DisplayName("REQUESTED에서 SUCCESS로 전이 성공")
        void transitTo_FromREQUESTED_ToSUCCESS_ShouldSucceed() {
            PaymentModel payment = createTestPayment();

            payment.transitTo(PaymentStatus.SUCCESS);

            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        }

        @Test
        @DisplayName("REQUESTED에서 FAILED로 전이 성공")
        void transitTo_FromREQUESTED_ToFAILED_ShouldSucceed() {
            PaymentModel payment = createTestPayment();

            payment.transitTo(PaymentStatus.FAILED);

            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        }

        @Test
        @DisplayName("REQUESTED에서 CANCELLED로 전이 성공")
        void transitTo_FromREQUESTED_ToCANCELLED_ShouldSucceed() {
            PaymentModel payment = createTestPayment();

            payment.transitTo(PaymentStatus.CANCELLED);

            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CANCELLED);
        }

        @Test
        @DisplayName("SUCCESS에서 FAILED로 전이 불가 — 예외 발생")
        void transitTo_FromSUCCESS_ToFAILED_ShouldThrow() {
            PaymentModel payment = createTestPayment();
            payment.transitTo(PaymentStatus.SUCCESS);

            assertThatThrownBy(() -> payment.transitTo(PaymentStatus.FAILED))
                    .isInstanceOf(CoreException.class);
        }

        @Test
        @DisplayName("SUCCESS로 전이 시 paidAt이 설정된다")
        void transitTo_SUCCESS_ShouldSetPaidAt() {
            PaymentModel payment = createTestPayment();

            payment.transitTo(PaymentStatus.SUCCESS);

            assertThat(payment.getPaidAt()).isNotNull();
        }

        @Test
        @DisplayName("FAILED로 전이 시 paidAt은 null이다")
        void transitTo_FAILED_ShouldNotSetPaidAt() {
            PaymentModel payment = createTestPayment();

            payment.transitTo(PaymentStatus.FAILED);

            assertThat(payment.getPaidAt()).isNull();
        }
    }

    @Nested
    @DisplayName("transactionKey 할당 검증")
    class AssignTransactionKeyTests {

        @Test
        @DisplayName("transactionKey 정상 설정")
        void assignTransactionKey_ShouldSet() {
            PaymentModel payment = createTestPayment();

            payment.assignTransactionKey("20250316:TR:abc123");

            assertThat(payment.getTransactionKey()).isEqualTo("20250316:TR:abc123");
        }
    }

    @Nested
    @DisplayName("failureReason 할당 검증")
    class AssignFailureReasonTests {

        @Test
        @DisplayName("failureReason 정상 설정")
        void assignFailureReason_ShouldSet() {
            PaymentModel payment = createTestPayment();

            payment.assignFailureReason("한도 초과");

            assertThat(payment.getFailureReason()).isEqualTo("한도 초과");
        }
    }

    @Nested
    @DisplayName("guard 검증")
    class GuardTests {

        @Test
        @DisplayName("orderId가 null이면 예외 발생")
        void guard_WithNullOrderId_ShouldThrow() {
            assertThatThrownBy(() -> PaymentModel.create(null, USER_ID, CARD_TYPE, CARD_NO, AMOUNT))
                    .isInstanceOf(CoreException.class);
        }

        @Test
        @DisplayName("userId가 null이면 예외 발생")
        void guard_WithNullUserId_ShouldThrow() {
            assertThatThrownBy(() -> PaymentModel.create(ORDER_ID, null, CARD_TYPE, CARD_NO, AMOUNT))
                    .isInstanceOf(CoreException.class);
        }

        @Test
        @DisplayName("amount가 0이면 예외 발생")
        void guard_WithZeroAmount_ShouldThrow() {
            assertThatThrownBy(() -> PaymentModel.create(ORDER_ID, USER_ID, CARD_TYPE, CARD_NO, BigDecimal.ZERO))
                    .isInstanceOf(CoreException.class);
        }

        @Test
        @DisplayName("amount가 음수이면 예외 발생")
        void guard_WithNegativeAmount_ShouldThrow() {
            assertThatThrownBy(() -> PaymentModel.create(ORDER_ID, USER_ID, CARD_TYPE, CARD_NO, BigDecimal.valueOf(-1)))
                    .isInstanceOf(CoreException.class);
        }
    }
}

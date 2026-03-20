package com.loopers.application.payment;

import com.loopers.domain.common.Money;
import com.loopers.domain.payment.Payment;
import com.loopers.domain.payment.PaymentStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("PaymentInfo 마스킹 테스트")
class PaymentInfoTest {

    @Nested
    @DisplayName("maskCardNo 직접 테스트")
    class MaskCardNoTest {

        @Test
        @DisplayName("16자리 카드번호는 마지막 4자리만 노출된다")
        void maskSixteenDigitCardNo() {
            String result = PaymentInfo.maskCardNo("4111111111111111");

            assertThat(result).isEqualTo("************1111");
        }

        @Test
        @DisplayName("4자리 이하 카드번호는 전체가 마스킹된다")
        void maskFourDigitCardNo() {
            String result = PaymentInfo.maskCardNo("4111");

            assertThat(result).isEqualTo("****");
        }

        @Test
        @DisplayName("null은 그대로 반환된다")
        void maskNullReturnsNull() {
            String result = PaymentInfo.maskCardNo(null);

            assertThat(result).isNull();
        }

        @Test
        @DisplayName("빈 문자열은 그대로 반환된다")
        void maskEmptyStringReturnsEmpty() {
            String result = PaymentInfo.maskCardNo("");

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("from() 팩토리를 통한 마스킹 검증")
    class FromFactoryTest {

        @Test
        @DisplayName("Payment에서 PaymentInfo 변환 시 카드번호가 마스킹된다")
        void fromPaymentMasksCardNo() {
            Payment payment = mock(Payment.class);
            when(payment.getId()).thenReturn(1L);
            when(payment.getOrderId()).thenReturn(10L);
            when(payment.getUserId()).thenReturn(100L);
            when(payment.getTransactionId()).thenReturn("txn-123");
            when(payment.getCardType()).thenReturn("VISA");
            when(payment.getCardNo()).thenReturn("4111111111111111");
            when(payment.getAmount()).thenReturn(Money.of(50000L));
            when(payment.getStatus()).thenReturn(PaymentStatus.SUCCESS);
            when(payment.getPgResponseMessage()).thenReturn("결제 성공");

            PaymentInfo info = PaymentInfo.from(payment);

            assertThat(info.getCardNo()).isEqualTo("************1111");
        }
    }
}

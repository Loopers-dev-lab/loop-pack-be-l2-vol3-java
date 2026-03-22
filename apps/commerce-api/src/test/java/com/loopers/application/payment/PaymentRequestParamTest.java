package com.loopers.application.payment;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 역할: PG로 넘길 {@link PaymentRequestParam} 생성 시 주문 최종 금액을
 * null 검증·반올림 후 정수 원으로 바꾸는 규칙을 단위 테스트한다 (06 §1.2).
 */
class PaymentRequestParamTest {

    @Nested
    @DisplayName("of 시")
    class Of {

        @Test
        @DisplayName("finalAmount가 null이면 BAD_REQUEST로 실패한다.")
        void of_withNullFinalAmount_shouldThrowBadRequest() {
            // given
            // when / then
            CoreException ex = assertThrows(CoreException.class,
                    () -> PaymentRequestParam.of(1L, "SAMSUNG", "1234", null, "http://cb"));
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @Test
        @DisplayName("정수 금액은 그대로 long으로 반영된다.")
        void of_withIntegerAmount_shouldUseSameValue() {
            // given
            PaymentRequestParam p = PaymentRequestParam.of(
                    10L, "SAMSUNG", "1234", new BigDecimal("20000"), "http://cb");
            // when
            // then
            assertThat(p.amount()).isEqualTo(20000L);
        }

        @Test
        @DisplayName("소수 이하는 HALF_UP으로 반올림 후 long으로 변환된다.")
        void of_withDecimalAmount_shouldRoundHalfUp() {
            // given
            PaymentRequestParam low = PaymentRequestParam.of(
                    1L, "A", "1", new BigDecimal("10000.49"), "http://cb");
            PaymentRequestParam high = PaymentRequestParam.of(
                    1L, "A", "1", new BigDecimal("10000.50"), "http://cb");
            // when
            // then
            assertThat(high.amount()).isEqualTo(10001L);
            assertThat(low.amount()).isEqualTo(10000L);
        }

        @Test
        @DisplayName("finalAmount가 long 범위를 초과하면 BAD_REQUEST로 실패한다.")
        void of_withOverflowAmount_shouldThrowBadRequest() {
            // given — Long.MAX_VALUE(9223372036854775807)를 1 초과
            BigDecimal overflow = new BigDecimal("9223372036854775808");

            // when / then
            CoreException ex = assertThrows(CoreException.class,
                    () -> PaymentRequestParam.of(1L, "SAMSUNG", "1234", overflow, "http://cb"));
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
            assertThat(ex.getCause()).isNotNull();
        }

        @Test
        @DisplayName("toString에는 카드번호(cardNo)가 노출되지 않는다.")
        void toString_shouldNotExposeCardNo() {
            // given
            String cardNo = "1234-5678-9814-1451";

            PaymentRequestParam p = PaymentRequestParam.of(
                    1L, "SAMSUNG", cardNo, new BigDecimal("20000"), "http://cb");

            // when
            String str = p.toString();

            // then
            assertThat(str).doesNotContain(cardNo);
            assertThat(str).contains("orderId=1");
            assertThat(str).contains("cardType=SAMSUNG");
        }
    }
}

package com.loopers.application.payment;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * PG 요청 파라미터 금액 변환 (06-payment-change-issues §1.2: 반올림 후 정수 원).
 */
class PaymentRequestParamTest {

    @Nested
    @DisplayName("of 시")
    class Of {

        @Test
        @DisplayName("finalAmount가 null이면 IllegalArgumentException을 던진다.")
        void of_withNullFinalAmount_shouldThrowIAE() {
            // given
            // when / then
            assertThrows(IllegalArgumentException.class,
                    () -> PaymentRequestParam.of(1L, "SAMSUNG", "1234", null, "http://cb"));
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
    }
}

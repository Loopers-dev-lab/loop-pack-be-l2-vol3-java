package com.loopers.application.payment;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PaymentCallbackParamTest {

    @Test
    @DisplayName("orderId가 null이면 BAD_REQUEST로 실패한다.")
    void constructor_whenOrderIdNull_shouldThrowBadRequest() {
        CoreException ex = assertThrows(CoreException.class,
                () -> new PaymentCallbackParam(null, true, "pg-tx", null, 100L));
        assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
    }

    @Test
    @DisplayName("amount가 음수이면 BAD_REQUEST로 실패한다.")
    void constructor_whenNegativeAmount_shouldThrowBadRequest() {
        CoreException ex = assertThrows(CoreException.class,
                () -> new PaymentCallbackParam(1L, false, null, null, -1L));
        assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
    }

    @Test
    @DisplayName("success가 true인데 pgTransactionId가 null이면 BAD_REQUEST로 실패한다.")
    void constructor_whenSuccessTrue_andPgTransactionIdNull_shouldThrowBadRequest() {
        CoreException ex = assertThrows(CoreException.class,
                () -> new PaymentCallbackParam(1L, true, null, null, 100L));
        assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
    }

    @Test
    @DisplayName("success가 true인데 pgTransactionId가 blank이면 BAD_REQUEST로 실패한다.")
    void constructor_whenSuccessTrue_andPgTransactionIdBlank_shouldThrowBadRequest() {
        CoreException ex = assertThrows(CoreException.class,
                () -> new PaymentCallbackParam(1L, true, "   ", null, 100L));
        assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
    }
}


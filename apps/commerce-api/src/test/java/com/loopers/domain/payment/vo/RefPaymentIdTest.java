package com.loopers.domain.payment.vo;

import com.loopers.support.error.CoreException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("RefPaymentId VO 테스트")
class RefPaymentIdTest {

    @Test
    @DisplayName("양수 Long 값으로 RefPaymentId 생성 성공")
    void create_withPositiveLong_success() {
        RefPaymentId refPaymentId = new RefPaymentId(1L);

        assertThat(refPaymentId.value()).isEqualTo(1L);
    }

    @Test
    @DisplayName("null 값으로 생성 시 예외 발생")
    void create_withNull_throwsException() {
        assertThatThrownBy(() -> new RefPaymentId(null))
                .isInstanceOf(CoreException.class);
    }

    @Test
    @DisplayName("0 이하 값으로 생성 시 예외 발생")
    void create_withZeroOrNegative_throwsException() {
        assertThatThrownBy(() -> new RefPaymentId(0L))
                .isInstanceOf(CoreException.class);

        assertThatThrownBy(() -> new RefPaymentId(-1L))
                .isInstanceOf(CoreException.class);
    }
}

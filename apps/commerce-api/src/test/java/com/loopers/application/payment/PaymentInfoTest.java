package com.loopers.application.payment;

import com.loopers.domain.payment.PaymentModel;
import com.loopers.domain.payment.PaymentStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 결제 응답 DTO와 도메인·콜백 매핑 (06 §5.1, §10.2).
 * 콜백 {@code paymentId}는 {@link PaymentModel#getPgTransactionId()}에 저장된다.
 */
class PaymentInfoTest {

    @Test
    @DisplayName("PENDING 결제는 pgTransactionId가 null이다.")
    void from_whenPending_shouldHaveNullPgTransactionId() {
        // given
        PaymentModel p = PaymentModel.createPending(42L);
        // when
        PaymentInfo info = PaymentInfo.from(p);
        // then — BaseEntity는 미영속 id를 0L로 둔다 (modules/jpa BaseEntity)
        assertThat(info.paymentId()).isEqualTo(0L);
        assertThat(info.orderId()).isEqualTo(42L);
        assertThat(info.status()).isEqualTo(PaymentStatus.PENDING.name());
        assertThat(info.pgTransactionId()).isNull();
    }

    @Test
    @DisplayName("markSuccess 후 pgTransactionId는 PG paymentId(거래 식별자 문자열)와 동일하다.")
    void from_afterMarkSuccess_shouldExposePgTransactionIdAsCallbackPaymentId() {
        // given
        PaymentModel p = PaymentModel.createPending(7L);
        String pgPaymentId = "pg-simulator-payment-id-99";
        p.markSuccess(pgPaymentId);
        // when
        PaymentInfo info = PaymentInfo.from(p);
        // then — API 응답 pgTransactionId 필드 = 콜백 paymentId 적재 값
        assertThat(info.pgTransactionId()).isEqualTo(pgPaymentId);
        assertThat(info.status()).isEqualTo(PaymentStatus.SUCCESS.name());
    }

    @Test
    @DisplayName("pending 팩토리는 DB id 없이 응답용 스냅샷을 만든다.")
    void pending_factory_shouldMatchStatusAndNullPgTxn() {
        PaymentInfo info = PaymentInfo.pending(100L, 200L);
        assertThat(info.paymentId()).isEqualTo(100L);
        assertThat(info.orderId()).isEqualTo(200L);
        assertThat(info.status()).isEqualTo("PENDING");
        assertThat(info.pgTransactionId()).isNull();
    }
}

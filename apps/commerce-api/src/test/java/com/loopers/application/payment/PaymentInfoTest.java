package com.loopers.application.payment;

import com.loopers.domain.payment.PaymentModel;
import com.loopers.domain.payment.PaymentStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 역할: API/애플리케이션 레이어 {@link PaymentInfo}가 도메인 {@link PaymentModel} 상태를 어떻게 노출하는지 단위 검증한다.
 * - 콜백 {@code paymentId} ↔ {@link PaymentModel#getPgTransactionId()} 매핑 문서화.
 * - 미영속 엔티티의 {@code paymentId}는 {@code BaseEntity} 관례(0L)를 따른다.
 */
class PaymentInfoTest {

    /** PENDING + PG 미확정: pgTransactionId null, paymentId는 미영속 0L. */
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

    /** 결제 성공 시 PG 거래 식별자가 응답 필드 pgTransactionId로 그대로 나간다. */
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

    /** Facade가 PG 호출 직후 쓰는 pending() 스냅샷 팩토리의 필드 기본값 검증. */
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

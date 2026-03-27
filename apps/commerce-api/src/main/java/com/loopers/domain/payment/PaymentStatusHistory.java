package com.loopers.domain.payment;

import com.loopers.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 결제 상태 전이 감사 로그.
 *
 * <p>Event Sourcing의 경량 적용 — 모든 결제 상태 전이를 INSERT-only 로그로 기록한다.
 * 5-layer recovery 구조에서 "언제, 어떤 경로로, 왜 상태가 바뀌었는가"를 추적한다.</p>
 *
 * @see PaymentStatus
 */
@Entity
@Table(name = "payment_status_history", indexes = {
    @Index(name = "idx_psh_payment_id", columnList = "payment_id")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PaymentStatusHistory extends BaseEntity {

    @Column(name = "payment_id", nullable = false)
    private Long paymentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", nullable = false)
    private PaymentStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false)
    private PaymentStatus toStatus;

    @Column(name = "reason", nullable = false, length = 50)
    private String reason;

    @Column(name = "detail", length = 500)
    private String detail;

    public static PaymentStatusHistory create(Long paymentId, PaymentStatus fromStatus,
            PaymentStatus toStatus, String reason, String detail) {
        PaymentStatusHistory h = new PaymentStatusHistory();
        h.paymentId = paymentId;
        h.fromStatus = fromStatus;
        h.toStatus = toStatus;
        h.reason = reason;
        h.detail = detail;
        return h;
    }
}

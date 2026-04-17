package com.loopers.domain.payment;

import com.loopers.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;

/**
 * 대사(Reconciliation) 불일치 기록.
 *
 * <p>복구가 정상 동작하면 불일치 건수는 0이어야 한다.
 * 불일치 발견 = 복구 로직에 버그가 있다는 신호.</p>
 *
 * @see <a href="05-payment-resilience.md §10.6">대사 배치</a>
 */
@Entity
@Table(name = "reconciliation_mismatch", indexes = {
    @Index(name = "idx_recon_mismatch_type", columnList = "type"),
    @Index(name = "idx_recon_mismatch_payment_id", columnList = "payment_id")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReconciliationMismatch extends BaseEntity {

    @Column(name = "type", nullable = false)
    private String type;

    @Column(name = "payment_id", nullable = false)
    private Long paymentId;

    @Column(name = "our_status", nullable = false)
    private String ourStatus;

    @Column(name = "external_status")
    private String externalStatus;

    @Column(name = "detected_at", nullable = false)
    private ZonedDateTime detectedAt;

    @Column(name = "resolved_at")
    private ZonedDateTime resolvedAt;

    @Column(name = "resolution")
    private String resolution;

    @Column(name = "note", columnDefinition = "TEXT")
    private String note;

    public static ReconciliationMismatch create(String type, Long paymentId,
                                                 String ourStatus, String externalStatus,
                                                 String note) {
        ReconciliationMismatch mismatch = new ReconciliationMismatch();
        mismatch.type = type;
        mismatch.paymentId = paymentId;
        mismatch.ourStatus = ourStatus;
        mismatch.externalStatus = externalStatus;
        mismatch.detectedAt = ZonedDateTime.now();
        mismatch.note = note;
        return mismatch;
    }

    public void resolve(String resolution, String note) {
        this.resolvedAt = ZonedDateTime.now();
        this.resolution = resolution;
        this.note = note;
    }
}

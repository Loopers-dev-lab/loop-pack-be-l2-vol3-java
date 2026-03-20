package com.loopers.domain.payment;

import java.util.Set;

/**
 * 내부 결제 상태.
 * PG 상태(PENDING/SUCCESS/FAILED)와 별개로 우리 시스템의 결제 흐름을 표현한다.
 *
 * <pre>
 * REQUESTED ──PG 응답 PENDING──→ PENDING ──콜백 SUCCESS──→ PAID
 *     │                            │
 *     │                            ├──콜백 FAILED──→ FAILED
 *     │                            │
 *     │                            └──콜백 미수신──→ UNKNOWN
 *     │
 *     ├──PG 요청 실패──→ FAILED
 *     │
 *     └──PG 타임아웃──→ UNKNOWN
 *
 * UNKNOWN ──PG 확인 SUCCESS──→ PAID
 * UNKNOWN ──PG 확인 FAILED──→ FAILED
 * </pre>
 */
public enum PaymentStatus {

    REQUESTED(Set.of("PENDING", "FAILED", "UNKNOWN")),
    PENDING(Set.of("PAID", "FAILED", "UNKNOWN")),
    PAID(Set.of()),
    FAILED(Set.of()),
    UNKNOWN(Set.of("PAID", "FAILED"));

    private final Set<String> allowedTransitions;

    PaymentStatus(Set<String> allowedTransitions) {
        this.allowedTransitions = allowedTransitions;
    }

    public boolean canTransitionTo(PaymentStatus target) {
        return allowedTransitions.contains(target.name());
    }

    public boolean isTerminal() {
        return allowedTransitions.isEmpty();
    }
}

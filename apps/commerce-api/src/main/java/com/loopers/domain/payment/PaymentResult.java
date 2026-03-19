package com.loopers.domain.payment;

/**
 * PaymentService.requestPayment()의 결과 (Domain Layer)
 *
 * Facade는 이 결과만 보고 성공/실패/불확실을 판단한다.
 * PG 응답 코드, 타임아웃 분기 등 결제 도메인 내부 로직은 캡슐화된다.
 */
public record PaymentResult(
        Status status,
        String transactionKey,
        String reason
) {
    public enum Status {
        APPROVED,   // PG 승인 완료
        FAILED,     // PG 명확한 거절
        UNKNOWN,    // 타임아웃/조회 실패 — 결제 여부 불확실
        PENDING     // PG 접수 완료 — 결과는 콜백으로 수신
    }

    public boolean isApproved() {
        return status == Status.APPROVED;
    }

    public boolean isFailed() {
        return status == Status.FAILED;
    }

    public boolean isUnknown() {
        return status == Status.UNKNOWN;
    }

    public boolean isPending() {
        return status == Status.PENDING;
    }

    public static PaymentResult approved(String transactionKey) {
        return new PaymentResult(Status.APPROVED, transactionKey, "정상 승인되었습니다.");
    }

    public static PaymentResult failed(String reason) {
        return new PaymentResult(Status.FAILED, null, reason);
    }

    public static PaymentResult unknown(String reason) {
        return new PaymentResult(Status.UNKNOWN, null, reason);
    }

    public static PaymentResult pending(String transactionKey) {
        return new PaymentResult(Status.PENDING, transactionKey, "PG 접수 완료 — 콜백 대기");
    }
}

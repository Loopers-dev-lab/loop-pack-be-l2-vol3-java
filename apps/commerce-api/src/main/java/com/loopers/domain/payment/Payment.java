package com.loopers.domain.payment;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

import java.time.ZonedDateTime;
import java.util.UUID;

public record Payment(
        UUID id,
        String memberId,
        UUID orderId,
        CardType cardType,
        String cardNo,
        int amount,
        PaymentStatus status,
        String pgTransactionKey,
        String reason,
        ZonedDateTime createdAt,
        ZonedDateTime updatedAt,
        ZonedDateTime deletedAt
) {
    private static final String CARD_NO_PATTERN = "^\\d{4}-\\d{4}-\\d{4}-\\d{4}$";

    public Payment {
        if (memberId == null || memberId.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "회원 ID는 필수입니다.");
        }
        if (orderId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "주문 ID는 필수입니다.");
        }
        if (cardType == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "카드 타입은 필수입니다.");
        }
        if (cardNo == null || cardNo.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "카드 번호는 필수입니다.");
        }
        if (!cardNo.matches(CARD_NO_PATTERN)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "카드 번호 형식이 올바르지 않습니다.");
        }
        if (amount <= 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "결제 금액은 0보다 커야 합니다.");
        }
        if (status == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "결제 상태는 필수입니다.");
        }

        if ((status == PaymentStatus.SUCCEEDED
                || status == PaymentStatus.CANCEL_REQUESTED
                || status == PaymentStatus.CANCEL_RECONCILE_REQUIRED
                || status == PaymentStatus.CANCELLED
                || status == PaymentStatus.CANCEL_FAILED)
                && (pgTransactionKey == null || pgTransactionKey.isBlank())) {
            throw new CoreException(ErrorType.BAD_REQUEST, "거래 키는 해당 결제 상태에서 필수입니다.");
        }

        if ((status == PaymentStatus.FAILED
                || status == PaymentStatus.CANCEL_FAILED
                || status == PaymentStatus.CANCEL_RECONCILE_REQUIRED)
                && (reason == null || reason.isBlank())) {
            throw new CoreException(ErrorType.BAD_REQUEST, "실패 상태에는 실패 사유가 필수입니다.");
        }
    }

    public Payment(String memberId, UUID orderId, CardType cardType, String cardNo, int amount) {
        this(
                null,
                memberId,
                orderId,
                cardType,
                cardNo,
                amount,
                PaymentStatus.REQUESTED,
                null,
                null,
                ZonedDateTime.now(),
                ZonedDateTime.now(),
                null
        );
    }

    public Payment markSucceeded(String transactionKey) {
        if (transactionKey == null || transactionKey.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "거래 키는 필수입니다.");
        }
        if (status != PaymentStatus.REQUESTED) {
            throw new CoreException(ErrorType.CONFLICT, "요청 상태의 결제만 승인할 수 있습니다.");
        }

        return new Payment(
                id,
                memberId,
                orderId,
                cardType,
                cardNo,
                amount,
                PaymentStatus.SUCCEEDED,
                transactionKey,
                null,
                createdAt,
                ZonedDateTime.now(),
                deletedAt
        );
    }

    public Payment markFailed(String failedReason) {
        if (failedReason == null || failedReason.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "실패 사유는 필수입니다.");
        }
        if (status != PaymentStatus.REQUESTED) {
            throw new CoreException(ErrorType.CONFLICT, "요청 상태의 결제만 실패 처리할 수 있습니다.");
        }

        return new Payment(
                id,
                memberId,
                orderId,
                cardType,
                cardNo,
                amount,
                PaymentStatus.FAILED,
                pgTransactionKey,
                failedReason,
                createdAt,
                ZonedDateTime.now(),
                deletedAt
        );
    }

    public Payment requestCancel() {
        if (status != PaymentStatus.SUCCEEDED && status != PaymentStatus.CANCEL_FAILED) {
            throw new CoreException(ErrorType.CONFLICT, "취소 요청 가능한 결제 상태가 아닙니다.");
        }

        return new Payment(
                id,
                memberId,
                orderId,
                cardType,
                cardNo,
                amount,
                PaymentStatus.CANCEL_REQUESTED,
                pgTransactionKey,
                null,
                createdAt,
                ZonedDateTime.now(),
                deletedAt
        );
    }

    public Payment markCancelled() {
        if (status != PaymentStatus.CANCEL_REQUESTED
                && status != PaymentStatus.CANCEL_RECONCILE_REQUIRED) {
            throw new CoreException(ErrorType.CONFLICT, "취소 진행 상태의 결제만 취소 확정할 수 있습니다.");
        }

        return new Payment(
                id,
                memberId,
                orderId,
                cardType,
                cardNo,
                amount,
                PaymentStatus.CANCELLED,
                pgTransactionKey,
                null,
                createdAt,
                ZonedDateTime.now(),
                deletedAt
        );
    }

    public Payment markCancelFailed(String failedReason) {
        if (failedReason == null || failedReason.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "취소 실패 사유는 필수입니다.");
        }
        if (status != PaymentStatus.CANCEL_REQUESTED
                && status != PaymentStatus.CANCEL_RECONCILE_REQUIRED) {
            throw new CoreException(ErrorType.CONFLICT, "취소 진행 상태의 결제만 취소 실패 처리할 수 있습니다.");
        }

        return new Payment(
                id,
                memberId,
                orderId,
                cardType,
                cardNo,
                amount,
                PaymentStatus.CANCEL_FAILED,
                pgTransactionKey,
                failedReason,
                createdAt,
                ZonedDateTime.now(),
                deletedAt
        );
    }

    public Payment markCancelReconcileRequired(String reconcileReason) {
        if (reconcileReason == null || reconcileReason.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "재처리 사유는 필수입니다.");
        }
        if (status != PaymentStatus.CANCEL_REQUESTED) {
            throw new CoreException(ErrorType.CONFLICT, "취소 요청 상태의 결제만 재처리 대기 상태로 전이할 수 있습니다.");
        }

        return new Payment(
                id,
                memberId,
                orderId,
                cardType,
                cardNo,
                amount,
                PaymentStatus.CANCEL_RECONCILE_REQUIRED,
                pgTransactionKey,
                reconcileReason,
                createdAt,
                ZonedDateTime.now(),
                deletedAt
        );
    }
}

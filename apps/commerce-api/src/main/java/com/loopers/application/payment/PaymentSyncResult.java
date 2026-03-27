package com.loopers.application.payment;

/**
 * 결제 동기화 결과 DTO.
 */
public record PaymentSyncResult(
        int totalCount,
        int successCount,
        int failCount,
        int skipCount
) {
    public boolean hasFailures() {
        return failCount > 0;
    }

    public boolean isAllSuccess() {
        return failCount == 0 && skipCount == 0;
    }
}

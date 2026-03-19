package com.loopers.domain.payment;

/**
 * PG 결제 상태 확인 응답 DTO (도메인 계층 표현).
 * PgClientImpl에서 PG의 meta/data 래핑 응답을 파싱 후 이 DTO로 변환한다.
 *
 * @param transactionKey PG 거래 키
 * @param status PG 측 결제 상태 ("SUCCESS", "FAILED", "PENDING")
 * @param reason 결제 실패 시 사유 (성공/처리중이면 null)
 */
public record PgPaymentStatusResponse(
        String transactionKey,
        String status,
        String reason
) {

    public boolean isSuccess() {
        return "SUCCESS".equals(status);
    }

    public boolean isFailed() {
        return "FAILED".equals(status);
    }

    // PG가 아직 처리 중인 경우 (다음 폴링 주기에 재확인)
    public boolean isPending() {
        return "PENDING".equals(status);
    }
}

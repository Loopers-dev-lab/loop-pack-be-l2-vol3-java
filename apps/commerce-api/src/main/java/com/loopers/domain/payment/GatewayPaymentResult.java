package com.loopers.domain.payment;

/**
 * PG 결제 요청/조회 결과를 도메인 관점에서 표현하는 DTO.
 * <p>
 * domain 레이어는 이 record만 알고, PG 스펙 종속 DTO(PgPaymentResponse 등)는 모른다.
 * infrastructure의 구현체가 PG 응답을 이 타입으로 변환하여 반환한다.
 * </p>
 *
 * @param transactionKey PG가 발급한 트랜잭션 식별자
 * @param success        결제 성공 여부
 * @param status         PG 결제 상태 ("PENDING", "SUCCESS", "FAILED")
 * @param reason         실패 사유 (성공 시 null)
 */
public record GatewayPaymentResult(
        String transactionKey,
        boolean success,
        String status,
        String reason
) {

    public boolean isPending() {
        return "PENDING".equals(status);
    }

    public boolean isCompleted() {
        return "SUCCESS".equals(status) || "FAILED".equals(status);
    }
}

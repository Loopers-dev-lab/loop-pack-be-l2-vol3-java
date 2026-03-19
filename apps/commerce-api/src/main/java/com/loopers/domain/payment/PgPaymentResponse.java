package com.loopers.domain.payment;

/**
 * PG 결제 요청 응답 DTO.
 * PG가 즉시 반환하는 "접수됨" 응답 (도메인 계층 표현).
 *
 * PgClientImpl에서 PG의 meta/data 래핑 응답을 파싱 후 이 DTO로 변환한다.
 *
 * @param transactionKey PG가 발급한 거래 고유 키 (형식: yyyyMMdd:TR:xxxxxx)
 * @param accepted 접수 성공 여부 (PG meta.result == SUCCESS && status == PENDING)
 * @param message PG 응답 메시지 (접수 거부 시 사유)
 * @param timeout PG 응답 시간 초과 여부 (true: 결과 불확실 → 폴링 대상, false: 접수 거부 확정)
 */
public record PgPaymentResponse(
        String transactionKey,
        boolean accepted,
        String message,
        boolean timeout
) {
}

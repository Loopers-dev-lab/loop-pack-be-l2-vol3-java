package com.loopers.domain.payment;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum PaymentStatus {
    PENDING("결제 생성됨, PG 미전송"),
    IN_PROGRESS("PG 전송 완료, 콜백 대기"),
    SUCCEEDED("결제 성공"),
    FAILED("결제 실패");

    private final String description;
}

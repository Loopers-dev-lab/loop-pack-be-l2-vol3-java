package com.loopers.domain.payment;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum PaymentStatus {
    REQUESTED("결제 생성됨, PG 요청 전"),
    IN_PROGRESS("PG 접수 완료, 승인 대기"),
    SUCCEEDED("결제 성공"),
    FAILED("결제 실패"),
    CANCELED("결제 취소됨");

    private final String description;
}

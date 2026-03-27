package com.loopers.domain.payment;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum PaymentStatus {
    REQUESTED("결제 생성됨, PG 요청 전"),
    SUCCEEDED("결제 성공"),
    FAILED("결제 실패"),
    CANCEL_REQUESTED("취소 요청됨, PG 취소 전"),
    CANCELED("결제 취소됨");

    private final String description;
}

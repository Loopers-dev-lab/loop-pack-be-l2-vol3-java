package com.loopers.domain.payment.gateway;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum PgType {
    TOSS("토스페이먼츠"),
    NICE("나이스페이");

    private final String description;
}

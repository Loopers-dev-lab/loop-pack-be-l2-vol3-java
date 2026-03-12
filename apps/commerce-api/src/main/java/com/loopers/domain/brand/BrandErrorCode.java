package com.loopers.domain.brand;

import com.loopers.support.error.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum BrandErrorCode implements ErrorCode {
    DUPLICATE_NAME(HttpStatus.CONFLICT, "BRAND_001", "이미 존재하는 브랜드명입니다."),
    NOT_FOUND(HttpStatus.NOT_FOUND, "BRAND_002", "브랜드를 찾을 수 없습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}

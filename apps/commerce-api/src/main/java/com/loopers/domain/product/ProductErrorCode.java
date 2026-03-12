package com.loopers.domain.product;

import com.loopers.support.error.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ProductErrorCode implements ErrorCode {
    NOT_FOUND(HttpStatus.NOT_FOUND, "PRODUCT_001", "상품을 찾을 수 없습니다."),
    PRICE_MISMATCH(HttpStatus.BAD_REQUEST, "PRODUCT_002", "상품 가격이 변경되었습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}

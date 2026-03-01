package com.loopers.domain.brand.exception;

public class BrandNotDeletedException extends RuntimeException {
    public BrandNotDeletedException(Long brandId) {
        super("삭제되지 않은 브랜드입니다. brandId=" + brandId);
    }
}

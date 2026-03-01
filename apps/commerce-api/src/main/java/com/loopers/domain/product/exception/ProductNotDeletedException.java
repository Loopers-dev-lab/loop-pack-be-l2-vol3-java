package com.loopers.domain.product.exception;

public class ProductNotDeletedException extends RuntimeException {
    public ProductNotDeletedException(Long productId) {
        super("삭제되지 않은 상품입니다. productId=" + productId);
    }
}

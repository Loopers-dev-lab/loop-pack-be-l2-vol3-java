package com.loopers.domain.product.exception;

public class ProductInsufficientStockException extends RuntimeException {
    public ProductInsufficientStockException() {
        super("재고가 부족합니다");
    }
}

package com.loopers.domain.product;

public class ProductViewEvent {

    public record Viewed(Long productId) {}
}

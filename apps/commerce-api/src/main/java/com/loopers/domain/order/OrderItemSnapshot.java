package com.loopers.domain.order;

public record OrderItemSnapshot(
        Long productId,
        String productName,
        long unitPrice,
        int quantity
) {
    public long lineAmount() {
        return (long) unitPrice * quantity;
    }
}

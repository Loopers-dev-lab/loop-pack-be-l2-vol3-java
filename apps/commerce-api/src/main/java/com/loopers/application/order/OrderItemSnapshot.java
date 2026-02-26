package com.loopers.application.order;

public record OrderItemSnapshot(
        Long productId,
        String productName,
        int unitPrice,
        int quantity
) {
    public long lineAmount() {
        return (long) unitPrice * quantity;
    }
}

package com.loopers.domain.stock;

public enum StockStatus {
    IN_STOCK,
    LOW_STOCK,
    OUT_OF_STOCK;

    public static StockStatus from(int quantity) {
        if (quantity <= 0) return OUT_OF_STOCK;
        if (quantity <= 10) return LOW_STOCK;
        return IN_STOCK;
    }
}

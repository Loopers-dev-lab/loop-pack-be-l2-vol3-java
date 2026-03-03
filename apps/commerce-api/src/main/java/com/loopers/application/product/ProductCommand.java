package com.loopers.application.product;

import java.math.BigDecimal;

public record ProductCommand() {

    public record Register(Long brandId, String name, BigDecimal price, Integer stockQuantity, String description) {
        public static Register of(Long brandId, String name, BigDecimal price, Integer stockQuantity, String description) {
            return new Register(brandId, name, price, stockQuantity, description);
        }
    }

    public record UpdateInfo(String name, BigDecimal price, Integer stockQuantity, String description) {
        public static UpdateInfo of(String name, BigDecimal price, Integer stockQuantity, String description) {
            return new UpdateInfo(name, price, stockQuantity, description);
        }
    }
}

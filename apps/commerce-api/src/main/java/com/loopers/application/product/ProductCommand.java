package com.loopers.application.product;

import java.math.BigDecimal;

public record ProductCommand() {

    public record Create(Long brandId, String name, BigDecimal price, Integer stockQuantity, String description) {
        public static Create of(Long brandId, String name, BigDecimal price, Integer stockQuantity, String description) {
            return new Create(brandId, name, price, stockQuantity, description);
        }
    }

    public record UpdateInfo(String name, BigDecimal price, Integer stockQuantity, String description) {
        public static UpdateInfo of(String name, BigDecimal price, Integer stockQuantity, String description) {
            return new UpdateInfo(name, price, stockQuantity, description);
        }
    }
}

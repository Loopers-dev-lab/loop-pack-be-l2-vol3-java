package com.loopers.domain.product.dto;

import java.util.List;

public class ProductInfo {

    public record StockDeduction(Long productId, String name, int price, int quantity, Long brandId) {

        public static List<Long> extractDistinctBrandIds(List<StockDeduction> deductions) {
            return deductions.stream()
                    .map(StockDeduction::brandId)
                    .distinct()
                    .toList();
        }
    }
}

package com.loopers.application.order;

import java.math.BigDecimal;
import java.util.List;

public record OrderCommand() {

    public record Create(
            Long userId,
            List<CreateItem> items
    ) {
        public static Create of(Long userId, List<CreateItem> items) {
            return new Create(userId, items);
        }
    }

    public record CreateItem(
            Long productId,
            String productName,
            BigDecimal price,
            int quantity
    ) {
        public static CreateItem of(Long productId, String productName,
                                         BigDecimal price, int quantity) {
            return new CreateItem(productId, productName, price, quantity);
        }
    }
}

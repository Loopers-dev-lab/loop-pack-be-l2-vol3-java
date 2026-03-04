package com.loopers.application.order;

import java.math.BigDecimal;
import java.util.List;

public record OrderCommand() {

    public record Place(List<PlaceItem> items, Long couponId) {
        public static Place of(List<PlaceItem> items) {
            return new Place(items, null);
        }

        public static Place of(List<PlaceItem> items, Long couponId) {
            return new Place(items, couponId);
        }
    }

    public record PlaceItem(Long productId, Integer quantity) {
        public static PlaceItem of(Long productId, Integer quantity) {
            return new PlaceItem(productId, quantity);
        }
    }

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

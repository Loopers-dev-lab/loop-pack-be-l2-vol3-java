package com.loopers.application.inventory;

import com.loopers.domain.inventory.Inventory;

public record InventoryInfo(
        Long id,
        Long productId,
        int quantity,
        int reservedQty,
        int availableQuantity
) {
    public static InventoryInfo from(Inventory inventory) {
        if (inventory == null) {
            throw new IllegalArgumentException("Inventory는 null일 수 없습니다.");
        }
        return new InventoryInfo(
                inventory.getId(),
                inventory.getProductId(),
                inventory.getQuantity(),
                inventory.getReservedQty(),
                inventory.getAvailableQuantity()
        );
    }
}

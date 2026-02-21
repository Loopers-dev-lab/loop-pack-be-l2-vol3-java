package com.loopers.domain.inventory;

import java.util.List;
import java.util.Optional;

public interface InventoryRepository {
    Inventory save(Inventory inventory);
    Optional<Inventory> findByProductId(Long productId);
    List<Inventory> findAllByProductIdIn(List<Long> productIds);
    void deleteByProductId(Long productId);
}

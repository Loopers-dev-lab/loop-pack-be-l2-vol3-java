package com.loopers.infrastructure.inventory;

import com.loopers.domain.inventory.Inventory;
import com.loopers.domain.inventory.InventoryRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 재고 리포지토리 구현체 (Infrastructure Layer)
 *
 * Domain 계층의 InventoryRepository 포트를 구현한다.
 * Spring Data JPA에 위임하며, soft delete 필터를 적용한다.
 */
@Repository
public class InventoryRepositoryImpl implements InventoryRepository {

    private final InventoryJpaRepository inventoryJpaRepository;

    public InventoryRepositoryImpl(InventoryJpaRepository inventoryJpaRepository) {
        this.inventoryJpaRepository = inventoryJpaRepository;
    }

    @Override
    public Inventory save(Inventory inventory) {
        return inventoryJpaRepository.save(inventory);
    }

    @Override
    public Optional<Inventory> findByProductId(Long productId) {
        return inventoryJpaRepository.findByProductIdAndDeletedAtIsNull(productId);
    }

    @Override
    public Optional<Inventory> findByProductIdForUpdate(Long productId) {
        return inventoryJpaRepository.findByProductIdForUpdate(productId);
    }

    @Override
    public List<Inventory> findAllByProductIdIn(List<Long> productIds) {
        return inventoryJpaRepository.findAllByProductIdInAndDeletedAtIsNull(productIds);
    }
}

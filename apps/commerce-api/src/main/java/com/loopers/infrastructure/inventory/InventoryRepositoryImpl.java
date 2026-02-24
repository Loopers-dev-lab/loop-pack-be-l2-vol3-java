package com.loopers.infrastructure.inventory;

import com.loopers.domain.inventory.Inventory;
import com.loopers.domain.inventory.InventoryRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 재고 리포지토리 구현체 (Infrastructure Layer)
 *
 * Domain 계층의 InventoryRepository 포트를 구현한다.
 * Mapper를 활용하여 Domain ↔ Entity 변환한다.
 * Spring Data JPA에 위임하며, soft delete 필터를 적용한다.
 */
@Repository
public class InventoryRepositoryImpl implements InventoryRepository {

    private final InventoryJpaRepository inventoryJpaRepository;
    private final InventoryMapper inventoryMapper;

    public InventoryRepositoryImpl(InventoryJpaRepository inventoryJpaRepository, InventoryMapper inventoryMapper) {
        this.inventoryJpaRepository = inventoryJpaRepository;
        this.inventoryMapper = inventoryMapper;
    }

    @Override
    public Inventory save(Inventory inventory) {
        // Domain → Entity
        InventoryEntity entity = inventoryMapper.toEntity(inventory);

        // JPA save
        InventoryEntity saved = inventoryJpaRepository.save(entity);

        // Entity → Domain
        return inventoryMapper.toDomain(saved);
    }

    @Override
    public Optional<Inventory> findByProductId(Long productId) {
        return inventoryJpaRepository.findByProductIdAndDeletedAtIsNull(productId)
            .map(inventoryMapper::toDomain);  // Entity → Domain
    }

    @Override
    public Optional<Inventory> findByProductIdForUpdate(Long productId) {
        return inventoryJpaRepository.findByProductIdForUpdate(productId)
            .map(inventoryMapper::toDomain);  // Entity → Domain
    }

    @Override
    public List<Inventory> findAllByProductIdIn(List<Long> productIds) {
        return inventoryJpaRepository.findAllByProductIdInAndDeletedAtIsNull(productIds).stream()
            .map(inventoryMapper::toDomain)  // Entity → Domain
            .collect(Collectors.toList());
    }
}

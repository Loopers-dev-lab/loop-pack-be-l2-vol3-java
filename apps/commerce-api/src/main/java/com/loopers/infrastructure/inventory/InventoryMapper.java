package com.loopers.infrastructure.inventory;

import com.loopers.domain.inventory.Inventory;
import org.springframework.stereotype.Component;

/**
 * InventoryMapper
 * Domain POJO ↔ JPA Entity 변환
 */
@Component
public class InventoryMapper {

    /**
     * Domain → JPA Entity
     */
    public InventoryEntity toEntity(Inventory inventory) {
        InventoryEntity entity = new InventoryEntity();
        entity.setId(inventory.getId());
        entity.setProductId(inventory.getProductId());
        entity.setQuantity(inventory.getQuantity());
        entity.setReservedQty(inventory.getReservedQty());
        entity.setCreatedAt(inventory.getCreatedAt());
        entity.setUpdatedAt(inventory.getUpdatedAt());
        entity.setDeletedAt(inventory.getDeletedAt());
        return entity;
    }

    /**
     * JPA Entity → Domain
     */
    public Inventory toDomain(InventoryEntity entity) {
        return Inventory.reconstitute(
            entity.getId(),
            entity.getProductId(),
            entity.getQuantity(),
            entity.getReservedQty(),
            entity.getCreatedAt(),
            entity.getUpdatedAt(),
            entity.getDeletedAt()
        );
    }
}

package com.loopers.domain.inventory;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.InventoryErrorType;
import java.util.List;
import java.util.Map;

public class InventoryService {

    private final InventoryRepository inventoryRepository;

    public InventoryService(InventoryRepository inventoryRepository) {
        this.inventoryRepository = inventoryRepository;
    }

    public Inventory create(Long productId, int quantity) {
        Inventory inventory = Inventory.create(productId, quantity);
        return inventoryRepository.save(inventory);
    }

    public void reserveAll(Map<Long, Integer> productQtyMap) {
        List<Long> productIds = List.copyOf(productQtyMap.keySet());
        List<Inventory> inventories = inventoryRepository.findAllByProductIdIn(productIds);

        if (inventories.size() != productIds.size()) {
            throw new CoreException(InventoryErrorType.INVENTORY_NOT_FOUND);
        }

        for (Inventory inventory : inventories) {
            int qty = productQtyMap.get(inventory.getProductId());
            inventory.reserve(qty);
        }
    }

    public void commitAll(Map<Long, Integer> productQtyMap) {
        List<Long> productIds = List.copyOf(productQtyMap.keySet());
        List<Inventory> inventories = inventoryRepository.findAllByProductIdIn(productIds);

        for (Inventory inventory : inventories) {
            int qty = productQtyMap.get(inventory.getProductId());
            inventory.commit(qty);
        }
    }

    public void releaseAll(Map<Long, Integer> productQtyMap) {
        List<Long> productIds = List.copyOf(productQtyMap.keySet());
        List<Inventory> inventories = inventoryRepository.findAllByProductIdIn(productIds);

        for (Inventory inventory : inventories) {
            int qty = productQtyMap.get(inventory.getProductId());
            inventory.release(qty);
        }
    }

    public void delete(Long productId) {
        inventoryRepository.deleteByProductId(productId);
    }
}

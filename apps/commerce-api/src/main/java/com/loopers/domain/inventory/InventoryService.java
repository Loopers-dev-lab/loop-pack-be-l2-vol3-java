package com.loopers.domain.inventory;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.InventoryErrorType;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * 재고 도메인 서비스
 *
 * 재고 생성, 조회, 예약/확정/해제, 삭제 비즈니스 로직을 담당한다.
 */
@Component
public class InventoryService {

    private final InventoryRepository inventoryRepository;

    public InventoryService(InventoryRepository inventoryRepository) {
        this.inventoryRepository = inventoryRepository;
    }

    /** 재고 생성 */
    @Transactional
    public Inventory create(Long productId, int quantity) {
        Inventory inventory = Inventory.create(productId, quantity);
        return inventoryRepository.save(inventory);
    }

    /** 상품별 재고 조회 */
    @Transactional(readOnly = true)
    public Inventory getByProductId(Long productId) {
        return inventoryRepository.findByProductId(productId)
                .orElseThrow(() -> new CoreException(InventoryErrorType.INVENTORY_NOT_FOUND));
    }

    /**
     * 일괄 예약 (비관적 락)
     * 개별 findByProductIdForUpdate로 비관적 락을 획득한다.
     */
    @Transactional
    public void reserveAll(Map<Long, Integer> productQtyMap) {
        for (Map.Entry<Long, Integer> entry : productQtyMap.entrySet()) {
            Inventory inventory = inventoryRepository.findByProductIdForUpdate(entry.getKey())
                    .orElseThrow(() -> new CoreException(InventoryErrorType.INVENTORY_NOT_FOUND));
            inventory.reserve(entry.getValue());
        }
    }

    /** 일괄 확정 (결제 완료) */
    @Transactional
    public void commitAll(Map<Long, Integer> productQtyMap) {
        for (Map.Entry<Long, Integer> entry : productQtyMap.entrySet()) {
            Inventory inventory = inventoryRepository.findByProductIdForUpdate(entry.getKey())
                    .orElseThrow(() -> new CoreException(InventoryErrorType.INVENTORY_NOT_FOUND));
            inventory.commit(entry.getValue());
        }
    }

    /** 일괄 해제 (주문 취소) */
    @Transactional
    public void releaseAll(Map<Long, Integer> productQtyMap) {
        for (Map.Entry<Long, Integer> entry : productQtyMap.entrySet()) {
            Inventory inventory = inventoryRepository.findByProductIdForUpdate(entry.getKey())
                    .orElseThrow(() -> new CoreException(InventoryErrorType.INVENTORY_NOT_FOUND));
            inventory.release(entry.getValue());
        }
    }

    /** 재고 소프트 삭제 (상품 삭제 시 연쇄) */
    @Transactional
    public void delete(Long productId) {
        inventoryRepository.findByProductId(productId)
                .ifPresent(Inventory::delete);
    }
}

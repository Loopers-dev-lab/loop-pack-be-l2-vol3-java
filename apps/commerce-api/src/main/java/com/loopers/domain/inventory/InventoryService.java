package com.loopers.domain.inventory;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.InventoryErrorType;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
     * productId 오름차순으로 락을 획득하여 데드락을 방지한다.
     */
    @Transactional
    public void reserveAll(Map<Long, Integer> productQtyMap) {
        List<Map.Entry<Long, Integer>> sortedEntries = productQtyMap.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .collect(Collectors.toList());

        for (Map.Entry<Long, Integer> entry : sortedEntries) {
            Inventory inventory = inventoryRepository.findByProductIdForUpdate(entry.getKey())
                    .orElseThrow(() -> new CoreException(InventoryErrorType.INVENTORY_NOT_FOUND));
            inventory.reserve(entry.getValue());
        }
    }

    /** 일괄 확정 (결제 완료) — productId 오름차순 락 획득 */
    @Transactional
    public void commitAll(Map<Long, Integer> productQtyMap) {
        List<Map.Entry<Long, Integer>> sortedEntries = productQtyMap.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .collect(Collectors.toList());

        for (Map.Entry<Long, Integer> entry : sortedEntries) {
            Inventory inventory = inventoryRepository.findByProductIdForUpdate(entry.getKey())
                    .orElseThrow(() -> new CoreException(InventoryErrorType.INVENTORY_NOT_FOUND));
            inventory.commit(entry.getValue());
        }
    }

    /** 일괄 해제 (주문 취소) — productId 오름차순 락 획득 */
    @Transactional
    public void releaseAll(Map<Long, Integer> productQtyMap) {
        List<Map.Entry<Long, Integer>> sortedEntries = productQtyMap.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .collect(Collectors.toList());

        for (Map.Entry<Long, Integer> entry : sortedEntries) {
            Inventory inventory = inventoryRepository.findByProductIdForUpdate(entry.getKey())
                    .orElseThrow(() -> new CoreException(InventoryErrorType.INVENTORY_NOT_FOUND));
            inventory.release(entry.getValue());
        }
    }

    /** 상품 ID 목록으로 재고 일괄 조회 (장바구니 조회용) */
    @Transactional(readOnly = true)
    public List<Inventory> getByProductIds(List<Long> productIds) {
        return inventoryRepository.findAllByProductIdIn(productIds);
    }

    /** 재고 소프트 삭제 (상품 삭제 시 연쇄) */
    @Transactional
    public void delete(Long productId) {
        inventoryRepository.findByProductId(productId)
                .ifPresent(Inventory::delete);
    }
}

package com.loopers.domain.inventory;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.InventoryErrorType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 재고 도메인 서비스
 *
 * 재고 생성, 조회, 예약/확정/해제, 삭제 비즈니스 로직을 담당한다.
 */
@Component
public class InventoryService {

    private static final Logger log = LoggerFactory.getLogger(InventoryService.class);

    private final InventoryRepository inventoryRepository;

    public InventoryService(InventoryRepository inventoryRepository) {
        this.inventoryRepository = inventoryRepository;
    }

    /** 재고 생성 */
    @Transactional(timeout = 30)
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
    @Transactional(timeout = 30)
    public void reserveAll(Map<Long, Integer> productQtyMap) {
        List<Map.Entry<Long, Integer>> sortedEntries = productQtyMap.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .collect(Collectors.toList());

        for (Map.Entry<Long, Integer> entry : sortedEntries) {
            Inventory inventory = inventoryRepository.findByProductIdForUpdate(entry.getKey())
                    .orElseThrow(() -> new CoreException(InventoryErrorType.INVENTORY_NOT_FOUND));
            inventory.reserve(entry.getValue());
            inventoryRepository.save(inventory);
        }
    }

    /**
     * 일괄 확정 (결제 완료) — productId 오름차순 락 획득
     *
     * 재고가 삭제된 상품은 skip한다.
     * 결제 실패 시 복구 과정에서 상품이 이미 삭제되었을 수 있기 때문이다.
     */
    @Transactional(timeout = 30)
    public void commitAll(Map<Long, Integer> productQtyMap) {
        List<Map.Entry<Long, Integer>> sortedEntries = productQtyMap.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .collect(Collectors.toList());

        for (Map.Entry<Long, Integer> entry : sortedEntries) {
            Optional<Inventory> inventoryOpt = inventoryRepository.findByProductIdForUpdate(entry.getKey());
            if (inventoryOpt.isEmpty()) {
                log.warn("재고 확정 skip — 재고 미존재 (productId={})", entry.getKey());
                continue;
            }
            Inventory inventory = inventoryOpt.get();
            inventory.commit(entry.getValue());
            inventoryRepository.save(inventory);
        }
    }

    /**
     * 일괄 해제 (주문 취소) — productId 오름차순 락 획득
     *
     * 주문은 "당시 스냅샷" 기준의 독립 도메인이므로,
     * 상품/재고 삭제 여부와 관계없이 주문 취소는 성공해야 한다.
     * 재고가 삭제된 상품은 skip하고 로그를 남긴다.
     *
     * @see <a href="멘토 피드백">앨런: "상품 삭제는 내부 정책 변경, 주문 취소는 계약 해제"</a>
     */
    @Transactional(timeout = 30)
    public void releaseAll(Map<Long, Integer> productQtyMap) {
        List<Map.Entry<Long, Integer>> sortedEntries = productQtyMap.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .collect(Collectors.toList());

        for (Map.Entry<Long, Integer> entry : sortedEntries) {
            Optional<Inventory> inventoryOpt = inventoryRepository.findByProductIdForUpdate(entry.getKey());
            if (inventoryOpt.isEmpty()) {
                log.warn("재고 해제 skip — 재고 미존재 (productId={}, qty={}). "
                        + "상품이 삭제되었을 수 있음", entry.getKey(), entry.getValue());
                continue;
            }
            Inventory inventory = inventoryOpt.get();
            inventory.release(entry.getValue());
            inventoryRepository.save(inventory);
        }
    }

    /** 상품 ID 목록으로 재고 일괄 조회 (장바구니 조회용) */
    @Transactional(readOnly = true)
    public List<Inventory> getByProductIds(List<Long> productIds) {
        return inventoryRepository.findAllByProductIdIn(productIds);
    }

    /** 재고 소프트 삭제 (상품 삭제 시 연쇄) */
    @Transactional(timeout = 30)
    public void delete(Long productId) {
        inventoryRepository.findByProductId(productId)
                .ifPresent(inventory -> {
                    inventory.delete();
                    inventoryRepository.save(inventory);
                });
    }
}

package com.loopers.domain.inventory;

import java.util.List;
import java.util.Optional;

/**
 * 재고 리포지토리 포트 (Domain Layer)
 *
 * 도메인이 인프라(JPA)에 의존하지 않도록 추상화한 인터페이스.
 * 실제 구현은 Infrastructure 계층의 InventoryRepositoryImpl이 담당한다.
 */
public interface InventoryRepository {
    Inventory save(Inventory inventory);
    Optional<Inventory> findByProductId(Long productId);

    /** 비관적 락 조회 (주문 예약 시 동시성 제어) */
    Optional<Inventory> findByProductIdForUpdate(Long productId);

    List<Inventory> findAllByProductIdIn(List<Long> productIds);
}

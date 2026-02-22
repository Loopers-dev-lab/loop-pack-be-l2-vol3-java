package com.loopers.infrastructure.inventory;

import com.loopers.domain.inventory.Inventory;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface InventoryJpaRepository extends JpaRepository<Inventory, Long> {

    Optional<Inventory> findByProductIdAndDeletedAtIsNull(Long productId);

    /** 비관적 락 조회 (주문 예약 시 동시성 제어) */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM Inventory i WHERE i.productId = :productId AND i.deletedAt IS NULL")
    Optional<Inventory> findByProductIdForUpdate(@Param("productId") Long productId);

    List<Inventory> findAllByProductIdInAndDeletedAtIsNull(List<Long> productIds);
}

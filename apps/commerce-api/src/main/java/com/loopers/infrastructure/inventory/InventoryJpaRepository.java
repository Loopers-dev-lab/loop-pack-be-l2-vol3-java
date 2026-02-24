package com.loopers.infrastructure.inventory;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA Repository
 * InventoryEntity 기준
 */
public interface InventoryJpaRepository extends JpaRepository<InventoryEntity, Long> {

    Optional<InventoryEntity> findByProductIdAndDeletedAtIsNull(Long productId);

    /** 비관적 락 조회 (주문 예약 시 동시성 제어) */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM InventoryEntity i WHERE i.productId = :productId AND i.deletedAt IS NULL")
    Optional<InventoryEntity> findByProductIdForUpdate(@Param("productId") Long productId);

    List<InventoryEntity> findAllByProductIdInAndDeletedAtIsNull(List<Long> productIds);
}

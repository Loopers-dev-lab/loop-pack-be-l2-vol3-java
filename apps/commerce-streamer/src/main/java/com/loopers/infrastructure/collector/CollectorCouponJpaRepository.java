package com.loopers.infrastructure.collector;

import com.loopers.domain.collector.CollectorCouponModel;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface CollectorCouponJpaRepository extends JpaRepository<CollectorCouponModel, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM CollectorCouponModel c WHERE c.id = :id AND c.deletedAt IS NULL")
    Optional<CollectorCouponModel> findByIdForUpdate(Long id);
}

package com.loopers.infrastructure.collector;

import com.loopers.domain.collector.ProductMetricsModel;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface ProductMetricsJpaRepository extends JpaRepository<ProductMetricsModel, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT pm FROM ProductMetricsModel pm WHERE pm.productId = :productId")
    Optional<ProductMetricsModel> findByProductIdForUpdate(Long productId);
}

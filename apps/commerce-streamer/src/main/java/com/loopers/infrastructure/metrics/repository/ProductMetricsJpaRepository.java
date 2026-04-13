package com.loopers.infrastructure.metrics.repository;

import com.loopers.infrastructure.metrics.entity.ProductMetricsEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface ProductMetricsJpaRepository extends JpaRepository<ProductMetricsEntity, Long> {
    List<ProductMetricsEntity> findByUpdatedAtAfter(LocalDateTime since);
}

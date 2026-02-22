package com.loopers.infrastructure.product;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OptionJpaRepository extends JpaRepository<OptionJpaEntity, Long> {
    List<OptionJpaEntity> findByProductIdAndDeletedFalse(Long productId);
    Optional<OptionJpaEntity> findByIdAndDeletedFalse(Long id);
}

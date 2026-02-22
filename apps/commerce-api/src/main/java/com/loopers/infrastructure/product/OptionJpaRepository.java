package com.loopers.infrastructure.product;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OptionJpaRepository extends JpaRepository<OptionJpaEntity, Long> {
    List<OptionJpaEntity> findByProductId(Long productId);
    void deleteByProductId(Long productId);
}

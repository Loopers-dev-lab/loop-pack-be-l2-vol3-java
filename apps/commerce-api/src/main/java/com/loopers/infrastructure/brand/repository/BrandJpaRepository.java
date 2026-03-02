package com.loopers.infrastructure.brand.repository;

import com.loopers.infrastructure.brand.entity.BrandEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BrandJpaRepository extends JpaRepository<BrandEntity, Long> {

}

package com.loopers.infrastructure.brand;

import com.loopers.domain.brand.Brand;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BrandJpaRepository extends JpaRepository<Brand, Long> {
    boolean existsByNameAndDeletedAtIsNull(String name);
    boolean existsByNameAndIdNotAndDeletedAtIsNull(String name, Long id);
}

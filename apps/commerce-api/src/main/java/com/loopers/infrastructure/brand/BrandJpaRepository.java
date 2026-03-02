package com.loopers.infrastructure.brand;

import com.loopers.domain.brand.Brand;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BrandJpaRepository extends JpaRepository<Brand, Long> {
    List<Brand> findByDeletedFalse();
    Optional<Brand> findByIdAndDeletedFalse(Long id);
    List<Brand> findByIdInAndDeletedFalse(List<Long> ids);
}

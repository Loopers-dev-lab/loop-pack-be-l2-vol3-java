package com.loopers.infrastructure.brand.persistence;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;

import com.loopers.domain.brand.Brand;

public interface BrandJpaRepository extends JpaRepository<Brand, Long> {

    Slice<Brand> findAllBy(Pageable pageable);

    boolean existsByName_ValueAndDeletedAtIsNull(String name);

    boolean existsByIdNotAndName_ValueAndDeletedAtIsNull(Long brandId, String name);
}

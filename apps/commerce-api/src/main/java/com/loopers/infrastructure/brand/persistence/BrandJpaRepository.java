package com.loopers.infrastructure.brand.persistence;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;

import com.loopers.domain.brand.Brand;

public interface BrandJpaRepository extends JpaRepository<Brand, Long> {

    Optional<Brand> findByIdAndDeletedAtIsNull(Long brandId);

    List<Brand> findAllByIdInAndDeletedAtIsNull(List<Long> brandIds);

    Slice<Brand> findAllBy(Pageable pageable);

    boolean existsByIdAndDeletedAtIsNull(Long brandId);

    boolean existsByName_ValueAndDeletedAtIsNull(String name);

    boolean existsByIdNotAndName_ValueAndDeletedAtIsNull(Long brandId, String name);
}

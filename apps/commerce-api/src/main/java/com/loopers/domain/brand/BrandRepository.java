package com.loopers.domain.brand;

import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;

public interface BrandRepository {

    Brand save(Brand brand);

    Optional<Brand> findById(Long brandId);

    Optional<Brand> findByIdAndDeletedAtIsNull(Long brandId);

    Slice<Brand> findAllBy(Pageable pageable);

    boolean existsByIdAndDeletedAtIsNull(Long brandId);

    boolean existsByNameAndDeletedAtIsNull(String name);

    boolean existsByIdNotAndNameAndDeletedAtIsNull(Long brandId, String name);
}

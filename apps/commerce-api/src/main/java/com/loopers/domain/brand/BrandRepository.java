package com.loopers.domain.brand;

import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;

public interface BrandRepository {

    Brand save(Brand brand);

    Optional<Brand> findById(Long brandId);

    Slice<Brand> findAllBy(Pageable pageable);

    boolean existsByNameAndDeletedAtIsNull(String name);
}

package com.loopers.domain.brand;

import com.loopers.domain.brand.vo.BrandName;

import java.util.Optional;

public interface BrandRepository {
    Brand save(Brand brand);

    Optional<Brand> findById(Long id);

    boolean existsById(Long id);

    boolean existsByName(BrandName name);

    void deleteRelatedProducts(Long brandId);

    void delete(Brand brand);
}

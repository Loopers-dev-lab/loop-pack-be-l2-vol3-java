package com.loopers.domain.product;

import java.util.Optional;

public interface BrandRepository {

    Brand save(Brand brand);

    Optional<Brand> findById(Long id);
}

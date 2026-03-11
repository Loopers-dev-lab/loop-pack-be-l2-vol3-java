package com.loopers.domain.brand;

import java.util.Optional;

public interface BrandRepository {

    Brand create(Brand brand);
    Brand update(Brand brand);
    Optional<Brand> findById(Long id);
    boolean existsById(Long id);
}

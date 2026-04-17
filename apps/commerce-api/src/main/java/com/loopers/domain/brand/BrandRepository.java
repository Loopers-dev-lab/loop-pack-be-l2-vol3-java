package com.loopers.domain.brand;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface BrandRepository {
    Brand save(Brand brand);
    Optional<Brand> findById(Long id);
    List<Brand> findAll();
    List<Brand> findAllByIds(Set<Long> ids);
}

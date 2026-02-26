package com.loopers.domain.repository;

import com.loopers.domain.model.brand.Brand;
import com.loopers.domain.model.brand.BrandName;

import java.util.List;
import java.util.Optional;

public interface BrandRepository {

    Brand save(Brand brand);

    Optional<Brand> findById(Long id);

    Optional<Brand> findActiveById(Long id);

    List<Brand> findAllActive();

    List<Brand> findAllByIds(List<Long> ids);

    boolean existsByName(BrandName name);
}

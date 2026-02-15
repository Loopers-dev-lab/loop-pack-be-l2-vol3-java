package com.loopers.domain.brand;

import com.loopers.domain.PageResult;

import java.util.Optional;

public interface BrandRepository {

    Brand save(Brand brand);

    Optional<Brand> findById(Long id);

    PageResult<Brand> findAll(int page, int size);
}

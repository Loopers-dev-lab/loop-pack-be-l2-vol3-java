package com.loopers.domain.brand;

import com.loopers.domain.PageResult;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface BrandRepository {

    Brand save(Brand brand);

    Optional<Brand> findById(Long id);

    Optional<Brand> findByIdWithLock(Long id);

    List<Brand> findAllByIds(Set<Long> ids);

    PageResult<Brand> findAll(int page, int size);
}

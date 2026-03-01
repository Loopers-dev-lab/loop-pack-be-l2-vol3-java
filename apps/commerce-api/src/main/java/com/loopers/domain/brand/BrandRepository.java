package com.loopers.domain.brand;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface BrandRepository {

    Brand save(Brand brand);

    Optional<Brand> findById(Long brandId);

    Optional<Brand> findByIdIncludingDeleted(Long brandId);

    Page<Brand> findAll(Pageable pageable);

    List<Brand> findAllByIds(Collection<Long> brandIds);

    boolean existsByName(String name);
}

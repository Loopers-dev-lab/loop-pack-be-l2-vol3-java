package com.loopers.domain.brand;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

public interface BrandRepository {
    Brand save(Brand brand);
    Optional<Brand> findById(Long id);
    List<Brand> findAllByIds(List<Long> ids);
    Page<Brand> findAll(Pageable pageable);
    boolean existsByName(String name);
    boolean existsByNameAndIdNot(String name, Long id);
}

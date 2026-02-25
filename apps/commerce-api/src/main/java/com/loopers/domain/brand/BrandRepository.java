package com.loopers.domain.brand;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

public interface BrandRepository {

    // Command

    Brand save(Brand brand);

    // Query

    Optional<Brand> findById(Long id);

    boolean existsByName(String name);

    boolean existsByNameAndIdNot(String name, Long id);

    Page<Brand> findAll(String name, Boolean deleted, Pageable pageable);

    List<Brand> findAllByIdIn(List<Long> ids);

    Page<Brand> findAllActive(String name, Pageable pageable);
}

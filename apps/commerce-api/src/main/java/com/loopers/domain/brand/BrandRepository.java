package com.loopers.domain.brand;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

public interface BrandRepository {
    Brand save(Brand brand);
    Optional<Brand> findById(Long id);
    Optional<Brand> findActiveById(Long id);
    boolean existsActiveByNameIgnoreCase(String name);
    boolean existsActiveByNameIgnoreCaseAndIdNot(String name, Long id);
    Page<Brand> findAllActive(Pageable pageable);
    List<Brand> findAllActiveByIdIn(List<Long> ids);
}

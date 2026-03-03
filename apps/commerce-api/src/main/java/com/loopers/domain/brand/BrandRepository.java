package com.loopers.domain.brand;

import com.loopers.domain.brand.vo.BrandName;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;
import java.util.UUID;

public interface BrandRepository {
    Brand save(Brand brand);

    Optional<Brand> findById(UUID id);

    Page<Brand> findAll(Pageable pageable);

    boolean existsById(UUID id);

    boolean existsByName(BrandName name);

    void delete(Brand brand);
}

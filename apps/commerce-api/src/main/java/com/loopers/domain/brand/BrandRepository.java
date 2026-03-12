package com.loopers.domain.brand;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface BrandRepository {
    BrandModel save(BrandModel brandModel);

    Optional<BrandModel> findById(Long id);

    Optional<BrandModel> findByName(String name);

    Page<BrandModel> findAll(Pageable pageable);

    List<BrandModel> findAllByIdIn(List<Long> ids);
}

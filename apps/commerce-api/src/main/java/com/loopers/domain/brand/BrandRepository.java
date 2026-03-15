package com.loopers.domain.brand;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface BrandRepository {
    BrandModel save(BrandModel brandModel);
    Optional<BrandModel> findById(Long id);
    List<BrandModel> findAllByIds(Set<Long> ids);
}

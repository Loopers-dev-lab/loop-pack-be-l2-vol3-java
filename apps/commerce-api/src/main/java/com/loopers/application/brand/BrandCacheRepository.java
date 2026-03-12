package com.loopers.application.brand;

import com.loopers.domain.brand.Brand;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface BrandCacheRepository {
    Optional<Brand> findById(UUID id);

    List<Brand> findAll();

    Map<UUID, String> findNamesByIds(Collection<UUID> brandIds);

    boolean existsById(UUID id);

    void save(Brand brand);

    void delete(UUID id);
}

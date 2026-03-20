package com.loopers.infrastructure.brand.db;

import com.loopers.application.brand.BrandCacheRepository;
import com.loopers.domain.brand.Brand;
import com.loopers.infrastructure.brand.BrandEntity;
import com.loopers.infrastructure.brand.BrandJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
@ConditionalOnProperty(prefix = "loopers.cache.brand-category", name = "enabled", havingValue = "false")
@RequiredArgsConstructor
public class BrandDbPassThroughCacheRepositoryImpl implements BrandCacheRepository {

    private final BrandJpaRepository brandJpaRepository;

    @Override
    public Optional<Brand> findById(UUID id) {
        return brandJpaRepository.findByReferenceIdAndDeletedAtIsNull(id)
                .map(BrandEntity::toDomain);
    }

    @Override
    public List<Brand> findAll() {
        return brandJpaRepository.findAllByDeletedAtIsNullOrderByIdAsc().stream()
                .map(BrandEntity::toDomain)
                .toList();
    }

    @Override
    public Map<UUID, String> findNamesByIds(Collection<UUID> brandIds) {
        LinkedHashMap<UUID, String> result = new LinkedHashMap<>();
        LinkedHashSet<UUID> distinctIds = new LinkedHashSet<>(brandIds);

        Map<UUID, String> persistedNames = brandJpaRepository.findAllByReferenceIdInAndDeletedAtIsNull(distinctIds).stream()
                .map(BrandEntity::toDomain)
                .collect(LinkedHashMap::new, (map, brand) -> map.put(brand.id(), brand.name().value()), LinkedHashMap::putAll);

        for (UUID brandId : distinctIds) {
            result.put(brandId, persistedNames.get(brandId));
        }
        return result;
    }

    @Override
    public boolean existsById(UUID id) {
        return brandJpaRepository.existsByReferenceIdAndDeletedAtIsNull(id);
    }

    @Override
    public void save(Brand brand) {
    }

    @Override
    public void delete(UUID id) {
    }
}

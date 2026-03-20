package com.loopers.infrastructure.brand.redis;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.application.brand.BrandCacheRepository;
import com.loopers.config.redis.RedisConfig;
import com.loopers.domain.brand.Brand;
import com.loopers.infrastructure.brand.BrandEntity;
import com.loopers.infrastructure.brand.BrandJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
@ConditionalOnProperty(prefix = "loopers.cache.brand-category", name = "enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class BrandCacheRepositoryImpl implements BrandCacheRepository {

    private static final String BRAND_KEY_PREFIX = "brand:";
    private static final String BRAND_ALL_KEY = "brand:all";

    @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER)
    private final RedisTemplate<String, String> redisTemplate;
    private final BrandJpaRepository brandJpaRepository;
    private final ObjectMapper objectMapper;

    @Override
    public Optional<Brand> findById(UUID id) {
        try {
            String cached = redisTemplate.opsForValue().get(brandKey(id));
            if (cached != null && !cached.isBlank()) {
                return Optional.of(readBrand(cached));
            }
        } catch (RuntimeException ignored) {
        }

        Optional<Brand> brand = brandJpaRepository.findByReferenceIdAndDeletedAtIsNull(id)
                .map(BrandEntity::toDomain);
        brand.ifPresent(this::save);
        return brand;
    }

    @Override
    public List<Brand> findAll() {
        try {
            String cached = redisTemplate.opsForValue().get(BRAND_ALL_KEY);
            if (cached != null && !cached.isBlank()) {
                return readBrands(cached);
            }
        } catch (RuntimeException ignored) {
        }

        List<Brand> brands = brandJpaRepository.findAllByDeletedAtIsNullOrderByIdAsc().stream()
                .map(BrandEntity::toDomain)
                .toList();
        if (!brands.isEmpty()) {
            saveAll(brands);
        }
        return brands;
    }

    @Override
    public Map<UUID, String> findNamesByIds(Collection<UUID> brandIds) {
        LinkedHashMap<UUID, String> result = new LinkedHashMap<>();
        LinkedHashSet<UUID> distinctIds = new LinkedHashSet<>(brandIds);
        LinkedHashSet<UUID> missingIds = new LinkedHashSet<>();

        for (UUID brandId : distinctIds) {
            try {
                String cached = redisTemplate.opsForValue().get(brandKey(brandId));
                if (cached != null && !cached.isBlank()) {
                    result.put(brandId, readBrand(cached).name().value());
                    continue;
                }
            } catch (RuntimeException ignored) {
            }
            missingIds.add(brandId);
        }

        if (!missingIds.isEmpty()) {
            Map<UUID, Brand> persistedBrands = brandJpaRepository.findAllByReferenceIdInAndDeletedAtIsNull(missingIds).stream()
                    .map(BrandEntity::toDomain)
                    .collect(LinkedHashMap::new, (map, brand) -> map.put(brand.id(), brand), LinkedHashMap::putAll);

            for (UUID missingId : missingIds) {
                Brand brand = persistedBrands.get(missingId);
                if (brand == null) {
                    result.put(missingId, null);
                    continue;
                }
                save(brand);
                result.put(missingId, brand.name().value());
            }
        }

        return result;
    }

    @Override
    public boolean existsById(UUID id) {
        return findById(id).isPresent();
    }

    @Override
    public void save(Brand brand) {
        try {
            redisTemplate.opsForValue().set(brandKey(brand.id()), writeBrand(BrandCacheDocument.from(brand)));
            redisTemplate.delete(BRAND_ALL_KEY);
        } catch (RuntimeException ignored) {
        }
    }

    @Override
    public void delete(UUID id) {
        try {
            redisTemplate.delete(brandKey(id));
            redisTemplate.delete(BRAND_ALL_KEY);
        } catch (RuntimeException ignored) {
        }
    }

    private void saveAll(List<Brand> brands) {
        try {
            List<BrandCacheDocument> documents = brands.stream()
                    .map(BrandCacheDocument::from)
                    .toList();
            redisTemplate.opsForValue().set(BRAND_ALL_KEY, writeBrands(documents));
            for (Brand brand : brands) {
                redisTemplate.opsForValue().set(brandKey(brand.id()), writeBrand(BrandCacheDocument.from(brand)));
            }
        } catch (RuntimeException ignored) {
        }
    }

    private String brandKey(UUID id) {
        return BRAND_KEY_PREFIX + id;
    }

    private Brand readBrand(String raw) {
        try {
            return objectMapper.readValue(raw, BrandCacheDocument.class).toDomain();
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("브랜드 Redis 데이터 역직렬화에 실패했습니다.", e);
        }
    }

    private List<Brand> readBrands(String raw) {
        try {
            JavaType type = objectMapper.getTypeFactory().constructCollectionType(List.class, BrandCacheDocument.class);
            List<BrandCacheDocument> documents = objectMapper.readValue(raw, type);
            return documents.stream()
                    .map(BrandCacheDocument::toDomain)
                    .toList();
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("브랜드 목록 Redis 데이터 역직렬화에 실패했습니다.", e);
        }
    }

    private String writeBrand(BrandCacheDocument document) {
        try {
            return objectMapper.writeValueAsString(document);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("브랜드 Redis 데이터 직렬화에 실패했습니다.", e);
        }
    }

    private String writeBrands(List<BrandCacheDocument> documents) {
        try {
            return objectMapper.writeValueAsString(documents);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("브랜드 목록 Redis 데이터 직렬화에 실패했습니다.", e);
        }
    }
}

package com.loopers.fake;

import com.loopers.domain.BaseEntity;
import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandRepository;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class FakeBrandRepository implements BrandRepository {

    private final Map<Long, Brand> store = new ConcurrentHashMap<>();
    private long sequence = 1L;

    @Override
    public Brand save(Brand brand) {
        if (brand.getId() == null || brand.getId() == 0L) {
            long id = sequence++;
            setBaseEntityId(brand, id);
        }
        store.put(brand.getId(), brand);
        return brand;
    }

    @Override
    public Optional<Brand> findById(Long id) {
        return Optional.ofNullable(store.get(id))
            .filter(brand -> brand.getDeletedAt() == null);
    }

    @Override
    public List<Brand> findAll() {
        return store.values().stream()
            .filter(brand -> brand.getDeletedAt() == null)
            .toList();
    }

    @Override
    public List<Brand> findAllByIds(Set<Long> ids) {
        return store.values().stream()
            .filter(brand -> brand.getDeletedAt() == null)
            .filter(brand -> ids.contains(brand.getId()))
            .toList();
    }

    private void setBaseEntityId(Object entity, long id) {
        try {
            Field idField = BaseEntity.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(entity, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}

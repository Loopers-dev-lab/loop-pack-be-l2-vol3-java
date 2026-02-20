package com.loopers.domain.brand;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

class FakeBrandRepository implements BrandRepository {

    private final Map<Long, Brand> store = new HashMap<>();
    private long sequence = 1L;

    @Override
    public Brand save(Brand brand) {
        store.put(sequence++, brand);
        return brand;
    }

    @Override
    public Optional<Brand> findById(Long id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public List<Brand> findAll() {
        return new ArrayList<>(store.values());
    }

    @Override
    public boolean existsByName(BrandName name) {
        return store.values().stream()
            .anyMatch(brand -> brand.getName().equals(name));
    }
}

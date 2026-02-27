package com.loopers.domain.brand;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

public class InMemoryBrandRepository implements BrandRepository {
    private final Map<Long, Brand> store = new HashMap<>();
    private final AtomicLong idGenerator = new AtomicLong(1);

    @Override
    public Brand save(Brand brand) {
        if (brand.getId() == 0L) {
            try {
                var idField = brand.getClass().getSuperclass().getDeclaredField("id");
                idField.setAccessible(true);
                idField.set(brand, idGenerator.getAndIncrement());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        store.put(brand.getId(), brand);
        return brand;
    }

    @Override
    public Optional<Brand> findById(Long id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public Page<Brand> findAllByDeletedAtIsNull(Pageable pageable) {
        var list = store.values().stream()
                        .filter(b -> b.getDeletedAt() == null)
                        .toList();
        return new PageImpl<>(list, pageable, list.size());
    }

    @Override
    public List<Brand> findAllByIdIn(Collection<Long> ids) {
        return store.values().stream()
                    .filter(b -> ids.contains(b.getId()))
                    .toList();
    }
}

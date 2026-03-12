package com.loopers.domain.brand;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

public class FakeBrandRepository implements BrandRepository {

    private final Map<Long, BrandModel> store = new HashMap<>();
    private final AtomicLong idGenerator = new AtomicLong(1);

    @Override
    public BrandModel save(BrandModel brandModel) {
        if (brandModel.getId() == 0L) {
            try {
                var idField = brandModel.getClass().getSuperclass().getDeclaredField("id");
                idField.setAccessible(true);
                idField.set(brandModel, idGenerator.getAndIncrement());
            } catch (NoSuchFieldException | IllegalAccessException e) {
                throw new RuntimeException(e);
            }
        }
        store.put(brandModel.getId(), brandModel);
        return brandModel;
    }

    @Override
    public Optional<BrandModel> findById(Long id) {
        return Optional.ofNullable(store.get(id))
            .filter(brand -> brand.getDeletedAt() == null);
    }

    @Override
    public Optional<BrandModel> findByName(String name) {
        return store.values().stream()
            .filter(brand -> brand.getDeletedAt() == null)
            .filter(brand -> brand.getName().equals(name))
            .findFirst();
    }

    @Override
    public Page<BrandModel> findAll(Pageable pageable) {
        List<BrandModel> activeModels = store.values().stream()
            .filter(brand -> brand.getDeletedAt() == null)
            .toList();

        int start = (int) pageable.getOffset();
        int end = Math.min(start + pageable.getPageSize(), activeModels.size());

        List<BrandModel> pageContent = start >= activeModels.size()
            ? new ArrayList<>()
            : activeModels.subList(start, end);

        return new PageImpl<>(pageContent, pageable, activeModels.size());
    }

    @Override
    public List<BrandModel> findAllByIdIn(List<Long> ids) {
        return store.values().stream()
            .filter(brand -> brand.getDeletedAt() == null)
            .filter(brand -> ids.contains(brand.getId()))
            .toList();
    }
}

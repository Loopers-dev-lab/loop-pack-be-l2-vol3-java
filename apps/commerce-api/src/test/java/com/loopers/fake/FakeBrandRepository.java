package com.loopers.fake;

import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandRepository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 테스트용 Fake BrandRepository.
 * Map 기반 in-memory 구현.
 */
public class FakeBrandRepository implements BrandRepository {

    private final Map<Long, Brand> store = new HashMap<>();
    private final AtomicLong idGenerator = new AtomicLong(1);

    @Override
    public Brand save(Brand brand) {
        Long id = brand.getId();
        if (id == null) {
            id = idGenerator.getAndIncrement();
            // reconstitute를 통해 ID가 할당된 새 객체 생성
            brand = Brand.reconstitute(
                id,
                brand.getName(),
                brand.getDescription(),
                brand.getLogoUrl(),
                brand.getCreatedAt(),
                brand.getUpdatedAt(),
                brand.getDeletedAt()
            );
        }
        store.put(id, brand);
        return brand;
    }

    @Override
    public Optional<Brand> findById(Long id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public Optional<Brand> findByIdActive(Long id) {
        return findById(id).filter(brand -> !brand.isDeleted());
    }

    @Override
    public List<Brand> findAllActive() {
        return store.values().stream()
            .filter(brand -> !brand.isDeleted())
            .toList();
    }

    @Override
    public boolean existsByName(String name) {
        return store.values().stream()
            .filter(brand -> !brand.isDeleted())
            .anyMatch(brand -> brand.getName().equals(name));
    }

    @Override
    public boolean existsByNameAndIdNot(String name, Long id) {
        return store.values().stream()
            .filter(brand -> !brand.isDeleted())
            .filter(brand -> !brand.getId().equals(id))
            .anyMatch(brand -> brand.getName().equals(name));
    }

    /**
     * 테스트용: 저장소 초기화
     */
    public void clear() {
        store.clear();
        idGenerator.set(1);
    }

    /**
     * 테스트용: 저장된 브랜드 수 조회
     */
    public int size() {
        return store.size();
    }
}
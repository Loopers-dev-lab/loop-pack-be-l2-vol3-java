package com.loopers.fake;

import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.product.ProductSort;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 테스트용 Fake ProductRepository.
 * Map 기반 in-memory 구현.
 */
public class FakeProductRepository implements ProductRepository {

    private final Map<Long, Product> store = new HashMap<>();
    private final AtomicLong idGenerator = new AtomicLong(1);

    @Override
    public Product save(Product product) {
        Long id = product.getId();
        if (id == null) {
            id = idGenerator.getAndIncrement();
            product = Product.reconstitute(
                id,
                product.getBrandId(),
                product.getName(),
                product.getDescription(),
                product.getPrice(),
                product.getStock(),
                product.getImageUrl(),
                product.getCreatedAt(),
                product.getUpdatedAt(),
                product.getDeletedAt()
            );
        }
        store.put(id, product);
        return product;
    }

    @Override
    public Optional<Product> findById(Long id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public Optional<Product> findByIdActive(Long id) {
        return findById(id).filter(product -> !product.isDeleted());
    }

    @Override
    public Optional<Product> findByIdWithLock(Long id) {
        // Fake에서는 락 없이 동일하게 동작
        return findByIdActive(id);
    }

    @Override
    public List<Product> findAllActive(ProductSort sort, int offset, int limit) {
        return store.values().stream()
            .filter(product -> !product.isDeleted())
            .sorted(getComparator(sort))
            .skip(offset)
            .limit(limit)
            .toList();
    }

    @Override
    public List<Product> findAllByBrandIdActive(Long brandId, ProductSort sort, int offset, int limit) {
        return store.values().stream()
            .filter(product -> !product.isDeleted())
            .filter(product -> product.getBrandId().equals(brandId))
            .sorted(getComparator(sort))
            .skip(offset)
            .limit(limit)
            .toList();
    }

    @Override
    public List<Product> findAllByBrandIdActive(Long brandId) {
        return store.values().stream()
            .filter(product -> !product.isDeleted())
            .filter(product -> product.getBrandId().equals(brandId))
            .toList();
    }

    @Override
    public List<Product> findAllByIds(List<Long> ids) {
        return store.values().stream()
            .filter(product -> ids.contains(product.getId()))
            .toList();
    }

    @Override
    public long countActive() {
        return store.values().stream()
            .filter(product -> !product.isDeleted())
            .count();
    }

    @Override
    public long countByBrandIdActive(Long brandId) {
        return store.values().stream()
            .filter(product -> !product.isDeleted())
            .filter(product -> product.getBrandId().equals(brandId))
            .count();
    }

    private Comparator<Product> getComparator(ProductSort sort) {
        return switch (sort) {
            case LATEST -> Comparator.comparing(Product::getCreatedAt).reversed();
            case PRICE_ASC -> Comparator.comparing(p -> p.getPrice().amount());
            case LIKES_DESC -> Comparator.comparing(Product::getCreatedAt).reversed(); // Application에서 처리
        };
    }

    /**
     * 테스트용: 저장소 초기화
     */
    public void clear() {
        store.clear();
        idGenerator.set(1);
    }

    /**
     * 테스트용: 저장된 상품 수 조회
     */
    public int size() {
        return store.size();
    }
}

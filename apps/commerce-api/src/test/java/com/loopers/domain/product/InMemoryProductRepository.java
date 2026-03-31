package com.loopers.domain.product;

import com.loopers.domain.product.ProductOrder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

public class InMemoryProductRepository implements ProductRepository {
    private final Map<Long, Product> store = new HashMap<>();
    private final AtomicLong idGenerator = new AtomicLong(1);

    @Override
    public Product save(Product product) {
        if (product.getId() == 0L) {
            try {
                var idField = product.getClass().getSuperclass().getDeclaredField("id");
                idField.setAccessible(true);
                idField.set(product, idGenerator.getAndIncrement());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        store.put(product.getId(), product);
        return product;
    }

    @Override
    public Optional<Product> findById(Long id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public Page<Product> findActiveProducts(Long brandId, ProductOrder sort, Pageable pageable) {
        Stream<Product> stream = store.values().stream()
                .filter(Product::isActive);

        if (brandId != null) {
            stream = stream.filter(p -> p.getBrandId().equals(brandId));
        }

        Comparator<Product> comparator = switch (sort) {
            case PRICE_ASC -> Comparator.comparing(Product::getPrice);
            // likes_desc는 Like 도메인 구현 전이므로 인메모리에서 id 기준으로 대체
            case LIKES_DESC, LATEST -> Comparator.comparing(Product::getId).reversed();
        };

        List<Product> list = stream.sorted(comparator).toList();
        return new PageImpl<>(list, pageable, list.size());
    }

    @Override
    public Page<Product> findAllProducts(Long brandId, Pageable pageable) {
        List<Product> list = store.values().stream()
                                  .filter(p -> p.getDeletedAt() == null)
                                  .filter(p -> brandId == null || p.getBrandId().equals(brandId))
                                  .sorted(Comparator.comparing(Product::getId).reversed())
                                  .toList();

        int start = (int) pageable.getOffset();
        int end = Math.min(start + pageable.getPageSize(), list.size());
        List<Product> content = start >= list.size() ? List.of() : list.subList(start, end);

        return new PageImpl<>(content, pageable, list.size());
    }

    @Override
    public List<Product> findAllByBrandIdAndDeletedAtIsNull(Long brandId) {
        return store.values().stream()
                .filter(p -> p.getBrandId().equals(brandId) && p.getDeletedAt() == null)
                .toList();
    }

    @Override
    public List<Product> findAllByIdInAndDeletedAtIsNull(List<Long> ids) {
        return store.values().stream()
                .filter(p -> ids.contains(p.getId()) && p.getDeletedAt() == null)
                .toList();
    }

    @Override
    public boolean decreaseStockIfEnough(Long productId, Integer quantity) {
        throw new UnsupportedOperationException("Atomic UPDATE는 DB에 의존하므로 통합테스트에서 커버합니다.");
    }

    @Override
    public int increaseStock(Long productId, Integer quantity) {
        throw new UnsupportedOperationException("Atomic UPDATE는 DB에 의존하므로 통합테스트에서 커버합니다.");
    }

}

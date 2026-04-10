package com.loopers.domain.product;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

public class FakeProductRepository implements ProductRepository {

    private final Map<Long, ProductModel> store = new HashMap<>();
    private final AtomicLong idGenerator = new AtomicLong(1);

    @Override
    public ProductModel save(ProductModel productModel) {
        if (productModel.getId() == 0L) {
            try {
                var idField = productModel.getClass().getSuperclass().getDeclaredField("id");
                idField.setAccessible(true);
                idField.set(productModel, idGenerator.getAndIncrement());
            } catch (NoSuchFieldException | IllegalAccessException e) {
                throw new RuntimeException(e);
            }
        }
        store.put(productModel.getId(), productModel);
        return productModel;
    }

    @Override
    public Optional<ProductModel> findById(Long id) {
        return Optional.ofNullable(store.get(id))
            .filter(product -> product.getDeletedAt() == null);
    }

    @Override
    public Page<ProductModel> findAll(Pageable pageable) {
        List<ProductModel> activeModels = store.values().stream()
            .filter(product -> product.getDeletedAt() == null)
            .toList();

        int start = (int) pageable.getOffset();
        int end = Math.min(start + pageable.getPageSize(), activeModels.size());

        List<ProductModel> pageContent = start >= activeModels.size()
            ? new ArrayList<>()
            : activeModels.subList(start, end);

        return new PageImpl<>(pageContent, pageable, activeModels.size());
    }

    @Override
    public List<ProductModel> findAll() {
        return store.values().stream()
            .filter(product -> product.getDeletedAt() == null)
            .toList();
    }

    @Override
    public Page<ProductModel> findAllByBrandId(Long brandId, Pageable pageable) {
        List<ProductModel> filtered = store.values().stream()
            .filter(product -> product.getDeletedAt() == null)
            .filter(product -> product.getBrandId().equals(brandId))
            .toList();

        int start = (int) pageable.getOffset();
        int end = Math.min(start + pageable.getPageSize(), filtered.size());

        List<ProductModel> pageContent = start >= filtered.size()
            ? new ArrayList<>()
            : filtered.subList(start, end);

        return new PageImpl<>(pageContent, pageable, filtered.size());
    }

    @Override
    public List<ProductModel> findAllByBrandId(Long brandId) {
        return store.values().stream()
            .filter(product -> product.getDeletedAt() == null)
            .filter(product -> product.getBrandId().equals(brandId))
            .toList();
    }

    @Override
    public List<ProductModel> findAllByIdIn(List<Long> ids) {
        return store.values().stream()
            .filter(product -> product.getDeletedAt() == null)
            .filter(product -> ids.contains(product.getId()))
            .toList();
    }

    @Override
    public Page<ProductModel> findAllSortedByLikeCountDesc(Pageable pageable) {
        List<ProductModel> all = store.values().stream()
                .filter(product -> product.getDeletedAt() == null)
                .toList();
        int start = (int) pageable.getOffset();
        int end = Math.min(start + pageable.getPageSize(), all.size());
        List<ProductModel> pageContent = start >= all.size()
                ? new ArrayList<>() : all.subList(start, end);
        return new PageImpl<>(pageContent, pageable, all.size());
    }

    @Override
    public Page<ProductModel> findAllByBrandIdSortedByLikeCountDesc(Long brandId, Pageable pageable) {
        List<ProductModel> filtered = store.values().stream()
                .filter(product -> product.getDeletedAt() == null)
                .filter(product -> product.getBrandId().equals(brandId))
                .toList();
        int start = (int) pageable.getOffset();
        int end = Math.min(start + pageable.getPageSize(), filtered.size());
        List<ProductModel> pageContent = start >= filtered.size()
                ? new ArrayList<>() : filtered.subList(start, end);
        return new PageImpl<>(pageContent, pageable, filtered.size());
    }

    @Override
    public Map<Long, Long> findLikeCountsByProductIds(List<Long> productIds) {
        return Collections.emptyMap();
    }

    @Override
    public long findLikeCountByProductId(Long productId) {
        return 0L;
    }

    @Override
    public int decreaseStock(Long id, int quantity) {
        return Optional.ofNullable(store.get(id))
                .filter(product -> product.getDeletedAt() == null)
                .filter(product -> product.getStock() >= quantity)
                .map(product -> {
                    product.decreaseStock(quantity);
                    return 1;
                })
                .orElse(0);
    }

    @Override
    public int increaseStock(Long id, int quantity) {
        return Optional.ofNullable(store.get(id))
                .filter(product -> product.getDeletedAt() == null)
                .map(product -> {
                    product.increaseStock(quantity);
                    return 1;
                })
                .orElse(0);
    }

    @Override
    public List<ProductModel> findByIdModulo(int divisor, int remainder) {
        return store.values().stream()
                .filter(product -> product.getDeletedAt() == null)
                .filter(product -> product.getId() % divisor == remainder)
                .toList();
    }
}

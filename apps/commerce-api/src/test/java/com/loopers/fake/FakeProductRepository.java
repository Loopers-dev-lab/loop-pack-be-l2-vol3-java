package com.loopers.fake;

import com.loopers.domain.BaseEntity;
import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.product.ProductWithBrand;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class FakeProductRepository implements ProductRepository {

    private final Map<Long, Product> store = new ConcurrentHashMap<>();
    private long sequence = 1L;
    private BrandRepository brandRepository;

    @Override
    public Product save(Product product) {
        if (product.getId() == null || product.getId() == 0L) {
            long id = sequence++;
            setBaseEntityId(product, id);
        }
        store.put(product.getId(), product);
        return product;
    }

    @Override
    public Optional<Product> findById(Long id) {
        return Optional.ofNullable(store.get(id))
                .filter(product -> product.getDeletedAt() == null);
    }

    @Override
    public List<Product> findAll() {
        return store.values().stream()
                .filter(product -> product.getDeletedAt() == null)
                .toList();
    }

    @Override
    public List<Product> findAllByBrandId(Long brandId) {
        return store.values().stream()
                .filter(product -> product.getDeletedAt() == null)
                .filter(product -> product.getBrandId().equals(brandId))
                .toList();
    }

    @Override
    public List<ProductWithBrand> findAllWithBrand() {
        return store.values().stream()
                .filter(product -> product.getDeletedAt() == null)
                .map(product -> new ProductWithBrand(product, resolveBrandName(product.getBrandId())))
                .toList();
    }

    @Override
    public List<ProductWithBrand> findAllWithBrand(String sort) {
        Comparator<Product> comparator = toComparator(sort);
        return store.values().stream()
                .filter(product -> product.getDeletedAt() == null)
                .sorted(comparator)
                .map(product -> new ProductWithBrand(product, resolveBrandName(product.getBrandId())))
                .toList();
    }

    @Override
    public List<ProductWithBrand> findAllByBrandIdWithBrand(Long brandId) {
        return store.values().stream()
                .filter(product -> product.getDeletedAt() == null)
                .filter(product -> product.getBrandId().equals(brandId))
                .map(product -> new ProductWithBrand(product, resolveBrandName(product.getBrandId())))
                .toList();
    }

    public void setBrandRepository(BrandRepository brandRepository) {
        this.brandRepository = brandRepository;
    }

    private String resolveBrandName(Long brandId) {
        if (brandRepository == null) return null;
        return brandRepository.findById(brandId)
                .map(Brand::getName)
                .orElse(null);
    }

    private Comparator<Product> toComparator(String sort) {
        if (sort == null) {
            return Comparator.comparing(Product::getId).reversed();
        }
        return switch (sort) {
            case "price_asc" -> Comparator.comparingInt(p -> p.getPrice().getValue());
            case "likes_desc" -> Comparator.comparingInt(Product::getLikeCount).reversed();
            default -> Comparator.comparing(Product::getId).reversed();
        };
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

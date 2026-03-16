package com.loopers.fake;

import com.loopers.domain.BaseEntity;
import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.product.ProductWithBrand;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

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
    public List<Product> findAllByIdsWithLock(List<Long> ids) {
        return ids.stream()
            .distinct()
            .map(store::get)
            .filter(p -> p != null && p.getDeletedAt() == null)
            .toList();
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
                .map(product -> new ProductWithBrand(product, resolveBrandName(product.getBrandId()), product.getLikeCount()))
                .toList();
    }

    @Override
    public List<ProductWithBrand> findAllWithBrand(String sort) {
        Comparator<Product> comparator = toComparator(sort);
        return store.values().stream()
                .filter(product -> product.getDeletedAt() == null)
                .sorted(comparator)
                .map(product -> new ProductWithBrand(product, resolveBrandName(product.getBrandId()), product.getLikeCount()))
                .toList();
    }

    @Override
    public List<ProductWithBrand> findAllByBrandIdWithBrand(Long brandId) {
        return store.values().stream()
                .filter(product -> product.getDeletedAt() == null)
                .filter(product -> product.getBrandId().equals(brandId))
                .map(product -> new ProductWithBrand(product, resolveBrandName(product.getBrandId()), product.getLikeCount()))
                .toList();
    }

    @Override
    public Page<ProductWithBrand> findAllWithBrand(String sort, Pageable pageable) {
        List<ProductWithBrand> all = findAllWithBrand(sort);
        return toPage(all, pageable);
    }

    @Override
    public Page<ProductWithBrand> findAllByBrandIdWithBrand(Long brandId, String sort, Pageable pageable) {
        Comparator<Product> comparator = toComparator(sort);
        List<ProductWithBrand> all = store.values().stream()
                .filter(product -> product.getDeletedAt() == null)
                .filter(product -> product.getBrandId().equals(brandId))
                .sorted(comparator)
                .map(product -> new ProductWithBrand(product, resolveBrandName(product.getBrandId()), product.getLikeCount()))
                .toList();
        return toPage(all, pageable);
    }

    @Override
    public int incrementLikeCount(Long productId) {
        Product product = store.get(productId);
        if (product == null || product.getDeletedAt() != null) return 0;
        setLikeCount(product, product.getLikeCount() + 1);
        return 1;
    }

    @Override
    public int decrementLikeCount(Long productId) {
        Product product = store.get(productId);
        if (product == null || product.getDeletedAt() != null) return 0;
        setLikeCount(product, Math.max(0, product.getLikeCount() - 1));
        return 1;
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
            case "likes_desc" -> Comparator.<Product, Integer>comparing(Product::getLikeCount).reversed()
                    .thenComparing(Comparator.comparing(Product::getId).reversed());
            default -> Comparator.comparing(Product::getId).reversed();
        };
    }

    private Page<ProductWithBrand> toPage(List<ProductWithBrand> all, Pageable pageable) {
        int start = (int) pageable.getOffset();
        int end = Math.min(start + pageable.getPageSize(), all.size());
        List<ProductWithBrand> pageContent = start < all.size() ? all.subList(start, end) : List.of();
        return new PageImpl<>(pageContent, pageable, all.size());
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

    private void setLikeCount(Product product, int count) {
        try {
            Field likeCountField = Product.class.getDeclaredField("likeCount");
            likeCountField.setAccessible(true);
            likeCountField.setInt(product, count);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}

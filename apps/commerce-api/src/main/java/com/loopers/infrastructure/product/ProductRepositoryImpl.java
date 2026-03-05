package com.loopers.infrastructure.product;

import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.product.ProductWithBrand;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class ProductRepositoryImpl implements ProductRepository {

    private final ProductJpaRepository productJpaRepository;

    @Override
    public Product save(Product product) {
        return productJpaRepository.save(product);
    }

    @Override
    public Optional<Product> findById(Long id) {
        return productJpaRepository.findByIdAndDeletedAtIsNull(id);
    }

    @Override
    public Optional<Product> findByIdWithLock(Long id) {
        return productJpaRepository.findByIdWithLock(id);
    }

    @Override
    public List<Product> findAllByIdsWithLock(List<Long> ids) {
        return productJpaRepository.findAllByIdsWithLock(ids);
    }

    @Override
    public List<Product> findAll() {
        return productJpaRepository.findAllByDeletedAtIsNull();
    }

    @Override
    public List<Product> findAllByBrandId(Long brandId) {
        return productJpaRepository.findAllByBrandIdAndDeletedAtIsNull(brandId);
    }

    @Override
    public List<ProductWithBrand> findAllWithBrand() {
        return productJpaRepository.findAllWithBrand().stream()
            .map(this::toProductWithBrand)
            .toList();
    }

    @Override
    public List<ProductWithBrand> findAllWithBrand(String sort) {
        return productJpaRepository.findAllWithBrand(toSort(sort)).stream()
            .map(this::toProductWithBrand)
            .toList();
    }

    @Override
    public List<ProductWithBrand> findAllByBrandIdWithBrand(Long brandId) {
        return productJpaRepository.findAllByBrandIdWithBrand(brandId).stream()
            .map(this::toProductWithBrand)
            .toList();
    }

    private ProductWithBrand toProductWithBrand(Object[] row) {
        return new ProductWithBrand((Product) row[0], (String) row[1], 0L);
    }

    private Sort toSort(String sort) {
        if (sort == null) {
            return Sort.by("createdAt").descending();
        }
        return switch (sort) {
            case "price_asc" -> Sort.by("price.value").ascending();
            default -> Sort.by("createdAt").descending();
        };
    }
}

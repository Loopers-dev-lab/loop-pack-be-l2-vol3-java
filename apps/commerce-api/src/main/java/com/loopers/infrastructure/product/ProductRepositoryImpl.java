package com.loopers.infrastructure.product;

import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class ProductRepositoryImpl implements ProductRepository {

    private final ProductJpaRepository productJpaRepository;

    // Command

    @Override
    public Product save(Product product) {
        return productJpaRepository.save(product);
    }

    @Override
    public int incrementLikeCount(Long productId) {
        return productJpaRepository.incrementLikeCount(productId);
    }

    @Override
    public int decrementLikeCountIfPositive(Long productId) {
        return productJpaRepository.decrementLikeCountIfPositive(productId);
    }

    @Override
    public List<Long> findIdsByBrandIdForCleanup(Long brandId, int batchSize) {
        return productJpaRepository.findIdsByBrandIdForCleanup(brandId, batchSize);
    }

    @Override
    public int softDeleteByIds(List<Long> ids) {
        return productJpaRepository.softDeleteByIds(ids);
    }

    // Query
    @Override
    public Optional<Product> findById(Long id) {
        return productJpaRepository.findById(id);
    }

    @Override
    public Optional<Product> findActiveById(Long id) {
        return productJpaRepository.findActiveById(id);
    }

    @Override
    public Optional<Product> findActiveWithActiveBrand(Long id) {
        return productJpaRepository.findActiveWithActiveBrand(id);
    }

    @Override
    public boolean existsActiveById(Long id) {
        return productJpaRepository.existsActiveWithActiveBrand(id);
    }

    @Override
    public List<Product> findAllByIdIn(Collection<Long> ids) {
        return productJpaRepository.findAllByIdIn(ids);
    }

    @Override
    public List<Product> findAllActiveByIdIn(Collection<Long> ids) {
        return productJpaRepository.findAllActiveWithActiveBrandByIdIn(ids);
    }

    @Override
    public Page<Product> findAll(String name, Long brandId, Boolean deleted, Pageable pageable) {
        return productJpaRepository.findAll(name, brandId, deleted, pageable);
    }

    @Override
    public Page<Product> findAllActive(Long brandId, Pageable pageable) {
        return productJpaRepository.findAllActive(brandId, pageable);
    }

    @Override
    public Page<Product> findAllActiveWithActiveBrand(Long brandId, Pageable pageable) {
        return productJpaRepository.findAllActiveWithActiveBrand(brandId, pageable);
    }

    @Override
    public List<Long> findBrandIdsWithUncleanedProducts() {
        return productJpaRepository.findBrandIdsWithUncleanedProducts();
    }

    @Override
    public List<Product> findAllActiveCursor(Long brandId, Long cursor, int limit) {
        return productJpaRepository.findAllActiveCursor(brandId, cursor, limit);
    }
}

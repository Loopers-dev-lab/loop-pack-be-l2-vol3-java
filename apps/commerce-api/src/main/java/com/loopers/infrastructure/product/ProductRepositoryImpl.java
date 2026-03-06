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
    public int decreaseStock(Long productId, int quantity) {
        return productJpaRepository.decreaseStock(productId, quantity);
    }

    @Override
    public int incrementLikeCount(Long productId) {
        return productJpaRepository.incrementLikeCount(productId);
    }

    @Override
    public int decrementLikeCount(Long productId) {
        return productJpaRepository.decrementLikeCount(productId);
    }

    @Override
    public int softDeleteByBrandIdInBatch(Long brandId, int batchSize) {
        return productJpaRepository.softDeleteByBrandIdInBatch(brandId, batchSize);
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
}

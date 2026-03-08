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
public class ProductRepositoryAdapter implements ProductRepository {

    private final ProductJpaRepository productJpaRepository;

    @Override
    public Optional<Product> findById(Long productId) {
        return productJpaRepository.findByIdAndDeletedAtIsNull(productId);
    }

    @Override
    public Optional<Product> findByIdIncludingDeleted(Long productId) {
        return productJpaRepository.findById(productId);
    }

    @Override
    public List<Product> findAllByBrandId(Long brandId) {
        return productJpaRepository.findAllByBrandIdAndDeletedAtIsNull(brandId);
    }

    @Override
    public List<Product> findAllByBrandIdIncludingDeleted(Long brandId) {
        return productJpaRepository.findAllByBrandId(brandId);
    }

    @Override
    public List<Product> findAllByIds(Collection<Long> productIds) {
        return productJpaRepository.findAllByIdInAndDeletedAtIsNull(productIds);
    }

    @Override
    public List<Product> findAllByIdsIncludingDeleted(Collection<Long> productIds) {
        return productJpaRepository.findAllByIdIn(productIds);
    }

    @Override
    public Page<Product> findByBrandId(Long brandId, Pageable pageable) {
        return productJpaRepository.findAllByBrandIdAndDeletedAtIsNull(brandId, pageable);
    }

    @Override
    public Page<Product> findAll(Pageable pageable) {
        return productJpaRepository.findAllByDeletedAtIsNull(pageable);
    }

    @Override
    public Product save(Product product) {
        return productJpaRepository.save(product);
    }

    @Override
    public boolean existsByName(String name) {
        return productJpaRepository.existsByNameAndDeletedAtIsNull(name);
    }
}

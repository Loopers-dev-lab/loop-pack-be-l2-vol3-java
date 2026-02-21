package com.loopers.infrastructure.product.persistence;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Repository;

import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductRepository;

import lombok.RequiredArgsConstructor;

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
        return productJpaRepository.findById(id);
    }

    @Override
    public List<Product> findAllByIdInAndDeletedAtIsNull(List<Long> productIds) {
        return productJpaRepository.findAllByIdInAndDeletedAtIsNull(productIds);
    }

    @Override
    public Slice<Product> findAllBy(Pageable pageable) {
        return productJpaRepository.findAllBy(pageable);
    }

    @Override
    public Slice<Product> findAllByBrandId(Long brandId, Pageable pageable) {
        return productJpaRepository.findAllByBrandId(brandId, pageable);
    }

    @Override
    public boolean existsByIdAndDeletedAtIsNull(Long productId) {
        return productJpaRepository.existsByIdAndDeletedAtIsNull(productId);
    }
}

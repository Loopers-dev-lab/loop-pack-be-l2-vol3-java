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
    public List<Product> findAllByBrandId(Long brandId) {
        return productJpaRepository.findAllByBrandId(brandId);
    }

    @Override
    public List<Product> findAllByIdIn(Collection<Long> ids) {
        return productJpaRepository.findAllByIdIn(ids);
    }

    @Override
    public Page<Product> findAll(String name, Long brandId, Boolean deleted, Pageable pageable) {
        return productJpaRepository.findAll(name, brandId, deleted, pageable);
    }

    @Override
    public Page<Product> findAllActive(Long brandId, Pageable pageable) {
        return productJpaRepository.findAllActive(brandId, pageable);
    }
}

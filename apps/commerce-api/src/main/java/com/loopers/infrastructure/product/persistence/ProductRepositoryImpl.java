package com.loopers.infrastructure.product.persistence;

import java.util.Optional;

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
}

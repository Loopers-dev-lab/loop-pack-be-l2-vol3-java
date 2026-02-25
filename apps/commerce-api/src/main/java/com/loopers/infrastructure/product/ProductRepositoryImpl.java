package com.loopers.infrastructure.product;

import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

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
}

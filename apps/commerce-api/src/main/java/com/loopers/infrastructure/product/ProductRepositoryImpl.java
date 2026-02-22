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

    @Override
    public Product save(Product product) {
        if (product.getId() == null) {
            ProductJpaEntity entity = ProductJpaEntity.from(product);
            ProductJpaEntity saved = productJpaRepository.save(entity);
            return saved.toDomain();
        }

        ProductJpaEntity entity = productJpaRepository.findById(product.getId())
                .orElseThrow(() -> new IllegalStateException("Product not found: " + product.getId()));
        entity.update(product);
        return entity.toDomain();
    }

    @Override
    public Optional<Product> findById(Long id) {
        return productJpaRepository.findById(id)
                .map(ProductJpaEntity::toDomain);
    }
}

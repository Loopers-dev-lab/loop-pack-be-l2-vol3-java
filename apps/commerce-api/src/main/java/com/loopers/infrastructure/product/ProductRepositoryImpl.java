package com.loopers.infrastructure.product;

import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.product.SortCondition;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
@Component
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
    public Optional<Product> findByIdForUpdate(Long id) {
        return productJpaRepository.findByIdForUpdate(id);
    }

    @Override
    public List<Product> findAll(SortCondition sort) {
        return switch (sort) {
            case latest -> productJpaRepository.findByDeletedAtIsNull(Sort.by(Sort.Direction.DESC, "createdAt"));
            case price_asc -> productJpaRepository.findByDeletedAtIsNull(Sort.by(Sort.Direction.ASC, "price"));
            case likes_desc -> productJpaRepository.findByDeletedAtIsNull(Sort.by(Sort.Direction.DESC, "likesCount"));
        };
    }
}
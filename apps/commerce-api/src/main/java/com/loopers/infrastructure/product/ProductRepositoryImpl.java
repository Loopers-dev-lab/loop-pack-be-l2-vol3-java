package com.loopers.infrastructure.product;

import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.product.ProductSortCondition;
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

    @Override
    public List<Product> findAll(ProductSortCondition condition) {
        Sort sort = switch (condition) {
            case LATEST -> Sort.by(Sort.Direction.DESC, "createdAt");
            case PRICE_ASC -> Sort.by(Sort.Direction.ASC, "price.amount");
            // TODO: QueryDSL 또는 별도 Read Model을 이용한 조회 최적화 예정
            // LIKES_DESC는 Like 테이블과 조인이 필요하여 현재는 LATEST로 대체
            case LIKES_DESC -> Sort.by(Sort.Direction.DESC, "createdAt");
        };

        return productJpaRepository.findAll(sort).stream()
                .map(ProductJpaEntity::toDomain)
                .toList();
    }
}

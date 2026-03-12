package com.loopers.infrastructure.product;

import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.product.query.ProductListCriteria;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class ProductRepositoryImpl implements ProductRepository {

    private final ProductJpaRepository productJpaRepository;

    @Override
    public Product save(Product product) {
        if (product.id() != null) {
            return productJpaRepository.findByReferenceId(product.id())
                    .map(entity -> {
                        entity.updateFrom(product);
                        if (product.deletedAt() != null) {
                            entity.delete();
                        }
                        return productJpaRepository.save(entity).toDomain();
                    })
                    .orElseGet(() -> productJpaRepository.save(ProductEntity.from(product)).toDomain());
        }
        ProductEntity entity = ProductEntity.from(product);
        ProductEntity saved = productJpaRepository.save(entity);
        return saved.toDomain();
    }

    @Override
    public Optional<Product> findById(UUID id) {
        return productJpaRepository.findByReferenceIdAndDeletedAtIsNull(id)
                .map(ProductEntity::toDomain);
    }

    @Override
    public List<Product> findAllByIdIn(List<UUID> ids) {
        return productJpaRepository.findAllByReferenceIdInAndDeletedAtIsNullOrderByIdAsc(ids)
                .stream()
                .map(ProductEntity::toDomain)
                .toList();
    }

    @Override
    public Optional<Product> findByIdIncludingDeleted(UUID id) {
        return productJpaRepository.findByReferenceId(id)
                .map(ProductEntity::toDomain);
    }

    @Override
    public Page<Product> search(ProductListCriteria criteria) {
        return productJpaRepository.findAll(ProductSpecifications.from(criteria), criteria.toPageable())
                .map(ProductEntity::toDomain);
    }

    @Override
    public Page<Product> findAllIncludingDeleted(ProductListCriteria criteria) {
        return productJpaRepository.findAll(ProductSpecifications.from(criteria, true), criteria.toPageable())
                .map(ProductEntity::toDomain);
    }

    @Override
    public List<UUID> findIdsByBrandId(UUID brandId) {
        return productJpaRepository.findReferenceIdsByBrandReferenceIdAndDeletedAtIsNull(brandId);
    }

    @Override
    public int updateLikeCount(UUID productId, long delta) {
        return productJpaRepository.updateLikeCount(productId, delta);
    }

    @Override
    public void softDeleteByBrandId(UUID brandId) {
        productJpaRepository.softDeleteByBrandReferenceId(brandId);
    }

    @Override
    public int decreaseStockAtomically(UUID productId, int quantity) {
        return productJpaRepository.decreaseStockAtomically(productId, quantity);
    }

    @Override
    public void delete(Product product) {
        productJpaRepository.findByReferenceId(product.id())
                .ifPresent(ProductEntity::delete);
    }
}

package com.loopers.infrastructure.product;

import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class ProductRepositoryImpl implements ProductRepository {

    private final ProductJpaRepository productJpaRepository;

    @Override
    public Product save(Product product) {
        if (product.id() != null) {
            return productJpaRepository.findById(product.id())
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
    public Optional<Product> findById(Long id) {
        return productJpaRepository.findByIdAndDeletedAtIsNull(id)
                .map(ProductEntity::toDomain);
    }

    @Override
    public Optional<Product> findByIdIncludingDeleted(Long id) {
        return productJpaRepository.findById(id)
                .map(ProductEntity::toDomain);
    }

    @Override
    public Page<Product> findAll(Long brandId, Pageable pageable) {
        if (brandId == null) {
            return productJpaRepository.findAllByDeletedAtIsNull(pageable).map(ProductEntity::toDomain);
        }
        return productJpaRepository.findAllByBrandIdAndDeletedAtIsNull(brandId, pageable).map(ProductEntity::toDomain);
    }

    @Override
    public Page<Product> findAllIncludingDeleted(Long brandId, Pageable pageable) {
        if (brandId == null) {
            return productJpaRepository.findAll(pageable).map(ProductEntity::toDomain);
        }
        return productJpaRepository.findAllByBrandId(brandId, pageable).map(ProductEntity::toDomain);
    }

    @Override
    public List<Long> findIdsByBrandId(Long brandId) {
        return productJpaRepository.findIdsByBrandIdAndDeletedAtIsNull(brandId);
    }

    @Override
    public void softDeleteByBrandId(Long brandId) {
        productJpaRepository.softDeleteByBrandId(brandId);
    }

    @Override
    public List<Product> findAllByIdInWithLock(List<Long> ids) {
        return productJpaRepository.findAllByIdInWithLock(ids)
                .stream().map(ProductEntity::toDomain).toList();
    }

    @Override
    public void delete(Product product) {
        productJpaRepository.findById(product.id())
                .ifPresent(ProductEntity::delete);
    }
}

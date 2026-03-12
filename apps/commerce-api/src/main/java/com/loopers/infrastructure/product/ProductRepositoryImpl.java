package com.loopers.infrastructure.product;

import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductRepository;
import com.loopers.infrastructure.brand.BrandEntity;
import com.loopers.infrastructure.brand.BrandJpaRepository;
import com.loopers.infrastructure.category.CategoryEntity;
import com.loopers.infrastructure.category.CategoryJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class ProductRepositoryImpl implements ProductRepository {

    private final ProductJpaRepository productJpaRepository;
    private final CategoryJpaRepository categoryJpaRepository;
    private final BrandJpaRepository brandJpaRepository;

    @Override
    public Product save(Product product) {
        CategoryEntity category = resolveCategory(product.categoryId());
        BrandEntity brand = resolveBrand(product.brandId());

        if (product.id() != null) {
            return productJpaRepository.findByReferenceId(product.id())
                    .map(entity -> {
                        entity.updateFrom(product, category.getId());
                        if (product.deletedAt() != null) {
                            entity.delete();
                        }
                        return productJpaRepository.save(entity).toDomain();
                    })
                    .orElseGet(() -> productJpaRepository.save(ProductEntity.from(product, category.getId(), brand.getId())).toDomain());
        }
        ProductEntity entity = ProductEntity.from(product, category.getId(), brand.getId());
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
    public Page<Product> findAll(UUID brandId, Pageable pageable) {
        if (brandId == null) {
            return productJpaRepository.findAllByDeletedAtIsNull(pageable).map(ProductEntity::toDomain);
        }
        return productJpaRepository.findAllByBrandReferenceIdAndDeletedAtIsNull(brandId, pageable).map(ProductEntity::toDomain);
    }

    @Override
    public Page<Product> findAllIncludingDeleted(UUID brandId, Pageable pageable) {
        if (brandId == null) {
            return productJpaRepository.findAll(pageable).map(ProductEntity::toDomain);
        }
        return productJpaRepository.findAllByBrandReferenceId(brandId, pageable).map(ProductEntity::toDomain);
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

    private CategoryEntity resolveCategory(UUID categoryReferenceId) {
        return categoryJpaRepository.findByReferenceIdAndDeletedAtIsNull(categoryReferenceId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않거나 삭제된 카테고리입니다."));
    }

    private BrandEntity resolveBrand(UUID brandReferenceId) {
        return brandJpaRepository.findByReferenceIdAndDeletedAtIsNull(brandReferenceId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않거나 삭제된 브랜드입니다."));
    }
}

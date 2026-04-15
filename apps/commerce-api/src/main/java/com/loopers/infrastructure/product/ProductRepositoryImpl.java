package com.loopers.infrastructure.product;

import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.product.ProductSortType;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
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
    public Optional<Product> findById(Long id) {
        return productJpaRepository.findByIdWithOptionsAndImages(id)
            .map(ProductEntity::toDomain);
    }

    @Override
    public Optional<Product> findByIdForUpdate(Long id) {
        return productJpaRepository.lockById(id)
            .flatMap(lockedId -> productJpaRepository.findByIdWithOptionsAndImages(lockedId)
                .map(ProductEntity::toDomain));
    }

    @Override
    public List<Product> findByIds(List<Long> ids) {
        return productJpaRepository.findAllByIdInAndDeletedAtIsNull(ids).stream()
            .map(ProductEntity::toDomain)
            .toList();
    }

    @Override
    public List<Product> findAllActive() {
        return productJpaRepository.findAllByDeletedAtIsNull().stream()
            .map(ProductEntity::toDomain)
            .toList();
    }

    @Override
    public List<Product> findAllActiveByCategoryId(Long categoryId) {
        return productJpaRepository.findAllByCategoryIdAndDeletedAtIsNull(categoryId).stream()
            .map(ProductEntity::toDomain)
            .toList();
    }

    @Override
    public List<Product> findAllActiveByBrandId(Long brandId) {
        return productJpaRepository.findAllByBrandIdAndDeletedAtIsNull(brandId).stream()
            .map(ProductEntity::toDomain)
            .toList();
    }

    @Override
    public List<Product> findAllActiveByCategoryIds(List<Long> categoryIds) {
        return productJpaRepository.findAllByCategoryIdInAndDeletedAtIsNull(categoryIds).stream()
            .map(ProductEntity::toDomain)
            .toList();
    }

    @Override
    public Page<Product> findProducts(Long categoryId, Long brandId, String keyword, ProductSortType sort, Pageable pageable) {
        return productJpaRepository.findProducts(categoryId, brandId, keyword, sort, pageable)
            .map(ProductEntity::toDomain);
    }

    @Override
    public Product save(Product product) {
        ProductEntity entity;
        if (product.getId() != null) {
            entity = productJpaRepository.findByIdWithOptionsAndImages(product.getId())
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "상품을 찾을 수 없습니다."));
            entity.update(
                product.getName(),
                product.getCategoryId(),
                product.getBasePrice(),
                product.getDiscount(),
                product.getDiscountType(),
                product.getStatus(),
                product.getLikeCount()
            );
            entity.syncOptions(product.getOptions());
            entity.syncImages(product.getImages());
        } else {
            entity = ProductEntity.from(product);
        }
        ProductEntity saved = productJpaRepository.save(entity);
        return saved.toDomain();
    }

    @Override
    public void delete(Long id) {
        productJpaRepository.deleteById(id);
    }

    @Override
    public void softDeleteAllByIds(List<Long> ids) {
        if (ids != null && !ids.isEmpty()) {
            productJpaRepository.softDeleteAllByIds(ids);
        }
    }

    @Override
    public boolean existsById(Long id) {
        return productJpaRepository.existsByIdAndDeletedAtIsNull(id);
    }

    @Override
    public Page<Product> findAllIncludingDeleted(Pageable pageable) {
        return productJpaRepository.findAllIncludingDeleted(pageable)
            .map(ProductEntity::toDomain);
    }

    @Override
    public Long increaseLikeCount(Long id) {
        productJpaRepository.increaseLikeCount(id);
        return productJpaRepository.findLikeCountById(id);
    }

    @Override
    public Long decreaseLikeCount(Long id) {
        productJpaRepository.decreaseLikeCount(id);
        return productJpaRepository.findLikeCountById(id);
    }
}
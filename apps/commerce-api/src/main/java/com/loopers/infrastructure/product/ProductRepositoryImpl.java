package com.loopers.infrastructure.product;

import com.loopers.domain.model.common.PageResult;
import com.loopers.domain.model.product.*;
import com.loopers.domain.repository.ProductRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class ProductRepositoryImpl implements ProductRepository {

    private final ProductJpaRepository productJpaRepository;

    public ProductRepositoryImpl(ProductJpaRepository productJpaRepository) {
        this.productJpaRepository = productJpaRepository;
    }

    @Override
    public Product save(Product product) {
        ProductJpaEntity entity = toEntity(product);
        ProductJpaEntity saved = productJpaRepository.save(entity);
        return toDomain(saved);
    }

    @Override
    public Optional<Product> findById(Long id) {
        return productJpaRepository.findById(id)
                .map(this::toDomain);
    }

    @Override
    public Optional<Product> findActiveById(Long id) {
        return findById(id).filter(p -> !p.isDeleted());
    }

    @Override
    public Optional<Product> findActiveByIdWithLock(Long id) {
        return productJpaRepository.findByIdForUpdate(id)
                .map(this::toDomain)
                .filter(p -> !p.isDeleted());
    }

    @Override
    public PageResult<Product> findAllActive(Long brandId, String sort, int page, int size) {
        Sort sorting = resolveSort(sort);
        PageRequest pageRequest = PageRequest.of(page, size, sorting);

        Page<ProductJpaEntity> jpaPage;
        if (brandId != null) {
            jpaPage = productJpaRepository.findAllByBrandIdAndDeletedAtIsNull(brandId, pageRequest);
        } else {
            jpaPage = productJpaRepository.findAllByDeletedAtIsNull(pageRequest);
        }

        List<Product> content = jpaPage.getContent().stream().map(this::toDomain).toList();
        return new PageResult<>(content, jpaPage.getNumber(), jpaPage.getSize(),
                jpaPage.getTotalElements(), jpaPage.getTotalPages());
    }

    @Override
    public List<Product> findAllByBrandId(Long brandId) {
        return productJpaRepository.findAllByBrandIdAndDeletedAtIsNull(brandId).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public List<Product> findAllActiveByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return productJpaRepository.findAllByIdInAndDeletedAtIsNull(ids).stream()
                .map(this::toDomain)
                .toList();
    }

    private Sort resolveSort(String sort) {
        if (sort == null) {
            return Sort.by(Sort.Direction.DESC, "createdAt");
        }
        return switch (sort) {
            case "price_asc" -> Sort.by(Sort.Direction.ASC, "price");
            case "price_desc" -> Sort.by(Sort.Direction.DESC, "price");
            case "likes_desc" -> Sort.by(Sort.Direction.DESC, "likeCount");
            default -> Sort.by(Sort.Direction.DESC, "createdAt");
        };
    }

    @Override
    public void incrementLikeCount(Long productId) {
        productJpaRepository.incrementLikeCount(productId);
    }

    @Override
    public void decrementLikeCount(Long productId) {
        productJpaRepository.decrementLikeCount(productId);
    }

    private ProductJpaEntity toEntity(Product product) {
        return new ProductJpaEntity(
                product.getId(),
                product.getBrandId(),
                product.getName().getValue(),
                product.getPrice().getValue(),
                product.getSalePrice() != null ? product.getSalePrice().getValue() : null,
                product.getStock().getValue(),
                product.getLikeCount(),
                product.getDescription(),
                product.getCreatedAt(),
                product.getUpdatedAt(),
                product.getDeletedAt()
        );
    }

    private Product toDomain(ProductJpaEntity entity) {
        return Product.reconstitute(new ProductData(
                entity.getId(),
                entity.getBrandId(),
                ProductName.of(entity.getName()),
                Price.of(entity.getPrice()),
                entity.getSalePrice() != null ? Price.of(entity.getSalePrice()) : null,
                Stock.of(entity.getStockQuantity()),
                entity.getLikeCount(),
                entity.getDescription(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.getDeletedAt()
        ));
    }
}

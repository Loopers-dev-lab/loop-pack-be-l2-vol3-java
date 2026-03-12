package com.loopers.infrastructure.persistence.jpa.product;

import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.product.ProductSort;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * ProductRepository 구현체.
 * JPA를 사용하여 Product 도메인 객체를 영속화.
 * Domain ↔ JPA Entity 변환은 ProductMapper를 통해 수행.
 */
@Repository
@RequiredArgsConstructor
public class ProductRepositoryImpl implements ProductRepository {

    private final ProductJpaRepository jpaRepository;

    @Override
    public Product save(Product product) {
        ProductJpaEntity entity;

        if (product.getId() == null) {
            // 신규 생성
            entity = ProductMapper.toJpaEntity(product);
        } else {
            // 기존 엔티티 업데이트
            entity = jpaRepository.findById(product.getId())
                .orElseGet(() -> ProductMapper.toJpaEntity(product));
            ProductMapper.updateJpaEntity(entity, product);
        }

        ProductJpaEntity saved = jpaRepository.save(entity);
        return ProductMapper.toDomain(saved);
    }

    @Override
    public Optional<Product> findById(Long id) {
        return jpaRepository.findById(id)
            .map(ProductMapper::toDomain);
    }

    @Override
    public Optional<Product> findByIdActive(Long id) {
        return jpaRepository.findByIdAndDeletedAtIsNull(id)
            .map(ProductMapper::toDomain);
    }

    @Override
    public Optional<Product> findByIdWithLock(Long id) {
        return jpaRepository.findByIdWithLock(id)
            .map(ProductMapper::toDomain);
    }

    @Override
    public List<Product> findAllActive(ProductSort sort, int offset, int limit) {
        Pageable pageable = createPageable(sort, offset, limit);
        return jpaRepository.findAllByDeletedAtIsNull(pageable).stream()
            .map(ProductMapper::toDomain)
            .toList();
    }

    @Override
    public List<Product> findAllByBrandIdActive(Long brandId, ProductSort sort, int offset, int limit) {
        Pageable pageable = createPageable(sort, offset, limit);
        return jpaRepository.findAllByBrandIdAndDeletedAtIsNull(brandId, pageable).stream()
            .map(ProductMapper::toDomain)
            .toList();
    }

    @Override
    public List<Product> findAllByBrandIdActive(Long brandId) {
        return jpaRepository.findAllByBrandIdAndDeletedAtIsNull(brandId).stream()
            .map(ProductMapper::toDomain)
            .toList();
    }

    @Override
    public List<Product> findAllByIds(List<Long> ids) {
        return jpaRepository.findAllByIdIn(ids).stream()
            .map(ProductMapper::toDomain)
            .toList();
    }

    @Override
    public long countActive() {
        return jpaRepository.countByDeletedAtIsNull();
    }

    @Override
    public long countByBrandIdActive(Long brandId) {
        return jpaRepository.countByBrandIdAndDeletedAtIsNull(brandId);
    }

    private Pageable createPageable(ProductSort sort, int offset, int limit) {
        int page = offset / limit;
        Sort jpaSort = switch (sort) {
            case LATEST -> Sort.by(Sort.Direction.DESC, "createdAt");
            case PRICE_ASC -> Sort.by(Sort.Direction.ASC, "price");
            case LIKES_DESC -> Sort.by(Sort.Direction.DESC, "createdAt"); // likes_desc는 Application에서 처리
        };
        return PageRequest.of(page, limit, jpaSort);
    }
}

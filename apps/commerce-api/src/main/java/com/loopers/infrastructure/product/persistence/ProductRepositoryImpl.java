package com.loopers.infrastructure.product.persistence;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Repository;

import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.product.ProductSortType;

import lombok.RequiredArgsConstructor;

/**
 * {@link ProductRepository}의 인프라스트럭처 구현체.
 *
 * <p>{@link ProductJpaRepository}에 위임하여 상품 영속성을 처리한다.</p>
 */
@Repository
@RequiredArgsConstructor
public class ProductRepositoryImpl implements ProductRepository {

    private final ProductJpaRepository productJpaRepository;

    @Override
    public Product save(Product product) {
        return productJpaRepository.save(product);
    }

    @Override
    public Optional<Product> findById(Long productId) {
        return productJpaRepository.findById(productId);
    }

    @Override
    public Optional<Product> findByIdAndDeletedAtIsNull(Long productId) {
        return productJpaRepository.findByIdAndDeletedAtIsNull(productId);
    }

    @Override
    public Optional<Product> findByIdAndDeletedAtIsNullForUpdate(Long productId) {
        return productJpaRepository.findByIdAndDeletedAtIsNullForUpdate(productId);
    }

    @Override
    public List<Product> findAllByIdInAndDeletedAtIsNull(List<Long> productIds) {
        return productJpaRepository.findAllByIdInAndDeletedAtIsNull(productIds);
    }

    @Override
    public Slice<Product> findAll(Long brandId, Pageable pageable) {
        if (brandId == null) {
            return productJpaRepository.findAllBy(pageable);
        }
        return productJpaRepository.findAllByBrandId(brandId, pageable);
    }

    @Override
    public Slice<Product> findAllActiveProducts(Long brandId, ProductSortType sortType, Pageable pageable) {
        Pageable sorted = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), sortType.getSort());
        if (brandId == null) {
            return productJpaRepository.findAllByDeletedAtIsNull(sorted);
        }
        return productJpaRepository.findAllByBrandIdAndDeletedAtIsNull(brandId, sorted);
    }

    @Override
    public List<Product> findAllByBrandIdAndDeletedAtIsNull(Long brandId) {
        return productJpaRepository.findAllByBrandIdAndDeletedAtIsNull(brandId);
    }

    @Override
    public void softDeleteAllByBrandId(Long brandId) {
        productJpaRepository.softDeleteAllByBrandId(brandId, ZonedDateTime.now());
    }

    @Override
    public boolean existsByIdAndDeletedAtIsNull(Long productId) {
        return productJpaRepository.existsByIdAndDeletedAtIsNull(productId);
    }
}

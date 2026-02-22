package com.loopers.infrastructure.product.persistence;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.product.ProductSortType;

import lombok.RequiredArgsConstructor;

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
    public List<Product> findAllByIdInAndDeletedAtIsNull(List<Long> productIds) {
        return productJpaRepository.findAllByIdInAndDeletedAtIsNull(productIds);
    }

    @Override
    public Slice<Product> findAllBy(Pageable pageable) {
        return productJpaRepository.findAllBy(pageable);
    }

    @Override
    public Slice<Product> findActiveProducts(ProductSortType sortType, Pageable pageable) {
        if (sortType == ProductSortType.LIKE_COUNT_DESC) {
            return productJpaRepository.findAllActiveOrderByLikeCountDesc(PageRequest.of(pageable.getPageNumber(), pageable.getPageSize()));
        }
        return productJpaRepository.findAllByDeletedAtIsNull(PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), toSort(sortType)));
    }

    @Override
    public Slice<Product> findActiveProductsByBrandId(Long brandId, ProductSortType sortType, Pageable pageable) {
        if (sortType == ProductSortType.LIKE_COUNT_DESC) {
            return productJpaRepository.findAllActiveByBrandIdOrderByLikeCountDesc(
                    brandId,
                    PageRequest.of(pageable.getPageNumber(), pageable.getPageSize())
            );
        }
        return productJpaRepository.findAllByBrandIdAndDeletedAtIsNull(
                brandId,
                PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), toSort(sortType))
        );
    }

    @Override
    public Slice<Product> findAllByBrandId(Long brandId, Pageable pageable) {
        return productJpaRepository.findAllByBrandId(brandId, pageable);
    }

    @Override
    public List<Product> findAllByBrandIdAndDeletedAtIsNull(Long brandId) {
        return productJpaRepository.findAllByBrandIdAndDeletedAtIsNull(brandId);
    }

    @Override
    public boolean existsByIdAndDeletedAtIsNull(Long productId) {
        return productJpaRepository.existsByIdAndDeletedAtIsNull(productId);
    }

    @Override
    public void softDeleteAllByBrandId(Long brandId) {
        productJpaRepository.softDeleteAllByBrandId(brandId, ZonedDateTime.now());
    }

    private Sort toSort(ProductSortType sortType) {
        return switch (sortType) {
            case CREATED_AT_DESC -> Sort.by(Sort.Direction.DESC, "createdAt");
            case PRICE_ASC -> Sort.by(Sort.Direction.ASC, "price.amount");
            case LIKE_COUNT_DESC -> throw new IllegalStateException("LIKE_COUNT_DESC is handled separately");
        };
    }
}

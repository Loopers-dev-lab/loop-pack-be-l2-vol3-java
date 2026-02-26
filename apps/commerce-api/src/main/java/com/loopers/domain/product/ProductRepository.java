package com.loopers.domain.product;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;

public interface ProductRepository {

    Product save(Product product);

    Optional<Product> findById(Long productId);

    Optional<Product> findByIdAndDeletedAtIsNull(Long productId);

    Optional<Product> findByIdAndDeletedAtIsNullForUpdate(Long productId);

    List<Product> findAllByIdInAndDeletedAtIsNull(List<Long> productIds);

    Slice<Product> findAll(Long brandId, Pageable pageable);

    Slice<Product> findAllActiveProducts(Long brandId, ProductSortType sortType, Pageable pageable);

    List<Product> findAllByBrandIdAndDeletedAtIsNull(Long brandId);

    void softDeleteAllByBrandId(Long brandId);

    boolean existsByIdAndDeletedAtIsNull(Long productId);
}

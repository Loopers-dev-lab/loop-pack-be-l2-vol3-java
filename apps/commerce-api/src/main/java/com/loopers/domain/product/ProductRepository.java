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

    List<Product> findAllByIdInAndDeletedAtIsNullForUpdate(List<Long> productIds);

    Slice<Product> findAllBy(Pageable pageable);

    Slice<Product> findAllByDeletedAtIsNull(ProductSortType sortType, Pageable pageable);

    Slice<Product> findAllByBrandIdAndDeletedAtIsNull(Long brandId, ProductSortType sortType, Pageable pageable);

    Slice<Product> findAllByBrandId(Long brandId, Pageable pageable);

    List<Product> findAllByBrandIdAndDeletedAtIsNull(Long brandId);

    void softDeleteAllByBrandId(Long brandId);
}

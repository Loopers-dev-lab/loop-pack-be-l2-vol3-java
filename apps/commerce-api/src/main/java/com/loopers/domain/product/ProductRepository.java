package com.loopers.domain.product;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;

public interface ProductRepository {

    Product save(Product product);

    Optional<Product> findById(Long id);

    List<Product> findAllByIdInAndDeletedAtIsNull(List<Long> productIds);

    Slice<Product> findAllBy(Pageable pageable);

    Slice<Product> findAllByBrandId(Long brandId, Pageable pageable);

    List<Product> findAllByBrandIdAndDeletedAtIsNull(Long brandId);

    boolean existsByIdAndDeletedAtIsNull(Long productId);

    boolean existsByIdNotAndNameAndDeletedAtIsNull(Long productId, String name);

    void softDeleteAllByBrandId(Long brandId);
}

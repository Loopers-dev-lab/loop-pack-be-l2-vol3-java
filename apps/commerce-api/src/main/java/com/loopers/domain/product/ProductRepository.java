package com.loopers.domain.product;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

public interface ProductRepository {

    ProductModel save(ProductModel product);

    int incrementLikeCount(Long productId);

    int decrementLikeCount(Long productId);

    Optional<ProductModel> findById(Long id);
    List<ProductModel> findAllByBrandId(Long brandId);

    List<ProductModel> findAllByIdInAndDeletedAtIsNull(List<Long> ids);
}

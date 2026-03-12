package com.loopers.domain.product;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ProductRepository {
    ProductModel save(ProductModel productModel);

    Optional<ProductModel> findById(Long id);

    Page<ProductModel> findAll(Pageable pageable);

    Page<ProductModel> findAllByBrandId(Long brandId, Pageable pageable);

    List<ProductModel> findAllByBrandId(Long brandId);

    List<ProductModel> findAllByIdIn(List<Long> ids);

    List<ProductModel> findAll();
}

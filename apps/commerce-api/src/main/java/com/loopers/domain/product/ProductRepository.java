package com.loopers.domain.product;

import java.util.List;
import java.util.Optional;

public interface ProductRepository {
    Product save(Product product);
    Optional<Product> findById(Long id);
    List<Product> findAll();
    List<Product> findAllByBrandId(Long brandId);

    // 조회 전용 (Brand JOIN)
    List<ProductWithBrand> findAllWithBrand();
    List<ProductWithBrand> findAllWithBrand(String sort);
    List<ProductWithBrand> findAllByBrandIdWithBrand(Long brandId);
}

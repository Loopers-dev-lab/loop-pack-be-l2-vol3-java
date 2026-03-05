package com.loopers.domain.product;

import java.util.List;
import java.util.Optional;

public interface ProductRepository {
    Product save(Product product);
    Optional<Product> findById(Long id);
    List<Product> findAllByIds(List<Long> ids);
    List<Product> findAll();
    List<Product> findAllByBrandId(Long brandId);

    // 재고 원자적 변경 (조건부 UPDATE)
    int decreaseStock(Long id, int quantity);
    int increaseStock(Long id, int quantity);

    // 조회 전용 (Brand JOIN)
    List<ProductWithBrand> findAllWithBrand();
    List<ProductWithBrand> findAllWithBrand(String sort);
    List<ProductWithBrand> findAllByBrandIdWithBrand(Long brandId);
}

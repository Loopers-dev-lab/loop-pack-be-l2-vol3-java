package com.loopers.domain.product;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

public interface ProductRepository {
    Product save(Product product);
    Optional<Product> findById(Long id);
    Page<Product> findActiveProducts(Long brandId, ProductOrder order, Pageable pageable);
    Page<Product> findAllProducts(Long brandId, Pageable pageable);
    List<Product> findAllByBrandIdAndDeletedAtIsNull(Long brandId);
    List<Product> findAllByIdInAndDeletedAtIsNull(List<Long> ids);
    boolean decreaseStockIfEnough(Long productId, Integer quantity);
    int increaseStock(Long productId, Integer quantity);
}

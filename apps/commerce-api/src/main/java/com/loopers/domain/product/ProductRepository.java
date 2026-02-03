package com.loopers.domain.product;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ProductRepository {
    Optional<ProductModel> find(Long id);
    List<ProductModel> findAll();
    Page<ProductModel> findAll(Pageable pageable);
    ProductModel save(ProductModel product);
    void delete(ProductModel product);
}

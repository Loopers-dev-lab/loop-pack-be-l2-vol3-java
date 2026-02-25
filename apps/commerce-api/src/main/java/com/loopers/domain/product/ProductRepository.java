package com.loopers.domain.product;

import java.util.Optional;

public interface ProductRepository {

    // Command
    Product save(Product product);

    // Query
    Optional<Product> findById(Long id);
}

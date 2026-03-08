package com.loopers.domain.product;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ProductRepository {

    // Command
    Product save(Product product);
    int decreaseStockIfEnough(Long productId, int quantity);
    int incrementLikeCount(Long productId);
    int decrementLikeCountIfPositive(Long productId);
    int softDeleteByBrandIdInBatch(Long brandId, int batchSize);

    // Query
    Optional<Product> findById(Long id);
    Optional<Product> findActiveById(Long id);
    Optional<Product> findActiveWithActiveBrand(Long id);
    boolean existsActiveById(Long id);

    List<Product> findAllByIdIn(Collection<Long> ids);
    List<Product> findAllActiveByIdIn(Collection<Long> ids);

    Page<Product> findAll(String name, Long brandId, Boolean deleted, Pageable pageable);
    Page<Product> findAllActive(Long brandId, Pageable pageable);
    Page<Product> findAllActiveWithActiveBrand(Long brandId, Pageable pageable);

    List<Long> findBrandIdsWithUncleanedProducts();
}

package com.loopers.domain.product;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductRepository {
    Product save(Product product);

    Optional<Product> findById(UUID id);

    List<Product> findAllByIdIn(List<UUID> ids);

    int decreaseStockAtomically(UUID productId, int quantity);

    Optional<Product> findByIdIncludingDeleted(UUID id);

    Page<Product> findAll(UUID brandId, Pageable pageable);

    Page<Product> findAllIncludingDeleted(UUID brandId, Pageable pageable);

    List<UUID> findIdsByBrandId(UUID brandId);

    int updateLikeCount(UUID productId, long delta);

    void softDeleteByBrandId(UUID brandId);

    void delete(Product product);

}

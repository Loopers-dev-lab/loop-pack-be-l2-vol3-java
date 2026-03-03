package com.loopers.domain.product;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductRepository {
    Product save(Product product);

    Optional<Product> findById(UUID id);

    List<Product> findAllByIdInWithLock(List<UUID> ids);

    Optional<Product> findByIdIncludingDeleted(UUID id);

    Page<Product> findAll(UUID brandId, Pageable pageable);

    Page<Product> findAllIncludingDeleted(UUID brandId, Pageable pageable);

    List<UUID> findIdsByBrandId(UUID brandId);

    void softDeleteByBrandId(UUID brandId);

    void delete(Product product);

}

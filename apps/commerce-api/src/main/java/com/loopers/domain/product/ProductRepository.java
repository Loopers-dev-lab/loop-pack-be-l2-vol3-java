package com.loopers.domain.product;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ProductRepository {

    Optional<Product> findById(Long productId);

    Optional<Product> findByIdIncludingDeleted(Long productId);

    List<Product> findAllByBrandId(Long brandId);

    List<Product> findAllByBrandIdIncludingDeleted(Long brandId);

    List<Product> findAllByIdsIncludingDeleted(Collection<Long> productIds);

    Page<Product> findByBrandId(Long brandId, Pageable pageable);

    Page<Product> findAll(Pageable pageable);

    Product save(Product product);

    boolean existsByName(String name);
}

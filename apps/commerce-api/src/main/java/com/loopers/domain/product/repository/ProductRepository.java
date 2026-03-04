package com.loopers.domain.product.repository;

import com.loopers.domain.product.model.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

public interface ProductRepository {

    Product save(Product product);

    void update(Product product);

    Optional<Product> findById(Long id);

    Page<Product> findAll(Pageable pageable, Long brandId);

    void deleteById(Long id);

    void deleteByBrandId(Long brandId);

    List<Product> findByIds(List<Long> ids);

    Optional<Product> findByIdWithLock(Long id);
}

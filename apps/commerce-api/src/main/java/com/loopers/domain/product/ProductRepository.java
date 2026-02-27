package com.loopers.domain.product;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

public interface ProductRepository {
    Product save(Product product);
    Optional<Product> findById(Long id);
    Page<Product> findAll(Pageable pageable);
    Page<Product> findAllByBrandId(Long brandId, Pageable pageable);
    List<Product> findAllByIds(List<Long> ids);
    List<Product> findAllByIdsForUpdate(List<Long> ids);
    List<Long> findIdsByBrandId(Long brandId);
    boolean existsByBrandIdAndName(Long brandId, String name);
    boolean existsByBrandIdAndNameAndIdNot(Long brandId, String name, Long id);
    void deleteAllByBrandId(Long brandId);
    void incrementLikeCount(Long productId);
    void decrementLikeCount(Long productId);
}

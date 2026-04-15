package com.loopers.domain.product;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

public interface ProductRepository {

    Optional<Product> findById(Long id);

    Optional<Product> findByIdForUpdate(Long id);

    List<Product> findAllActive();

    List<Product> findAllActiveByCategoryId(Long categoryId);

    List<Product> findAllActiveByBrandId(Long brandId);

    List<Product> findAllActiveByCategoryIds(List<Long> categoryIds);

    Page<Product> findProducts(Long categoryId, Long brandId, String keyword, ProductSortType sort, Pageable pageable);

    List<Product> findByIds(List<Long> ids);

    Product save(Product product);

    void delete(Long id);

    void softDeleteAllByIds(List<Long> ids);

    boolean existsById(Long id);

    Page<Product> findAllIncludingDeleted(Pageable pageable);

    Long increaseLikeCount(Long id);

    Long decreaseLikeCount(Long id);
}
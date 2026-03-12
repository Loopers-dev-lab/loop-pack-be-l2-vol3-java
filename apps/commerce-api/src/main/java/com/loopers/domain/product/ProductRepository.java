package com.loopers.domain.product;

import com.loopers.domain.product.query.ProductCursorPage;
import com.loopers.domain.product.query.ProductListCriteria;
import org.springframework.data.domain.Page;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductRepository {
    Product save(Product product);

    Optional<Product> findById(UUID id);

    List<Product> findAllByIdIn(List<UUID> ids);

    int decreaseStockAtomically(UUID productId, int quantity);

    Optional<Product> findByIdIncludingDeleted(UUID id);

    ProductCursorPage searchByCursor(ProductListCriteria criteria);

    Page<Product> findAllIncludingDeleted(ProductListCriteria criteria);

    List<UUID> findIdsByBrandId(UUID brandId);

    int updateLikeCount(UUID productId, long delta);

    void softDeleteByBrandId(UUID brandId);

    void delete(Product product);

}

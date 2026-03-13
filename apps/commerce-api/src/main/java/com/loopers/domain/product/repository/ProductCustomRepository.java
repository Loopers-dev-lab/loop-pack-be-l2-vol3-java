package com.loopers.domain.product.repository;

import com.loopers.domain.product.model.ProductItem;
import com.loopers.support.enums.SortFilter;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;

public interface ProductCustomRepository {

    Page<ProductItem> findProductList(Long brandId, SortFilter sortFilter, Pageable pageable);
    Optional<ProductItem> findProduct(Long productId);
}

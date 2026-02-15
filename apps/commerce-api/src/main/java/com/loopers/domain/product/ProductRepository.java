package com.loopers.domain.product;

public interface ProductRepository {

    void softDeleteAllByBrandId(Long brandId);
}

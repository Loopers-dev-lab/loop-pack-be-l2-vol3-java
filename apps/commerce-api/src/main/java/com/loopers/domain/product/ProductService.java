package com.loopers.domain.product;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;

    public void deleteAllByBrandId(Long brandId) {
        productRepository.softDeleteAllByBrandId(brandId);
    }
}

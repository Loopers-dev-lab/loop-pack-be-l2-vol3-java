package com.loopers.domain.product.model;

public record ProductItem(
        Long id,
        String name,
        Long brandId,
        String brandName,
        int price,
        int stock,
        String displayStatus,
        long favoriteCnt,
        boolean isFavorite
) {}

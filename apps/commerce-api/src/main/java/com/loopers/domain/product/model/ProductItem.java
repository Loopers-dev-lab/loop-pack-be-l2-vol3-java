package com.loopers.domain.product.model;

import com.loopers.domain.product.vo.DisplayStatus;

public record ProductItem(
        Long id,
        String name,
        Long brandId,
        String brandName,
        int price,
        int stock,
        DisplayStatus displayStatus,
        long favoriteCnt,
        boolean isFavorite,
        Long ranking
) {
    public ProductItem withIsFavorite(boolean isFavorite) {
        return new ProductItem(id, name, brandId, brandName, price, stock, displayStatus, favoriteCnt, isFavorite, ranking);
    }

    public ProductItem withFavoriteCnt(long favoriteCnt) {
        return new ProductItem(id, name, brandId, brandName, price, stock, displayStatus, favoriteCnt, isFavorite, ranking);
    }

    public ProductItem withRanking(Long ranking) {
        return new ProductItem(id, name, brandId, brandName, price, stock, displayStatus, favoriteCnt, isFavorite, ranking);
    }
}

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
) {
    public ProductItem withIsFavorite(boolean isFavorite) {
        return new ProductItem(id, name, brandId, brandName, price, stock, displayStatus, favoriteCnt, isFavorite);
    }

    public ProductItem withFavoriteCnt(long favoriteCnt) {
        return new ProductItem(id, name, brandId, brandName, price, stock, displayStatus, favoriteCnt, isFavorite);
    }
}

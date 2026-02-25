package com.loopers.application.like;

import com.loopers.domain.brand.Brand;
import com.loopers.domain.like.Like;
import com.loopers.domain.product.Product;

public record LikedProductDetail(Like like, Product product, Brand brand) {
}

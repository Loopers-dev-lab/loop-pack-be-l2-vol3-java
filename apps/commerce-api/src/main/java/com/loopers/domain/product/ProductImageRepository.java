package com.loopers.domain.product;

import java.util.List;

public interface ProductImageRepository {

    ProductImageModel save(ProductImageModel image);

    List<ProductImageModel> findAllByProductId(Long productId);

    List<ProductImageModel> findAllByProductIdAndImageType(Long productId, ImageType imageType);

    void deleteAllByProductId(Long productId);
}

package com.loopers.infrastructure.product;

import com.loopers.domain.product.ImageType;
import com.loopers.domain.product.ProductImageModel;
import com.loopers.domain.product.ProductImageRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class ProductImageRepositoryImpl implements ProductImageRepository {

    private final ProductImageJpaRepository productImageJpaRepository;

    @Override
    public ProductImageModel save(ProductImageModel image) {
        return productImageJpaRepository.save(image);
    }

    @Override
    public List<ProductImageModel> findAllByProductId(Long productId) {
        return productImageJpaRepository.findAllByProductIdAndDeletedAtIsNullOrderBySortOrder(productId);
    }

    @Override
    public List<ProductImageModel> findAllByProductIdAndImageType(Long productId, ImageType imageType) {
        return productImageJpaRepository
                .findAllByProductIdAndImageTypeAndDeletedAtIsNullOrderBySortOrder(productId, imageType);
    }

    @Override
    public void deleteAllByProductId(Long productId) {
        productImageJpaRepository.findAllByProductIdAndDeletedAtIsNull(productId)
                .forEach(ProductImageModel::delete);
    }
}

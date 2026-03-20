package com.loopers.domain.product;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProductImageService {

    private final ProductImageRepository productImageRepository;

    @Transactional
    public ProductImageModel addImage(Long productId, String imageUrl, ImageType imageType, int sortOrder) {
        return productImageRepository.save(
                ProductImageModel.create(productId, imageUrl, imageType, sortOrder));
    }

    @Transactional(readOnly = true)
    public List<ProductImageModel> getImagesByProductId(Long productId) {
        return productImageRepository.findAllByProductId(productId);
    }

    @Transactional(readOnly = true)
    public List<ProductImageModel> getImagesByProductIdAndType(Long productId, ImageType imageType) {
        return productImageRepository.findAllByProductIdAndImageType(productId, imageType);
    }

    @Transactional
    public void deleteAllByProductId(Long productId) {
        productImageRepository.deleteAllByProductId(productId);
    }
}

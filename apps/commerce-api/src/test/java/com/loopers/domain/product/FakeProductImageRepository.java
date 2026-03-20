package com.loopers.domain.product;

import java.lang.reflect.Field;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

public class FakeProductImageRepository implements ProductImageRepository {

    private final Map<Long, ProductImageModel> store = new HashMap<>();
    private final AtomicLong idSequence = new AtomicLong(1);

    @Override
    public ProductImageModel save(ProductImageModel image) {
        if (image.getId() == null || image.getId() == 0L) {
            setId(image, idSequence.getAndIncrement());
        }
        store.put(image.getId(), image);
        return image;
    }

    @Override
    public List<ProductImageModel> findAllByProductId(Long productId) {
        return store.values().stream()
                .filter(img -> img.getDeletedAt() == null)
                .filter(img -> img.getProductId().equals(productId))
                .sorted(Comparator.comparingInt(ProductImageModel::getSortOrder))
                .toList();
    }

    @Override
    public List<ProductImageModel> findAllByProductIdAndImageType(Long productId, ImageType imageType) {
        return store.values().stream()
                .filter(img -> img.getDeletedAt() == null)
                .filter(img -> img.getProductId().equals(productId))
                .filter(img -> img.getImageType() == imageType)
                .sorted(Comparator.comparingInt(ProductImageModel::getSortOrder))
                .toList();
    }

    @Override
    public void deleteAllByProductId(Long productId) {
        store.values().stream()
                .filter(img -> img.getProductId().equals(productId))
                .filter(img -> img.getDeletedAt() == null)
                .forEach(ProductImageModel::delete);
    }

    private void setId(ProductImageModel image, long id) {
        try {
            Field idField = image.getClass().getSuperclass().getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(image, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}

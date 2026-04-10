package com.loopers.domain.product;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

public class FakeProductLikeRepository implements ProductLikeRepository {

    private final Map<Long, ProductLikeModel> store = new HashMap<>();
    private final AtomicLong idGenerator = new AtomicLong(1);

    @Override
    public ProductLikeModel save(ProductLikeModel productLike) {
        if (productLike.getId() == null) {
            try {
                var idField = productLike.getClass().getDeclaredField("id");
                idField.setAccessible(true);
                idField.set(productLike, idGenerator.getAndIncrement());
            } catch (NoSuchFieldException | IllegalAccessException e) {
                throw new RuntimeException(e);
            }
        }
        store.put(productLike.getId(), productLike);
        return productLike;
    }

    @Override
    public Optional<ProductLikeModel> findByUserIdAndProductId(Long userId, Long productId) {
        return store.values().stream()
            .filter(like -> like.getUserId().equals(userId) && like.getProductId().equals(productId))
            .findFirst();
    }

    @Override
    public void delete(ProductLikeModel productLike) {
        store.remove(productLike.getId());
    }

    @Override
    public List<ProductLikeModel> findAllByUserId(Long userId) {
        return store.values().stream()
            .filter(like -> like.getUserId().equals(userId))
            .toList();
    }

    @Override
    public long countByProductId(Long productId) {
        return store.values().stream()
            .filter(like -> like.getProductId().equals(productId))
            .count();
    }

    @Override
    public Map<Long, Long> countByProductIds(List<Long> productIds) {
        Map<Long, Long> countMap = store.values().stream()
                .filter(like -> productIds.contains(like.getProductId()))
                .collect(Collectors.groupingBy(
                        ProductLikeModel::getProductId,
                        Collectors.counting()));
        productIds.forEach(id -> countMap.putIfAbsent(id, 0L));
        return countMap;
    }
}

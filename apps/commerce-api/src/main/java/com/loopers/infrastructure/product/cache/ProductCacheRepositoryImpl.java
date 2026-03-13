package com.loopers.infrastructure.product.cache;

import com.loopers.domain.product.model.ProductItem;
import com.loopers.domain.product.repository.ProductCacheRepository;
import com.loopers.support.cache.RedisCacheManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
@Component
public class ProductCacheRepositoryImpl implements ProductCacheRepository {

    private static final String KEY_PREFIX = "product:";
    private static final String LIKES_SUFFIX = ":likes";
    private static final String FIRST_PAGE_KEY = "product-list:first-page";
    private static final long TTL_SECONDS = 3600;
    private static final long FIRST_PAGE_TTL_SECONDS = 300;

    private final RedisCacheManager redisCacheManager;

    @Override
    public Optional<ProductItem> get(Long productId) {
        return redisCacheManager.get(KEY_PREFIX + productId, ProductCacheDto.class)
                .map(this::toItem);
    }

    @Override
    public void put(Long productId, ProductItem item) {
        redisCacheManager.put(KEY_PREFIX + productId, toDto(item), TTL_SECONDS);
    }

    @Override
    public void evict(Long productId) {
        redisCacheManager.evict(KEY_PREFIX + productId);
    }

    @Override
    public Optional<CachedPage> getFirstPage() {
        return redisCacheManager.get(FIRST_PAGE_KEY, ProductListCacheDto.class)
                .map(dto -> new CachedPage(
                        dto.items().stream().map(this::toItem).toList(),
                        dto.totalElements()
                ));
    }

    @Override
    public void putFirstPage(List<ProductItem> items, long totalElements) {
        List<ProductCacheDto> dtos = items.stream().map(this::toDto).toList();
        redisCacheManager.put(FIRST_PAGE_KEY, new ProductListCacheDto(dtos, totalElements), FIRST_PAGE_TTL_SECONDS);
    }

    @Override
    public void evictFirstPage() {
        redisCacheManager.evict(FIRST_PAGE_KEY);
    }

    @Override
    public Optional<Long> getLikeCount(Long productId) {
        return redisCacheManager.getCount(KEY_PREFIX + productId + LIKES_SUFFIX);
    }

    @Override
    public void initLikeCount(Long productId, long count) {
        redisCacheManager.setCount(KEY_PREFIX + productId + LIKES_SUFFIX, count, TTL_SECONDS);
    }

    @Override
    public void incrementLikeCount(Long productId) {
        redisCacheManager.increment(KEY_PREFIX + productId + LIKES_SUFFIX);
    }

    @Override
    public void decrementLikeCount(Long productId) {
        redisCacheManager.decrement(KEY_PREFIX + productId + LIKES_SUFFIX);
    }

    private ProductCacheDto toDto(ProductItem item) {
        return new ProductCacheDto(
                item.id(), item.name(), item.brandId(), item.brandName(),
                item.price(), item.stock(), item.displayStatus()
        );
    }

    private ProductItem toItem(ProductCacheDto dto) {
        return new ProductItem(
                dto.id(), dto.name(), dto.brandId(), dto.brandName(),
                dto.price(), dto.stock(), dto.displayStatus(), 0L, false
        );
    }
}

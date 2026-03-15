package com.loopers.application.product.cache;

import static com.loopers.application.product.cache.ProductCacheConstants.*;

import org.springframework.stereotype.Component;

import com.loopers.domain.product.ModifyProduct;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductService;
import com.loopers.support.cache.CacheRepository;

import lombok.RequiredArgsConstructor;

/**
 * 상품 쓰기를 담당하는 캐시 오케스트레이터.
 *
 * <p>DB 변경과 캐시 무효화/Write-Through를 함께 처리한다.
 * UseCase는 이 서비스만 호출하면 되며, 캐시의 존재를 알 필요가 없다.</p>
 *
 * @see ProductCacheReader
 */
@Component
@RequiredArgsConstructor
public class ProductCacheWriter {

    private final ProductService productService;
    private final CacheRepository cacheRepository;

    /**
     * 상품을 소프트 삭제하고, 삭제에 성공하면 목록 및 상세 캐시를 무효화한다.
     *
     * @param productId 상품 ID
     * @return 실제로 삭제가 수행되었으면 true, 이미 삭제된 상태면 false
     */
    public boolean delete(Long productId) {
        boolean deleted = productService.delete(productId);
        if (deleted) {
            cacheRepository.evict(LIST_KEY.pattern());
            cacheRepository.evict(DETAIL_KEY.of(productId));
        }
        return deleted;
    }

    /**
     * 상품 정보를 수정하고, 상세 캐시를 overwrite한다.
     *
     * <p>목록 캐시(ID 리스트)는 evict하지 않는다. 목록 캐시에는 ID만 저장되어 있으므로
     * 상품 데이터 변경이 ID 리스트에 영향을 주지 않는다.</p>
     *
     * @param product 상품 수정 정보
     */
    public void update(ModifyProduct product) {
        Product updated = productService.update(product);
        cacheRepository.put(DETAIL_KEY.of(product.productId()), updated, detailTtl());
    }

    /**
     * 상품의 좋아요 수를 1 증가시키고, 상세 캐시에 Write-Through한다.
     *
     * @param productId 상품 ID
     */
    public void increaseLikeCount(Long productId) {
        productService.increaseLikeCount(productId);
        refreshCache(productId);
    }

    /**
     * 상품의 좋아요 수를 1 감소시키고, 상세 캐시에 Write-Through한다.
     *
     * @param productId 상품 ID
     */
    public void decreaseLikeCount(Long productId) {
        productService.decreaseLikeCount(productId);
        refreshCache(productId);
    }

    /**
     * DB에서 최신 상품을 조회하여 상세 캐시를 갱신한다.
     */
    private void refreshCache(Long productId) {
        Product product = productService.getActiveProduct(productId);
        cacheRepository.put(DETAIL_KEY.of(productId), product, detailTtl());
    }
}

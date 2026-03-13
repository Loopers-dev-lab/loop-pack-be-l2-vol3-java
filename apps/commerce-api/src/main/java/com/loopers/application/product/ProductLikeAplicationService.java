package com.loopers.application.product;

import com.loopers.application.product.cache.EvictPublicProductDetailCache;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProductLikeAplicationService {

    private final ProductRepository productRepository;

    @Transactional(readOnly = true)
    public void validateLikeable(UUID productId) {
        if (productRepository.findById(productId).isPresent()) {
            return;
        }
        if (productRepository.findByIdIncludingDeleted(productId).isPresent()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "삭제된 상품은 좋아요할 수 없습니다.");
        }
        throw new CoreException(ErrorType.NOT_FOUND, "상품을 찾을 수 없습니다.");
    }

    @Transactional(readOnly = true)
    public void validateCancelable(UUID productId) {
        productRepository.findById(productId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "상품을 찾을 수 없습니다."));
    }

    @Transactional
    @EvictPublicProductDetailCache
    public void increaseLikeCount(UUID productId) {
        int updatedCount = productRepository.updateLikeCount(productId, 1);
        if (updatedCount == 0) {
            throw new CoreException(ErrorType.NOT_FOUND, "상품을 찾을 수 없습니다.");
        }
    }

    @Transactional
    @EvictPublicProductDetailCache
    public void decreaseLikeCount(UUID productId) {
        int updatedCount = productRepository.updateLikeCount(productId, -1);
        if (updatedCount == 0) {
            throw new CoreException(ErrorType.NOT_FOUND, "상품을 찾을 수 없습니다.");
        }
    }

    @Transactional
    @EvictPublicProductDetailCache
    public void decreaseLikeCountIfPresent(UUID productId) {
        productRepository.updateLikeCount(productId, -1);
    }

    @Transactional(readOnly = true)
    public Page<Product> getMyLikedProducts(Page<UUID> likedProductIds, Pageable pageable) {
        List<UUID> ids = likedProductIds.getContent();
        if (ids.isEmpty()) {
            return new PageImpl<>(List.of(), pageable, 0);
        }

        Map<UUID, Product> productsById = productRepository.findAllByIdIn(ids).stream()
                .collect(LinkedHashMap::new, (map, product) -> map.put(product.id(), product), LinkedHashMap::putAll);

        List<Product> products = ids.stream()
                .map(productsById::get)
                .filter(java.util.Objects::nonNull)
                .toList();
        return new PageImpl<>(products, pageable, products.size());
    }
}

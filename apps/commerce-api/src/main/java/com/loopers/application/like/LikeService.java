package com.loopers.application.like;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.loopers.domain.like.Like;
import com.loopers.domain.like.LikeRepository;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.support.page.Page;
import com.loopers.support.page.PageSize;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class LikeService {

    private final LikeRepository likeRepository;
    private final ProductRepository productRepository;

    @Transactional
    public void likeProduct(Long userId, Long productId) {
        Product product = productRepository.findByIdAndDeletedAtIsNullForUpdate(productId)
                .orElseThrow(() -> new CoreException(ErrorType.PRODUCT_NOT_FOUND));
        if (likeRepository.existsByUserIdAndProductId(userId, productId)) {
            return;
        }
        Like like = Like.create(userId, productId);
        likeRepository.save(like);
        product.increaseLikeCount();
    }

    @Transactional
    public void unlikeProduct(Long userId, Long productId) {
        Product product = productRepository.findByIdAndDeletedAtIsNullForUpdate(productId)
                .orElseThrow(() -> new CoreException(ErrorType.PRODUCT_NOT_FOUND));
        likeRepository.findByUserIdAndProductId(userId, productId)
                .ifPresent(like -> {
                    likeRepository.delete(like);
                    product.decreaseLikeCount();
                });
    }

    @Transactional(readOnly = true)
    public Page<LikedProductResult> getLikedProducts(Long userId, PageSize pageSize) {
        Slice<Like> likes = likeRepository.findAllByUserId(
                userId,
                pageSize.toPageable(Sort.by(Sort.Direction.DESC, "likedAt"))
        );

        if (!likes.hasContent()) {
            return new Page<>(Collections.emptyList(), false);
        }

        List<Long> productIds = likes.stream()
                .map(Like::getProductId)
                .toList();
        Map<Long, Product> products = productRepository.findAllByIdInAndDeletedAtIsNull(productIds).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));
        List<LikedProductResult> results = productIds.stream()
                .filter(products::containsKey)
                .map(productId -> LikedProductResult.from(products.get(productId)))
                .toList();
        return new Page<>(results, likes.hasNext());
    }
}

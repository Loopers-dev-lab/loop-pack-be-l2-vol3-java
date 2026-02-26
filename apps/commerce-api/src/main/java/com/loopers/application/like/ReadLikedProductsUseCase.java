package com.loopers.application.like;

import java.util.List;
import java.util.Map;

import org.springframework.transaction.annotation.Transactional;

import com.loopers.domain.product.ProductService;
import com.loopers.application.shared.annotation.UseCase;
import com.loopers.domain.like.Like;
import com.loopers.domain.like.LikeService;
import com.loopers.domain.product.Product;
import com.loopers.support.page.Page;
import com.loopers.support.page.PageSize;

import lombok.RequiredArgsConstructor;

/**
 * 사용자가 좋아요한 상품 목록을 조회합니다.
 *
 * <p>좋아요 목록을 최신순으로 조회한 뒤, 해당 상품 정보를 매핑하여 반환합니다.</p>
 */
@UseCase
@RequiredArgsConstructor
public class ReadLikedProductsUseCase {

    private final LikeService likeService;
    private final ProductService productService;

    /**
     * @param userId 사용자 ID
     * @param pageSize 페이지 크기
     * @return 좋아요한 상품 목록 페이지 (최신순)
     */
    @Transactional(readOnly = true)
    public Page<LikedProductResult> execute(Long userId, PageSize pageSize) {
        Page<Like> likes = likeService.getLikes(userId, pageSize);
        List<Long> likedProductIds = likes.content()
                .stream()
                .map(Like::getProductId)
                .toList();
        Map<Long, Product> likedProducts = productService.getProductsByIds(likedProductIds);
        return new Page<>(
                likedProductIds.stream()
                        .filter(likedProducts::containsKey)
                        .map(productId -> LikedProductResult.from(likedProducts.get(productId)))
                        .toList(),
                likes.hasNext()
        );
    }
}

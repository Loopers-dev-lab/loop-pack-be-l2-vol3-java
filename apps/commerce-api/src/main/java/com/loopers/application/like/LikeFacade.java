package com.loopers.application.like;

import com.loopers.application.product.ProductLikeAplicationService;
import com.loopers.domain.member.Member;
import com.loopers.domain.product.Product;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LikeFacade {

    private final LikeApplicationService likeApplicationService;
    private final ProductLikeAplicationService productLikeAplicationService;

    public void register(Long productId, Member member) {
        String memberId = member.id().value();
        productLikeAplicationService.validateLikeable(productId);
        likeApplicationService.register(memberId, productId);
        productLikeAplicationService.increaseLikeCount(productId);
    }

    public void cancel(Long productId, Member member) {
        String memberId = member.id().value();
        likeApplicationService.assertLiked(memberId, productId);
        productLikeAplicationService.validateCancelable(productId);
        likeApplicationService.cancel(memberId, productId);
        productLikeAplicationService.decreaseLikeCount(productId);
    }

    public Page<Product> getMyLikes(String memberId, Pageable pageable) {
        Page<Long> likedProductIds = likeApplicationService.getMyLikeProductIds(memberId, pageable);
        return productLikeAplicationService.getMyLikedProducts(likedProductIds, pageable);
    }
}

package com.loopers.application.like;

import com.loopers.application.product.ProductLikeAplicationService;
import com.loopers.domain.member.Member;
import com.loopers.domain.product.Product;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class LikeFacade {

    private final LikeApplicationService likeApplicationService;
    private final ProductLikeAplicationService productLikeAplicationService;

    public void register(UUID productId, Member member) {
        String memberId = member.id().value();
        productLikeAplicationService.validateLikeable(productId);
        likeApplicationService.register(memberId, productId);
    }

    public void cancel(UUID productId, Member member) {
        String memberId = member.id().value();
        likeApplicationService.assertLiked(memberId, productId);
        productLikeAplicationService.validateCancelable(productId);
        likeApplicationService.cancel(memberId, productId);
    }

    public Page<Product> getMyLikes(String memberId, Pageable pageable) {
        Page<UUID> likedProductIds = likeApplicationService.getMyLikeProductIds(memberId, pageable);
        return productLikeAplicationService.getMyLikedProducts(likedProductIds, pageable);
    }
}

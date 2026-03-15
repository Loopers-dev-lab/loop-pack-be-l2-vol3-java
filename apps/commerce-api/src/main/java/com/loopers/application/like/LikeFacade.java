package com.loopers.application.like;

import com.loopers.domain.like.ProductLikeService;
import com.loopers.domain.member.MemberModel;
import com.loopers.domain.member.MemberService;
import com.loopers.domain.product.ProductService;
import com.loopers.infrastructure.product.ProductCacheService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Component
public class LikeFacade {

    private final ProductLikeService productLikeService;
    private final MemberService memberService;
    private final ProductService productService;
    private final ProductCacheService productCacheService;

    @Transactional
    public void addLike(String loginId, String password, Long productId) {
        MemberModel member = memberService.getMyInfo(loginId, password);
        productService.getById(productId);
        productLikeService.addLike(member.getId(), productId);
        productService.increaseLikeCount(productId);

        // 캐시 무효화
        productCacheService.evictProductDetail(productId);
        productCacheService.evictProductList();
    }

    @Transactional
    public void removeLike(String loginId, String password, Long productId) {
        MemberModel member = memberService.getMyInfo(loginId, password);
        productService.getById(productId);
        productLikeService.removeLike(member.getId(), productId);
        productService.decreaseLikeCount(productId);

        // 캐시 무효화
        productCacheService.evictProductDetail(productId);
        productCacheService.evictProductList();
    }

    @Transactional(readOnly = true)
    public long countByProductId(Long productId) {
        return productLikeService.countByProductId(productId);
    }
}

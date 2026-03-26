package com.loopers.application.like;

import com.loopers.domain.like.ProductLikeService;
import com.loopers.domain.like.event.ProductLikedEvent;
import com.loopers.domain.like.event.ProductUnlikedEvent;
import com.loopers.domain.member.MemberModel;
import com.loopers.domain.member.MemberService;
import com.loopers.domain.product.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Component
public class LikeFacade {

    private final ProductLikeService productLikeService;
    private final MemberService memberService;
    private final ProductService productService;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public void addLike(String loginId, String password, Long productId) {
        MemberModel member = memberService.getMyInfo(loginId, password);
        productService.getById(productId);
        productLikeService.addLike(member.getId(), productId);

        // 집계 + 캐시는 이벤트 핸들러에서 비동기 처리
        eventPublisher.publishEvent(ProductLikedEvent.from(member.getId(), productId));
    }

    @Transactional
    public void removeLike(String loginId, String password, Long productId) {
        MemberModel member = memberService.getMyInfo(loginId, password);
        productService.getById(productId);
        productLikeService.removeLike(member.getId(), productId);

        eventPublisher.publishEvent(ProductUnlikedEvent.from(member.getId(), productId));
    }

    @Transactional(readOnly = true)
    public long countByProductId(Long productId) {
        return productLikeService.countByProductId(productId);
    }
}

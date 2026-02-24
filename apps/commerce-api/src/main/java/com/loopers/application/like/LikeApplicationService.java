package com.loopers.application.like;

import com.loopers.domain.like.Like;
import com.loopers.domain.like.LikeDomainService;
import com.loopers.domain.product.ProductDomainService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@RequiredArgsConstructor
@Component
public class LikeApplicationService {

    private final LikeDomainService likeService;
    private final ProductDomainService productService;

    /**
     * 단일 트랜잭션에서 Like aggregate와 Product aggregate를 함께 수정한다.
     * "하나의 트랜잭션 = 하나의 Aggregate" 원칙의 의도적 예외:
     * likeCount는 비정규화 카운터이며, Like 엔티티가 source of truth이다.
     * 일관성과 단순성을 위해 동일 트랜잭션에서 원자적으로 처리한다.
     */
    @Transactional
    public void like(Long userId, Long productId) {
        likeService.like(userId, productId);
        productService.incrementLikeCount(productId);
    }

    /**
     * 단일 트랜잭션에서 Like aggregate와 Product aggregate를 함께 수정한다.
     * "하나의 트랜잭션 = 하나의 Aggregate" 원칙의 의도적 예외:
     * likeCount는 비정규화 카운터이며, Like 엔티티가 source of truth이다.
     * 일관성과 단순성을 위해 동일 트랜잭션에서 원자적으로 처리한다.
     */
    @Transactional
    public void unlike(Long userId, Long productId) {
        likeService.unlike(userId, productId);
        productService.decrementLikeCount(productId);
    }

    @Transactional(readOnly = true)
    public List<Like> getMyLikes(Long userId) {
        return likeService.getMyLikes(userId);
    }
}

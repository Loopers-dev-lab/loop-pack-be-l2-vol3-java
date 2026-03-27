package com.loopers.application.like;

import com.loopers.domain.like.Like;
import com.loopers.domain.like.LikedEvent;
import com.loopers.domain.like.LikeRepository;
import com.loopers.domain.product.ProductRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Component
public class LikeService {

    private final LikeRepository likeRepository;
    private final ProductRepository productRepository;
    private final ApplicationEventPublisher eventPublisher;

    // 이 메서드의 책임은 "좋아요 저장"까지다.
    // product.likesCount 업데이트는 LikedEventListener가 AFTER_COMMIT에서 처리한다.
    // 이렇게 분리하면 LikeService가 Product 도메인을 직접 조작하지 않아도 된다.
    @Transactional
    public void like(Long memberId, Long productId) {
        if (likeRepository.existsByMemberIdAndProductId(memberId, productId)) {
            return;
        }
        if (!productRepository.existsById(productId)) {
            throw new CoreException(ErrorType.NOT_FOUND, "[id = " + productId + "] 상품을 찾을 수 없습니다.");
        }
        likeRepository.save(new Like(memberId, productId));
        // 트랜잭션 커밋 후 LikedEventListener에서 처리됨
        eventPublisher.publishEvent(new LikedEvent(memberId, productId));
    }

    // TODO: UnlikedEvent 설계 후 동일한 패턴으로 이벤트 발행으로 교체 예정
    @Transactional
    public void unlike(Long memberId, Long productId) {
        if (!likeRepository.existsByMemberIdAndProductId(memberId, productId)) {
            return;
        }
        if (!productRepository.existsById(productId)) {
            throw new CoreException(ErrorType.NOT_FOUND, "[id = " + productId + "] 상품을 찾을 수 없습니다.");
        }
        likeRepository.deleteByMemberIdAndProductId(memberId, productId);
    }
}

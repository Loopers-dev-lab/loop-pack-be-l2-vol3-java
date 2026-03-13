package com.loopers.application.like;

import com.loopers.domain.like.Like;
import com.loopers.domain.like.LikeRepository;
import com.loopers.domain.product.ProductRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Component
public class LikeService {

    private final LikeRepository likeRepository;
    private final ProductRepository productRepository;

    @Transactional
    public void like(Long memberId, Long productId) {
        if (likeRepository.existsByMemberIdAndProductId(memberId, productId)) {
            return;
        }
        productRepository.findById(productId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[id = " + productId + "] 상품을 찾을 수 없습니다."));
        likeRepository.save(new Like(memberId, productId));
    }

    @Transactional
    public void unlike(Long memberId, Long productId) {
        likeRepository.deleteByMemberIdAndProductId(memberId, productId);
    }
}

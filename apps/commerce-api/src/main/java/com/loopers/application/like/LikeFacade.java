package com.loopers.application.like;

import com.loopers.domain.like.Like;
import com.loopers.domain.like.LikeService;
import com.loopers.domain.product.ProductRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@RequiredArgsConstructor
@Component
public class LikeFacade {

    private final LikeService likeService;
    private final ProductRepository productRepository;

    public LikeInfo like(Long userId, Long productId) {
        validateProductExists(productId);
        Like like = likeService.like(userId, productId);
        return LikeInfo.from(like);
    }

    public void unlike(Long userId, Long productId) {
        validateProductExists(productId);
        likeService.unlike(userId, productId);
    }

    public List<LikeInfo> getLikesByUserId(Long userId) {
        return likeService.findByUserId(userId).stream()
            .map(LikeInfo::from)
            .toList();
    }

    private void validateProductExists(Long productId) {
        if (!productRepository.existsById(productId)) {
            throw new CoreException(ErrorType.NOT_FOUND, "상품을 찾을 수 없습니다");
        }
    }
}

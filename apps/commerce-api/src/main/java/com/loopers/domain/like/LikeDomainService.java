package com.loopers.domain.like;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;

import java.util.List;

@RequiredArgsConstructor
public class LikeDomainService {

    private final LikeRepository likeRepository;

    public void like(Long userId, Long productId) {
        if (likeRepository.existsByUserIdAndProductId(userId, productId)) {
            throw new CoreException(ErrorType.CONFLICT, "이미 좋아요한 상품입니다.");
        }
        likeRepository.save(new Like(userId, productId));
    }

    public void unlike(Long userId, Long productId) {
        Like like = likeRepository.findByUserIdAndProductId(userId, productId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "좋아요를 찾을 수 없습니다."));
        likeRepository.delete(like);
    }

    public List<Like> getMyLikes(Long userId) {
        return likeRepository.findAllByUserId(userId);
    }
}

package com.loopers.domain.like;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class LikeService {

    private final LikeRepository likeRepository;

    public Like like(Long userId, Long productId) {
        return likeRepository.findByUserIdAndProductId(userId, productId)
            .orElseGet(() -> {
                Like like = new Like(userId, productId);
                return likeRepository.save(like);
            });
    }

    public void unlike(Long userId, Long productId) {
        likeRepository.findByUserIdAndProductId(userId, productId)
            .ifPresent(likeRepository::delete);
    }

    public long countByProductId(Long productId) {
        return likeRepository.countByProductId(productId);
    }
}

package com.loopers.domain.like;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Component
public class LikeService {

    private final LikeRepository likeRepository;

    @Transactional
    public Like like(Long userId, Long productId) {
        return likeRepository.findByUserIdAndProductId(userId, productId)
            .orElseGet(() -> {
                Like like = new Like(userId, productId);
                return likeRepository.save(like);
            });
    }

    @Transactional
    public void unlike(Long userId, Long productId) {
        likeRepository.findByUserIdAndProductId(userId, productId)
            .ifPresent(likeRepository::delete);
    }

    @Transactional(readOnly = true)
    public List<Like> findByUserId(Long userId) {
        return likeRepository.findByUserId(userId);
    }

    @Transactional(readOnly = true)
    public long countByProductId(Long productId) {
        return likeRepository.countByProductId(productId);
    }
}

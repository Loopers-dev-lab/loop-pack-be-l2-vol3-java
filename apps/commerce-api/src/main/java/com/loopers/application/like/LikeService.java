package com.loopers.application.like;

import com.loopers.domain.like.Like;
import com.loopers.domain.like.LikeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class LikeService {

    private final LikeRepository likeRepository;

    // Command

    @Transactional
    public boolean like(Long userId, Long productId) {
        Optional<Like> existing = likeRepository.findByUserIdAndProductId(userId, productId);
        if (existing.isPresent()) {
            return false;
        }

        Like like = Like.create(userId, productId);
        likeRepository.save(like);
        return true;
    }

    @Transactional
    public boolean unlike(Long userId, Long productId) {
        Optional<Like> existing = likeRepository.findByUserIdAndProductId(userId, productId);
        if (existing.isEmpty()) {
            return false;
        }

        likeRepository.delete(existing.get());
        return true;
    }

    // Query

    @Transactional(readOnly = true)
    public Page<Like> findLikedActiveProducts(Long userId, Pageable pageable) {
        return likeRepository.findAllByUserIdWithActiveProduct(userId, pageable);
    }
}

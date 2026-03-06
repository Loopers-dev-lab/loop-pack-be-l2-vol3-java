package com.loopers.application.like;

import com.loopers.domain.like.Like;
import com.loopers.domain.like.LikeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LikeService {

    private final LikeRepository likeRepository;

    // Command

    @Transactional
    public boolean like(Long userId, Long productId) {
        if (likeRepository.existsByUserIdAndProductId(userId, productId)) {
            return false;
        }

        try {
            Like like = Like.create(userId, productId);
            likeRepository.save(like);
            return true;
        } catch (DataIntegrityViolationException e) {
            return false;
        }
    }

    @Transactional
    public boolean unlike(Long userId, Long productId) {
        int deleted = likeRepository.deleteByUserIdAndProductId(userId, productId);
        return deleted > 0;
    }

    // Query

    @Transactional(readOnly = true)
    public Page<Like> findLikedActiveProducts(Long userId, Pageable pageable) {
        return likeRepository.findAllByUserIdWithActiveProduct(userId, pageable);
    }
}

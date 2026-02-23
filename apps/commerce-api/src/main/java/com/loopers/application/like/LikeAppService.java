package com.loopers.application.like;

import com.loopers.domain.like.Like;
import com.loopers.domain.like.LikeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class LikeAppService {
    private final LikeRepository likeRepository;

    @Transactional
    public Like addLike(Long userId, Long productId) {
        Optional<Like> existingLike = likeRepository.findByUserIdAndProductId(userId, productId);
        if (existingLike.isPresent()) {
            return existingLike.get();
        }
        Like like = Like.create(userId, productId);
        return likeRepository.save(like);
    }

    @Transactional
    public void removeLike(Long userId, Long productId) {
        likeRepository.findByUserIdAndProductId(userId, productId)
                .ifPresent(likeRepository::delete);
    }

    @Transactional(readOnly = true)
    public long countByProductId(Long productId) {
        return likeRepository.countByProductId(productId);
    }

    @Transactional(readOnly = true)
    public boolean isLikedByUser(Long userId, Long productId) {
        return likeRepository.findByUserIdAndProductId(userId, productId).isPresent();
    }

    @Transactional(readOnly = true)
    public List<Like> getLikesByUserId(Long userId) {
        return likeRepository.findByUserId(userId);
    }

    @Transactional
    public boolean toggleLike(Long userId, Long productId) {
        Optional<Like> existingLike = likeRepository.findByUserIdAndProductId(userId, productId);
        if (existingLike.isPresent()) {
            likeRepository.delete(existingLike.get());
            return false;
        }
        Like like = Like.create(userId, productId);
        likeRepository.save(like);
        return true;
    }
}

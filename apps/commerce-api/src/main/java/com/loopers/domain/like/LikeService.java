package com.loopers.domain.like;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorMessage;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LikeService {

    private final LikeRepository likeRepository;

    @Transactional
    public Like like(Long productId, Long userId) {
        if (likeRepository.existByUniqueId(productId, userId)) {
            throw new CoreException(ErrorType.BAD_REQUEST, ErrorMessage.Like.ALREADY_LIKED);
        }

        Like like = Like.create(null, productId, userId);

        return likeRepository.save(like);
    }

    @Transactional
    public void unlike(Long productId, Long userId) {
        Like like = findByUniqueId(productId, userId);

        likeRepository.delete(like);
    }

    @Transactional(readOnly = true)
    public boolean isLiked(Long productId, Long userId) {
        return likeRepository.existByUniqueId(productId, userId);
    }

    @Transactional(readOnly = true)
    public Like findByUniqueId(Long productId, Long userId) {
        return likeRepository.findByUniqueId(productId, userId)
            .orElseThrow(() -> new CoreException(ErrorType.BAD_REQUEST, ErrorMessage.Like.LIKE_NOT_FOUND));
    }
}

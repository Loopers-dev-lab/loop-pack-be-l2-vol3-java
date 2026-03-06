package com.loopers.domain.like;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Component
public class LikeService {

    private final LikeRepository likeRepository;

    public Like like(Long userId, Long productId) {
        return likeRepository.findByUserIdAndProductId(userId, productId)
            .orElseGet(() -> {
                try {
                    return likeRepository.save(new Like(userId, productId));
                } catch (DataIntegrityViolationException e) {
                    return likeRepository.findByUserIdAndProductId(userId, productId)
                        .orElseThrow(() -> new CoreException(ErrorType.INTERNAL_ERROR, "좋아요 처리 중 오류가 발생했습니다"));
                }
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

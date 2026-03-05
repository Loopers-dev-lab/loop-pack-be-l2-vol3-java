package com.loopers.domain.like;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@RequiredArgsConstructor
@Component
public class LikeService {

    private final LikeRepository likeRepository;

    // 좋아요 등록 (BR-L01: 중복 방지)
    @Transactional
    public Like create(Long userId, Long productId) {
        if (likeRepository.existsByUserIdAndProductId(userId, productId)) {
            throw new CoreException(ErrorType.CONFLICT, "이미 좋아요한 상품입니다.");
        }
        Like like = new Like(userId, productId);
        return likeRepository.save(like);
    }

    // 좋아요 취소 (BR-L02: 좋아요하지 않은 상품은 취소 불가)
    @Transactional
    public void delete(Long userId, Long productId) {
        if (!likeRepository.existsByUserIdAndProductId(userId, productId)) {
            throw new CoreException(ErrorType.NOT_FOUND, "좋아요 상태가 아닙니다.");
        }
        likeRepository.deleteByUserIdAndProductId(userId, productId);
    }

    // 좋아요 목록 조회 (BR-L03: 자신의 좋아요 목록만 조회)
    @Transactional(readOnly = true)
    public List<Like> findAllByUserId(Long userId) {
        return likeRepository.findAllByUserId(userId);
    }

    // 상품 삭제 시 cascade hard delete (US-P07)
    @Transactional
    public void deleteAllByProductId(Long productId) {
        likeRepository.deleteAllByProductId(productId);
    }

    // 브랜드 삭제 시 cascade hard delete (US-B06)
    @Transactional
    public void deleteAllByProductIds(List<Long> productIds) {
        if (productIds.isEmpty()) {
            return;
        }
        likeRepository.deleteAllByProductIds(productIds);
    }
}

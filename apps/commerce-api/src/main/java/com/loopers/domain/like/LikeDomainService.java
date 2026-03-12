package com.loopers.domain.like;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 좋아요 도메인 서비스.
 * 좋아요 등록/취소 정책을 캡슐화.
 * 상태 없이 입력/출력이 명확한 "함수의 객체화".
 */
@Component
@RequiredArgsConstructor
public class LikeDomainService {

    private final LikeRepository likeRepository;

    /**
     * 좋아요 등록.
     * 중복 좋아요 시 CONFLICT 예외 발생.
     *
     * @param userId 사용자 ID
     * @param productId 상품 ID
     * @return 생성된 Like
     * @throws CoreException 이미 좋아요한 경우
     */
    public Like like(Long userId, Long productId) {
        if (likeRepository.exists(userId, productId)) {
            throw new CoreException(ErrorType.CONFLICT, "이미 좋아요한 상품입니다.");
        }
        return likeRepository.save(Like.create(userId, productId));
    }

    /**
     * 좋아요 취소.
     * 멱등하게 동작 - 존재하지 않아도 예외 없이 처리.
     *
     * @param userId 사용자 ID
     * @param productId 상품 ID
     */
    public void unlike(Long userId, Long productId) {
        likeRepository.findByUserIdAndProductId(userId, productId)
            .ifPresent(likeRepository::delete);
    }

    /**
     * 상품의 좋아요 수 조회.
     *
     * @param productId 상품 ID
     * @return 좋아요 수
     */
    public long countByProductId(Long productId) {
        return likeRepository.countByProductId(productId);
    }

    /**
     * 여러 상품의 좋아요 수 일괄 조회.
     *
     * @param productIds 상품 ID 목록
     * @return 상품 ID → 좋아요 수 Map
     */
    public Map<Long, Long> countByProductIds(List<Long> productIds) {
        return likeRepository.countByProductIds(productIds);
    }
}

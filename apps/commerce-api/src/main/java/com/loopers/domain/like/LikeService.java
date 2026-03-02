package com.loopers.domain.like;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;

import com.loopers.domain.shared.annotation.DomainService;
import com.loopers.support.page.Page;
import com.loopers.support.page.PageSize;

import lombok.RequiredArgsConstructor;

/**
 * 좋아요 도메인의 핵심 비즈니스 규칙을 담당하는 도메인 서비스.
 */
@DomainService
@RequiredArgsConstructor
public class LikeService {

    private final LikeRepository likeRepository;

    /**
     * 상품에 좋아요를 추가한다.
     *
     * @param userId    사용자 ID
     * @param productId 상품 ID
     * @return 좋아요가 새로 생성되었으면 true, 이미 존재하면 false
     */
    @Transactional
    public boolean like(Long userId, Long productId) {
        if (likeRepository.existsByUserIdAndProductId(userId, productId)) {
            return false;
        }
        Like like = Like.create(userId, productId);
        likeRepository.save(like);
        return true;
    }

    /**
     * 사용자가 특정 상품에 좋아요했는지 확인한다.
     *
     * @param userId    사용자 ID (null이면 false 반환)
     * @param productId 상품 ID
     * @return 좋아요 여부
     */
    @Transactional(readOnly = true)
    public boolean isLiked(Long userId, Long productId) {
        if (userId == null) {
            return false;
        }
        return likeRepository.existsByUserIdAndProductId(userId, productId);
    }

    /**
     * 사용자의 좋아요 목록을 페이지 단위로 조회한다.
     *
     * @param userId   사용자 ID
     * @param pageSize 페이지 크기
     * @return 좋아요 목록 페이지 (최신순)
     */
    @Transactional(readOnly = true)
    public Page<Like> getLikes(Long userId, PageSize pageSize) {
        Slice<Like> likes = likeRepository.findAllByUserId(
                userId,
                pageSize.toPageable(Sort.by(Sort.Direction.DESC, "likedAt"))
        );
        return new Page<>(likes.getContent(), likes.hasNext());
    }

    /**
     * 주어진 상품 ID 목록 중 사용자가 좋아요한 상품 ID 집합을 반환한다.
     *
     * @param userId     사용자 ID (null이면 빈 집합 반환)
     * @param productIds 확인할 상품 ID 목록
     * @return 좋아요한 상품 ID 집합
     */
    @Transactional(readOnly = true)
    public Set<Long> getLikedProductIds(Long userId, List<Long> productIds) {
        if (userId == null) {
            return Collections.emptySet();
        }
        return new HashSet<>(likeRepository.findProductIdsByUserIdAndProductIdIn(userId, productIds));
    }

    /**
     * 상품의 좋아요를 취소한다.
     *
     * @param userId    사용자 ID
     * @param productId 상품 ID
     * @return 좋아요가 실제로 삭제되었으면 true, 존재하지 않았으면 false
     */
    @Transactional
    public boolean unlike(Long userId, Long productId) {
        return likeRepository.findByUserIdAndProductId(userId, productId)
                .map(like -> {
                    likeRepository.delete(like);
                    return true;
                })
                .orElse(false);
    }

    /**
     * 특정 상품의 모든 좋아요를 삭제한다.
     *
     * @param productId 상품 ID
     */
    @Transactional
    public void deleteLikesByProductId(Long productId) {
        likeRepository.deleteAllByProductId(productId);
    }

    /**
     * 여러 상품의 모든 좋아요를 일괄 삭제한다.
     *
     * @param productIds 상품 ID 목록
     */
    @Transactional
    public void deleteLikesByProductIds(List<Long> productIds) {
        if (productIds.isEmpty()) {
            return;
        }
        likeRepository.deleteAllByProductIdIn(productIds);
    }
}

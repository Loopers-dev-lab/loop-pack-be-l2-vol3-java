package com.loopers.domain.like;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;

/**
 * 좋아요 도메인 리포지토리 인터페이스.
 */
public interface LikeRepository {

    /**
     * 좋아요를 저장한다.
     *
     * @param like 저장할 좋아요
     * @return 저장된 좋아요
     */
    Like save(Like like);

    /**
     * 사용자 ID와 상품 ID로 좋아요를 조회한다.
     *
     * @param userId    사용자 ID
     * @param productId 상품 ID
     * @return 좋아요 (존재하지 않으면 빈 Optional)
     */
    Optional<Like> findByUserIdAndProductId(Long userId, Long productId);

    /**
     * 특정 사용자의 좋아요 목록을 페이징 조회한다.
     *
     * @param userId   사용자 ID
     * @param pageable 페이징 조건
     * @return 좋아요 슬라이스
     */
    Slice<Like> findAllByUserId(Long userId, Pageable pageable);

    /**
     * 특정 사용자가 좋아요한 상품 ID 목록을 조회한다.
     *
     * <p>주어진 상품 ID 목록 중 사용자가 좋아요한 것만 필터링하여 반환한다.</p>
     *
     * @param userId     사용자 ID
     * @param productIds 필터링 대상 상품 ID 목록
     * @return 사용자가 좋아요한 상품 ID 목록
     */
    List<Long> findProductIdsByUserIdAndProductIdIn(Long userId, List<Long> productIds);

    /**
     * 사용자의 특정 상품 좋아요 존재 여부를 확인한다.
     *
     * @param userId    사용자 ID
     * @param productId 상품 ID
     * @return 좋아요가 존재하면 true
     */
    boolean existsByUserIdAndProductId(Long userId, Long productId);

    /**
     * 좋아요를 삭제한다.
     *
     * @param like 삭제할 좋아요
     */
    void delete(Like like);

    /**
     * 특정 상품의 모든 좋아요를 삭제한다.
     *
     * @param productId 상품 ID
     */
    void deleteAllByProductId(Long productId);

    /**
     * 여러 상품의 모든 좋아요를 일괄 삭제한다.
     *
     * @param productIds 상품 ID 목록
     */
    void deleteAllByProductIdIn(List<Long> productIds);
}

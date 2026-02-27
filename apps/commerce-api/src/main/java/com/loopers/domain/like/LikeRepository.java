package com.loopers.domain.like;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 좋아요 도메인 리포지토리 인터페이스.
 * DIP(의존성 역전 원칙)에 따라 도메인 계층에서 정의하며, 인프라스트럭처 계층에서 구현한다.
 */
public interface LikeRepository {

    /**
     * 좋아요를 저장한다.
     *
     * @param like 저장할 좋아요 엔티티
     * @return 저장된 좋아요 엔티티
     */
    LikeModel save(LikeModel like);

    /**
     * 복합 PK로 좋아요를 조회한다.
     *
     * @param id 복합 PK (userId + productId)
     * @return 좋아요 (Optional)
     */
    Optional<LikeModel> findById(LikeId id);

    /**
     * 좋아요를 삭제한다.
     *
     * @param like 삭제할 좋아요 엔티티
     */
    void delete(LikeModel like);

    /**
     * 특정 사용자의 좋아요 목록을 조회한다.
     *
     * @param userId 사용자 ID
     * @return 사용자의 좋아요 목록
     */
    List<LikeModel> findAllByUserId(String userId);

    /**
     * 특정 상품의 좋아요 수를 조회한다.
     *
     * @param productId 상품 ID
     * @return 좋아요 수
     */
    long countByProductId(String productId);

    /**
     * 여러 상품의 좋아요 수를 일괄 조회한다 (N+1 방지용 배치 쿼리).
     *
     * @param productIds 상품 ID 목록
     * @return 상품 ID → 좋아요 수 맵
     */
    Map<String, Long> countByProductIds(Collection<String> productIds);
}

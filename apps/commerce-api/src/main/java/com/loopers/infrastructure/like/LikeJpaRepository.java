package com.loopers.infrastructure.like;

import com.loopers.domain.like.LikeId;
import com.loopers.domain.like.LikeModel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

/**
 * 좋아요 엔티티에 대한 Spring Data JPA Repository 인터페이스.
 *
 * <p>JpaRepository를 상속받아 기본 CRUD 메서드가 자동 제공되며,
 * 복합 PK({@link LikeId})를 사용한다.</p>
 */
public interface LikeJpaRepository extends JpaRepository<LikeModel, LikeId> {

    /**
     * 사용자 ID로 해당 사용자의 좋아요 목록을 조회한다.
     *
     * <p>Spring Data JPA 쿼리 메서드: 메서드 이름으로부터 자동 생성되는 쿼리를 사용한다.</p>
     *
     * @param userId 사용자 ID
     * @return 해당 사용자의 좋아요 목록
     */
    List<LikeModel> findAllByUserId(String userId);

    /**
     * 상품 ID에 대한 좋아요 수를 조회한다.
     *
     * <p>Spring Data JPA 쿼리 메서드: COUNT 쿼리가 자동 생성된다.</p>
     *
     * @param productId 상품 ID
     * @return 해당 상품의 좋아요 수
     */
    long countByProductId(String productId);

    /**
     * 여러 상품의 좋아요 수를 GROUP BY로 일괄 조회한다.
     *
     * @param productIds 상품 ID 목록
     * @return [productId, count] 배열 목록
     */
    @Query("SELECT l.productId, COUNT(l) FROM LikeModel l WHERE l.productId IN :productIds GROUP BY l.productId")
    List<Object[]> countByProductIdIn(@Param("productIds") Collection<String> productIds);
}

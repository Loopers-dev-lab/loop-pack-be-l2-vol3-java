package com.loopers.infrastructure.product;

import com.loopers.domain.product.ProductRevisionId;
import com.loopers.domain.product.ProductRevisionModel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * 상품 변경 이력 엔티티에 대한 Spring Data JPA Repository 인터페이스.
 *
 * <p>JpaRepository를 상속받아 기본 CRUD 메서드가 자동 제공되며,
 * 복합 PK({@link ProductRevisionId}: product_id + revision_seq)를 사용한다.</p>
 */
public interface ProductRevisionJpaRepository extends JpaRepository<ProductRevisionModel, ProductRevisionId> {

    /**
     * 상품 ID로 변경 이력을 최신순(revision_seq 내림차순)으로 조회한다.
     *
     * <p>Spring Data JPA 쿼리 메서드: 메서드 이름으로부터 자동 생성되는 쿼리를 사용한다.</p>
     *
     * @param productId 상품 ID
     * @return 해당 상품의 변경 이력 목록 (최신순)
     */
    List<ProductRevisionModel> findAllByProductIdOrderByRevisionSeqDesc(Long productId);
}

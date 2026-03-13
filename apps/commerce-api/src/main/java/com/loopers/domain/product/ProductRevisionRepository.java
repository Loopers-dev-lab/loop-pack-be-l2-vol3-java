package com.loopers.domain.product;

import java.util.List;
import java.util.Optional;

/**
 * 상품 변경 이력 리포지토리 인터페이스.
 * <p>
 * DIP(의존성 역전 원칙)에 따라 도메인 계층에 정의되며,
 * infrastructure 계층의 {@code ProductRevisionRepositoryImpl}이 구현한다.
 * </p>
 */
public interface ProductRevisionRepository {

    /**
     * 상품 변경 이력을 저장한다.
     *
     * @param revision 저장할 변경 이력 엔티티
     * @return 저장된 변경 이력 엔티티
     */
    ProductRevisionModel save(ProductRevisionModel revision);

    /**
     * 특정 상품의 모든 변경 이력을 조회한다.
     *
     * @param productId 상품 ID
     * @return 해당 상품의 변경 이력 목록
     */
    List<ProductRevisionModel> findAllByProductId(Long productId);

    /**
     * 복합 PK(productId + revisionSeq)로 변경 이력을 조회한다.
     *
     * @param id 변경 이력 복합 기본키
     * @return 변경 이력 엔티티 (존재하지 않으면 빈 Optional)
     */
    Optional<ProductRevisionModel> findById(ProductRevisionId id);
}

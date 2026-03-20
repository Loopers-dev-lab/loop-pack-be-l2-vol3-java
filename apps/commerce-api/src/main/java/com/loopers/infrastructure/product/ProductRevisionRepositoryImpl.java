package com.loopers.infrastructure.product;

import com.loopers.domain.product.ProductRevisionId;
import com.loopers.domain.product.ProductRevisionModel;
import com.loopers.domain.product.ProductRevisionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 도메인 {@link ProductRevisionRepository} 인터페이스의 인프라스트럭처 구현체.
 *
 * <p>DIP(의존성 역전 원칙)에 따라 도메인 계층에서 정의한 Repository 인터페이스를 구현하며,
 * 내부적으로 {@link ProductRevisionJpaRepository}에 위임하여 실제 데이터 접근을 수행한다.</p>
 */
@Repository
@RequiredArgsConstructor
public class ProductRevisionRepositoryImpl implements ProductRevisionRepository {

    private final ProductRevisionJpaRepository jpaRepository;

    /**
     * 상품 변경 이력을 저장한다.
     *
     * @param revision 저장할 상품 변경 이력 엔티티
     * @return 저장된 상품 변경 이력 엔티티
     */
    @Override
    public ProductRevisionModel save(ProductRevisionModel revision) {
        return jpaRepository.save(revision);
    }

    /**
     * 상품 ID로 변경 이력 목록을 최신순으로 조회한다.
     *
     * @param productId 상품 ID
     * @return 해당 상품의 변경 이력 목록 (최신순)
     */
    @Override
    public List<ProductRevisionModel> findAllByProductId(Long productId) {
        return jpaRepository.findAllByProductIdOrderByRevisionSeqDesc(productId);
    }

    /**
     * 복합 PK로 상품 변경 이력을 조회한다.
     *
     * @param id 복합 PK (상품 ID + 리비전 시퀀스)
     * @return 상품 변경 이력 (Optional)
     */
    @Override
    public Optional<ProductRevisionModel> findById(ProductRevisionId id) {
        return jpaRepository.findById(id);
    }
}

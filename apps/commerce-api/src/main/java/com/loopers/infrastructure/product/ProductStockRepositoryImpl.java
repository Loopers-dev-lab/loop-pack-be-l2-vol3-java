package com.loopers.infrastructure.product;

import com.loopers.domain.product.ProductStockModel;
import com.loopers.domain.product.ProductStockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 도메인 {@link ProductStockRepository} 인터페이스의 인프라스트럭처 구현체.
 *
 * <p>DIP(의존성 역전 원칙)에 따라 도메인 계층에서 정의한 Repository 인터페이스를 구현하며,
 * 내부적으로 {@link ProductStockJpaRepository}에 위임하여 실제 데이터 접근을 수행한다.
 * CAS(Compare-And-Set) 기반의 재고 관리로 오버셀을 방지한다.</p>
 */
@Repository
@RequiredArgsConstructor
public class ProductStockRepositoryImpl implements ProductStockRepository {

    private final ProductStockJpaRepository jpaRepository;

    /**
     * 상품 재고를 저장한다.
     *
     * @param stock 저장할 상품 재고 엔티티
     * @return 저장된 상품 재고 엔티티
     */
    @Override
    public ProductStockModel save(ProductStockModel stock) {
        return jpaRepository.save(stock);
    }

    /**
     * 상품 ID로 재고를 조회한다.
     *
     * @param productId 상품 ID
     * @return 상품 재고 (Optional)
     */
    @Override
    public Optional<ProductStockModel> findByProductId(String productId) {
        return jpaRepository.findById(productId);
    }

    /**
     * CAS 방식으로 재고를 예약(hold)한다.
     *
     * @param productId 상품 ID
     * @param qty       예약할 수량
     * @return 변경된 행 수 (0이면 재고 부족으로 예약 실패)
     */
    @Override
    public int reserveStock(String productId, int qty) {
        return jpaRepository.reserveStock(productId, qty);
    }

    /**
     * CAS 방식으로 예약된 재고를 해제(release)한다.
     *
     * @param productId 상품 ID
     * @param qty       해제할 수량
     * @return 변경된 행 수 (0이면 해제 실패)
     */
    @Override
    public int releaseStock(String productId, int qty) {
        return jpaRepository.releaseStock(productId, qty);
    }

    /**
     * CAS 방식으로 예약된 재고를 확정(commit)한다.
     *
     * @param productId 상품 ID
     * @param qty       확정할 수량
     * @return 변경된 행 수 (0이면 확정 실패)
     */
    @Override
    public int commitStock(String productId, int qty) {
        return jpaRepository.commitStock(productId, qty);
    }

    /**
     * 상품 ID 목록으로 재고를 일괄 조회한다.
     *
     * @param productIds 상품 ID 목록
     * @return 해당 상품들의 재고 목록
     */
    @Override
    public List<ProductStockModel> findAllByProductIds(Collection<String> productIds) {
        return jpaRepository.findAllById(productIds);
    }
}

package com.loopers.domain.product;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 상품 재고 리포지토리 인터페이스.
 * <p>
 * DIP(의존성 역전 원칙)에 따라 도메인 계층에 정의되며,
 * infrastructure 계층의 {@code ProductStockRepositoryImpl}이 구현한다.
 * CAS(Compare-And-Set) 기반 재고 예약/해제/확정 메서드를 포함하여
 * 오버셀(초과 판매) 방지를 위한 동시성 안전한 재고 관리를 지원한다.
 * </p>
 */
public interface ProductStockRepository {

    /**
     * 재고를 저장한다.
     *
     * @param stock 저장할 재고 엔티티
     * @return 저장된 재고 엔티티
     */
    ProductStockModel save(ProductStockModel stock);

    /**
     * 상품 ID로 재고를 조회한다.
     *
     * @param productId 상품 ID
     * @return 재고 엔티티 (존재하지 않으면 빈 Optional)
     */
    Optional<ProductStockModel> findByProductId(Long productId);

    /**
     * CAS(Compare-And-Set) 방식으로 재고를 예약(hold)한다.
     * <p>
     * {@code UPDATE product_stocks SET reserved = reserved + :qty WHERE product_id = :productId AND (on_hand - reserved) >= :qty}
     * 형태의 조건부 UPDATE로 가용 재고가 충분한 경우에만 예약이 수행되어 오버셀을 방지한다.
     * </p>
     *
     * @param productId 상품 ID
     * @param qty       예약할 수량
     * @return 영향받은 행 수 (0이면 가용 재고 부족으로 예약 실패)
     */
    int reserveStock(Long productId, int qty);

    /**
     * CAS(Compare-And-Set) 방식으로 예약된 재고를 해제(release)한다.
     * <p>
     * {@code UPDATE product_stocks SET reserved = reserved - :qty WHERE product_id = :productId AND reserved >= :qty}
     * 형태의 조건부 UPDATE로 예약 재고가 충분한 경우에만 해제가 수행된다.
     * 주문 취소/만료 시 사용된다.
     * </p>
     *
     * @param productId 상품 ID
     * @param qty       해제할 수량
     * @return 영향받은 행 수 (0이면 예약 재고 부족으로 해제 실패)
     */
    int releaseStock(Long productId, int qty);

    /**
     * CAS(Compare-And-Set) 방식으로 예약된 재고를 확정(commit)한다.
     * <p>
     * 결제 완료 시 예약 재고를 실제 출고로 확정하는 연산이다.
     * {@code reserved -= :qty, on_hand -= :qty} 형태로 동시에 차감한다.
     * </p>
     *
     * @param productId 상품 ID
     * @param qty       확정할 수량
     * @return 영향받은 행 수 (0이면 확정 실패)
     */
    int commitStock(Long productId, int qty);

    /**
     * 상품 ID 목록으로 재고를 일괄 조회한다.
     *
     * @param productIds 상품 ID 목록
     * @return 해당 상품들의 재고 목록
     */
    List<ProductStockModel> findAllByProductIds(Collection<Long> productIds);
}

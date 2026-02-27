package com.loopers.infrastructure.product;

import com.loopers.domain.product.ProductStockModel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 상품 재고 엔티티에 대한 Spring Data JPA Repository 인터페이스.
 *
 * <p>JpaRepository를 상속받아 기본 CRUD 메서드가 자동 제공되며,
 * CAS(Compare-And-Set) 기반의 재고 예약/해제/확정 쿼리를 정의하여
 * 오버셀(초과 판매)을 방지한다.</p>
 */
public interface ProductStockJpaRepository extends JpaRepository<ProductStockModel, String> {

    /**
     * CAS 방식으로 재고를 예약(hold)한다.
     *
     * <p>가용 재고(on_hand - reserved)가 요청 수량 이상인 경우에만
     * reserved를 증가시켜 동시성 안전하게 재고를 예약한다.</p>
     *
     * @param productId 상품 ID
     * @param qty       예약할 수량
     * @return 변경된 행 수 (0이면 재고 부족으로 예약 실패)
     */
    @Modifying
    @Query("UPDATE ProductStockModel s SET s.reserved = s.reserved + :qty " +
           "WHERE s.productId = :productId AND (s.onHand - s.reserved) >= :qty")
    int reserveStock(@Param("productId") String productId, @Param("qty") int qty);

    /**
     * CAS 방식으로 예약된 재고를 해제(release)한다.
     *
     * <p>예약 수량(reserved)이 해제 요청 수량 이상인 경우에만
     * reserved를 감소시킨다. 주문 취소/만료 시 사용된다.</p>
     *
     * @param productId 상품 ID
     * @param qty       해제할 수량
     * @return 변경된 행 수 (0이면 해제 실패)
     */
    @Modifying
    @Query("UPDATE ProductStockModel s SET s.reserved = s.reserved - :qty " +
           "WHERE s.productId = :productId AND s.reserved >= :qty")
    int releaseStock(@Param("productId") String productId, @Param("qty") int qty);

    /**
     * CAS 방식으로 예약된 재고를 확정(commit)한다.
     *
     * <p>결제 완료 시 on_hand와 reserved를 동시에 감소시켜
     * 실 재고를 차감한다.</p>
     *
     * @param productId 상품 ID
     * @param qty       확정할 수량
     * @return 변경된 행 수 (0이면 확정 실패)
     */
    @Modifying
    @Query("UPDATE ProductStockModel s " +
           "SET s.onHand = s.onHand - :qty, s.reserved = s.reserved - :qty " +
           "WHERE s.productId = :productId AND s.reserved >= :qty")
    int commitStock(@Param("productId") String productId, @Param("qty") int qty);
}

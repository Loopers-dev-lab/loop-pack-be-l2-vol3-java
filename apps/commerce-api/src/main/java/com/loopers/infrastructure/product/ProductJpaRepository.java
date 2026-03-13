package com.loopers.infrastructure.product;

import com.loopers.domain.product.Product;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProductJpaRepository extends JpaRepository<Product, Long> {

    /**
     * 비관적 쓰기 락(PESSIMISTIC_WRITE)으로 상품을 조회한다.
     * → 내부적으로 SELECT ... FOR UPDATE 쿼리가 실행된다.
     *
     * ─── 낙관적 락 vs 비관적 락 선택 기준 ────────────────────────────────────
     * [낙관적 락 - @Version 기반]
     *   - 읽기 시 락을 잡지 않고, 커밋 시 version 값을 비교해 충돌을 감지
     *   - 충돌이 드물고, 충돌 발생 시 재시도가 저렴한 경우 유리
     *   - 충돌 발생 → OptimisticLockException → 롤백 → 재시도 필요
     *   - 재고처럼 동시 요청이 많으면 대부분 실패 후 재시도 → 성능 저하
     *
     * [비관적 락 - SELECT FOR UPDATE]
     *   - 조회 시점에 DB 행 잠금 획득 → 다른 트랜잭션은 트랜잭션 종료까지 대기
     *   - 충돌이 잦고, 실패 비용(사용자 재주문)이 높은 경우 유리
     *   - 재고 차감처럼 "줄어드는 자원"은 경합이 높을수록 낙관적 락의 재시도 비용이 커짐
     *
     * 결론: 재고는 비관적 락이 더 예측 가능하고 구현이 단순하다.
     *
     * ─── 데드락 위험과 대응 ───────────────────────────────────────────────────
     * SELECT FOR UPDATE를 여러 행에 걸쳐 사용할 경우 락 획득 순서가 다르면 데드락 발생.
     * 이 쿼리를 호출하는 OrderDomainService.prepareOrderLines()에서
     * productId 오름차순 정렬 후 호출하여 락 순서를 전역적으로 통일한다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Product p WHERE p.id = :id")
    Optional<Product> findByIdForUpdate(@Param("id") Long id);

    @Query("SELECT p FROM Product p LEFT JOIN com.loopers.domain.like.Like l ON l.productId = p.id " +
           "WHERE p.deletedAt IS NULL GROUP BY p ORDER BY COUNT(l) DESC")
    List<Product> findAllOrderByLikesDesc();
}

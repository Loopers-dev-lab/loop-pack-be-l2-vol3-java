package com.loopers.infrastructure.coupon;

import com.loopers.domain.coupon.IssuedCoupon;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface IssuedCouponJpaRepository extends JpaRepository<IssuedCoupon, Long> {

    /**
     * 비관적 쓰기 락(PESSIMISTIC_WRITE)으로 발급 쿠폰을 조회한다.
     * → 내부적으로 SELECT ... FOR UPDATE 쿼리가 실행된다.
     *
     * ─── 쿠폰에 비관적 락을 적용한 이유 ─────────────────────────────────────
     * 쿠폰은 "단 1회만 사용 가능"이라는 엄격한 제약을 가진다.
     * 이 제약을 애플리케이션 레벨(코드)만으로 보장하면 Lost Update 문제가 발생한다.
     *
     * 시나리오 (락 없을 때):
     *   T1: SELECT → status=AVAILABLE (사용 가능 확인)
     *   T2: SELECT → status=AVAILABLE (동시에 사용 가능 확인)
     *   T1: UPDATE status=USED (쿠폰 사용)
     *   T2: UPDATE status=USED (이미 사용된 쿠폰을 또 사용 → 중복!)
     *
     * 비관적 락 적용 후:
     *   T1: SELECT FOR UPDATE → 행 잠금 획득
     *   T2: SELECT FOR UPDATE → T1 종료까지 대기
     *   T1: UPDATE status=USED, COMMIT → 잠금 해제
     *   T2: 락 획득 후 SELECT → status=USED → 예외 발생 → 중복 사용 방지
     *
     * ─── 낙관적 락(@Version) 대신 비관적 락을 선택한 이유 ────────────────────
     * 낙관적 락도 중복 사용을 막을 수 있다. 그러나:
     * - 쿠폰 중복 사용은 "실패 감지"보다 "사전 차단"이 중요하다.
     * - 낙관적 락은 커밋 시점에 실패를 알게 되므로 DB 작업(재고 차감 등)을 이미 수행한 후다.
     * - 비관적 락은 조회 시점에 독점권을 확보하므로 불필요한 작업 수행 자체를 막는다.
     * - 쿠폰 1건에 대한 동시 요청 빈도는 높지 않아 대기 비용이 크지 않다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM IssuedCoupon c WHERE c.id = :id")
    Optional<IssuedCoupon> findByIdForUpdate(@Param("id") Long id);

    List<IssuedCoupon> findByMemberId(Long memberId);

    boolean existsByMemberIdAndCouponTemplateId(Long memberId, Long couponTemplateId);

    List<IssuedCoupon> findByCouponTemplateId(Long couponTemplateId, Pageable pageable);

    long countByCouponTemplateId(Long couponTemplateId);
}

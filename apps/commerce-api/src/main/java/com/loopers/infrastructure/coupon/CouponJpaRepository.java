package com.loopers.infrastructure.coupon;

import com.loopers.domain.coupon.CouponModel;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 쿠폰 JPA 레포지토리.
 */
public interface CouponJpaRepository extends JpaRepository<CouponModel, Long> {

    /**
     * 비관적 쓰기 락(SELECT FOR UPDATE)으로 쿠폰을 조회한다.
     * 동시 발급 요청 시 중복 발급 방지를 위해 사용된다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM CouponModel c WHERE c.couponId = :couponId")
    Optional<CouponModel> findByIdWithLock(@Param("couponId") Long couponId);

    @Query("SELECT c FROM CouponModel c WHERE c.couponId IN :ids")
    List<CouponModel> findAllByCouponIdIn(@Param("ids") Collection<Long> ids);
}

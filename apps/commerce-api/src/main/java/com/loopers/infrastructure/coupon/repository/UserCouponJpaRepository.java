package com.loopers.infrastructure.coupon.repository;

import com.loopers.domain.coupon.model.UserCouponItem;
import com.loopers.infrastructure.coupon.entity.UserCouponEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserCouponJpaRepository extends JpaRepository<UserCouponEntity, Long> {

    Page<UserCouponEntity> findByMemberId(Long memberId, Pageable pageable);

    Page<UserCouponEntity> findByCouponTemplateId(Long couponTemplateId, Pageable pageable);

    boolean existsByMemberIdAndCouponTemplateId(Long memberId, Long couponTemplateId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT uc FROM UserCouponEntity uc WHERE uc.id = :id")
    Optional<UserCouponEntity> findByIdWithLock(@Param("id") Long id);

    @Query("SELECT new com.loopers.domain.coupon.model.UserCouponItem(" +
           "uc.id, ct.id, ct.name, ct.type, ct.discountValue, ct.minOrderAmount, " +
           "uc.status, ct.expiredAt, uc.usedAt) " +
           "FROM UserCouponEntity uc JOIN CouponTemplateEntity ct " +
           "ON uc.couponTemplateId = ct.id " +
           "WHERE uc.memberId = :memberId")
    Page<UserCouponItem> findByMemberIdWithTemplate(@Param("memberId") Long memberId, Pageable pageable);
}

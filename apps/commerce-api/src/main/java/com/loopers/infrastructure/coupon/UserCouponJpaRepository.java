package com.loopers.infrastructure.coupon;

import com.loopers.domain.coupon.UserCouponModel;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface UserCouponJpaRepository extends JpaRepository<UserCouponModel, Long> {

    @Query("SELECT CASE WHEN COUNT(uc) > 0 THEN true ELSE false END FROM UserCouponModel uc WHERE uc.userId = :userId AND uc.coupon.id = :couponId AND uc.deletedAt IS NULL")
    boolean existsByUserIdAndCouponId(Long userId, Long couponId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT uc FROM UserCouponModel uc JOIN FETCH uc.coupon c WHERE uc.id = :id AND uc.userId = :userId AND uc.deletedAt IS NULL")
    Optional<UserCouponModel> findByIdAndUserIdForUpdate(Long id, Long userId);

    @Query("SELECT uc FROM UserCouponModel uc JOIN FETCH uc.coupon c WHERE uc.userId = :userId AND uc.deletedAt IS NULL ORDER BY uc.createdAt DESC")
    List<UserCouponModel> findAllByUserId(Long userId);

    @Query(
        value = "SELECT uc FROM UserCouponModel uc JOIN FETCH uc.coupon c WHERE c.id = :couponId AND uc.deletedAt IS NULL",
        countQuery = "SELECT COUNT(uc) FROM UserCouponModel uc WHERE uc.coupon.id = :couponId AND uc.deletedAt IS NULL"
    )
    Page<UserCouponModel> findAllByCouponId(Long couponId, Pageable pageable);
}

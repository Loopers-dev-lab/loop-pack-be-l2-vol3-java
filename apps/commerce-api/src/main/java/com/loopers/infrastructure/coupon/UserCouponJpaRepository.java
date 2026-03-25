package com.loopers.infrastructure.coupon;

import com.loopers.domain.coupon.UserCoupon;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

public interface UserCouponJpaRepository extends JpaRepository<UserCoupon, Long> {

    List<UserCoupon> findAllByUserIdAndDeletedAtIsNull(Long userId);

    Page<UserCoupon> findAllByCouponTemplateIdAndDeletedAtIsNull(Long couponTemplateId, Pageable pageable);

    Optional<UserCoupon> findByIdAndUserIdAndDeletedAtIsNull(Long id, Long userId);

    // 원자적 쿠폰 사용: WHERE used_at IS NULL 조건으로 동시 요청 중 하나만 성공
    // flushAutomatically: UPDATE 전 pending 변경사항 flush 보장
    // clearAutomatically 미사용: 같은 트랜잭션 내 Product 등 다른 관리 엔티티의 dirty checking을 유지하기 위함
    @Modifying(flushAutomatically = true)
    @Query("UPDATE UserCoupon uc SET uc.usedAt = :now WHERE uc.id = :id AND uc.userId = :userId AND uc.usedAt IS NULL AND uc.deletedAt IS NULL")
    int useIfAvailable(@Param("id") Long id, @Param("userId") Long userId, @Param("now") LocalDateTime now);

    // 결제 실패 시 쿠폰 사용 취소: WHERE used_at IS NOT NULL 조건으로 이미 복구된 건 무시 (멱등성)
    @Modifying(flushAutomatically = true)
    @Query("UPDATE UserCoupon uc SET uc.usedAt = null WHERE uc.id = :id AND uc.userId = :userId AND uc.usedAt IS NOT NULL AND uc.deletedAt IS NULL")
    int restoreUsedCoupon(@Param("id") Long id, @Param("userId") Long userId);

    // 쿠폰 템플릿 삭제 시 발급 쿠폰 연쇄 soft delete (US-C07, BR-C05)
    @Modifying
    @Query("UPDATE UserCoupon uc SET uc.deletedAt = :now WHERE uc.couponTemplateId = :couponTemplateId AND uc.deletedAt IS NULL")
    void softDeleteAllByCouponTemplateId(@Param("couponTemplateId") Long couponTemplateId, @Param("now") ZonedDateTime now);
}

package com.loopers.domain.coupon;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface UserCouponRepository {

    UserCoupon save(UserCoupon userCoupon);

    // 내 쿠폰 목록 조회 (US-C02)
    List<UserCoupon> findAllByUserId(Long userId);

    // 특정 템플릿의 발급 내역 조회 (US-C08)
    Page<UserCoupon> findAllByCouponTemplateId(Long couponTemplateId, Pageable pageable);

    // 주문 시 발급 쿠폰 유효성 확인 (소유권 포함, US-O01)
    Optional<UserCoupon> findByIdAndUserId(Long id, Long userId);

    // 원자적 쿠폰 사용 처리. 동시 요청 중 하나만 성공하도록 WHERE used_at IS NULL 조건 사용
    // 반환값: 업데이트된 행 수 (1이면 성공, 0이면 이미 사용됨)
    int useIfAvailable(Long id, Long userId, LocalDateTime now);

    // 결제 실패 시 쿠폰 사용 취소 (보상 트랜잭션). WHERE used_at IS NOT NULL로 멱등성 보장
    // 반환값: 업데이트된 행 수 (1이면 복구 성공, 0이면 이미 복구됨)
    int restoreUsedCoupon(Long id, Long userId);

    // 쿠폰 템플릿 삭제 시 연쇄 soft delete (US-C07, BR-C05)
    void deleteAllByCouponTemplateId(Long couponTemplateId);
}

package com.loopers.application.coupon;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.loopers.domain.coupon.Coupon;
import com.loopers.domain.coupon.CouponRepository;
import com.loopers.domain.coupon.OwnedCoupon;
import com.loopers.domain.coupon.OwnedCouponRepository;
import com.loopers.domain.eventhandled.EventHandledRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 쿠폰 발급 이벤트 처리를 조율하는 서비스.
 *
 * <p>Redis SETNX로 1차 멱등 필터링 후, DB 중복 체크를 거쳐
 * 쿠폰 발급 수량 증가와 보유 쿠폰 생성을 하나의 트랜잭션으로 처리한다.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CouponIssueService {

    private final EventHandledRepository eventHandledRepository;
    private final CouponRepository couponRepository;
    private final OwnedCouponRepository ownedCouponRepository;

    /**
     * 쿠폰 발급을 처리한다.
     *
     * <ol>
     *   <li>Redis SETNX로 이벤트 중복 여부를 확인한다.</li>
     *   <li>쿠폰을 조회하고 DB 레벨에서 중복 발급을 체크한다.</li>
     *   <li>쿠폰 발급 수량을 증가시키고 보유 쿠폰을 저장한다.</li>
     * </ol>
     *
     * @param eventId  멱등 처리를 위한 이벤트 식별자
     * @param couponId 발급할 쿠폰 ID
     * @param userId   발급 대상 사용자 ID
     */
    @Transactional
    public void issue(String eventId, Long couponId, Long userId) {
        if (!eventHandledRepository.markIfAbsent(eventId)) {
            log.debug("[CouponIssue] 중복 이벤트 skip: eventId={}", eventId);
            return;
        }

        Coupon coupon = couponRepository.findById(couponId)
                .orElseThrow(() -> new IllegalArgumentException("쿠폰을 찾을 수 없습니다: couponId=" + couponId));

        if (ownedCouponRepository.existsByCouponAndUserId(coupon, userId)) {
            log.debug("[CouponIssue] 이미 발급된 쿠폰 skip: couponId={}, userId={}", couponId, userId);
            return;
        }

        coupon.issue();
        ownedCouponRepository.save(OwnedCoupon.create(coupon, userId));
    }
}

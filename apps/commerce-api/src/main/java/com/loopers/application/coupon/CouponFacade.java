package com.loopers.application.coupon;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.domain.coupon.*;
import com.loopers.infrastructure.redis.CouponIssueRequestRedisRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CouponFacade {

    private final CouponRepository couponRepository;
    private final CouponIssueRepository couponIssueRepository;
    private final CouponIssueRequestRedisRepository couponIssueRequestRedisRepository;
    private final KafkaTemplate<Object, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    // ── Admin: 쿠폰 템플릿 CRUD ──

    @Transactional
    public Coupon createCoupon(String name, DiscountType discountType, int discountValue,
                               int minOrderAmount, ZonedDateTime expiredAt) {
        Coupon coupon = new Coupon(name, discountType, discountValue, minOrderAmount, expiredAt);
        return couponRepository.save(coupon);
    }

    public Coupon getCoupon(Long couponId) {
        return couponRepository.findById(couponId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "쿠폰을 찾을 수 없습니다."));
    }

    public List<Coupon> getCoupons() {
        return couponRepository.findAll();
    }

    @Transactional
    public Coupon updateCoupon(Long couponId, String name, DiscountType discountType,
                               int discountValue, int minOrderAmount, ZonedDateTime expiredAt) {
        Coupon coupon = couponRepository.findById(couponId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "쿠폰을 찾을 수 없습니다."));
        coupon.changeName(name);
        coupon.changeDiscount(discountType, discountValue);
        coupon.changeMinOrderAmount(minOrderAmount);
        coupon.changeExpiredAt(expiredAt);
        return coupon;
    }

    @Transactional
    public void deleteCoupon(Long couponId) {
        Coupon coupon = couponRepository.findById(couponId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "쿠폰을 찾을 수 없습니다."));
        coupon.delete();
    }

    // ── Admin: 발급 내역 조회 ──

    public List<CouponIssue> getCouponIssues(Long couponId) {
        couponRepository.findById(couponId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "쿠폰을 찾을 수 없습니다."));
        return couponIssueRepository.findAllByCouponId(couponId);
    }

    // ── 대고객: 쿠폰 발급 ──

    @Transactional
    public CouponIssue issueCoupon(Long couponId, Long memberId) {
        Coupon coupon = couponRepository.findById(couponId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "쿠폰을 찾을 수 없습니다."));
        ZonedDateTime now = ZonedDateTime.now(clock);
        if (now.isAfter(coupon.getExpiredAt())) {
            throw new CoreException(ErrorType.BAD_REQUEST, "만료된 쿠폰은 발급할 수 없습니다.");
        }
        CouponIssue couponIssue = new CouponIssue(couponId, memberId, coupon.getExpiredAt());
        return couponIssueRepository.save(couponIssue);
    }

    // ── 대고객: 내 쿠폰 목록 ──

    public List<CouponIssue> getMyCoupons(Long memberId) {
        return couponIssueRepository.findAllByMemberId(memberId);
    }

    // ── 주문 연동: 쿠폰 적용 ──

    @Transactional
    public CouponApplyResult applyCouponToOrder(Long couponIssueId, Long memberId, int orderPrice) {
        ZonedDateTime now = ZonedDateTime.now(clock);

        CouponIssue couponIssue = couponIssueRepository.findById(couponIssueId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "쿠폰을 찾을 수 없습니다."));

        if (!couponIssue.getMemberId().equals(memberId)) {
            throw new CoreException(ErrorType.FORBIDDEN, "본인의 쿠폰만 사용할 수 있습니다.");
        }

        Coupon coupon = couponRepository.findById(couponIssue.getCouponId())
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "쿠폰 템플릿을 찾을 수 없습니다."));

        coupon.validateUsable(orderPrice, now);
        int discountAmount = coupon.calculateDiscount(orderPrice);

        int updated = couponIssueRepository.markAsUsed(couponIssueId, now);
        if (updated == 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "이미 사용되었거나 만료된 쿠폰입니다.");
        }

        return new CouponApplyResult(couponIssueId, discountAmount);
    }

    // ── 주문 연동: 쿠폰에 주문 ID 연결 ──

    @Transactional
    public void linkCouponToOrder(Long couponIssueId, Long orderId) {
        CouponIssue couponIssue = couponIssueRepository.findById(couponIssueId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "쿠폰을 찾을 수 없습니다."));
        couponIssue.linkOrder(orderId);
    }

    // ── 주문 연동: 쿠폰 복원 ──

    @Transactional
    public void restoreCoupon(Long couponIssueId) {
        CouponIssue couponIssue = couponIssueRepository.findById(couponIssueId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "쿠폰을 찾을 수 없습니다."));
        couponIssue.cancelUse(ZonedDateTime.now(clock));
    }

    // ── 선착순 쿠폰: 비동기 발급 요청 ──

    public CouponIssueRequestInfo requestCouponIssue(Long couponId, Long memberId) {
        Coupon coupon = couponRepository.findById(couponId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "쿠폰을 찾을 수 없습니다."));
        ZonedDateTime now = ZonedDateTime.now(clock);
        if (now.isAfter(coupon.getExpiredAt())) {
            throw new CoreException(ErrorType.BAD_REQUEST, "만료된 쿠폰은 발급 요청할 수 없습니다.");
        }

        Long requestId = couponIssueRequestRedisRepository.nextId();
        couponIssueRequestRedisRepository.save(requestId, couponId, memberId, "PENDING", null);

        try {
            Map<String, Object> payload = Map.of(
                "requestId", requestId,
                "couponId", couponId,
                "memberId", memberId
            );
            String json = objectMapper.writeValueAsString(payload);
            kafkaTemplate.send("coupon-issue-requests", String.valueOf(couponId), json);
        } catch (Exception e) {
            log.error("쿠폰 발급 요청 Kafka 전송 실패: requestId={}", requestId, e);
            couponIssueRequestRedisRepository.save(requestId, couponId, memberId, "REJECTED", "Kafka 전송 실패");
            throw new CoreException(ErrorType.INTERNAL_ERROR, "쿠폰 발급 요청에 실패했습니다.");
        }

        return new CouponIssueRequestInfo(requestId, couponId, memberId, CouponIssueRequestStatus.PENDING, null);
    }

    public CouponIssueRequestInfo getIssueRequest(Long requestId) {
        return couponIssueRequestRedisRepository.findById(requestId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "발급 요청을 찾을 수 없습니다."));
    }

    public ZonedDateTime now() {
        return ZonedDateTime.now(clock);
    }
}

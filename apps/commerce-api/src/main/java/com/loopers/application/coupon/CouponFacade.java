package com.loopers.application.coupon;

import com.loopers.domain.coupon.Coupon;
import com.loopers.domain.coupon.CouponIssueMessage;
import com.loopers.domain.coupon.CouponIssueRequest;
import com.loopers.domain.coupon.CouponIssueRequestRepository;
import com.loopers.domain.coupon.CouponService;
import com.loopers.domain.coupon.CouponType;
import com.loopers.domain.coupon.UserCoupon;
import com.loopers.domain.coupon.UserCouponService;
import com.loopers.domain.outbox.OutboxEventTopics;
import com.loopers.domain.users.UserService;
import com.loopers.domain.users.Users;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import java.time.ZonedDateTime;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class CouponFacade {

    private final CouponService couponService;
    private final UserCouponService userCouponService;
    private final UserService userService;
    private final CouponIssueRequestRepository couponIssueRequestRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    // ─────────────────────────────────────────────
    // Admin: 쿠폰 템플릿 CRUD (FR-1~4)
    // ─────────────────────────────────────────────

    public CouponInfo createCoupon(String name, String type, int value, int minOrderAmount, ZonedDateTime expiredAt) {
        CouponType couponType = CouponType.valueOf(type);
        Coupon coupon = couponService.createCoupon(name, couponType, value, minOrderAmount, expiredAt);
        return CouponInfo.from(coupon);
    }

    public CouponInfo createCoupon(String name, String type, int value, int minOrderAmount, ZonedDateTime expiredAt, Integer maxIssuable) {
        CouponType couponType = CouponType.valueOf(type);
        Coupon coupon = couponService.createCoupon(name, couponType, value, minOrderAmount, expiredAt, maxIssuable);
        return CouponInfo.from(coupon);
    }

    public CouponInfo updateCoupon(Long couponId, String name, String requestedType, Integer requestedValue, int minOrderAmount, ZonedDateTime expiredAt) {
        CouponType parsedType = requestedType != null ? CouponType.valueOf(requestedType) : null;
        Coupon coupon = couponService.updateCoupon(couponId, name, parsedType, requestedValue, minOrderAmount, expiredAt);
        return CouponInfo.from(coupon);
    }

    public void deleteCoupon(Long couponId) {
        couponService.deleteCoupon(couponId);
    }

    public CouponInfo getCoupon(Long couponId) {
        return CouponInfo.from(couponService.getCoupon(couponId));
    }

    public Page<CouponInfo> getCoupons(int page, int size) {
        return couponService.getCoupons(PageRequest.of(page, size))
            .map(CouponInfo::from);
    }

    // ─────────────────────────────────────────────
    // Admin: 발급 내역 조회 (FR-5)
    // ─────────────────────────────────────────────

    public Page<UserCouponInfo> getIssuedCoupons(Long couponId, int page, int size) {
        couponService.getCoupon(couponId); // validate coupon exists
        return userCouponService.getIssuedCoupons(couponId, PageRequest.of(page, size))
            .map(UserCouponInfo::from);
    }

    // ─────────────────────────────────────────────
    // Customer: 쿠폰 발급 (FR-6)
    // ─────────────────────────────────────────────

    public UserCouponInfo issueCoupon(String loginId, String password, Long couponId) {
        Users user = userService.authenticate(loginId, password);
        Coupon coupon = couponService.getCouponForIssue(couponId);
        UserCoupon userCoupon = userCouponService.issueCoupon(user.getId(), coupon);
        return UserCouponInfo.from(userCoupon);
    }

    // ─────────────────────────────────────────────
    // Customer: 선착순 쿠폰 비동기 발급 요청 (FR-8)
    // ─────────────────────────────────────────────

    public CouponIssueRequestInfo requestIssueAsync(String loginId, String password, Long couponId) {
        Users user = userService.authenticate(loginId, password);
        couponService.getCouponForIssue(couponId); // 쿠폰 존재 + 삭제 여부 검증

        CouponIssueRequest request = couponIssueRequestRepository.save(
            CouponIssueRequest.create(couponId, user.getId())
        );

        // TODO [Q3-B]: 요청 저장을 Redis로 전환 검토.
        //   기준: coupon_issue_requests 테이블 10M+ 행 초과 또는 상태 조회 P99 > 50ms 초과 시.
        //   주의: Redis 전환 시 장애 복구(영속성) 전략 별도 설계 필요 (TTL 기반 만료 처리 등).

        kafkaTemplate.send(OutboxEventTopics.COUPON_ISSUE, couponId.toString(),
            new CouponIssueMessage(request.getRequestId(), couponId, user.getId()));

        // TODO: Kafka 발행 실패 시 PENDING 상태가 영구 유지됨.
        //   개선: @Scheduled 릴레이가 PENDING 상태 N분 초과 요청을 재발행하도록 추가 필요.

        return CouponIssueRequestInfo.from(request);
    }

    public CouponIssueRequestInfo getIssueRequestStatus(String loginId, String password, String requestId) {
        Users user = userService.authenticate(loginId, password);
        CouponIssueRequest request = couponIssueRequestRepository.findByRequestId(requestId)
            .orElseThrow(() -> new CoreException(ErrorType.COUPON_ISSUE_REQUEST_NOT_FOUND));

        // TODO [Q1-B]: 폴링 → WebSocket/SSE 전환 검토.
        //   기준: 발급 처리 시간 P99 > 3초 또는 폴링 요청이 TPS 500 초과 시.
        //   전환 방법: Spring WebSocket + STOMP 또는 SSE(SseEmitter) 사용.
        //   테스트: 1,000명 동시 발급 시나리오에서 폴링 횟수 측정 → 평균 3회 미만이면 폴링 유지.

        if (!request.getUserId().equals(user.getId())) {
            throw new CoreException(ErrorType.FORBIDDEN);
        }
        return CouponIssueRequestInfo.from(request);
    }

    // ─────────────────────────────────────────────
    // Customer: 내 쿠폰 목록 조회 (FR-7)
    // ─────────────────────────────────────────────

    public Page<UserCouponInfo> getMyCoupons(String loginId, String password, int page, int size) {
        Users user = userService.authenticate(loginId, password);
        return userCouponService.getMyCoupons(user.getId(), PageRequest.of(page, size))
            .map(UserCouponInfo::from);
    }
}

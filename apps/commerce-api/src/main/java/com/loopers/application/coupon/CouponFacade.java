package com.loopers.application.coupon;

import com.loopers.domain.coupon.CouponIssueRequestEvent;
import com.loopers.domain.coupon.CouponIssueResult;
import com.loopers.domain.coupon.CouponIssueResultRepository;
import com.loopers.domain.coupon.CouponService;
import com.loopers.domain.coupon.CouponStockRepository;
import com.loopers.domain.coupon.CouponTemplate;
import com.loopers.domain.coupon.UserCoupon;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@RequiredArgsConstructor
@Component
public class CouponFacade {

    private final CouponService couponService;
    private final CouponIssueResultRepository couponIssueResultRepository;
    private final CouponStockRepository couponStockRepository;
    private final ApplicationEventPublisher eventPublisher;

    // 쿠폰 발급 (US-C01)
    @Transactional
    public UserCouponInfo issue(Long userId, Long couponTemplateId) {
        UserCoupon savedUserCoupon = couponService.issue(userId, couponTemplateId);
        return UserCouponInfo.from(savedUserCoupon, LocalDateTime.now());
    }

    /**
     * 선착순 쿠폰 발급 요청 (비동기).
     * 1) 만료 검증 → 2) Redis Lua 원자 연산 (중복 + 재고) → 3) PENDING 생성 → 4) Outbox 이벤트 발행.
     * 실제 발급은 Consumer가 처리한다.
     */
    @Transactional
    public CouponIssueResultInfo requestIssue(Long userId, Long couponTemplateId) {
        // 만료 사전 필터
        CouponTemplate template = couponService.findTemplateById(couponTemplateId);
        template.validateNotExpired(LocalDateTime.now());

        // Redis Lua 원자 연산: 중복 체크(SETNX) + 조건부 재고 차감을 1회 호출로 처리
        if (template.getMaxIssueCount() != null) {
            long result = couponStockRepository.tryIssueRequest(couponTemplateId, userId);
            if (result == -2) {
                throw new CoreException(ErrorType.CONFLICT, "이미 발급 요청된 쿠폰입니다.");
            }
            if (result == -1) {
                throw new CoreException(ErrorType.BAD_REQUEST, "쿠폰이 모두 소진되었습니다.");
            }
        }

        // PENDING 상태로 발급 결과 생성
        CouponIssueResult result = couponIssueResultRepository.save(new CouponIssueResult(couponTemplateId, userId));

        // Outbox 경유 Kafka 이벤트 발행 (BEFORE_COMMIT 리스너가 처리)
        eventPublisher.publishEvent(new CouponIssueRequestEvent(result.getId(), couponTemplateId, userId));

        return CouponIssueResultInfo.from(result);
    }

    // 발급 결과 조회 (Polling)
    @Transactional(readOnly = true)
    public CouponIssueResultInfo findIssueResult(Long userId, Long resultId) {
        CouponIssueResult result = couponIssueResultRepository.findByIdAndUserId(resultId, userId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "존재하지 않는 발급 요청입니다."));
        return CouponIssueResultInfo.from(result);
    }

    /**
     * 내 쿠폰 목록 조회 (US-C02)
     * 쿠폰의 상태는 조회 시각 기준으로 계산한다 (BR-C04).
     * expiredAt은 UserCoupon에 스냅샷되어 있으므로 템플릿 조회 불필요.
     */
    @Transactional(readOnly = true)
    public List<UserCouponInfo> findMyIssuedCoupons(Long userId) {
        LocalDateTime now = LocalDateTime.now();
        return couponService.findAllIssuedByUserId(userId).stream()
                .map(userCoupon -> UserCouponInfo.from(userCoupon, now))
                .toList();
    }
}

package com.loopers.application.user;

import com.loopers.domain.coupon.CouponModel;
import com.loopers.domain.coupon.CouponService;
import com.loopers.domain.coupon.SignupCouponPolicy;
import com.loopers.domain.user.UserService;
import com.loopers.domain.user.event.UserSignedUpEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class SignupBenefitHandler {

    private final UserService userService;
    private final CouponService couponService;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(UserSignedUpEvent event) {
        try {
            userService.addPoint(event.userId(), 1000L);
            CouponModel welcomeCoupon = couponService.register(
                    SignupCouponPolicy.WELCOME.toCreateCommand());
            couponService.issue(welcomeCoupon.getId(), event.userId());
        } catch (Exception e) {
            log.warn("[SignupBenefit] 혜택 지급 실패 — userId={}", event.userId(), e);
        }
    }
}

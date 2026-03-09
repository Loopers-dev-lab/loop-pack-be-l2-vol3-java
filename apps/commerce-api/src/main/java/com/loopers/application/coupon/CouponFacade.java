package com.loopers.application.coupon;

import com.loopers.application.coupon.dto.FindMyCouponResDto;
import com.loopers.application.coupon.dto.IssueCouponResDto;
import com.loopers.domain.coupon.model.UserCoupon;
import com.loopers.domain.coupon.model.UserCouponItem;
import com.loopers.domain.coupon.service.CouponService;
import com.loopers.domain.member.model.Member;
import com.loopers.domain.member.service.MemberService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Component
@Transactional(readOnly = true)
public class CouponFacade {

    private final CouponService couponService;
    private final MemberService memberService;

    @Transactional(rollbackFor = Exception.class)
    public IssueCouponResDto issueCoupon(String loginId, String password, Long couponTemplateId) {
        Member member = memberService.findMember(loginId, password);
        UserCoupon userCoupon = couponService.issueCoupon(couponTemplateId, member.getId());
        return IssueCouponResDto.from(userCoupon);
    }

    public Page<FindMyCouponResDto> getMyCoupons(String loginId, String password, Pageable pageable) {
        Member member = memberService.findMember(loginId, password);
        Page<UserCouponItem> items = couponService.getUserCouponsWithTemplate(member.getId(), pageable);
        return items.map(FindMyCouponResDto::from);
    }
}

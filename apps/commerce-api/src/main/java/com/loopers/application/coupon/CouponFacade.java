package com.loopers.application.coupon;

import com.loopers.application.coupon.dto.CouponIssueRequestResDto;
import com.loopers.application.coupon.dto.FindMyCouponResDto;
import com.loopers.application.coupon.dto.IssueCouponResDto;
import com.loopers.domain.coupon.model.CouponIssueRequest;
import com.loopers.domain.coupon.model.FirstComeCoupon;
import com.loopers.domain.coupon.model.UserCoupon;
import com.loopers.domain.coupon.model.UserCouponItem;
import com.loopers.domain.coupon.repository.CouponIssueRequestRepository;
import com.loopers.domain.coupon.service.CouponService;
import com.loopers.domain.coupon.service.FirstComeCouponService;
import com.loopers.domain.member.model.Member;
import com.loopers.domain.member.service.MemberService;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@RequiredArgsConstructor
@Component
@Transactional(readOnly = true)
public class CouponFacade {

    private final CouponService couponService;
    private final MemberService memberService;
    private final FirstComeCouponService firstComeCouponService;
    private final CouponIssueRequestRepository couponIssueRequestRepository;
    private final KafkaTemplate<Object, Object> kafkaTemplate;

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

    @Transactional(rollbackFor = Exception.class)
    public CouponIssueRequestResDto requestFirstComeIssue(String loginId, String password, Long couponTemplateId) {
        Member member = memberService.findMember(loginId, password);
        FirstComeCoupon fcCoupon = firstComeCouponService.getByTemplateId(couponTemplateId);

        firstComeCouponService.addToQueue(fcCoupon, member.getId());
        try {
            CouponIssueRequest request = couponIssueRequestRepository.save(
                    CouponIssueRequest.create(couponTemplateId, member.getId()));

            kafkaTemplate.send("coupon-issue-requests",
                    String.valueOf(couponTemplateId),
                    Map.of("requestId", request.getId(),
                            "couponTemplateId", couponTemplateId,
                            "memberId", member.getId()));

            return CouponIssueRequestResDto.from(request);
        } catch (Exception e) {
            firstComeCouponService.removeFromQueue(couponTemplateId, member.getId());
            throw e;
        }
    }

    public CouponIssueRequestResDto getIssueRequestStatus(String loginId, String password, Long requestId) {
        Member member = memberService.findMember(loginId, password);
        CouponIssueRequest request = couponIssueRequestRepository.findById(requestId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "존재하지 않는 발급 요청입니다."));
        if (!request.getMemberId().equals(member.getId())) {
            throw new CoreException(ErrorType.NOT_FOUND, "존재하지 않는 발급 요청입니다.");
        }
        return CouponIssueRequestResDto.from(request);
    }
}

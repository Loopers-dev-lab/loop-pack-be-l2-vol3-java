package com.loopers.application.coupon;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.application.OutboxEventHelper;
import com.loopers.domain.coupon.Coupon;
import com.loopers.domain.coupon.CouponIssueRequest;
import com.loopers.domain.coupon.CouponIssueRequestRepository;
import com.loopers.domain.coupon.CouponService;
import com.loopers.domain.coupon.CouponTemplate;
import com.loopers.domain.coupon.CouponTemplateService;
import com.loopers.domain.outbox.OutboxEvent;
import com.loopers.domain.outbox.OutboxEventRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorMessage;
import com.loopers.support.error.ErrorType;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CouponFacade {

    private final CouponTemplateService couponTemplateService;
    private final CouponService couponService;
    private final CouponIssueRequestRepository couponIssueRequestRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public CouponTemplateInfo createCouponTemplate(CreateCouponTemplateCommand command) {
        CouponTemplate template = couponTemplateService.create(
            command.name(), command.discountType(), command.discountValue(), command.minOrderAmount(), command.expiredAt(), command.maxIssuanceCount()
        );
        return CouponTemplateInfo.from(template);
    }

    @Transactional
    public CouponTemplateInfo updateCouponTemplate(UpdateCouponTemplateCommand command) {
        CouponTemplate template = couponTemplateService.update(
            command.couponId(), command.name(), command.discountType(), command.discountValue(), command.minOrderAmount(), command.expiredAt()
        );
        return CouponTemplateInfo.from(template);
    }

    @Transactional
    public void deleteCouponTemplate(Long couponId) {
        couponTemplateService.delete(couponId);
    }

    @Transactional(readOnly = true)
    public CouponTemplateInfo getCouponTemplate(Long couponId) {
        CouponTemplate template = couponTemplateService.getById(couponId);
        return CouponTemplateInfo.from(template);
    }

    @Transactional(readOnly = true)
    public List<CouponTemplateInfo> getCouponTemplates(int page, int size) {
        return couponTemplateService.findAll(page, size).stream()
            .map(CouponTemplateInfo::from)
            .toList();
    }

    @Transactional
    public CouponInfo issueCoupon(Long userId, Long templateId) {
        CouponTemplate template = couponTemplateService.getById(templateId);
        Coupon coupon = couponService.issue(userId, template);
        return CouponInfo.from(coupon);
    }

    @Transactional(readOnly = true)
    public List<CouponInfo> getMyCoupons(Long userId) {
        return couponService.findByUserId(userId).stream()
            .map(CouponInfo::from)
            .toList();
    }

    @Transactional
    public CouponIssueRequestInfo requestCouponIssue(Long userId, Long templateId) {
        couponTemplateService.getById(templateId); // 템플릿 존재 여부 확인

        CouponIssueRequest request = CouponIssueRequest.create(userId, templateId);
        CouponIssueRequest saved = couponIssueRequestRepository.save(request);

        outboxEventRepository.save(OutboxEvent.create(
            "coupon-issue-requests",
            OutboxEventHelper.toJson(objectMapper, Map.of(
                "requestId", saved.getId(),
                "userId", userId,
                "templateId", templateId
            )),
            String.valueOf(templateId)
        ));

        return CouponIssueRequestInfo.from(saved);
    }

    @Transactional(readOnly = true)
    public CouponIssueRequestInfo getCouponIssueRequest(Long userId, Long requestId) {
        CouponIssueRequest request = couponIssueRequestRepository.findById(requestId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, ErrorMessage.Coupon.COUPON_ISSUE_REQUEST_NOT_FOUND));

        if (!request.getUserId().equals(userId)) {
            throw new CoreException(ErrorType.NOT_FOUND, ErrorMessage.Coupon.COUPON_ISSUE_REQUEST_NOT_FOUND);
        }

        return CouponIssueRequestInfo.from(request);
    }
}

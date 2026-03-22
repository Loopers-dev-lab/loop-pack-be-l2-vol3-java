package com.loopers.domain.coupon;

import com.loopers.domain.eventhandled.EventHandledModel;
import com.loopers.domain.eventhandled.EventHandledRepository;
import com.loopers.infrastructure.coupon.CouponIssueRequestJpaRepository;
import com.loopers.infrastructure.coupon.CouponTemplateJpaRepository;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CouponIssueService {

    private static final String TOPIC = "coupon-issue-requests";

    private final JPAQueryFactory queryFactory;
    private final CouponTemplateJpaRepository couponTemplateJpaRepository;
    private final CouponIssueRequestJpaRepository couponIssueRequestJpaRepository;
    private final UserCouponRepository userCouponRepository;
    private final EventHandledRepository eventHandledRepository;

    @Transactional
    public void processIssue(String eventId, String requestId, Long couponTemplateId, Long memberId) {
        if (eventHandledRepository.existsByEventId(eventId)) {
            return;
        }

        CouponIssueRequestModel request = couponIssueRequestJpaRepository.findByRequestId(requestId)
                .orElseThrow(() -> new IllegalStateException("발급 요청을 찾을 수 없습니다: " + requestId));

        if (userCouponRepository.existsByRefMemberIdAndRefCouponTemplateId(memberId, couponTemplateId)) {
            request.markAsRejected();
            eventHandledRepository.save(EventHandledModel.create(eventId, TOPIC));
            return;
        }

        QCouponTemplateModel template = QCouponTemplateModel.couponTemplateModel;
        long affected = queryFactory
                .update(template)
                .set(template.issuedCount, template.issuedCount.add(1))
                .where(template.id.eq(couponTemplateId)
                        .and(template.totalQuantity.isNull()
                                .or(template.issuedCount.lt(template.totalQuantity))))
                .execute();

        if (affected > 0) {
            userCouponRepository.save(UserCouponModel.create(memberId, couponTemplateId));
            request.markAsIssued();
        } else {
            request.markAsRejected();
        }

        eventHandledRepository.save(EventHandledModel.create(eventId, TOPIC));
    }
}

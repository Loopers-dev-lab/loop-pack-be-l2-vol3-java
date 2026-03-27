package com.loopers.infrastructure.coupon;

import com.loopers.domain.coupon.CouponTemplateRepository;
import com.loopers.domain.coupon.QCouponTemplateModel;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class CouponTemplateRepositoryImpl implements CouponTemplateRepository {

    private final JPAQueryFactory queryFactory;

    @Override
    public long decreaseStockIfAvailable(Long couponTemplateId) {
        QCouponTemplateModel template = QCouponTemplateModel.couponTemplateModel;
        return queryFactory
                .update(template)
                .set(template.issuedCount, template.issuedCount.add(1))
                .where(template.id.eq(couponTemplateId)
                        .and(template.totalQuantity.isNull()
                                .or(template.issuedCount.lt(template.totalQuantity))))
                .execute();
    }
}

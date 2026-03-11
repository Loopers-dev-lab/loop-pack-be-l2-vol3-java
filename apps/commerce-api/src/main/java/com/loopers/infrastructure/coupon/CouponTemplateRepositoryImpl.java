package com.loopers.infrastructure.coupon;

import static com.loopers.infrastructure.coupon.QCouponTemplateEntity.couponTemplateEntity;

import com.loopers.domain.coupon.CouponSearchCondition;
import com.loopers.domain.coupon.CouponTemplate;
import com.loopers.domain.coupon.CouponTemplateRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorMessage;
import com.loopers.support.error.ErrorType;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CouponTemplateRepositoryImpl implements CouponTemplateRepository {

    private final CouponTemplateJpaRepository couponTemplateJpaRepository;
    private final JPAQueryFactory queryFactory;

    @Override
    public CouponTemplate save(CouponTemplate template) {
        CouponTemplateEntity entity = CouponTemplateEntity.create(template);
        return couponTemplateJpaRepository.save(entity).toDomain();
    }

    @Override
    public Optional<CouponTemplate> findById(Long id) {
        return couponTemplateJpaRepository.findById(id).map(CouponTemplateEntity::toDomain);
    }

    @Override
    public List<CouponTemplate> findAll(CouponSearchCondition condition) {
        return queryFactory
            .selectFrom(couponTemplateEntity)
            .orderBy(couponTemplateEntity.createdAt.desc())
            .offset((long) condition.page() * condition.size())
            .limit(condition.size())
            .fetch()
            .stream()
            .map(CouponTemplateEntity::toDomain)
            .toList();
    }

    @Override
    public CouponTemplate update(CouponTemplate template) {
        CouponTemplateEntity entity = couponTemplateJpaRepository.findById(template.getId())
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, ErrorMessage.Coupon.COUPON_TEMPLATE_NOT_FOUND));
        entity.updateFrom(template);
        return couponTemplateJpaRepository.save(entity).toDomain();
    }

    @Override
    public void delete(Long id) {
        if (!couponTemplateJpaRepository.existsById(id)) {
            throw new CoreException(ErrorType.NOT_FOUND, ErrorMessage.Coupon.COUPON_TEMPLATE_NOT_FOUND);
        }
        couponTemplateJpaRepository.deleteById(id);
    }
}

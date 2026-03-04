package com.loopers.infrastructure.coupon.repository.impl;

import com.loopers.domain.coupon.model.CouponTemplate;
import com.loopers.domain.coupon.repository.CouponTemplateRepository;
import com.loopers.infrastructure.coupon.entity.CouponTemplateEntity;
import com.loopers.infrastructure.coupon.repository.CouponTemplateJpaRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.Optional;

@RequiredArgsConstructor
@Component
public class CouponTemplateRepositoryImpl implements CouponTemplateRepository {

    private final CouponTemplateJpaRepository jpaRepository;

    @Override
    public CouponTemplate save(CouponTemplate couponTemplate) {
        CouponTemplateEntity entity = jpaRepository.save(CouponTemplateEntity.toEntity(couponTemplate));
        return entity.toModel();
    }

    @Override
    public Optional<CouponTemplate> findById(Long id) {
        return jpaRepository.findById(id).map(CouponTemplateEntity::toModel);
    }

    @Override
    public Page<CouponTemplate> findAll(Pageable pageable) {
        return jpaRepository.findAll(pageable).map(CouponTemplateEntity::toModel);
    }

    @Override
    public void update(CouponTemplate couponTemplate) {
        CouponTemplateEntity entity = jpaRepository.findById(couponTemplate.getId())
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "존재하지 않는 쿠폰 템플릿입니다."));

        entity.update(
                couponTemplate.getName().value(),
                couponTemplate.getType(),
                couponTemplate.getDiscountValue().value(),
                couponTemplate.getMinOrderAmount().value(),
                couponTemplate.getExpiredAt()
        );
    }

    @Override
    public void deleteById(Long id) {
        CouponTemplateEntity entity = jpaRepository.findById(id)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "존재하지 않는 쿠폰 템플릿입니다."));
        entity.delete();
    }
}

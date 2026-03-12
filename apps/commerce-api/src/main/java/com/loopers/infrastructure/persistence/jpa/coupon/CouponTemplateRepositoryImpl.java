package com.loopers.infrastructure.persistence.jpa.coupon;

import com.loopers.domain.coupon.CouponTemplate;
import com.loopers.domain.coupon.CouponTemplateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * CouponTemplateRepository 구현체.
 */
@Repository
@RequiredArgsConstructor
public class CouponTemplateRepositoryImpl implements CouponTemplateRepository {

    private final CouponTemplateJpaRepository jpaRepository;

    @Override
    public CouponTemplate save(CouponTemplate template) {
        CouponTemplateJpaEntity entity;

        if (template.getId() == null) {
            entity = CouponTemplateMapper.toJpaEntity(template);
        } else {
            entity = jpaRepository.findById(template.getId())
                .orElseGet(() -> CouponTemplateMapper.toJpaEntity(template));
            CouponTemplateMapper.updateJpaEntity(entity, template);
        }

        CouponTemplateJpaEntity saved = jpaRepository.save(entity);
        return CouponTemplateMapper.toDomain(saved);
    }

    @Override
    public Optional<CouponTemplate> findById(Long id) {
        return jpaRepository.findById(id)
            .map(CouponTemplateMapper::toDomain);
    }

    @Override
    public Optional<CouponTemplate> findByIdActive(Long id) {
        return jpaRepository.findByIdAndDeletedAtIsNull(id)
            .map(CouponTemplateMapper::toDomain);
    }

    @Override
    public Optional<CouponTemplate> findByIdWithLock(Long id) {
        return jpaRepository.findByIdWithLock(id)
            .map(CouponTemplateMapper::toDomain);
    }

    @Override
    public Page<CouponTemplate> findAllActive(Pageable pageable) {
        Page<CouponTemplateJpaEntity> page = jpaRepository.findAllByDeletedAtIsNull(pageable);
        return new PageImpl<>(
            page.getContent().stream().map(CouponTemplateMapper::toDomain).toList(),
            pageable,
            page.getTotalElements()
        );
    }
}

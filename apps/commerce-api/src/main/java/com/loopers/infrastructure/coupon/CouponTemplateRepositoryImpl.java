package com.loopers.infrastructure.coupon;

import com.loopers.domain.coupon.CouponTemplate;
import com.loopers.domain.coupon.CouponTemplateRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Repository
public class CouponTemplateRepositoryImpl implements CouponTemplateRepository {

    private final CouponTemplateJpaRepository couponTemplateJpaRepository;
    private final CouponTemplateMapper couponTemplateMapper;

    public CouponTemplateRepositoryImpl(CouponTemplateJpaRepository couponTemplateJpaRepository,
                                         CouponTemplateMapper couponTemplateMapper) {
        this.couponTemplateJpaRepository = couponTemplateJpaRepository;
        this.couponTemplateMapper = couponTemplateMapper;
    }

    @Override
    public CouponTemplate save(CouponTemplate couponTemplate) {
        CouponTemplateEntity entity = couponTemplateMapper.toEntity(couponTemplate);
        CouponTemplateEntity saved = couponTemplateJpaRepository.save(entity);
        return couponTemplateMapper.toDomain(saved);
    }

    @Override
    public Optional<CouponTemplate> findById(Long id) {
        return couponTemplateJpaRepository.findById(id)
                .map(couponTemplateMapper::toDomain);
    }

    @Override
    public Optional<CouponTemplate> findByIdForUpdate(Long id) {
        return couponTemplateJpaRepository.findByIdForUpdate(id)
                .map(couponTemplateMapper::toDomain);
    }

    @Override
    public List<CouponTemplate> findAllByIdIn(Set<Long> ids) {
        return couponTemplateJpaRepository.findAllByIdIn(ids).stream()
                .map(couponTemplateMapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public List<CouponTemplate> findAll(int page, int size) {
        return couponTemplateJpaRepository.findAll(
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))
        ).getContent()
                .stream()
                .map(couponTemplateMapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public List<CouponTemplate> findAllIssuable() {
        return couponTemplateJpaRepository.findAllIssuable(
                com.loopers.domain.coupon.CouponTemplateStatus.ACTIVE,
                java.time.ZonedDateTime.now()
        ).stream()
                .map(couponTemplateMapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public long count() {
        return couponTemplateJpaRepository.count();
    }
}

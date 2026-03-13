package com.loopers.infrastructure.coupon;

import com.loopers.domain.coupon.IssuedCoupon;
import com.loopers.domain.coupon.IssuedCouponRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
@Component
public class IssuedCouponRepositoryImpl implements IssuedCouponRepository {

    private final IssuedCouponJpaRepository issuedCouponJpaRepository;

    @Override
    public IssuedCoupon save(IssuedCoupon issuedCoupon) {
        return issuedCouponJpaRepository.save(issuedCoupon);
    }

    @Override
    public Optional<IssuedCoupon> findById(Long id) {
        return issuedCouponJpaRepository.findById(id);
    }

    @Override
    public Optional<IssuedCoupon> findByIdForUpdate(Long id) {
        return issuedCouponJpaRepository.findByIdForUpdate(id);
    }

    @Override
    public List<IssuedCoupon> findByMemberId(Long memberId) {
        return issuedCouponJpaRepository.findByMemberId(memberId);
    }

    @Override
    public boolean existsByMemberIdAndCouponTemplateId(Long memberId, Long couponTemplateId) {
        return issuedCouponJpaRepository.existsByMemberIdAndCouponTemplateId(memberId, couponTemplateId);
    }

    @Override
    public List<IssuedCoupon> findByCouponTemplateId(Long couponTemplateId, int page, int size) {
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return issuedCouponJpaRepository.findByCouponTemplateId(couponTemplateId, pageRequest);
    }

    @Override
    public long countByCouponTemplateId(Long couponTemplateId) {
        return issuedCouponJpaRepository.countByCouponTemplateId(couponTemplateId);
    }
}

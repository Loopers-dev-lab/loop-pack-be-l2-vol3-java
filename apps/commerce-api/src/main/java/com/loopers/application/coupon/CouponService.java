package com.loopers.application.coupon;

import com.loopers.domain.coupon.Coupon;
import com.loopers.domain.coupon.CouponRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CouponService {

    private final CouponRepository couponRepository;

    // Command

    @Transactional
    public Coupon register(CouponCommand.Register command) {
        Coupon coupon = Coupon.create(
                command.name(),
                command.type(),
                command.value(),
                command.minOrderAmount(),
                command.maxIssueCount(),
                command.expiredAt()
        );
        return couponRepository.save(coupon);
    }

    @Transactional
    public void delete(Long id) {
        Coupon coupon = couponRepository.findById(id)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "존재하지 않는 쿠폰입니다"));
        coupon.validateNotDeleted();
        coupon.delete();
    }

    @Transactional
    public Coupon issue(Long id) {
        Coupon coupon = couponRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "존재하지 않는 쿠폰입니다"));
        coupon.validateNotDeleted();
        coupon.issue();
        return coupon;
    }

    @Transactional
    public Coupon updateInfo(Long id, CouponCommand.UpdateInfo command) {
        Coupon coupon = couponRepository.findById(id)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "존재하지 않는 쿠폰입니다"));
        coupon.updateInfo(command.name(), command.value(), command.minOrderAmount(),
                command.maxIssueCount(), command.expiredAt());
        return coupon;
    }

    // Query

    @Transactional(readOnly = true)
    public Coupon getActiveCoupon(Long id) {
        Coupon coupon = couponRepository.findById(id)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "존재하지 않는 쿠폰입니다"));
        coupon.validateNotDeleted();
        return coupon;
    }

    @Transactional(readOnly = true)
    public List<Coupon> findAllByIds(List<Long> ids) {
        return couponRepository.findAllByIds(ids);
    }

    @Transactional(readOnly = true)
    public Page<Coupon> findActiveCoupons(Pageable pageable) {
        return couponRepository.findAllActive(pageable);
    }
}

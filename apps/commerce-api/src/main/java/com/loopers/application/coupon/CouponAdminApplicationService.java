package com.loopers.application.coupon;

import com.loopers.application.coupon.command.CreateCouponCommand;
import com.loopers.application.coupon.command.UpdateCouponCommand;
import com.loopers.application.coupon.view.CouponIssueView;
import com.loopers.domain.coupon.Coupon;
import com.loopers.domain.coupon.CouponRepository;
import com.loopers.domain.coupon.IssuedCouponRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CouponAdminApplicationService {

    private final CouponRepository couponRepository;
    private final IssuedCouponRepository issuedCouponRepository;

    @Transactional(readOnly = true)
    public Page<Coupon> list(Pageable pageable) {
        return couponRepository.findAll(pageable);
    }

    @Transactional(readOnly = true)
    public Coupon findById(UUID couponId) {
        return couponRepository.findById(couponId)
                .orElseThrow(() -> new CoreException(ErrorType.BAD_REQUEST, "쿠폰을 찾을 수 없습니다."));
    }

    @Transactional
    public Coupon create(CreateCouponCommand command) {
        Coupon coupon = new Coupon(
                command.name(),
                command.type(),
                command.value(),
                command.minOrderAmount(),
                command.totalQuantity(),
                command.expiredAt()
        );
        return couponRepository.save(coupon);
    }

    @Transactional
    public Coupon update(UUID couponId, UpdateCouponCommand command) {
        Coupon coupon = couponRepository.findById(couponId)
                .orElseThrow(() -> new CoreException(ErrorType.BAD_REQUEST, "쿠폰을 찾을 수 없습니다."));
        Coupon updated = coupon.update(
                command.name(),
                command.type(),
                command.value(),
                command.minOrderAmount(),
                command.totalQuantity(),
                command.expiredAt()
        );
        return couponRepository.save(updated);
    }

    @Transactional
    public void delete(UUID couponId) {
        Coupon coupon = couponRepository.findById(couponId)
                .orElseThrow(() -> new CoreException(ErrorType.BAD_REQUEST, "쿠폰을 찾을 수 없습니다."));
        couponRepository.delete(coupon);
    }

    @Transactional(readOnly = true)
    public Page<CouponIssueView> listIssues(UUID couponId, Pageable pageable) {
        couponRepository.findById(couponId)
                .orElseThrow(() -> new CoreException(ErrorType.BAD_REQUEST, "쿠폰을 찾을 수 없습니다."));
        return issuedCouponRepository.findByCouponId(couponId, pageable).map(CouponIssueView::from);
    }
}

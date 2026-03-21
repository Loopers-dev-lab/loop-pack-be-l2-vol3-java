package com.loopers.application.coupon;

import com.loopers.application.coupon.command.UseCouponCommand;
import com.loopers.application.coupon.view.MyCouponView;
import com.loopers.domain.coupon.Coupon;
import com.loopers.domain.coupon.CouponRepository;
import com.loopers.domain.coupon.CouponStatus;
import com.loopers.domain.coupon.IssuedCoupon;
import com.loopers.domain.coupon.IssuedCouponRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CouponApplicationService {

    private final CouponRepository couponRepository;
    private final IssuedCouponRepository issuedCouponRepository;

    @Transactional
    public void issue(UUID couponId, String memberId) {
        Coupon coupon = couponRepository.findById(couponId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "쿠폰을 찾을 수 없습니다."));

        if (!coupon.isUsableAt(LocalDateTime.now())) {
            throw new CoreException(ErrorType.BAD_REQUEST, "만료된 쿠폰은 발급할 수 없습니다.");
        }

        IssuedCoupon issuedCoupon = new IssuedCoupon(
                memberId,
                couponId,
                CouponStatus.AVAILABLE,
                LocalDateTime.now(),
                coupon.expiredAt(),
                null
        );

        try {
            issuedCouponRepository.save(issuedCoupon);
        } catch (DataIntegrityViolationException e) {
            throw new CoreException(ErrorType.CONFLICT, "이미 발급된 쿠폰입니다.");
        }
    }

    @Transactional(readOnly = true)
    public List<MyCouponView> listMyCoupons(String memberId) {
        List<IssuedCoupon> issuedCoupons = issuedCouponRepository.findByMemberId(memberId);
        if (issuedCoupons.isEmpty()) {
            return List.of();
        }

        List<UUID> couponIds = issuedCoupons.stream().map(IssuedCoupon::couponId).distinct().toList();
        Map<UUID, Coupon> couponsById = couponRepository.findAllMapByIdIn(couponIds);
        LocalDateTime now = LocalDateTime.now();

        return issuedCoupons.stream()
                .map(issued -> {
                    Coupon coupon = couponsById.get(issued.couponId());
                    if (coupon == null) {
                        throw new CoreException(ErrorType.NOT_FOUND, "쿠폰을 찾을 수 없습니다.");
                    }
                    return new MyCouponView(
                            issued.couponId(),
                            coupon.name(),
                            coupon.type(),
                            coupon.value(),
                            coupon.minOrderAmount(),
                            issued.expiredAt(),
                            issued.resolveStatusAt(now)
                    );
                })
                .toList();
    }

    @Transactional
    public void use(UseCouponCommand command) {
        IssuedCoupon issuedCoupon = issuedCouponRepository.findByMemberIdAndCouponId(command.memberId(), command.couponId())
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "발급된 쿠폰을 찾을 수 없습니다."));

        Coupon coupon = couponRepository.findById(command.couponId())
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "쿠폰을 찾을 수 없습니다."));

        issuedCoupon.validateOwner(command.memberId());
        if (command.orderAmount() < coupon.minOrderAmount()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "최소 주문 금액 미달로 쿠폰을 사용할 수 없습니다.");
        }

        int updatedCount = issuedCouponRepository.markUsedAtomically(command.memberId(), command.couponId(), LocalDateTime.now());
        if (updatedCount == 0) {
            throw new CoreException(ErrorType.CONFLICT, "이미 사용되었거나 만료된 쿠폰입니다.");
        }
    }

    @Transactional
    public void cancelUse(UUID couponId, String memberId) {
        IssuedCoupon issuedCoupon = issuedCouponRepository.findByMemberIdAndCouponId(memberId, couponId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "발급된 쿠폰을 찾을 수 없습니다."));

        issuedCoupon.validateOwner(memberId);
        int updatedCount = issuedCouponRepository.markAvailableAtomically(memberId, couponId);
        if (updatedCount == 0) {
            throw new CoreException(ErrorType.CONFLICT, "사용 취소할 수 없는 쿠폰입니다.");
        }
    }

    @Transactional(readOnly = true)
    public int calculateDiscount(UUID couponId, int orderAmount) {
        Coupon coupon = couponRepository.findById(couponId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "쿠폰을 찾을 수 없습니다."));
        return coupon.calculateDiscount(orderAmount);
    }
}

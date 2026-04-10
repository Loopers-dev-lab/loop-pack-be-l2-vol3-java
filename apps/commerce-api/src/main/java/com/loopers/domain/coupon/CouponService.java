package com.loopers.domain.coupon;

import com.loopers.domain.coupon.dto.CouponCommand;
import com.loopers.support.error.CoreException;
import java.time.ZonedDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CouponService {

    private final CouponRepository couponRepository;
    private final OwnedCouponRepository ownedCouponRepository;

    @Transactional
    public CouponModel register(CouponCommand.Create command) {
        return couponRepository.save(CouponModel.create(
                command.name(), command.discountType(), command.discountValue(),
                command.minOrderAmount(), command.totalQuantity(), command.expiredAt()));
    }

    @Transactional(readOnly = true)
    public CouponModel getById(Long id) {
        return couponRepository.findById(id)
                .orElseThrow(() -> new CoreException(CouponErrorCode.NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public Page<CouponModel> getAll(Pageable pageable) {
        return couponRepository.findAll(pageable);
    }

    @Transactional
    public void update(Long id, CouponCommand.Update command) {
        getById(id).update(command.name(), command.expiredAt());
    }

    @Transactional
    public void delete(Long id) {
        getById(id).delete();
    }

    @Transactional(readOnly = true)
    public Page<OwnedCouponModel> getIssuedCoupons(Long couponId, Pageable pageable) {
        return ownedCouponRepository.findAllByCouponId(couponId, pageable);
    }

    @Transactional
    public OwnedCouponModel issue(Long couponId, Long userId) {
        CouponModel coupon = couponRepository.findById(couponId)
                .orElseThrow(() -> new CoreException(CouponErrorCode.NOT_FOUND));
        ownedCouponRepository.findByCouponIdAndUserId(couponId, userId)
                .ifPresent(owned -> {
                    throw new CoreException(CouponErrorCode.ALREADY_ISSUED);
                });
        coupon.issue();
        return ownedCouponRepository.save(OwnedCouponModel.create(coupon, userId));
    }

    @Transactional
    public long useAndCalculateDiscount(Long ownedCouponId, Long userId, Long orderId,
                                        long orderAmount) {
        OwnedCouponModel owned = ownedCouponRepository.findById(ownedCouponId)
                .orElseThrow(() -> new CoreException(CouponErrorCode.NOT_FOUND));
        owned.validateMinOrderAmount(orderAmount);
        owned.validateUsable(userId);
        int updated = ownedCouponRepository.useByIdWhenAvailable(
                ownedCouponId, orderId, ZonedDateTime.now());
        if (updated == 0) {
            throw new CoreException(CouponErrorCode.ALREADY_USED);
        }
        return owned.calculateDiscount(orderAmount);
    }

    @Transactional
    public void restoreByOrderId(Long orderId) {
        ownedCouponRepository.findByOrderId(orderId)
                .ifPresent(OwnedCouponModel::restore);
    }

    @Transactional(readOnly = true)
    public List<OwnedCouponModel> getMyOwnedCoupons(Long userId) {
        return ownedCouponRepository.findAllByUserId(userId);
    }
}

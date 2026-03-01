package com.loopers.application.coupon;

import java.time.ZonedDateTime;

import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;

import com.loopers.application.shared.annotation.UseCase;
import com.loopers.domain.coupon.Coupon;
import com.loopers.domain.coupon.CouponType;
import com.loopers.domain.coupon.OwnedCoupon;
import com.loopers.domain.coupon.OwnedCouponRepository;
import com.loopers.domain.coupon.OwnedCouponStatus;
import com.loopers.support.page.Page;
import com.loopers.support.page.PageSize;

import lombok.RequiredArgsConstructor;

@UseCase
@RequiredArgsConstructor
public class ReadMyOwnedCouponsUseCase {

    private final OwnedCouponRepository ownedCouponRepository;

    @Transactional(readOnly = true)
    public Page<Result> execute(Long userId, PageSize pageSize) {
        Slice<OwnedCoupon> ownedCoupons = ownedCouponRepository.findAllByUserId(
                userId,
                pageSize.toPageable(Sort.by(Sort.Direction.DESC, "createdAt"))
        );

        return new Page<>(
                ownedCoupons.getContent().stream()
                        .map(Result::from)
                        .toList(),
                ownedCoupons.hasNext()
        );
    }

    public record Result(
            Long id,
            OwnedCouponStatus status,
            ZonedDateTime createdAt,
            String name,
            CouponType couponType,
            Long discountValue,
            Long maxDiscountPrice,
            Long minOrderPrice,
            ZonedDateTime expiredAt
    ) {

        public static Result from(OwnedCoupon ownedCoupon) {
            Coupon coupon = ownedCoupon.getCoupon();
            return new Result(
                    ownedCoupon.getId(),
                    ownedCoupon.getStatus(),
                    ownedCoupon.getCreatedAt(),
                    coupon.getName().getValue(),
                    coupon.getType(),
                    coupon.getDiscountValue(),
                    coupon.getMaxDiscountPriceAmount(),
                    coupon.getMinOrderPrice().getAmount(),
                    coupon.getExpiredAt()
            );
        }
    }
}
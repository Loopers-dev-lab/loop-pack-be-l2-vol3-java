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

/**
 * 사용자가 본인의 보유 쿠폰 목록을 조회합니다.
 *
 * <p>생성일 기준 최신순으로 페이징 조회한다.</p>
 */
@UseCase
@RequiredArgsConstructor
public class ReadMyOwnedCouponsUseCase {

    private final OwnedCouponRepository ownedCouponRepository;

    /**
     * @param userId   사용자 ID
     * @param pageSize 페이징 조건
     * @return 보유 쿠폰 목록 페이지
     */
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
            ZonedDateTime expiredAt,
            ZonedDateTime usedAt
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
                    coupon.getExpiredAt(),
                    ownedCoupon.getUsedAt()
            );
        }
    }
}

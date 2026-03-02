package com.loopers.application.coupon;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;

import com.loopers.application.shared.annotation.UseCase;
import com.loopers.domain.coupon.Coupon;
import com.loopers.domain.coupon.CouponType;
import com.loopers.domain.coupon.OwnedCoupon;
import com.loopers.domain.coupon.OwnedCouponRepository;
import com.loopers.domain.coupon.OwnedCouponStatus;
import com.loopers.domain.user.User;
import com.loopers.domain.user.UserRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.support.page.Page;
import com.loopers.support.page.PageSize;

import lombok.RequiredArgsConstructor;

/**
 * 어드민이 특정 쿠폰의 발급 내역을 조회합니다.
 *
 * <p>발급 내역에 사용자 정보를 포함하여 반환한다.</p>
 */
@UseCase
@RequiredArgsConstructor
public class ReadOwnedCouponsUseCase {

    private final OwnedCouponRepository ownedCouponRepository;
    private final UserRepository userRepository;

    /**
     * @param couponId 쿠폰 ID
     * @param pageSize 페이징 조건
     * @return 발급 내역 페이지
     */
    @Transactional(readOnly = true)
    public Page<Result> execute(Long couponId, PageSize pageSize) {
        Slice<OwnedCoupon> ownedCoupons = ownedCouponRepository.findAllByCouponId(
                couponId,
                pageSize.toPageable(Sort.by(Sort.Direction.DESC, "createdAt"))
        );

        List<Long> userIds = ownedCoupons.getContent().stream()
                .map(OwnedCoupon::getUserId)
                .distinct()
                .toList();
        Map<Long, User> users = userRepository.findAllByIdIn(userIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));

        return new Page<>(
                ownedCoupons.getContent().stream()
                        .map(ownedCoupon -> {
                            User user = users.get(ownedCoupon.getUserId());
                            if (user == null) {
                                throw new CoreException(ErrorType.USER_NOT_FOUND);
                            }
                            return Result.from(ownedCoupon, user);
                        })
                        .toList(),
                ownedCoupons.hasNext()
        );
    }

    public record Result(
            Long id,
            OwnedCouponStatus status,
            ZonedDateTime createdAt,
            Long userId,
            String loginId,
            String userName,
            String name,
            CouponType couponType,
            Long discountValue,
            Long maxDiscountPrice,
            Long minOrderPrice,
            ZonedDateTime expiredAt,
            ZonedDateTime usedAt
    ) {

        public static Result from(OwnedCoupon ownedCoupon, User user) {
            Coupon coupon = ownedCoupon.getCoupon();
            return new Result(
                    ownedCoupon.getId(),
                    ownedCoupon.getStatus(),
                    ownedCoupon.getCreatedAt(),
                    ownedCoupon.getUserId(),
                    user.getLoginId().getValue(),
                    user.getName().getValue(),
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

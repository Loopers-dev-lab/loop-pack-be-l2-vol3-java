package com.loopers.application.coupon;

import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;

import com.loopers.application.shared.annotation.UseCase;
import com.loopers.domain.coupon.Coupon;
import com.loopers.domain.coupon.CouponRepository;
import com.loopers.support.page.Page;
import com.loopers.support.page.PageSize;

import lombok.RequiredArgsConstructor;

/**
 * 어드민이 쿠폰 목록을 조회합니다.
 *
 * <p>삭제된 쿠폰을 포함한 전체 목록을 최신 등록순으로 페이징하여 반환합니다.</p>
 */
@UseCase
@RequiredArgsConstructor
public class ReadCouponsUseCase {

    private final CouponRepository couponRepository;

    /**
     * @param pageSize 페이지 크기
     * @return 쿠폰 목록 페이지 (최신순)
     */
    @Transactional(readOnly = true)
    public Page<CouponResult> execute(PageSize pageSize) {
        Slice<Coupon> coupons = couponRepository.findAllBy(
                pageSize.toPageable(Sort.by(Sort.Direction.DESC, "createdAt"))
        );
        return new Page<>(coupons.getContent(), coupons.hasNext())
                .map(CouponResult::from);
    }
}

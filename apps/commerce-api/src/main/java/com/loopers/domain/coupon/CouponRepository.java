package com.loopers.domain.coupon;

import com.loopers.support.page.PageQuery;
import com.loopers.support.page.PagedResult;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 쿠폰 템플릿 레포지토리 인터페이스 (도메인 레이어).
 */
public interface CouponRepository {

    Optional<CouponModel> findById(Long couponId);

    Optional<CouponModel> findByIdWithLock(Long couponId);

    CouponModel save(CouponModel coupon);

    List<CouponModel> findAll();

    List<CouponModel> findAllByIdIn(Collection<Long> ids);

    PagedResult<CouponModel> findAllPaged(PageQuery query);
}

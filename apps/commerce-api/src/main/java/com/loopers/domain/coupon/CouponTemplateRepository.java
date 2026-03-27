package com.loopers.domain.coupon;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface CouponTemplateRepository {

    CouponTemplate save(CouponTemplate template);

    Optional<CouponTemplate> findById(Long id);

    Page<CouponTemplate> findAll(Pageable pageable);

    // 배치 조회 — N+1 방지 (내 쿠폰 목록 조회 시 템플릿 expiredAt 일괄 로드)
    List<CouponTemplate> findAllByIds(Collection<Long> ids);

    // 수량 제한 쿠폰 템플릿 조회 (보정 스케줄러용)
    List<CouponTemplate> findAllWithMaxIssueCount();
}

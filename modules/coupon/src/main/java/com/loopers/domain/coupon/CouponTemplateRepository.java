package com.loopers.domain.coupon;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;

/**
 * 쿠폰 템플릿 영속성 인터페이스.
 */
public interface CouponTemplateRepository {

    Optional<CouponTemplateModel> findById(Long id);

    Optional<CouponTemplateModel> findByIdAndNotDeleted(Long id);

    /** 선착순·발급 직렬화용 비관적 락 */
    Optional<CouponTemplateModel> findByIdAndNotDeletedForUpdate(Long id);

    Page<CouponTemplateModel> findNotDeleted(Pageable pageable);

    /** 조회 전용 프로젝션. 트래픽·연관관계 고려 시 사용. */
    Page<CouponTemplateProjection> findNotDeletedAsProjection(Pageable pageable);

    Optional<CouponTemplateProjection> findByIdAndNotDeletedAsProjection(Long id);

    CouponTemplateModel save(CouponTemplateModel template);
}

package com.loopers.domain.coupon;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * 쿠폰 도메인 서비스.
 * 발급, 내 쿠폰 목록, 주문 시 유효성 검증 및 사용 처리(1회 사용).
 */
@Service
public class CouponService {

    private final CouponTemplateRepository couponTemplateRepository;
    private final IssuedCouponRepository issuedCouponRepository;

    public CouponService(CouponTemplateRepository couponTemplateRepository,
                         IssuedCouponRepository issuedCouponRepository) {
        this.couponTemplateRepository = couponTemplateRepository;
        this.issuedCouponRepository = issuedCouponRepository;
    }

    /**
     * 쿠폰을 발급한다. 동일 사용자·동일 템플릿 중복 발급은 허용한다(요구사항에 1인 1장 제한 없음).
     * 템플릿이 없거나 삭제/만료 시 예외.
     */
    @Transactional
    public IssuedCouponModel issue(Long userId, Long couponTemplateId) {
        CouponTemplateModel template = couponTemplateRepository.findByIdAndNotDeleted(couponTemplateId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "쿠폰을 찾을 수 없습니다."));
        if (template.isExpired(ZonedDateTime.now())) {
            throw new CoreException(ErrorType.BAD_REQUEST, "만료된 쿠폰은 발급할 수 없습니다.");
        }
        IssuedCouponModel issued = IssuedCouponModel.issue(userId, couponTemplateId, template.getExpiredAt());
        return issuedCouponRepository.save(issued);
    }

    /**
     * 사용자별 발급 쿠폰 목록. 상태(AVAILABLE/USED/EXPIRED)는 엔티티 기준으로 판단.
     */
    @Transactional(readOnly = true)
    public Page<IssuedCouponModel> findByUserId(Long userId, Pageable pageable) {
        return issuedCouponRepository.findByUserId(userId, pageable);
    }

    /** 조회 전용: 프로젝션으로 내 쿠폰 목록 반환. */
    @Transactional(readOnly = true)
    public Page<IssuedCouponProjection> findByUserIdAsProjection(Long userId, Pageable pageable) {
        return issuedCouponRepository.findByUserIdAsProjection(userId, pageable);
    }

    /**
     * 주문 시 쿠폰 유효성 검증 후 사용 처리. 낙관적 락(@Version)으로 동시 사용 방지.
     * 일반 SELECT로 조회 후 상태를 USED로 변경하고, 커밋 시점에 JPA가 버전을 비교한다. 다른 트랜잭션이 먼저 사용했으면 OptimisticLockException으로 전체 롤백. (05-transaction-query §3.1)
     * 소유자 불일치·이미 사용·만료·최소 주문 금액 미충족 시 예외.
     *
     * @return 할인 결과(할인 전/할인액/최종 금액). 호출 측에서 주문 스냅샷에 반영.
     */
    @Transactional
    public CouponDiscount validateAndUse(Long issuedCouponId, Long userId, java.math.BigDecimal orderAmountBeforeDiscount) {
        IssuedCouponModel issued = issuedCouponRepository.findById(issuedCouponId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "쿠폰을 찾을 수 없습니다."));
        if (!issued.getUserId().equals(userId)) {
            throw new CoreException(ErrorType.NOT_FOUND, "쿠폰을 찾을 수 없습니다.");
        }
        CouponTemplateModel template = couponTemplateRepository.findByIdAndNotDeleted(issued.getCouponId())
                .orElseThrow(() -> new CoreException(ErrorType.BAD_REQUEST, "해당 쿠폰은 더 이상 사용할 수 없습니다."));
        ZonedDateTime now = ZonedDateTime.now();
        CouponDiscount discount = issued.use(
                orderAmountBeforeDiscount,
                template.getMinOrderAmount(),
                template.getType(),
                template.getValue(),
                now);
        issuedCouponRepository.save(issued);
        return discount;
    }

    @Transactional(readOnly = true)
    public Optional<CouponTemplateModel> findTemplateById(Long id) {
        return couponTemplateRepository.findById(id);
    }

    @Transactional(readOnly = true)
    public Optional<CouponTemplateModel> findTemplateByIdAndNotDeleted(Long id) {
        return couponTemplateRepository.findByIdAndNotDeleted(id);
    }

    /** 조회 전용: 단건 템플릿 프로젝션. */
    @Transactional(readOnly = true)
    public Optional<CouponTemplateProjection> findTemplateByIdAndNotDeletedAsProjection(Long id) {
        return couponTemplateRepository.findByIdAndNotDeletedAsProjection(id);
    }

    @Transactional(readOnly = true)
    public Page<CouponTemplateModel> findTemplatesNotDeleted(Pageable pageable) {
        return couponTemplateRepository.findNotDeleted(pageable);
    }

    /** 조회 전용: 프로젝션으로 템플릿 목록 반환. */
    @Transactional(readOnly = true)
    public Page<CouponTemplateProjection> findTemplatesNotDeletedAsProjection(Pageable pageable) {
        return couponTemplateRepository.findNotDeletedAsProjection(pageable);
    }

    @Transactional
    public CouponTemplateModel persistTemplate(CouponTemplateModel template) {
        return couponTemplateRepository.save(template);
    }

    @Transactional(readOnly = true)
    public Page<IssuedCouponModel> findByCouponId(Long couponId, Pageable pageable) {
        return issuedCouponRepository.findByCouponId(couponId, pageable);
    }

    /** 조회 전용: 프로젝션으로 발급 이력 반환. */
    @Transactional(readOnly = true)
    public Page<IssuedCouponProjection> findByCouponIdAsProjection(Long couponId, Pageable pageable) {
        return issuedCouponRepository.findByCouponIdAsProjection(couponId, pageable);
    }
}

package com.loopers.domain.coupon;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.OptimisticLockException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.Optional;

/**
 * 쿠폰 도메인 서비스.
 * 발급, 내 쿠폰 목록, 주문 시 유효성 검증 및 사용 처리(1회 사용).
 */
@Service
public class CouponService {

    private final CouponTemplateRepository couponTemplateRepository;
    private final IssuedCouponRepository issuedCouponRepository;
    private final CouponIssueRequestRepository couponIssueRequestRepository;
    /**
     * REQUIRES_NEW 전파가 적용된 프록시 자신.
     * validateAndUse → selfProxy().validateAndUseInNewTransaction(...) 호출로 트랜잭션 경계를 분리한다.
     * 필드 주입(@Lazy)로 순환 의존을 피하고, 단위 테스트(new CouponService(...))에서는 null 이므로 this를 사용한다.
     */
    @Lazy
    @Autowired(required = false)
    private CouponService self;

    public CouponService(CouponTemplateRepository couponTemplateRepository,
                         IssuedCouponRepository issuedCouponRepository,
                         CouponIssueRequestRepository couponIssueRequestRepository) {
        this.couponTemplateRepository = couponTemplateRepository;
        this.issuedCouponRepository = issuedCouponRepository;
        this.couponIssueRequestRepository = couponIssueRequestRepository;
    }

    private CouponService selfProxy() {
        return self != null ? self : this;
    }

    /**
     * 쿠폰을 동기 발급한다. 동일 사용자·동일 템플릿 중복 발급은 불가.
     * 템플릿이 없거나 삭제/만료·선착순 소진 시 예외.
     */
    @Transactional
    public IssuedCouponModel issue(Long userId, Long couponTemplateId) {
        CouponTemplateModel template = couponTemplateRepository.findByIdAndNotDeletedForUpdate(couponTemplateId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "쿠폰을 찾을 수 없습니다."));
        ZonedDateTime now = ZonedDateTime.now();
        if (template.isExpired(now)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "만료된 쿠폰은 발급할 수 없습니다.");
        }
        if (issuedCouponRepository.existsByUserIdAndCouponId(userId, couponTemplateId)) {
            throw new CoreException(ErrorType.CONFLICT, "이미 발급받은 쿠폰입니다.");
        }
        if (template.isSoldOut()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "쿠폰이 모두 소진되었습니다.");
        }
        IssuedCouponModel issued = IssuedCouponModel.issue(userId, couponTemplateId, template.getExpiredAt());
        IssuedCouponModel saved = saveIssuedCouponOrConflict(issued);
        template.incrementIssuedCountAfterSuccessfulIssue();
        couponTemplateRepository.save(template);
        return saved;
    }

    /**
     * 비동기 쿠폰 발급: 요청 행이 있으면 상태를 갱신하고, 없으면 레거시 메시지로 간주해 {@link #issueIfAbsent}만 수행한다.
     */
    @Transactional
    public void processCouponIssueRequest(String requestId, Long userId, Long couponTemplateId) {
        Optional<CouponIssueRequestModel> row = couponIssueRequestRepository.findByRequestId(requestId);
        if (row.isEmpty()) {
            issueIfAbsent(userId, couponTemplateId);
            return;
        }
        CouponIssueRequestModel req = row.get();
        if (!req.isPending()) {
            return;
        }
        CouponTemplateModel template = couponTemplateRepository.findByIdAndNotDeletedForUpdate(couponTemplateId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "쿠폰을 찾을 수 없습니다."));
        ZonedDateTime now = ZonedDateTime.now();
        if (template.isExpired(now)) {
            req.markRejected(CouponIssueRequestStatus.REJECTED_EXPIRED);
            couponIssueRequestRepository.save(req);
            return;
        }
        if (issuedCouponRepository.existsByUserIdAndCouponId(userId, couponTemplateId)) {
            req.markRejected(CouponIssueRequestStatus.REJECTED_DUPLICATE);
            couponIssueRequestRepository.save(req);
            return;
        }
        if (template.isSoldOut()) {
            req.markRejected(CouponIssueRequestStatus.REJECTED_SOLD_OUT);
            couponIssueRequestRepository.save(req);
            return;
        }
        IssuedCouponModel issued = IssuedCouponModel.issue(userId, couponTemplateId, template.getExpiredAt());
        IssuedCouponModel saved = saveIssuedCouponOrConflict(issued);
        template.incrementIssuedCountAfterSuccessfulIssue();
        couponTemplateRepository.save(template);
        req.markIssued(saved.getId());
        couponIssueRequestRepository.save(req);
    }

    /**
     * 레거시 Kafka 페이로드(요청 행 없음)용. 락·중복·선착순을 반영한다.
     */
    @Transactional
    public void issueIfAbsent(Long userId, Long couponTemplateId) {
        CouponTemplateModel template = couponTemplateRepository.findByIdAndNotDeletedForUpdate(couponTemplateId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "쿠폰을 찾을 수 없습니다."));
        ZonedDateTime now = ZonedDateTime.now();
        if (template.isExpired(now)) {
            return;
        }
        if (issuedCouponRepository.existsByUserIdAndCouponId(userId, couponTemplateId)) {
            return;
        }
        if (template.isSoldOut()) {
            return;
        }
        IssuedCouponModel issued = IssuedCouponModel.issue(userId, couponTemplateId, template.getExpiredAt());
        saveIssuedCouponOrConflict(issued);
        template.incrementIssuedCountAfterSuccessfulIssue();
        couponTemplateRepository.save(template);
    }

    private IssuedCouponModel saveIssuedCouponOrConflict(IssuedCouponModel issued) {
        try {
            return issuedCouponRepository.save(issued);
        } catch (DataIntegrityViolationException e) {
            throw new CoreException(ErrorType.CONFLICT, "이미 발급받은 쿠폰입니다.", e);
        }
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
     * 내부적으로 REQUIRES_NEW 트랜잭션에서 1회 재시도하며, 모두 실패 시 CONFLICT(409)로 변환한다. (05-transaction-query §3.1, §3.4, §9.1)
     * 소유자 불일치·이미 사용·만료·최소 주문 금액 미충족 시 예외.
     *
     * @return 할인 결과(할인 전/할인액/최종 금액). 호출 측에서 주문 스냅샷에 반영.
     */
    public CouponDiscount validateAndUse(Long issuedCouponId, Long userId, java.math.BigDecimal orderAmountBeforeDiscount) {
        final int maxAttempts = 2;
        final long backoffMs = 50L;

        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            try {
                // REQUIRES_NEW 전파가 적용된 프록시를 통해 호출해야 부모 트랜잭션이 rollback-only로 마킹되지 않는다.
                return selfProxy().validateAndUseInNewTransaction(issuedCouponId, userId, orderAmountBeforeDiscount);
            } catch (OptimisticLockException | ObjectOptimisticLockingFailureException e) {
                if (attempt == maxAttempts - 1) {
                    throw new CoreException(ErrorType.CONFLICT, "잠시 후 다시 시도해 주세요.", e);
                }
                try {
                    Thread.sleep(backoffMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new CoreException(ErrorType.INTERNAL_ERROR, "재시도 대기 중 중단되었습니다.", ie);
                }
            }
        }
        throw new IllegalStateException("unreachable");
    }

    /**
     * REQUIRES_NEW 트랜잭션에서 쿠폰을 한 번 사용 처리한다.
     * 낙관락 충돌 시 OptimisticLockException이 발생하며, 호출 측에서 재시도 여부를 결정한다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    CouponDiscount validateAndUseInNewTransaction(Long issuedCouponId,
                                                  Long userId,
                                                  java.math.BigDecimal orderAmountBeforeDiscount) {
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

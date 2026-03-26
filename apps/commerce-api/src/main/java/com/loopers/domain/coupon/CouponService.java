package com.loopers.domain.coupon;

import com.loopers.support.enums.DiscountType;
import com.loopers.support.enums.UserCouponStatus;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.support.page.PageQuery;
import com.loopers.support.page.PagedResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

/**
 * 쿠폰 도메인 서비스.
 * <p>
 * 쿠폰 템플릿 CRUD, 사용자 쿠폰 발급/사용 비즈니스 로직을 담당한다.
 * 쿠폰 발급 시 비관적 락(SELECT FOR UPDATE)을 사용하여 중복 발급을 방지한다.
 * </p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CouponService {

    private final CouponRepository couponRepository;
    private final UserCouponRepository userCouponRepository;

    // ============================
    // 관리자용 메서드
    // ============================

    /**
     * 쿠폰 템플릿을 생성한다.
     */
    @Transactional
    public CouponModel createCoupon(String name, DiscountType discountType, BigDecimal discountValue,
                                     BigDecimal minOrderAmount, LocalDateTime expiredAt) {
        CouponModel coupon = CouponModel.create(name, discountType, discountValue, minOrderAmount, expiredAt);
        return couponRepository.save(coupon);
    }

    /**
     * 쿠폰 목록을 조회한다 (관리자 — 삭제된 쿠폰 포함).
     */
    public List<CouponModel> findAllForAdmin() {
        return couponRepository.findAll();
    }

    /**
     * 쿠폰 상세를 조회한다 (관리자 — 삭제된 쿠폰 포함).
     *
     * @throws CoreException 쿠폰이 없는 경우 COUPON_NOT_FOUND
     */
    public CouponModel findByIdForAdmin(Long couponId) {
        return couponRepository.findById(couponId)
                .orElseThrow(() -> new CoreException(ErrorType.COUPON_NOT_FOUND));
    }

    /**
     * 쿠폰 정보를 수정한다.
     *
     * @throws CoreException 쿠폰이 없는 경우 COUPON_NOT_FOUND
     */
    @Transactional
    public CouponModel updateCoupon(Long couponId, String name, DiscountType discountType,
                                     BigDecimal discountValue, BigDecimal minOrderAmount,
                                     LocalDateTime expiredAt) {
        CouponModel coupon = couponRepository.findById(couponId)
                .orElseThrow(() -> new CoreException(ErrorType.COUPON_NOT_FOUND));
        coupon.updateInfo(name, discountType, discountValue, minOrderAmount, expiredAt);
        return couponRepository.save(coupon);
    }

    /**
     * 쿠폰을 소프트 삭제한다.
     *
     * @throws CoreException 쿠폰이 없는 경우 COUPON_NOT_FOUND
     */
    @Transactional
    public void deleteCoupon(Long couponId) {
        CouponModel coupon = couponRepository.findById(couponId)
                .orElseThrow(() -> new CoreException(ErrorType.COUPON_NOT_FOUND));
        coupon.softDelete();
        couponRepository.save(coupon);
    }

    /**
     * 특정 쿠폰의 발급 내역을 조회한다 (관리자).
     */
    public List<UserCouponModel> findIssueHistory(Long couponId) {
        return userCouponRepository.findAllByCouponId(couponId);
    }

    /**
     * 관리자용 쿠폰 목록을 페이징 조회한다.
     */
    public PagedResult<CouponModel> findAllForAdminPaged(PageQuery query) {
        return couponRepository.findAllPaged(query);
    }

    /**
     * 특정 쿠폰의 발급 내역을 페이징 조회한다 (관리자).
     */
    public PagedResult<UserCouponModel> findIssueHistoryPaged(Long couponId, PageQuery query) {
        return userCouponRepository.findAllByCouponIdPaged(couponId, query);
    }

    // ============================
    // 대고객 메서드
    // ============================

    /**
     * 쿠폰을 발급한다.
     * <p>
     * 비관적 락을 사용하여 동시 발급 요청 시 중복 발급을 방지한다.
     * DB UNIQUE KEY(user_id, coupon_id)가 최후 안전망으로 작동한다.
     * </p>
     *
     * @throws CoreException 쿠폰 없음(COUPON_NOT_FOUND), 만료/삭제(COUPON_NOT_APPLICABLE),
     *                       중복 발급(COUPON_ALREADY_ISSUED)
     */
    @Transactional
    public UserCouponModel issueCoupon(Long userId, Long couponId) {
        CouponModel coupon = couponRepository.findByIdWithLock(couponId)
                .orElseThrow(() -> new CoreException(ErrorType.COUPON_NOT_FOUND));

        coupon.validateApplicable(null);

        if (userCouponRepository.existsByUserIdAndCouponId(userId, couponId)) {
            throw new CoreException(ErrorType.COUPON_ALREADY_ISSUED);
        }

        UserCouponModel userCoupon = UserCouponModel.create(userId, couponId);
        return userCouponRepository.save(userCoupon);
    }

    /**
     * 내 쿠폰 목록을 조회한다 (AVAILABLE/USED/EXPIRED 모두 포함).
     */
    public List<UserCouponModel> findMyCoupons(Long userId) {
        return userCouponRepository.findAllByUserId(userId);
    }

    // ============================
    // OrderFacade 내부용 메서드
    // ============================

    /**
     * 유효한 발급 쿠폰을 조회한다.
     * <p>
     * 비관적 락으로 조회하여 동시 주문 시 쿠폰 중복 사용을 방지한다.
     * </p>
     *
     * @param userId       쿠폰 소유자 ID
     * @param userCouponId 발급 쿠폰 ID
     * @return 조회된 UserCouponModel (AVAILABLE 상태)
     * @throws CoreException 쿠폰 없음/타 유저 소유(USER_COUPON_NOT_FOUND),
     *                       사용 불가(COUPON_NOT_AVAILABLE)
     */
    @Transactional
    public UserCouponModel validateAndGetUserCoupon(Long userId, Long userCouponId) {
        UserCouponModel userCoupon = userCouponRepository.findByIdWithLock(userCouponId)
                .filter(uc -> uc.getUserId().equals(userId))
                .orElseThrow(() -> new CoreException(ErrorType.USER_COUPON_NOT_FOUND));

        if (!userCoupon.isAvailable()) {
            throw new CoreException(ErrorType.COUPON_NOT_AVAILABLE);
        }
        return userCoupon;
    }

    /**
     * 쿠폰을 사용 완료 처리한다. 반드시 상위 트랜잭션 내에서 호출해야 한다.
     *
     * @param userCouponId 발급 쿠폰 ID
     * @param orderId      사용된 주문 ID
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void markCouponAsUsed(Long userCouponId, Long orderId) {
        UserCouponModel userCoupon = userCouponRepository.findByIdWithLock(userCouponId)
                .orElseThrow(() -> new CoreException(ErrorType.USER_COUPON_NOT_FOUND));
        userCoupon.markAsUsed(orderId);
        userCouponRepository.save(userCoupon);
    }

    /**
     * ID 목록으로 쿠폰 템플릿을 일괄 조회한다 (N+1 방지).
     */
    public List<CouponModel> findAllByIds(Collection<Long> couponIds) {
        return couponRepository.findAllByIdIn(couponIds);
    }

    /**
     * 주문 취소/만료 시 사용된 쿠폰을 복원한다 (멱등).
     * 반드시 상위 트랜잭션 내에서 호출해야 한다.
     *
     * @param orderId 주문 ID
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void restoreCoupon(Long orderId) {
        userCouponRepository.findByOrderId(orderId)
                .ifPresent(UserCouponModel::restoreToAvailable);
    }

    // ============================
    // CAS 기반 쿠폰 상태 전이 메서드
    // ============================

    /**
     * 쿠폰을 AVAILABLE → RESERVED 로 CAS 선점한다.
     * 반드시 상위 트랜잭션(주문 생성 TX) 내에서 호출해야 한다.
     *
     * @param userCouponId 발급 쿠폰 ID
     * @throws CoreException CAS 실패 시 COUPON_NOT_AVAILABLE
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void reserveCoupon(Long userCouponId) {
        int updated = userCouponRepository.updateStatusCas(
                userCouponId, UserCouponStatus.AVAILABLE, UserCouponStatus.RESERVED);
        if (updated == 0) {
            throw new CoreException(ErrorType.COUPON_NOT_AVAILABLE);
        }
    }

    /**
     * 쿠폰을 RESERVED → USED 로 확정한다 (Relay 처리).
     *
     * @param userCouponId 발급 쿠폰 ID
     * @param orderId      사용된 주문 ID
     * @throws CoreException CAS 실패 시 COUPON_NOT_AVAILABLE
     */
    @Transactional
    public void confirmCouponUsed(Long userCouponId, Long orderId) {
        int updated = userCouponRepository.updateStatusCas(
                userCouponId, UserCouponStatus.RESERVED, UserCouponStatus.USED);
        if (updated == 0) {
            throw new CoreException(ErrorType.COUPON_NOT_AVAILABLE);
        }
        userCouponRepository.findById(userCouponId).ifPresent(uc -> {
            uc.markAsUsedWithOrder(orderId);
            userCouponRepository.save(uc);
        });
    }

    /**
     * 쿠폰을 RESERVED → AVAILABLE 로 복원한다 (Relay 처리).
     * RESERVED 상태가 아닌 경우 USED → AVAILABLE 로도 시도한다.
     *
     * @param userCouponId 발급 쿠폰 ID
     */
    @Transactional
    public void restoreCouponByAction(Long userCouponId) {
        int updated = userCouponRepository.updateStatusCas(
                userCouponId, UserCouponStatus.RESERVED, UserCouponStatus.AVAILABLE);
        if (updated == 0) {
            // RESERVED가 아닌 경우 USED → AVAILABLE 시도
            userCouponRepository.updateStatusCas(
                    userCouponId, UserCouponStatus.USED, UserCouponStatus.AVAILABLE);
        }
        userCouponRepository.findById(userCouponId).ifPresent(uc -> {
            uc.clearOrderInfo();
            userCouponRepository.save(uc);
        });
    }
}

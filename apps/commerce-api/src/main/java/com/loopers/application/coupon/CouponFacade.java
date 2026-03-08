package com.loopers.application.coupon;

import com.loopers.domain.coupon.Coupon;
import com.loopers.domain.coupon.CouponService;
import com.loopers.domain.coupon.CouponIssue;
import com.loopers.domain.coupon.CouponIssueRepository;
import com.loopers.domain.coupon.CouponRepository;
import com.loopers.domain.coupon.CouponType;
import com.loopers.domain.user.UserRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CouponFacade {

    private final CouponRepository couponRepository;
    private final CouponIssueRepository couponIssueRepository;
    private final UserRepository userRepository;
    private final CouponService couponService;

    // ─────────────────────────────────────────
    // Admin: 쿠폰 템플릿 관리
    // ─────────────────────────────────────────

    @Transactional
    public CouponDto.CouponInfo registerTemplate(String name, String description, CouponType type, BigDecimal discountValue, ZonedDateTime expiredAt) {
        Coupon coupon = Coupon.create(name, description, type, discountValue, expiredAt);
        return CouponDto.CouponInfo.from(couponRepository.save(coupon));
    }

    @Transactional(readOnly = true)
    public Page<CouponDto.CouponInfo> getTemplates(Pageable pageable) {
        return couponRepository.findAll(pageable)
            .map(CouponDto.CouponInfo::from);
    }

    @Transactional(readOnly = true)
    public CouponDto.CouponInfo getTemplate(Long couponId) {
        Coupon coupon = couponRepository.findById(couponId)
            .orElseThrow(() -> new CoreException(ErrorType.COUPON_NOT_FOUND));
        return CouponDto.CouponInfo.from(coupon);
    }

    @Transactional
    public CouponDto.CouponInfo updateTemplate(Long couponId, String name, String description, CouponType type, BigDecimal discountValue, ZonedDateTime expiredAt) {
        Coupon coupon = couponRepository.findById(couponId)
            .orElseThrow(() -> new CoreException(ErrorType.COUPON_NOT_FOUND));
        coupon.update(name, description, type, discountValue, expiredAt);
        return CouponDto.CouponInfo.from(couponRepository.save(coupon));
    }

    @Transactional
    public void deleteTemplate(Long couponId) {
        Coupon coupon = couponRepository.findById(couponId)
            .orElseThrow(() -> new CoreException(ErrorType.COUPON_NOT_FOUND));
        coupon.delete();
        couponRepository.save(coupon);
    }

    @Transactional(readOnly = true)
    public Page<CouponDto.CouponIssueInfo> getIssues(Long couponId, Pageable pageable) {
        couponRepository.findById(couponId)
            .orElseThrow(() -> new CoreException(ErrorType.COUPON_NOT_FOUND));
        return couponIssueRepository.findByCouponId(couponId, pageable)
            .map(CouponDto.CouponIssueInfo::from);
    }

    // ─────────────────────────────────────────
    // User: 쿠폰 발급 / 목록 조회
    // ─────────────────────────────────────────

    // @Transactional 미적용 의도:
    // insert-first 패턴에서 save() 실패 시 DataIntegrityViolationException을 catch하여
    // 기존 발급건을 재조회(멱등 처리)하려면, save()가 독립 트랜잭션으로 완전히 롤백된 후
    // catch 블록이 새 트랜잭션으로 실행되어야 한다.
    // @Transactional이 있으면 예외 발생 시 트랜잭션이 rollback-only로 마킹되어 재조회가 불가능하다.
    public CouponDto.CouponIssueInfo issue(Long userId, Long couponId) {
        userRepository.findById(userId)
            .orElseThrow(() -> new CoreException(ErrorType.USER_NOT_FOUND));
        Coupon coupon = couponRepository.findById(couponId)
            .orElseThrow(() -> new CoreException(ErrorType.COUPON_NOT_FOUND));

        CouponIssue issue = couponService.createIssue(couponId, userId, coupon.getExpiredAt());
        try {
            return CouponDto.CouponIssueInfo.from(couponIssueRepository.save(issue));
        } catch (DataIntegrityViolationException e) {
            return couponIssueRepository.findByCouponIdAndUserId(couponId, userId)
                .map(CouponDto.CouponIssueInfo::from)
                .orElseThrow(() -> e);
        }
    }

    @Transactional(readOnly = true)
    public Page<CouponDto.MyCouponInfo> getMyCoupons(Long userId, Pageable pageable) {
        userRepository.findById(userId)
            .orElseThrow(() -> new CoreException(ErrorType.USER_NOT_FOUND));

        Page<CouponIssue> issues = couponIssueRepository.findByUserId(userId, pageable);
        List<Long> couponIds = issues.getContent().stream()
            .map(CouponIssue::getCouponId)
            .distinct()
            .toList();

        Map<Long, Coupon> couponMap = couponRepository.findAllByIds(couponIds).stream()
            .collect(Collectors.toMap(Coupon::getId, Function.identity()));

        return issues.map(issue -> CouponDto.MyCouponInfo.from(issue, couponMap.get(issue.getCouponId())));
    }
}

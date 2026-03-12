package com.loopers.domain.coupon;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CouponService {

    private final CouponTemplateRepository couponTemplateRepository;
    private final UserCouponRepository userCouponRepository;

    /**
     * 쿠폰 발급 (US-C01)
     * 템플릿 조회 → 발급 시점의 만료일 스냅샷 저장
     */
    @Transactional
    public UserCoupon issue(Long userId, Long couponTemplateId) {
        // 쿠폰 템플릿 존재 여부 검증
        CouponTemplate template = findTemplateById(couponTemplateId);
        return userCouponRepository.save(new UserCoupon(couponTemplateId, userId, template.getExpiredAt()));
    }

    /**
     * 내 쿠폰 목록 조회 (US-C02)
     * EXPIRED 상태 계산은 호출자(Facade)에서 LocalDateTime.now()와 함께 처리한다 (BR-C04).
     */
    @Transactional(readOnly = true)
    public List<UserCoupon> findAllIssuedByUserId(Long userId) {
        return userCouponRepository.findAllByUserId(userId);
    }

    /**
     * 주문 시 쿠폰 적용 (BR-O10, BR-O11, BR-O12)
     * 소유권 확인 → 만료/최소 주문 금액 검증 → 원자적 사용 처리 → 할인 금액 반환
     * 원자적 UPDATE(WHERE used_at IS NULL)로 동시 요청 중 하나만 성공을 보장한다.
     */
    @Transactional
    public int applyCoupon(Long userCouponId, Long userId, int originalAmount) {
        // 쿠폰 존재 여부 검증
        UserCoupon userCoupon = userCouponRepository.findByIdAndUserId(userCouponId, userId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "존재하지 않는 쿠폰입니다."));

        // 만료 검증 — 발급 시점 스냅샷 기준 (템플릿 변경에 영향받지 않음)
        userCoupon.validateNotExpired(LocalDateTime.now());

        CouponTemplate template = findTemplateById(userCoupon.getCouponTemplateId());

        // 최소 주문 금액 검증 — 현재 템플릿 기준 (minOrderAmount는 스냅샷 대상이 아니라고 가정)
        template.validateMinOrderAmount(originalAmount);

        // 쿠폰 사용
        // 낙관적 락의 방식과 비슷하지만 used_at 체크만으로 가능한 이유는 쿠폰은 한 번만 사용할 수 있고 used_at의 null 여부만 체크하면 되기 때문
        int updated = userCouponRepository.useIfAvailable(userCouponId, userId, LocalDateTime.now());
        // 여러 기기에서 동시에 쿠폰 사용시, 하나의 쿠폰만 사용되게 하기 위함.
        if (updated == 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "이미 사용된 쿠폰입니다.");
        }
        return template.calculateDiscount(originalAmount);
    }

    // ---- 관리자 ----

    // 쿠폰 템플릿 목록 조회 (US-C03)
    @Transactional(readOnly = true)
    public Page<CouponTemplate> findAllTemplates(Pageable pageable) {
        return couponTemplateRepository.findAll(pageable);
    }

    // 쿠폰 템플릿 단건 조회 (US-C04). 존재하지 않으면 NOT_FOUND 예외
    @Transactional(readOnly = true)
    public CouponTemplate findTemplateById(Long id) {
        return couponTemplateRepository.findById(id)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "존재하지 않는 쿠폰 템플릿입니다."));
    }

    // 쿠폰 템플릿 등록 (US-C05)
    @Transactional
    public CouponTemplate register(String name, CouponType type, int value, Integer minOrderAmount, LocalDateTime expiredAt) {
        CouponTemplate template = new CouponTemplate(name, type, value, minOrderAmount, expiredAt);
        return couponTemplateRepository.save(template);
    }

    // 쿠폰 템플릿 수정 (US-C06). dirty checking으로 저장
    @Transactional
    public CouponTemplate update(Long id, String name, CouponType type, int value, Integer minOrderAmount, LocalDateTime expiredAt) {
        CouponTemplate template = findTemplateById(id);
        template.update(name, type, value, minOrderAmount, expiredAt);
        return template;
    }

    /**
     * 쿠폰 템플릿 삭제 (US-C07, BR-C05)
     * 발급 쿠폰 soft delete → 템플릿 soft delete 순서로 처리한다.
     */
    @Transactional
    public void delete(Long id) {
        CouponTemplate template = findTemplateById(id);
        userCouponRepository.deleteAllByCouponTemplateId(id);
        template.delete();
    }

    // 템플릿 배치 조회 — ID → CouponTemplate 맵 반환 (N+1 방지)
    @Transactional(readOnly = true)
    public Map<Long, CouponTemplate> findTemplatesByIds(Collection<Long> ids) {
        return couponTemplateRepository.findAllByIds(ids).stream()
                .collect(Collectors.toMap(CouponTemplate::getId, t -> t));
    }

    // 특정 쿠폰 템플릿의 발급 내역 조회 (US-C08)
    @Transactional(readOnly = true)
    public Page<UserCoupon> findIssuesByTemplateId(Long templateId, Pageable pageable) {
        findTemplateById(templateId); // 템플릿 존재 확인: 없으면 404, 있으면 빈 목록 대신 실제 결과 반환
        return userCouponRepository.findAllByCouponTemplateId(templateId, pageable);
    }
}

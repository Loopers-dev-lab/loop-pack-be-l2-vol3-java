package com.loopers.application.coupon;

import com.loopers.domain.coupon.Coupon;
import com.loopers.domain.coupon.CouponService;
import com.loopers.domain.coupon.CouponIssue;
import com.loopers.domain.coupon.CouponIssueRepository;
import com.loopers.domain.coupon.CouponIssueStatus;
import com.loopers.domain.coupon.CouponRepository;
import com.loopers.domain.coupon.CouponType;
import com.loopers.domain.user.User;
import com.loopers.domain.user.UserRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CouponFacadeTest {

    private CouponRepository couponRepository;
    private CouponIssueRepository couponIssueRepository;
    private UserRepository userRepository;
    private CouponFacade couponFacade;

    @BeforeEach
    void setUp() {
        couponRepository = mock(CouponRepository.class);
        couponIssueRepository = mock(CouponIssueRepository.class);
        userRepository = mock(UserRepository.class);
        couponFacade = new CouponFacade(
            couponRepository,
            couponIssueRepository,
            userRepository,
            new CouponService()
        );
    }

    // ─────────────────────────────────────────
    // Admin: 쿠폰 템플릿 등록
    // ─────────────────────────────────────────

    @DisplayName("registerTemplate: 유효한 정보로 쿠폰 템플릿을 등록할 수 있다")
    @Test
    void registerTemplate_success() {
        when(couponRepository.save(any(Coupon.class))).thenAnswer(i -> i.getArgument(0));

        CouponDto.CouponInfo result = couponFacade.registerTemplate(
            "신규 가입 쿠폰", "설명", CouponType.FIXED, BigDecimal.valueOf(5000), ZonedDateTime.now().plusDays(30)
        );

        assertThat(result.name()).isEqualTo("신규 가입 쿠폰");
        assertThat(result.type()).isEqualTo(CouponType.FIXED);
        assertThat(result.discountValue()).isEqualByComparingTo("5000");
        assertThat(result.expiredAt()).isAfter(ZonedDateTime.now());
        verify(couponRepository).save(any(Coupon.class));
    }

    @DisplayName("registerTemplate: 이름이 비어있으면 등록에 실패한다")
    @Test
    void registerTemplate_fail_blankName() {
        assertThatThrownBy(() ->
            couponFacade.registerTemplate(
                "", "설명", CouponType.FIXED, BigDecimal.valueOf(5000), ZonedDateTime.now().plusDays(30)
            )
        ).isInstanceOf(IllegalArgumentException.class);

        verify(couponRepository, never()).save(any());
    }

    // ─────────────────────────────────────────
    // Admin: 쿠폰 템플릿 목록/상세 조회
    // ─────────────────────────────────────────

    @DisplayName("getTemplates: 쿠폰 템플릿 목록을 페이징으로 조회할 수 있다")
    @Test
    void getTemplates_success() {
        Coupon coupon = Coupon.create("쿠폰A", "설명", CouponType.FIXED, BigDecimal.valueOf(5000), ZonedDateTime.now().plusDays(7));
        PageRequest pageable = PageRequest.of(0, 10);
        when(couponRepository.findAll(pageable)).thenReturn(new PageImpl<>(List.of(coupon)));

        Page<CouponDto.CouponInfo> result = couponFacade.getTemplates(pageable);

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().getFirst().name()).isEqualTo("쿠폰A");
    }

    @DisplayName("getTemplate: 쿠폰 템플릿 상세를 조회할 수 있다")
    @Test
    void getTemplate_success() {
        Coupon coupon = Coupon.create("쿠폰A", "설명", CouponType.FIXED, BigDecimal.valueOf(5000), ZonedDateTime.now().plusDays(7));
        when(couponRepository.findById(1L)).thenReturn(Optional.of(coupon));

        CouponDto.CouponInfo result = couponFacade.getTemplate(1L);

        assertThat(result.name()).isEqualTo("쿠폰A");
    }

    @DisplayName("getTemplate: 존재하지 않는 템플릿 조회 시 예외가 발생한다")
    @Test
    void getTemplate_fail_notFound() {
        when(couponRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> couponFacade.getTemplate(999L))
            .isInstanceOf(CoreException.class)
            .satisfies(e -> assertThat(((CoreException) e).getErrorType())
                .isEqualTo(ErrorType.COUPON_NOT_FOUND));
    }

    // ─────────────────────────────────────────
    // Admin: 쿠폰 템플릿 수정
    // ─────────────────────────────────────────

    @DisplayName("updateTemplate: 쿠폰 템플릿 정보를 수정할 수 있다")
    @Test
    void updateTemplate_success() {
        Coupon coupon = Coupon.create("기존 쿠폰", "기존 설명", CouponType.FIXED, BigDecimal.valueOf(5000), ZonedDateTime.now().plusDays(7));
        when(couponRepository.findById(1L)).thenReturn(Optional.of(coupon));
        when(couponRepository.save(any(Coupon.class))).thenAnswer(i -> i.getArgument(0));

        CouponDto.CouponInfo result = couponFacade.updateTemplate(
            1L, "수정된 쿠폰", "수정된 설명", CouponType.RATE, BigDecimal.valueOf(15), ZonedDateTime.now().plusDays(14)
        );

        assertThat(result.name()).isEqualTo("수정된 쿠폰");
        assertThat(result.type()).isEqualTo(CouponType.RATE);
        assertThat(result.discountValue()).isEqualByComparingTo("15");
    }

    @DisplayName("updateTemplate: 존재하지 않는 템플릿 수정 시 예외가 발생한다")
    @Test
    void updateTemplate_fail_notFound() {
        when(couponRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
            couponFacade.updateTemplate(
                999L, "수정", "설명", CouponType.FIXED, BigDecimal.valueOf(1000), ZonedDateTime.now().plusDays(7)
            )
        ).isInstanceOf(CoreException.class)
         .satisfies(e -> assertThat(((CoreException) e).getErrorType())
             .isEqualTo(ErrorType.COUPON_NOT_FOUND));
    }

    // ─────────────────────────────────────────
    // Admin: 쿠폰 템플릿 삭제
    // ─────────────────────────────────────────

    @DisplayName("deleteTemplate: 쿠폰 템플릿을 삭제할 수 있다")
    @Test
    void deleteTemplate_success() {
        Coupon coupon = Coupon.create("쿠폰", "설명", CouponType.FIXED, BigDecimal.valueOf(5000), ZonedDateTime.now().plusDays(7));
        when(couponRepository.findById(1L)).thenReturn(Optional.of(coupon));
        when(couponRepository.save(any(Coupon.class))).thenAnswer(i -> i.getArgument(0));

        assertThatCode(() -> couponFacade.deleteTemplate(1L))
            .doesNotThrowAnyException();

        verify(couponRepository).save(any(Coupon.class));
    }

    @DisplayName("deleteTemplate: 존재하지 않는 템플릿 삭제 시 예외가 발생한다")
    @Test
    void deleteTemplate_fail_notFound() {
        when(couponRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> couponFacade.deleteTemplate(999L))
            .isInstanceOf(CoreException.class)
            .satisfies(e -> assertThat(((CoreException) e).getErrorType())
                .isEqualTo(ErrorType.COUPON_NOT_FOUND));
    }

    // ─────────────────────────────────────────
    // Admin: 발급 내역 조회
    // ─────────────────────────────────────────

    @DisplayName("getIssues: 특정 쿠폰의 발급 내역을 페이징으로 조회할 수 있다")
    @Test
    void getIssues_success() {
        Coupon coupon = Coupon.create("쿠폰", "설명", CouponType.FIXED, BigDecimal.valueOf(5000), ZonedDateTime.now().plusDays(7));
        CouponIssue issue = CouponIssue.create(1L, 10L, ZonedDateTime.now().plusDays(7));
        PageRequest pageable = PageRequest.of(0, 10);

        when(couponRepository.findById(1L)).thenReturn(Optional.of(coupon));
        when(couponIssueRepository.findByCouponId(1L, pageable)).thenReturn(new PageImpl<>(List.of(issue)));

        Page<CouponDto.CouponIssueInfo> result = couponFacade.getIssues(1L, pageable);

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().getFirst().userId()).isEqualTo(10L);
    }

    @DisplayName("getIssues: 존재하지 않는 쿠폰의 발급 내역 조회 시 예외가 발생한다")
    @Test
    void getIssues_fail_couponNotFound() {
        when(couponRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> couponFacade.getIssues(999L, PageRequest.of(0, 10)))
            .isInstanceOf(CoreException.class)
            .satisfies(e -> assertThat(((CoreException) e).getErrorType())
                .isEqualTo(ErrorType.COUPON_NOT_FOUND));
    }

    // ─────────────────────────────────────────
    // User: 쿠폰 발급 요청
    // ─────────────────────────────────────────

    @DisplayName("issue: 사용자가 쿠폰 발급을 요청하면 발급 내역이 생성된다")
    @Test
    void issue_success() {
        Long userId = 1L;
        Long couponId = 10L;
        Coupon coupon = Coupon.create("쿠폰", "설명", CouponType.FIXED, BigDecimal.valueOf(5000), ZonedDateTime.now().plusDays(7));

        when(userRepository.findById(userId)).thenReturn(Optional.of(mock(User.class)));
        when(couponRepository.findById(couponId)).thenReturn(Optional.of(coupon));
        when(couponIssueRepository.save(any(CouponIssue.class))).thenAnswer(i -> i.getArgument(0));

        CouponDto.CouponIssueInfo result = couponFacade.issue(userId, couponId);

        assertThat(result.userId()).isEqualTo(userId);
        assertThat(result.couponId()).isEqualTo(couponId);
        assertThat(result.status()).isEqualTo(CouponIssueStatus.AVAILABLE);
        verify(couponIssueRepository).save(any(CouponIssue.class));
    }

    @DisplayName("issue: 이미 발급된 쿠폰을 재요청하면 기존 발급 내역을 반환한다 (멱등)")
    @Test
    void issue_idempotent_whenAlreadyIssued() {
        Long userId = 1L;
        Long couponId = 10L;
        Coupon coupon = Coupon.create("쿠폰", "설명", CouponType.FIXED, BigDecimal.valueOf(5000), ZonedDateTime.now().plusDays(7));
        CouponIssue existing = CouponIssue.create(couponId, userId, ZonedDateTime.now().plusDays(7));

        when(userRepository.findById(userId)).thenReturn(Optional.of(mock(User.class)));
        when(couponRepository.findById(couponId)).thenReturn(Optional.of(coupon));
        when(couponIssueRepository.save(any(CouponIssue.class)))
            .thenThrow(new DataIntegrityViolationException("duplicate coupon issue"));
        when(couponIssueRepository.findByCouponIdAndUserId(couponId, userId)).thenReturn(Optional.of(existing));

        CouponDto.CouponIssueInfo result = couponFacade.issue(userId, couponId);

        assertThat(result.userId()).isEqualTo(userId);
        verify(couponIssueRepository).save(any(CouponIssue.class));
        verify(couponIssueRepository).findByCouponIdAndUserId(couponId, userId);
    }

    @DisplayName("issue: 존재하지 않는 사용자가 발급 요청하면 예외가 발생한다")
    @Test
    void issue_fail_userNotFound() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> couponFacade.issue(99L, 1L))
            .isInstanceOf(CoreException.class)
            .satisfies(e -> assertThat(((CoreException) e).getErrorType())
                .isEqualTo(ErrorType.USER_NOT_FOUND));
    }

    @DisplayName("issue: 존재하지 않는 쿠폰 발급 요청 시 예외가 발생한다")
    @Test
    void issue_fail_couponNotFound() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(mock(User.class)));
        when(couponRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> couponFacade.issue(1L, 999L))
            .isInstanceOf(CoreException.class)
            .satisfies(e -> assertThat(((CoreException) e).getErrorType())
                .isEqualTo(ErrorType.COUPON_NOT_FOUND));
    }

    // ─────────────────────────────────────────
    // User: 내 쿠폰 목록 조회
    // ─────────────────────────────────────────

    @DisplayName("getMyCoupons: 사용자의 보유 쿠폰 목록을 조회할 수 있다")
    @Test
    void getMyCoupons_success() {
        Long userId = 1L;
        Long couponTemplateId = 1L;
        Coupon coupon = mock(Coupon.class);
        when(coupon.getId()).thenReturn(couponTemplateId);
        when(coupon.getName()).thenReturn("쿠폰");
        when(coupon.getType()).thenReturn(CouponType.FIXED);
        when(coupon.getDiscountValue()).thenReturn(BigDecimal.valueOf(5000));
        CouponIssue issue = CouponIssue.create(couponTemplateId, userId, ZonedDateTime.now().plusDays(7));
        PageRequest pageable = PageRequest.of(0, 10);

        when(userRepository.findById(userId)).thenReturn(Optional.of(mock(User.class)));
        when(couponIssueRepository.findByUserId(userId, pageable)).thenReturn(new PageImpl<>(List.of(issue)));
        when(couponRepository.findAllByIds(any())).thenReturn(List.of(coupon));

        Page<CouponDto.MyCouponInfo> result = couponFacade.getMyCoupons(userId, pageable);

        assertThat(result.getTotalElements()).isEqualTo(1);
    }

    @DisplayName("getMyCoupons: 존재하지 않는 사용자 조회 시 예외가 발생한다")
    @Test
    void getMyCoupons_fail_userNotFound() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> couponFacade.getMyCoupons(99L, PageRequest.of(0, 10)))
            .isInstanceOf(CoreException.class)
            .satisfies(e -> assertThat(((CoreException) e).getErrorType())
                .isEqualTo(ErrorType.USER_NOT_FOUND));
    }
}

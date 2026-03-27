package com.loopers.application.coupon;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.domain.coupon.*;
import com.loopers.fake.FakeCouponIssueRepository;
import com.loopers.fake.FakeCouponIssueRequestRepository;
import com.loopers.fake.FakeCouponRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.Clock;
import java.time.ZonedDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class CouponFacadeTest {

    private CouponFacade couponFacade;
    private FakeCouponRepository couponRepository;
    private FakeCouponIssueRepository couponIssueRepository;
    private FakeCouponIssueRequestRepository issueRequestRepository;
    private KafkaTemplate<Object, Object> kafkaTemplate;
    private final Clock clock = Clock.systemDefaultZone();

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        couponRepository = new FakeCouponRepository();
        couponIssueRepository = new FakeCouponIssueRepository();
        issueRequestRepository = new FakeCouponIssueRequestRepository();
        kafkaTemplate = mock(KafkaTemplate.class);
        couponFacade = new CouponFacade(couponRepository, couponIssueRepository,
            issueRequestRepository, kafkaTemplate, new ObjectMapper(), clock);
    }

    @Nested
    @DisplayName("쿠폰 템플릿 생성")
    class CreateCoupon {

        @DisplayName("쿠폰 템플릿을 생성하면 저장되어 반환된다")
        @Test
        void createCoupon_savesCoupon() {
            Coupon result = couponFacade.createCoupon(
                "신규가입 10% 할인", DiscountType.RATE, 10, 10000,
                ZonedDateTime.now().plusDays(30));

            assertThat(result.getId()).isGreaterThan(0L);
            assertThat(result.getName()).isEqualTo("신규가입 10% 할인");
            assertThat(result.getDiscountType()).isEqualTo(DiscountType.RATE);
            assertThat(result.getDiscountValue()).isEqualTo(10);
            assertThat(result.getMinOrderAmount()).isEqualTo(10000);
        }
    }

    @Nested
    @DisplayName("쿠폰 템플릿 조회")
    class GetCoupon {

        @DisplayName("존재하는 쿠폰을 조회하면 반환된다")
        @Test
        void getCoupon_whenExists_returnsCoupon() {
            Coupon coupon = couponFacade.createCoupon(
                "할인", DiscountType.FIXED, 1000, 0, ZonedDateTime.now().plusDays(30));

            Coupon result = couponFacade.getCoupon(coupon.getId());

            assertThat(result.getId()).isEqualTo(coupon.getId());
        }

        @DisplayName("존재하지 않는 쿠폰을 조회하면 예외가 발생한다")
        @Test
        void getCoupon_whenNotExists_throwsException() {
            assertThatThrownBy(() -> couponFacade.getCoupon(999L))
                .isInstanceOf(CoreException.class)
                .extracting(e -> ((CoreException) e).getErrorType())
                .isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("쿠폰 템플릿 수정")
    class UpdateCoupon {

        @DisplayName("쿠폰을 수정하면 변경사항이 반영된다")
        @Test
        void updateCoupon_updatesFields() {
            Coupon coupon = couponFacade.createCoupon(
                "기존 이름", DiscountType.FIXED, 1000, 0, ZonedDateTime.now().plusDays(30));
            ZonedDateTime newExpiredAt = ZonedDateTime.now().plusDays(60);

            Coupon result = couponFacade.updateCoupon(
                coupon.getId(), "새 이름", DiscountType.RATE, 20, 5000, newExpiredAt);

            assertThat(result.getName()).isEqualTo("새 이름");
            assertThat(result.getDiscountType()).isEqualTo(DiscountType.RATE);
            assertThat(result.getDiscountValue()).isEqualTo(20);
            assertThat(result.getMinOrderAmount()).isEqualTo(5000);
        }
    }

    @Nested
    @DisplayName("쿠폰 템플릿 삭제")
    class DeleteCoupon {

        @DisplayName("쿠폰을 삭제하면 조회되지 않는다")
        @Test
        void deleteCoupon_softDeletes() {
            Coupon coupon = couponFacade.createCoupon(
                "할인", DiscountType.FIXED, 1000, 0, ZonedDateTime.now().plusDays(30));

            couponFacade.deleteCoupon(coupon.getId());

            assertThatThrownBy(() -> couponFacade.getCoupon(coupon.getId()))
                .isInstanceOf(CoreException.class)
                .extracting(e -> ((CoreException) e).getErrorType())
                .isEqualTo(ErrorType.NOT_FOUND);
        }

        @DisplayName("존재하지 않는 쿠폰을 삭제하면 예외가 발생한다")
        @Test
        void deleteCoupon_whenNotExists_throwsException() {
            assertThatThrownBy(() -> couponFacade.deleteCoupon(999L))
                .isInstanceOf(CoreException.class)
                .extracting(e -> ((CoreException) e).getErrorType())
                .isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("쿠폰 발급")
    class IssueCoupon {

        @DisplayName("쿠폰을 발급하면 CouponIssue가 생성된다")
        @Test
        void issueCoupon_createsCouponIssue() {
            Coupon coupon = couponFacade.createCoupon(
                "할인", DiscountType.FIXED, 1000, 0, ZonedDateTime.now().plusDays(30));

            CouponIssue result = couponFacade.issueCoupon(coupon.getId(), 1L);

            assertThat(result.getId()).isGreaterThan(0L);
            assertThat(result.getCouponId()).isEqualTo(coupon.getId());
            assertThat(result.getMemberId()).isEqualTo(1L);
            assertThat(result.getStatus()).isEqualTo(CouponIssueStatus.AVAILABLE);
        }

        @DisplayName("만료된 쿠폰은 발급할 수 없다")
        @Test
        void issueCoupon_whenExpired_throwsException() {
            Coupon coupon = couponFacade.createCoupon(
                "할인", DiscountType.FIXED, 1000, 0, ZonedDateTime.now().minusDays(1));

            assertThatThrownBy(() -> couponFacade.issueCoupon(coupon.getId(), 1L))
                .isInstanceOf(CoreException.class)
                .extracting(e -> ((CoreException) e).getErrorType())
                .isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("존재하지 않는 쿠폰은 발급할 수 없다")
        @Test
        void issueCoupon_whenNotExists_throwsException() {
            assertThatThrownBy(() -> couponFacade.issueCoupon(999L, 1L))
                .isInstanceOf(CoreException.class)
                .extracting(e -> ((CoreException) e).getErrorType())
                .isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("내 쿠폰 목록 조회")
    class GetMyCoupons {

        @DisplayName("발급받은 쿠폰 목록이 반환된다")
        @Test
        void getMyCoupons_returnsCouponIssues() {
            Coupon coupon1 = couponFacade.createCoupon(
                "할인1", DiscountType.FIXED, 1000, 0, ZonedDateTime.now().plusDays(30));
            Coupon coupon2 = couponFacade.createCoupon(
                "할인2", DiscountType.RATE, 10, 0, ZonedDateTime.now().plusDays(30));
            couponFacade.issueCoupon(coupon1.getId(), 1L);
            couponFacade.issueCoupon(coupon2.getId(), 1L);
            couponFacade.issueCoupon(coupon1.getId(), 2L);

            List<CouponIssue> result = couponFacade.getMyCoupons(1L);

            assertThat(result).hasSize(2);
            assertThat(result).allSatisfy(issue ->
                assertThat(issue.getMemberId()).isEqualTo(1L));
        }

        @DisplayName("쿠폰이 없으면 빈 리스트가 반환된다")
        @Test
        void getMyCoupons_whenNone_returnsEmpty() {
            List<CouponIssue> result = couponFacade.getMyCoupons(999L);

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("발급 내역 조회")
    class GetCouponIssues {

        @DisplayName("쿠폰의 발급 내역이 반환된다")
        @Test
        void getCouponIssues_returnsIssues() {
            Coupon coupon = couponFacade.createCoupon(
                "할인", DiscountType.FIXED, 1000, 0, ZonedDateTime.now().plusDays(30));
            couponFacade.issueCoupon(coupon.getId(), 1L);
            couponFacade.issueCoupon(coupon.getId(), 2L);

            List<CouponIssue> result = couponFacade.getCouponIssues(coupon.getId());

            assertThat(result).hasSize(2);
        }

        @DisplayName("존재하지 않는 쿠폰의 발급 내역을 조회하면 예외가 발생한다")
        @Test
        void getCouponIssues_whenCouponNotExists_throwsException() {
            assertThatThrownBy(() -> couponFacade.getCouponIssues(999L))
                .isInstanceOf(CoreException.class)
                .extracting(e -> ((CoreException) e).getErrorType())
                .isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("주문 연동: 쿠폰 적용")
    class ApplyCouponToOrder {

        @DisplayName("유효한 쿠폰을 적용하면 할인 금액이 반환된다")
        @Test
        void applyCouponToOrder_returnsDiscount() {
            Coupon coupon = couponFacade.createCoupon(
                "5000원 할인", DiscountType.FIXED, 5000, 10000,
                ZonedDateTime.now().plusDays(30));
            CouponIssue issue = couponFacade.issueCoupon(coupon.getId(), 1L);

            CouponApplyResult result = couponFacade.applyCouponToOrder(
                issue.getId(), 1L, 100000);

            assertThat(result.discountAmount()).isEqualTo(5000);
            assertThat(result.couponIssueId()).isEqualTo(issue.getId());
            assertThat(issue.getStatus()).isEqualTo(CouponIssueStatus.USED);
        }

        @DisplayName("타인의 쿠폰을 적용하면 예외가 발생한다")
        @Test
        void applyCouponToOrder_withOtherMember_throwsException() {
            Coupon coupon = couponFacade.createCoupon(
                "할인", DiscountType.FIXED, 5000, 0,
                ZonedDateTime.now().plusDays(30));
            CouponIssue issue = couponFacade.issueCoupon(coupon.getId(), 2L);

            assertThatThrownBy(() -> couponFacade.applyCouponToOrder(
                issue.getId(), 1L, 100000))
                .isInstanceOf(CoreException.class)
                .extracting(e -> ((CoreException) e).getErrorType())
                .isEqualTo(ErrorType.FORBIDDEN);
        }
    }

    @Nested
    @DisplayName("선착순 쿠폰 발급 요청")
    class RequestCouponIssue {

        @DisplayName("유효한 쿠폰에 발급 요청하면 PENDING 상태의 CouponIssueRequest가 생성된다")
        @Test
        void requestCouponIssue_createsPendingRequest() {
            Coupon coupon = couponFacade.createCoupon(
                "선착순 할인", DiscountType.FIXED, 5000, 0,
                ZonedDateTime.now().plusDays(30));

            CouponIssueRequest result = couponFacade.requestCouponIssue(coupon.getId(), 1L);

            assertThat(result.getId()).isNotNull();
            assertThat(result.getCouponId()).isEqualTo(coupon.getId());
            assertThat(result.getMemberId()).isEqualTo(1L);
            assertThat(result.getStatus()).isEqualTo(CouponIssueRequestStatus.PENDING);
        }

        @DisplayName("발급 요청 시 Kafka에 메시지가 발행된다")
        @Test
        void requestCouponIssue_sendsKafkaMessage() {
            Coupon coupon = couponFacade.createCoupon(
                "선착순 할인", DiscountType.FIXED, 5000, 0,
                ZonedDateTime.now().plusDays(30));

            couponFacade.requestCouponIssue(coupon.getId(), 1L);

            verify(kafkaTemplate).send(eq("coupon-issue-requests"),
                eq(String.valueOf(coupon.getId())), anyString());
        }

        @DisplayName("만료된 쿠폰은 발급 요청할 수 없다")
        @Test
        void requestCouponIssue_whenExpired_throwsException() {
            Coupon coupon = couponFacade.createCoupon(
                "할인", DiscountType.FIXED, 5000, 0,
                ZonedDateTime.now().minusDays(1));

            assertThatThrownBy(() -> couponFacade.requestCouponIssue(coupon.getId(), 1L))
                .isInstanceOf(CoreException.class)
                .extracting(e -> ((CoreException) e).getErrorType())
                .isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("존재하지 않는 쿠폰은 발급 요청할 수 없다")
        @Test
        void requestCouponIssue_whenNotExists_throwsException() {
            assertThatThrownBy(() -> couponFacade.requestCouponIssue(999L, 1L))
                .isInstanceOf(CoreException.class)
                .extracting(e -> ((CoreException) e).getErrorType())
                .isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("발급 요청 상태 조회")
    class GetIssueRequest {

        @DisplayName("저장된 발급 요청을 조회하면 반환된다")
        @Test
        void getIssueRequest_whenExists_returnsRequest() {
            Coupon coupon = couponFacade.createCoupon(
                "선착순 할인", DiscountType.FIXED, 5000, 0,
                ZonedDateTime.now().plusDays(30));
            CouponIssueRequest saved = couponFacade.requestCouponIssue(coupon.getId(), 1L);

            CouponIssueRequest result = couponFacade.getIssueRequest(saved.getId());

            assertThat(result.getId()).isEqualTo(saved.getId());
            assertThat(result.getStatus()).isEqualTo(CouponIssueRequestStatus.PENDING);
        }

        @DisplayName("존재하지 않는 발급 요청을 조회하면 예외가 발생한다")
        @Test
        void getIssueRequest_whenNotExists_throwsException() {
            assertThatThrownBy(() -> couponFacade.getIssueRequest(999L))
                .isInstanceOf(CoreException.class)
                .extracting(e -> ((CoreException) e).getErrorType())
                .isEqualTo(ErrorType.NOT_FOUND);
        }
    }
}

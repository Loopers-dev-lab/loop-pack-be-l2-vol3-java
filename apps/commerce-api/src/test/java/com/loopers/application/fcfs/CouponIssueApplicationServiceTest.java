package com.loopers.application.fcfs;

import com.loopers.application.event.CouponIssueRequestCreatedEvent;
import com.loopers.domain.fcfs.CouponIssueRequest;
import com.loopers.domain.fcfs.CouponIssueRequestStatus;
import com.loopers.domain.fcfs.FcfsCoupon;
import com.loopers.fake.FakeApplicationEventPublisher;
import com.loopers.fake.FakeCouponIssueRequestRepository;
import com.loopers.fake.FakeFcfsCouponRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("CouponIssueApplicationService 테스트")
class CouponIssueApplicationServiceTest {

    private FakeFcfsCouponRepository fcfsCouponRepository;
    private FakeCouponIssueRequestRepository couponIssueRequestRepository;
    private FakeApplicationEventPublisher eventPublisher;
    private CouponIssueApplicationService service;

    @BeforeEach
    void setUp() {
        fcfsCouponRepository = new FakeFcfsCouponRepository();
        couponIssueRequestRepository = new FakeCouponIssueRequestRepository();
        eventPublisher = new FakeApplicationEventPublisher();
        service = new CouponIssueApplicationService(
            fcfsCouponRepository,
            couponIssueRequestRepository,
            eventPublisher
        );
    }

    @Nested
    @DisplayName("쿠폰 발급 요청")
    class RequestIssue {

        @Test
        @DisplayName("성공 - 발급 요청이 PENDING 상태로 저장되고 이벤트가 발행된다")
        void 발급_요청_성공() {
            // Arrange
            FcfsCoupon coupon = fcfsCouponRepository.save(FcfsCoupon.create("선착순 쿠폰", 100));
            Long userId = 1L;

            // Act
            CouponIssueRequestResult result = service.requestIssue(coupon.getId(), userId);

            // Assert
            assertThat(result.requestId()).isNotNull();
            assertThat(result.status()).isEqualTo(CouponIssueRequestStatus.PENDING);

            // 요청이 저장되었는지 확인
            Optional<CouponIssueRequest> savedRequest = couponIssueRequestRepository.findByRequestId(result.requestId());
            assertThat(savedRequest).isPresent();
            assertThat(savedRequest.get().getStatus()).isEqualTo(CouponIssueRequestStatus.PENDING);
            assertThat(savedRequest.get().getCouponId()).isEqualTo(coupon.getId());
            assertThat(savedRequest.get().getUserId()).isEqualTo(userId);
        }

        @Test
        @DisplayName("성공 - 발급 요청 시 CouponIssueRequestCreatedEvent 발행")
        void 발급_요청_시_이벤트_발행() {
            // Arrange
            FcfsCoupon coupon = fcfsCouponRepository.save(FcfsCoupon.create("선착순 쿠폰", 100));
            Long userId = 1L;

            // Act
            CouponIssueRequestResult result = service.requestIssue(coupon.getId(), userId);

            // Assert
            assertThat(eventPublisher.hasEventOfType(CouponIssueRequestCreatedEvent.class)).isTrue();
            List<CouponIssueRequestCreatedEvent> events = eventPublisher.getEventsOfType(CouponIssueRequestCreatedEvent.class);
            assertThat(events).hasSize(1);

            CouponIssueRequestCreatedEvent event = events.get(0);
            assertThat(event.requestId()).isEqualTo(result.requestId());
            assertThat(event.couponId()).isEqualTo(coupon.getId());
            assertThat(event.userId()).isEqualTo(userId);
        }

        @Test
        @DisplayName("실패 - 존재하지 않는 쿠폰")
        void 존재하지_않는_쿠폰_예외() {
            // Arrange
            Long nonExistentCouponId = 999L;
            Long userId = 1L;

            // Act & Assert
            CoreException ex = assertThrows(CoreException.class,
                () -> service.requestIssue(nonExistentCouponId, userId));
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("발급 결과 조회")
    class GetIssueResult {

        @Test
        @DisplayName("성공 - 존재하는 requestId로 결과 조회")
        void 발급_결과_조회_성공() {
            // Arrange
            FcfsCoupon coupon = fcfsCouponRepository.save(FcfsCoupon.create("선착순 쿠폰", 100));
            CouponIssueRequestResult requestResult = service.requestIssue(coupon.getId(), 1L);

            // Act
            CouponIssueResultResponse result = service.getIssueResult(requestResult.requestId());

            // Assert
            assertThat(result.requestId()).isEqualTo(requestResult.requestId());
            assertThat(result.status()).isEqualTo(CouponIssueRequestStatus.PENDING);
            assertThat(result.failureReason()).isNull();
        }

        @Test
        @DisplayName("실패 - 존재하지 않는 requestId")
        void 존재하지_않는_requestId_예외() {
            // Act & Assert
            CoreException ex = assertThrows(CoreException.class,
                () -> service.getIssueResult("non-existent-request-id"));
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }
}

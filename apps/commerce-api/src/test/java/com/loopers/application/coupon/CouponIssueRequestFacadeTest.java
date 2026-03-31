package com.loopers.application.coupon;

import com.loopers.domain.coupon.CouponIssueRequest;
import com.loopers.domain.coupon.CouponPromotion;
import com.loopers.domain.coupon.InMemoryCouponIssueRequestRepository;
import com.loopers.domain.coupon.InMemoryCouponPromotionRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class CouponIssueRequestFacadeTest {

    private InMemoryCouponPromotionRepository promotionRepository;
    private InMemoryCouponIssueRequestRepository issueRequestRepository;
    private FakeCouponIssueCountManager issueCountManager;
    private FakeOutboxEventPublisher outboxEventPublisher;
    private CouponIssueRequestFacade facade;

    @BeforeEach
    void setUp() {
        promotionRepository = new InMemoryCouponPromotionRepository();
        issueRequestRepository = new InMemoryCouponIssueRequestRepository();
        issueCountManager = new FakeCouponIssueCountManager();
        outboxEventPublisher = new FakeOutboxEventPublisher();
        facade = new CouponIssueRequestFacade(
                promotionRepository, issueRequestRepository, issueCountManager, outboxEventPublisher
        );
    }

    @DisplayName("선착순 쿠폰 발급 요청 시,")
    @Nested
    class IssueRequest {

        @DisplayName("유효한 프로모션이면 PENDING 상태의 요청을 생성하고 이벤트를 발행한다.")
        @Test
        void createsPendingRequest_whenPromotionIsActive() {
            // arrange
            ZonedDateTime now = ZonedDateTime.now();
            CouponPromotion promotion = promotionRepository.save(
                    CouponPromotion.create(1L, 100, now.minusHours(1), now.plusDays(1))
            );

            // act
            CouponIssueRequestInfo result = facade.issueRequest(100L, promotion.getCouponId());

            // assert
            assertAll(
                    () -> assertThat(result.couponId()).isEqualTo(1L),
                    () -> assertThat(result.userId()).isEqualTo(100L),
                    () -> assertThat(result.status()).isEqualTo("PENDING"),
                    () -> assertThat(outboxEventPublisher.getPublishedCount()).isEqualTo(1)
            );
        }

        @DisplayName("선착순 프로모션이 아닌 쿠폰이면 NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenPromotionNotExists() {
            // act
            CoreException result = assertThrows(CoreException.class,
                    () -> facade.issueRequest(100L, 999L));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }

        @DisplayName("아직 시작되지 않은 프로모션이면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenPromotionNotStarted() {
            // arrange
            ZonedDateTime now = ZonedDateTime.now();
            CouponPromotion promotion = promotionRepository.save(
                    CouponPromotion.create(1L, 100, now.plusHours(1), now.plusDays(1))
            );

            // act
            CoreException result = assertThrows(CoreException.class,
                    () -> facade.issueRequest(100L, promotion.getCouponId()));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("수량이 초과되면 BAD_REQUEST 예외가 발생하고 카운터가 되돌려진다.")
        @Test
        void throwsBadRequest_whenQuantityExceeded() {
            // arrange
            ZonedDateTime now = ZonedDateTime.now();
            CouponPromotion promotion = promotionRepository.save(
                    CouponPromotion.create(1L, 2, now.minusHours(1), now.plusDays(1))
            );
            // 이미 2개 발급됨
            issueCountManager.setCount(1L, 2);

            // act
            CoreException result = assertThrows(CoreException.class,
                    () -> facade.issueRequest(100L, promotion.getCouponId()));

            // assert
            assertAll(
                    () -> assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST),
                    () -> assertThat(issueCountManager.getCount(1L)).isEqualTo(2) // 되돌려짐
            );
        }

        @DisplayName("동일 사용자가 중복 요청하면 CONFLICT 예외가 발생하고 카운터가 되돌려진다.")
        @Test
        void throwsConflict_whenDuplicateRequest() {
            // arrange
            ZonedDateTime now = ZonedDateTime.now();
            CouponPromotion promotion = promotionRepository.save(
                    CouponPromotion.create(1L, 100, now.minusHours(1), now.plusDays(1))
            );
            facade.issueRequest(100L, promotion.getCouponId());

            // act
            CoreException result = assertThrows(CoreException.class,
                    () -> facade.issueRequest(100L, promotion.getCouponId()));

            // assert
            assertAll(
                    () -> assertThat(result.getErrorType()).isEqualTo(ErrorType.CONFLICT),
                    () -> assertThat(issueCountManager.getCount(1L)).isEqualTo(1) // 되돌려짐
            );
        }
    }

    @DisplayName("발급 요청 조회 시,")
    @Nested
    class GetIssueRequest {

        @DisplayName("존재하는 요청이면 정보를 반환한다.")
        @Test
        void returnsInfo_whenRequestExists() {
            // arrange
            ZonedDateTime now = ZonedDateTime.now();
            promotionRepository.save(
                    CouponPromotion.create(1L, 100, now.minusHours(1), now.plusDays(1))
            );
            CouponIssueRequestInfo created = facade.issueRequest(100L, 1L);

            // act
            CouponIssueRequestInfo result = facade.getIssueRequest(100L, created.requestId());

            // assert
            assertAll(
                    () -> assertThat(result.requestId()).isEqualTo(created.requestId()),
                    () -> assertThat(result.status()).isEqualTo("PENDING")
            );
        }

        @DisplayName("존재하지 않는 요청이면 NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenRequestNotExists() {
            // act
            CoreException result = assertThrows(CoreException.class,
                    () -> facade.getIssueRequest(100L, 999L));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    // === Test Doubles ===

    static class FakeCouponIssueCountManager implements CouponIssueCountManager {
        private final Map<Long, Long> counts = new HashMap<>();

        @Override
        public long increment(Long couponId) {
            return counts.merge(couponId, 1L, Long::sum);
        }

        @Override
        public void decrement(Long couponId) {
            counts.computeIfPresent(couponId, (k, v) -> v - 1);
        }

        void setCount(Long couponId, long count) {
            counts.put(couponId, count);
        }

        long getCount(Long couponId) {
            return counts.getOrDefault(couponId, 0L);
        }
    }

    static class FakeOutboxEventPublisher extends com.loopers.application.outbox.OutboxEventPublisher {
        private int publishedCount = 0;

        FakeOutboxEventPublisher() {
            super(null);
        }

        @Override
        public void publish(com.loopers.event.EventType type, com.loopers.event.EventPayload payload, Long partitionKey) {
            publishedCount++;
        }

        int getPublishedCount() {
            return publishedCount;
        }
    }
}

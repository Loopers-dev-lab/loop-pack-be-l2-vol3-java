package com.loopers.application.coupon;

import com.loopers.domain.coupon.CouponIssueRequest;
import com.loopers.domain.coupon.CouponIssueRequestRepository;
import com.loopers.domain.coupon.CouponIssueStatus;
import com.loopers.domain.coupon.IssuedCoupon;
import com.loopers.infrastructure.coupon.CouponJpaRepository;
import com.loopers.infrastructure.coupon.IssuedCouponJpaRepository;
import com.loopers.utils.DatabaseCleanUp;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class CouponIssueProcessorIntegrationTest {

    @Autowired
    private CouponIssueProcessor couponIssueProcessor;

    @Autowired
    private CouponIssueRequestRepository couponIssueRequestRepository;

    @Autowired
    private CouponJpaRepository couponJpaRepository;

    @Autowired
    private IssuedCouponJpaRepository issuedCouponJpaRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @BeforeEach
    void setUp() {
        databaseCleanUp.truncateAllTables();
    }

    private void insertCoupon(Long couponId, int maxIssueCount) {
        transactionTemplate.executeWithoutResult(status -> {
            entityManager.createNativeQuery(
                    "INSERT INTO coupons (id, max_issue_count, issued_count, expired_at) " +
                    "VALUES (:id, :max, 0, :expired)")
                    .setParameter("id", couponId)
                    .setParameter("max", maxIssueCount)
                    .setParameter("expired", LocalDateTime.now().plusDays(7))
                    .executeUpdate();
        });
    }

    private void insertPendingRequest(String eventId, Long couponId, Long userId) {
        transactionTemplate.executeWithoutResult(status -> {
            entityManager.createNativeQuery(
                    "INSERT INTO coupon_issue_requests (event_id, coupon_id, user_id, status, created_at) " +
                    "VALUES (:eventId, :couponId, :userId, 'PENDING', NOW())")
                    .setParameter("eventId", eventId)
                    .setParameter("couponId", couponId)
                    .setParameter("userId", userId)
                    .executeUpdate();
        });
    }

    @Nested
    class 발급_성공 {

        @Test
        void 유효한_요청이면_쿠폰이_발급되고_상태가_COMPLETED가_된다() {
            insertCoupon(1L, 100);
            insertPendingRequest("evt-1", 1L, 100L);

            couponIssueProcessor.process("evt-1", 1L, 100L);

            CouponIssueRequest request = couponIssueRequestRepository.findByEventId("evt-1").orElseThrow();
            assertThat(request.getStatus()).isEqualTo(CouponIssueStatus.COMPLETED);
        }

        @Test
        void 발급_후_issued_coupon_레코드가_생성된다() {
            insertCoupon(1L, 100);
            insertPendingRequest("evt-1", 1L, 100L);

            couponIssueProcessor.process("evt-1", 1L, 100L);

            assertThat(issuedCouponJpaRepository.existsByCouponIdAndUserId(1L, 100L)).isTrue();
        }
    }

    @Nested
    class 수량_소진 {

        @Test
        void 남은_수량이_0이면_상태가_REJECTED가_된다() {
            insertCoupon(1L, 0);
            insertPendingRequest("evt-1", 1L, 100L);

            couponIssueProcessor.process("evt-1", 1L, 100L);

            CouponIssueRequest request = couponIssueRequestRepository.findByEventId("evt-1").orElseThrow();
            assertThat(request.getStatus()).isEqualTo(CouponIssueStatus.REJECTED);
            assertThat(request.getRejectReason()).contains("소진");
        }
    }

    @Nested
    class 중복_발급_방지 {

        @Test
        void 같은_couponId_userId로_다시_요청하면_REJECTED가_된다() {
            insertCoupon(1L, 100);
            insertPendingRequest("evt-1", 1L, 100L);
            couponIssueProcessor.process("evt-1", 1L, 100L);

            // 두 번째 요청
            insertPendingRequest("evt-2", 1L, 100L);
            couponIssueProcessor.process("evt-2", 1L, 100L);

            CouponIssueRequest request = couponIssueRequestRepository.findByEventId("evt-2").orElseThrow();
            assertThat(request.getStatus()).isEqualTo(CouponIssueStatus.REJECTED);
            assertThat(request.getRejectReason()).contains("이미 발급");
        }
    }

    @Nested
    class 요청_상태_검증 {

        @Test
        void eventId가_존재하지_않으면_아무_처리도_하지_않는다() {
            insertCoupon(1L, 100);

            couponIssueProcessor.process("not-exists", 1L, 100L);

            assertThat(issuedCouponJpaRepository.count()).isZero();
        }

        @Test
        void 이미_COMPLETED된_요청이면_아무_처리도_하지_않는다() {
            insertCoupon(1L, 100);
            insertPendingRequest("evt-1", 1L, 100L);
            couponIssueProcessor.process("evt-1", 1L, 100L); // COMPLETED

            // 같은 eventId로 다시 호출
            couponIssueProcessor.process("evt-1", 1L, 100L);

            // issued_coupon은 1개만
            assertThat(issuedCouponJpaRepository.count()).isEqualTo(1);
        }
    }
}

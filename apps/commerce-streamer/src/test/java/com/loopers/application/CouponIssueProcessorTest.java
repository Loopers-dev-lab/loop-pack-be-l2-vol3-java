package com.loopers.application;

import com.loopers.domain.coupon.CouponIssueRequest;
import com.loopers.domain.coupon.IssuedCoupon;
import com.loopers.infrastructure.coupon.CouponIssueRequestJpaRepository;
import com.loopers.infrastructure.coupon.IssuedCouponJpaRepository;
import com.loopers.utils.DatabaseCleanUp;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest
@EmbeddedKafka(
        partitions = 1,
        brokerProperties = {"listeners=PLAINTEXT://localhost:0"},
        topics = {"catalog-events", "order-events", "coupon-issue-requests"}
)
class CouponIssueProcessorTest {

    @Autowired
    private CouponIssueProcessor couponIssueProcessor;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @Autowired
    private IssuedCouponJpaRepository issuedCouponJpaRepository;

    @Autowired
    private CouponIssueRequestJpaRepository couponIssueRequestJpaRepository;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("유효한 발급 요청이면 issued_coupon을 생성하고 요청 상태를 SUCCESS로 변경한다.")
    @Test
    void processesIssueRequest() {
        // arrange
        Long couponId = insertCoupon();
        Long requestId = insertPendingRequest(couponId, 1L);

        // act
        couponIssueProcessor.process(requestId, couponId, 1L);

        // assert
        CouponIssueRequest request = couponIssueRequestJpaRepository.findById(requestId).orElseThrow();
        assertAll(
            () -> assertThat(issuedCouponJpaRepository.existsByUserIdAndCouponId(1L, couponId)).isTrue(),
            () -> assertThat(request.getStatus()).isEqualTo(CouponIssueRequest.Status.SUCCESS),
            () -> assertThat(request.getReason()).isNull()
        );
    }

    @DisplayName("이미 발급된 쿠폰이면 요청 상태를 FAILED로 변경한다.")
    @Test
    void failsWhenCouponAlreadyIssued() {
        // arrange
        Long couponId = insertCoupon();
        insertIssuedCoupon(couponId, 1L);
        Long requestId = insertPendingRequest(couponId, 1L);

        // act
        couponIssueProcessor.process(requestId, couponId, 1L);

        // assert
        CouponIssueRequest request = couponIssueRequestJpaRepository.findById(requestId).orElseThrow();
        assertAll(
            () -> assertThat(request.getStatus()).isEqualTo(CouponIssueRequest.Status.FAILED),
            () -> assertThat(request.getReason()).isEqualTo("이미 발급된 쿠폰입니다.")
        );
    }

    private Long insertCoupon() {
        return transactionTemplate.execute(status -> {
            entityManager.createNativeQuery("""
                INSERT INTO coupons (name, discount_type, discount_value, min_order_amount, expires_at, created_at, updated_at)
                VALUES ('이벤트 쿠폰', 'FIXED', 1000, 1000, DATE_ADD(NOW(), INTERVAL 30 DAY), NOW(6), NOW(6))
                """)
                .executeUpdate();
            return ((Number) entityManager.createNativeQuery("SELECT LAST_INSERT_ID()").getSingleResult()).longValue();
        });
    }

    private Long insertPendingRequest(Long couponId, Long userId) {
        return transactionTemplate.execute(status -> {
            entityManager.createNativeQuery("""
                INSERT INTO coupon_issue_requests (coupon_id, user_id, status, created_at, updated_at)
                VALUES (:couponId, :userId, 'PENDING', NOW(6), NOW(6))
                """)
                .setParameter("couponId", couponId)
                .setParameter("userId", userId)
                .executeUpdate();
            return ((Number) entityManager.createNativeQuery("SELECT LAST_INSERT_ID()").getSingleResult()).longValue();
        });
    }

    private void insertIssuedCoupon(Long couponId, Long userId) {
        issuedCouponJpaRepository.save(IssuedCoupon.create(userId, couponId, LocalDateTime.now().plusDays(30)));
    }
}

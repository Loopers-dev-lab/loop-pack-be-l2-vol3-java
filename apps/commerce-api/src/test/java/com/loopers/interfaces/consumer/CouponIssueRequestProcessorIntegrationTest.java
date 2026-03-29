package com.loopers.interfaces.consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import com.loopers.domain.coupon.Coupon;
import com.loopers.domain.coupon.CouponIssueMessage;
import com.loopers.domain.coupon.CouponIssueRequest;
import com.loopers.domain.coupon.CouponIssueRequestStatus;
import com.loopers.domain.coupon.CouponType;
import com.loopers.domain.coupon.UserCoupon;
import com.loopers.infrastructure.coupon.CouponIssueRequestJpaRepository;
import com.loopers.infrastructure.coupon.CouponJpaRepository;
import com.loopers.infrastructure.coupon.UserCouponJpaRepository;
import com.loopers.utils.DatabaseCleanUp;
import java.time.ZonedDateTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class CouponIssueRequestProcessorIntegrationTest {

    private static final ZonedDateTime FUTURE = ZonedDateTime.now().plusYears(1);

    @Autowired
    private CouponIssueRequestProcessor processor;

    @Autowired
    private CouponJpaRepository couponJpaRepository;

    @Autowired
    private CouponIssueRequestJpaRepository couponIssueRequestJpaRepository;

    @Autowired
    private UserCouponJpaRepository userCouponJpaRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("쿠폰 발급 처리 성공")
    @Nested
    class ProcessSuccess {

        @DisplayName("유효한 쿠폰에 최초 발급 요청이면, UserCoupon이 저장되고 요청 상태가 SUCCESS로 변경된다.")
        @Test
        void savesUserCoupon_andMarksSuccess_whenValidRequest() {
            // arrange
            Coupon coupon = couponJpaRepository.save(new Coupon("10% 할인", CouponType.RATE, 10, 0, FUTURE));
            CouponIssueRequest request = couponIssueRequestJpaRepository.save(
                CouponIssueRequest.create(coupon.getId(), 1L)
            );
            CouponIssueMessage message = new CouponIssueMessage(request.getRequestId(), coupon.getId(), 1L);

            // act
            processor.process(message);

            // assert
            CouponIssueRequest updated = couponIssueRequestJpaRepository.findByRequestId(request.getRequestId()).orElseThrow();
            List<UserCoupon> userCoupons = userCouponJpaRepository.findAll();
            assertAll(
                () -> assertThat(updated.getStatus()).isEqualTo(CouponIssueRequestStatus.SUCCESS),
                () -> assertThat(updated.getFailureReason()).isNull(),
                () -> assertThat(userCoupons).hasSize(1),
                () -> assertThat(userCoupons.get(0).getUserId()).isEqualTo(1L),
                () -> assertThat(userCoupons.get(0).getCouponId()).isEqualTo(coupon.getId())
            );
        }

        @DisplayName("maxIssuable이 null인 쿠폰은 수량 제한 없이 발급된다.")
        @Test
        void savesUserCoupon_whenCouponHasNoIssuableLimit() {
            // arrange
            Coupon coupon = couponJpaRepository.save(new Coupon("무제한 쿠폰", CouponType.FIXED, 1000, 0, FUTURE, null));
            CouponIssueRequest request = couponIssueRequestJpaRepository.save(
                CouponIssueRequest.create(coupon.getId(), 1L)
            );
            CouponIssueMessage message = new CouponIssueMessage(request.getRequestId(), coupon.getId(), 1L);

            // act
            processor.process(message);

            // assert
            CouponIssueRequest updated = couponIssueRequestJpaRepository.findByRequestId(request.getRequestId()).orElseThrow();
            assertAll(
                () -> assertThat(updated.getStatus()).isEqualTo(CouponIssueRequestStatus.SUCCESS),
                () -> assertThat(userCouponJpaRepository.findAll()).hasSize(1)
            );
        }
    }

    @DisplayName("쿠폰 없음으로 인한 발급 실패")
    @Nested
    class ProcessFailedCouponNotFound {

        @DisplayName("존재하지 않는 couponId면, 요청 상태가 FAILED로 변경되고 UserCoupon은 저장되지 않는다.")
        @Test
        void marksFailed_whenCouponDoesNotExist() {
            // arrange
            long nonExistentCouponId = 99999L;
            CouponIssueRequest request = couponIssueRequestJpaRepository.save(
                CouponIssueRequest.create(nonExistentCouponId, 1L)
            );
            CouponIssueMessage message = new CouponIssueMessage(request.getRequestId(), nonExistentCouponId, 1L);

            // act
            processor.process(message);

            // assert
            CouponIssueRequest updated = couponIssueRequestJpaRepository.findByRequestId(request.getRequestId()).orElseThrow();
            assertAll(
                () -> assertThat(updated.getStatus()).isEqualTo(CouponIssueRequestStatus.FAILED),
                () -> assertThat(updated.getFailureReason()).isEqualTo("쿠폰을 찾을 수 없거나 만료됨"),
                () -> assertThat(userCouponJpaRepository.findAll()).isEmpty()
            );
        }
    }

    @DisplayName("선착순 마감으로 인한 발급 실패")
    @Nested
    class ProcessFailedLimitExceeded {

        @DisplayName("발급된 수량이 maxIssuable 이상이면, 요청 상태가 FAILED(선착순 마감)로 변경된다.")
        @Test
        void marksFailed_whenIssuedCountReachesMaxIssuable() {
            // arrange
            Coupon coupon = couponJpaRepository.save(new Coupon("선착순 쿠폰", CouponType.FIXED, 1000, 0, FUTURE, 1));
            // 이미 1명 발급 완료 → 선착순 마감
            userCouponJpaRepository.save(new UserCoupon(10L, coupon.getId(), coupon.getName()));

            CouponIssueRequest request = couponIssueRequestJpaRepository.save(
                CouponIssueRequest.create(coupon.getId(), 2L)
            );
            CouponIssueMessage message = new CouponIssueMessage(request.getRequestId(), coupon.getId(), 2L);

            // act
            processor.process(message);

            // assert
            CouponIssueRequest updated = couponIssueRequestJpaRepository.findByRequestId(request.getRequestId()).orElseThrow();
            assertAll(
                () -> assertThat(updated.getStatus()).isEqualTo(CouponIssueRequestStatus.FAILED),
                () -> assertThat(updated.getFailureReason()).isEqualTo("선착순 마감"),
                () -> assertThat(userCouponJpaRepository.countByCouponId(coupon.getId())).isEqualTo(1L)
            );
        }
    }

    @DisplayName("중복 발급으로 인한 발급 실패")
    @Nested
    class ProcessFailedDuplicate {

        @DisplayName("이미 해당 쿠폰을 가진 유저가 재요청하면, 요청 상태가 FAILED(중복 발급)로 변경된다.")
        @Test
        void marksFailed_whenUserAlreadyHasCoupon() {
            // arrange
            Coupon coupon = couponJpaRepository.save(new Coupon("10% 할인", CouponType.RATE, 10, 0, FUTURE));
            long userId = 1L;
            userCouponJpaRepository.save(new UserCoupon(userId, coupon.getId(), coupon.getName()));

            CouponIssueRequest request = couponIssueRequestJpaRepository.save(
                CouponIssueRequest.create(coupon.getId(), userId)
            );
            CouponIssueMessage message = new CouponIssueMessage(request.getRequestId(), coupon.getId(), userId);

            // act
            processor.process(message);

            // assert
            CouponIssueRequest updated = couponIssueRequestJpaRepository.findByRequestId(request.getRequestId()).orElseThrow();
            assertAll(
                () -> assertThat(updated.getStatus()).isEqualTo(CouponIssueRequestStatus.FAILED),
                () -> assertThat(updated.getFailureReason()).isEqualTo("중복 발급"),
                () -> assertThat(userCouponJpaRepository.countByCouponId(coupon.getId())).isEqualTo(1L)
            );
        }
    }
}
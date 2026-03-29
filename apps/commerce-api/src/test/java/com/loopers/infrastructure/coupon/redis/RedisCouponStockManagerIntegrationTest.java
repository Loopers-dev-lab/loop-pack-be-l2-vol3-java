package com.loopers.infrastructure.coupon.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;

import java.time.ZonedDateTime;

import com.loopers.config.redis.RedisConfig;
import com.loopers.domain.coupon.CouponStockManager;
import com.loopers.domain.coupon.CouponIssueStatus;
import com.loopers.support.BaseIntegrationTest;

@DisplayName("RedisCouponStockManager 통합 테스트")
class RedisCouponStockManagerIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private CouponStockManager couponStockManager;

    @Autowired
    @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER)
    private RedisTemplate<String, String> redisTemplate;

    @DisplayName("쿠폰 발급을 시도할 때,")
    @Nested
    class TryIssue {

        @DisplayName("초기화 후 첫 발급이면, SUCCESS를 반환한다.")
        @Test
        void returnsSuccess_whenFirstIssue() {
            // arrange
            Long couponId = 1L;
            Long userId = 100L;
            couponStockManager.initialize(couponId, 10, ZonedDateTime.now().plusDays(30));

            // act
            CouponIssueStatus result = couponStockManager.issueCoupon(couponId, userId);

            // assert
            assertAll(
                    () -> assertThat(result).as("발급 결과").isEqualTo(CouponIssueStatus.SUCCESS),
                    () -> assertThat(redisTemplate.opsForValue().get("coupon:1:stock"))
                            .as("잔여 수량").isEqualTo("9"),
                    () -> assertThat(redisTemplate.opsForSet().isMember("coupon:1:users", "100"))
                            .as("유저 Set 포함 여부").isTrue()
            );
        }

        @DisplayName("동일 사용자가 재시도하면, DUPLICATE를 반환한다.")
        @Test
        void returnsDuplicate_whenSameUserRetries() {
            // arrange
            Long couponId = 1L;
            Long userId = 100L;
            couponStockManager.initialize(couponId, 10, ZonedDateTime.now().plusDays(30));
            couponStockManager.issueCoupon(couponId, userId);

            // act
            CouponIssueStatus result = couponStockManager.issueCoupon(couponId, userId);

            // assert
            assertAll(
                    () -> assertThat(result).as("발급 결과").isEqualTo(CouponIssueStatus.DUPLICATE),
                    () -> assertThat(redisTemplate.opsForValue().get("coupon:1:stock"))
                            .as("잔여 수량 (변하지 않음)").isEqualTo("9")
            );
        }

        @DisplayName("수량이 소진되면, SOLD_OUT을 반환한다.")
        @Test
        void returnsSoldOut_whenQuantityExhausted() {
            // arrange
            Long couponId = 1L;
            couponStockManager.initialize(couponId, 2, ZonedDateTime.now().plusDays(30));
            couponStockManager.issueCoupon(couponId, 1L);
            couponStockManager.issueCoupon(couponId, 2L);

            // act
            CouponIssueStatus result = couponStockManager.issueCoupon(couponId, 3L);

            // assert
            assertAll(
                    () -> assertThat(result).as("발급 결과").isEqualTo(CouponIssueStatus.SOLD_OUT),
                    () -> assertThat(redisTemplate.opsForValue().get("coupon:1:stock"))
                            .as("잔여 수량 (0 미만으로 내려가지 않음)").isEqualTo("0"),
                    () -> assertThat(redisTemplate.opsForSet().isMember("coupon:1:users", "3"))
                            .as("실패한 유저는 Set에 추가되지 않음").isFalse()
            );
        }
    }

    @DisplayName("잔여 수량을 초기화할 때,")
    @Nested
    class Initialize {

        @DisplayName("등록되지 않은 쿠폰이면, 잔여 수량을 저장한다.")
        @Test
        void storesStock_whenNotExists() {
            // act
            couponStockManager.initialize(1L, 100, ZonedDateTime.now().plusDays(30));

            // assert
            assertThat(redisTemplate.opsForValue().get("coupon:1:stock"))
                    .as("잔여 수량").isEqualTo("100");
        }

        @DisplayName("이미 등록된 쿠폰이면, 덮어쓰지 않는다.")
        @Test
        void doesNotOverwrite_whenAlreadyExists() {
            // arrange
            couponStockManager.initialize(1L, 100, ZonedDateTime.now().plusDays(30));

            // act
            couponStockManager.initialize(1L, 200, ZonedDateTime.now().plusDays(30));

            // assert
            assertThat(redisTemplate.opsForValue().get("coupon:1:stock"))
                    .as("잔여 수량 (변하지 않음)").isEqualTo("100");
        }
    }
}

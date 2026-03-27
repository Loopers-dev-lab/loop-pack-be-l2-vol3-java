package com.loopers.infrastructure.coupon.redis;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;

import com.loopers.config.redis.RedisConfig;
import com.loopers.domain.coupon.CouponIssueStatusManager;
import com.loopers.support.BaseIntegrationTest;

@DisplayName("RedisCouponIssueStatusManager 통합 테스트")
class RedisCouponIssueStatusManagerIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private CouponIssueStatusManager couponIssueStatusManager;

    @Autowired
    @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER)
    private RedisTemplate<String, String> redisTemplate;

    @DisplayName("발급 상태를 PENDING으로 저장할 때,")
    @Nested
    class MarkPending {

        @DisplayName("Redis에 PENDING 상태가 저장된다.")
        @Test
        void storesPendingStatus() {
            // arrange
            Long couponId = 1L;
            Long userId = 100L;

            // act
            couponIssueStatusManager.markPending(couponId, userId);

            // assert
            String status = redisTemplate.opsForValue().get("coupon-issue-status:1:100");
            assertThat(status).isEqualTo("PENDING");
        }

        @DisplayName("TTL이 설정된다.")
        @Test
        void setsTtl() {
            // arrange
            Long couponId = 1L;
            Long userId = 100L;

            // act
            couponIssueStatusManager.markPending(couponId, userId);

            // assert
            Long ttl = redisTemplate.getExpire("coupon-issue-status:1:100");
            assertThat(ttl).isGreaterThan(0);
        }
    }

    @DisplayName("발급 상태를 조회할 때,")
    @Nested
    class GetStatus {

        @DisplayName("상태가 존재하면, 상태값을 반환한다.")
        @Test
        void returnsStatus_whenExists() {
            // arrange
            couponIssueStatusManager.markPending(1L, 100L);

            // act
            Optional<String> status = couponIssueStatusManager.getStatus(1L, 100L);

            // assert
            assertThat(status).isPresent().hasValue("PENDING");
        }

        @DisplayName("상태가 없으면, empty를 반환한다.")
        @Test
        void returnsEmpty_whenNotExists() {
            // act
            Optional<String> status = couponIssueStatusManager.getStatus(999L, 999L);

            // assert
            assertThat(status).isEmpty();
        }
    }
}

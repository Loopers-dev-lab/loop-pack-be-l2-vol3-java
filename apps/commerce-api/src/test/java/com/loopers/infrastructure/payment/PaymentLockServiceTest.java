package com.loopers.infrastructure.payment;

import com.loopers.domain.payment.PaymentLock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import org.springframework.data.redis.core.script.RedisScript;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PaymentLockService 단위 테스트")
class PaymentLockServiceTest {

    @Mock RedisTemplate<String, String> redisTemplate;
    @Mock ValueOperations<String, String> valueOperations;

    private PaymentLockService lockService;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        lockService = new PaymentLockService(redisTemplate);
    }

    @Test
    @DisplayName("첫 번째 락 획득 시 true를 반환한다")
    void tryLock_First_ShouldReturnTrue() {
        when(valueOperations.setIfAbsent(eq("payment:lock:100"), any(), any(Duration.class)))
                .thenReturn(true);

        boolean result = lockService.tryLock(100L, "user1:123");

        assertThat(result).isTrue();
        verify(valueOperations).setIfAbsent(eq("payment:lock:100"), eq("user1:123"), any(Duration.class));
    }

    @Test
    @DisplayName("동일 orderId 두 번째 락 획득 시 false를 반환한다")
    void tryLock_Duplicate_ShouldReturnFalse() {
        when(valueOperations.setIfAbsent(eq("payment:lock:100"), any(), any(Duration.class)))
                .thenReturn(false);

        boolean result = lockService.tryLock(100L, "user2:456");

        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("Redis 장애 시 PaymentLockException을 던진다")
    void tryLock_WithRedisFailure_ShouldThrowPaymentLockException() {
        when(valueOperations.setIfAbsent(any(), any(), any(Duration.class)))
                .thenThrow(new RuntimeException("Redis connection refused"));

        assertThatThrownBy(() -> lockService.tryLock(100L, "user1:123"))
                .isInstanceOf(PaymentLock.PaymentLockException.class);
    }

    @Test
    @DisplayName("unlock 시 Lua 스크립트로 owner 검증 후 삭제한다")
    void unlock_ShouldExecuteLuaScriptWithOwnerVerification() {
        when(redisTemplate.execute(any(RedisScript.class), any(List.class), any()))
                .thenReturn(1L);

        lockService.unlock(100L, "user1:123");

        verify(redisTemplate).execute(
                any(RedisScript.class),
                eq(List.of("payment:lock:100")),
                eq("user1:123")
        );
    }

    @Test
    @DisplayName("unlock 시 owner 불일치면 삭제하지 않는다 (Lua 반환값 0)")
    void unlock_WithDifferentOwner_ShouldNotDelete() {
        when(redisTemplate.execute(any(RedisScript.class), any(List.class), any()))
                .thenReturn(0L);

        // 예외 없이 정상 종료 (로그만 남김)
        lockService.unlock(100L, "wrong-owner");

        verify(redisTemplate).execute(
                any(RedisScript.class),
                eq(List.of("payment:lock:100")),
                eq("wrong-owner")
        );
    }

    @Test
    @DisplayName("unlock 중 Redis 장애 시 예외를 삼킨다")
    void unlock_WithRedisFailure_ShouldSwallowException() {
        when(redisTemplate.execute(any(RedisScript.class), any(List.class), any()))
                .thenThrow(new RuntimeException("Redis down"));

        // 예외가 발생하지 않음
        lockService.unlock(100L, "user1:123");
    }

    @Test
    @DisplayName("SETNX 반환값이 null이면 false로 처리한다")
    void tryLock_WithNullReturn_ShouldReturnFalse() {
        when(valueOperations.setIfAbsent(any(), any(), any(Duration.class)))
                .thenReturn(null);

        boolean result = lockService.tryLock(100L, "user1:123");

        assertThat(result).isFalse();
    }
}

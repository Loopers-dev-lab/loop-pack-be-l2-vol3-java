package com.loopers.application.queue;

import com.loopers.config.RateLimitProperties;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("RateLimitModeEvaluator 단위 테스트")
class RateLimitModeEvaluatorTest {

    private RateLimitModeEvaluator evaluator;
    private MeterRegistry meterRegistry;
    private AtomicLong currentThreads;
    private AtomicLong maxThreads;

    @BeforeEach
    void setUp() {
        RateLimitProperties properties = new RateLimitProperties(
                true, 0.85, 0.60, 0, 1000, 10, 10
        );
        meterRegistry = new SimpleMeterRegistry();
        currentThreads = new AtomicLong(0);
        maxThreads = new AtomicLong(200);
        meterRegistry.gauge("tomcat.threads.current", currentThreads);
        meterRegistry.gauge("tomcat.threads.config.max", maxThreads);

        evaluator = new RateLimitModeEvaluator(properties, meterRegistry);
    }

    @Nested
    @DisplayName("INACTIVE → ACTIVE 전환")
    class ActivateTest {

        @Test
        @DisplayName("Tomcat 스레드 사용률 85% 이상이면 ACTIVE")
        void activate_whenAboveThreshold() {
            currentThreads.set(170L);
            evaluator.evaluate();
            assertThat(evaluator.isActive()).isTrue();
        }

        @Test
        @DisplayName("85% 미만이면 INACTIVE 유지")
        void stayInactive_whenBelowThreshold() {
            currentThreads.set(100L);
            evaluator.evaluate();
            assertThat(evaluator.isActive()).isFalse();
        }
    }

    @Nested
    @DisplayName("ACTIVE → INACTIVE 전환")
    class DeactivateTest {

        @Test
        @DisplayName("60% 미만이면 INACTIVE 복귀")
        void deactivate_whenBelowThreshold() {
            currentThreads.set(180L);
            evaluator.evaluate();
            assertThat(evaluator.isActive()).isTrue();

            currentThreads.set(100L);
            evaluator.evaluate();
            assertThat(evaluator.isActive()).isFalse();
        }

        @Test
        @DisplayName("히스테리시스: 60%~85% 사이면 현재 상태 유지")
        void hysteresis_betweenThresholds_maintainsState() {
            currentThreads.set(180L);
            evaluator.evaluate();
            assertThat(evaluator.isActive()).isTrue();

            currentThreads.set(140L);
            evaluator.evaluate();
            assertThat(evaluator.isActive()).as("60~85% 사이에서는 ACTIVE 유지").isTrue();
        }
    }

    @Nested
    @DisplayName("cooldown")
    class CooldownTest {

        @Test
        @DisplayName("cooldown 기간 중에는 비활성화하지 않음")
        void cooldown_preventsDeactivation() {
            RateLimitProperties propsWithCooldown = new RateLimitProperties(
                    true, 0.85, 0.60, 60, 1000, 10, 10
            );
            RateLimitModeEvaluator evalWithCooldown = new RateLimitModeEvaluator(propsWithCooldown, meterRegistry);

            currentThreads.set(180L);
            evalWithCooldown.evaluate();
            assertThat(evalWithCooldown.isActive()).isTrue();

            currentThreads.set(50L);
            evalWithCooldown.evaluate();
            assertThat(evalWithCooldown.isActive()).as("cooldown 60초 내에는 ACTIVE 유지").isTrue();
        }
    }

    @Test
    @DisplayName("enabled=false이면 평가하지 않음")
    void disabled_noEvaluation() {
        RateLimitProperties disabledProps = new RateLimitProperties(
                false, 0.85, 0.60, 0, 1000, 10, 10
        );
        RateLimitModeEvaluator disabledEval = new RateLimitModeEvaluator(disabledProps, meterRegistry);

        currentThreads.set(200L);
        disabledEval.evaluate();
        assertThat(disabledEval.isActive()).isFalse();
    }
}

package com.loopers.verification;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.SimpleApplicationEventMulticaster;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ApplicationEvent 한계 검증 — Step 2(Outbox + Kafka) 전환 근거")
class ApplicationEventLimitationTest {

    // ──────────────────────────────────────────────────────────────────────────
    // 한계 1: JVM 경계
    // ──────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("한계 1: JVM 경계 — ApplicationEvent는 같은 Multicaster(= 같은 ApplicationContext) 안에서만 전달된다")
    class JvmBoundaryTest {

        // Spring의 이벤트 전달 핵심 컴포넌트: SimpleApplicationEventMulticaster
        // 각 ApplicationContext 인스턴스는 자체 Multicaster를 보유한다.
        // commerce-api와 commerce-streamer는 각각 독립적인 JVM 프로세스 = 독립적인 Multicaster.

        @Test
        @DisplayName("서로 다른 Multicaster(= 서로 다른 ApplicationContext/프로세스) 간에는 이벤트가 전달되지 않는다")
        void event_isNotDelivered_acrossMulticasters() {
            // commerce-api의 Multicaster (Spring이 각 ApplicationContext에 1개씩 생성)
            SimpleApplicationEventMulticaster apiMulticaster = new SimpleApplicationEventMulticaster();
            // commerce-streamer의 Multicaster (별도 JVM 프로세스의 ApplicationContext)
            SimpleApplicationEventMulticaster streamerMulticaster = new SimpleApplicationEventMulticaster();

            AtomicBoolean streamerReceived = new AtomicBoolean(false);

            // commerce-streamer 리스너 등록 (streamer 쪽 Multicaster에만)
            streamerMulticaster.addApplicationListener(
                    (ApplicationListener<ApplicationEvent>) event -> streamerReceived.set(true)
            );

            // commerce-api에서 이벤트 발행 → api Multicaster만 notify
            ApplicationEvent likedEvent = new ApplicationEvent("LikedEvent") {};
            apiMulticaster.multicastEvent(likedEvent);

            assertThat(streamerReceived.get())
                    .as("commerce-streamer(별도 Multicaster = 별도 JVM 프로세스)는 commerce-api가 발행한 이벤트를 수신하지 못한다")
                    .isFalse();
        }

        @Test
        @DisplayName("같은 Multicaster 안에서는 이벤트가 정상 전달된다 — 대조군")
        void event_isDelivered_withinSameMulticaster() {
            SimpleApplicationEventMulticaster multicaster = new SimpleApplicationEventMulticaster();
            AtomicBoolean received = new AtomicBoolean(false);

            multicaster.addApplicationListener(
                    (ApplicationListener<ApplicationEvent>) event -> received.set(true)
            );

            multicaster.multicastEvent(new ApplicationEvent("LikedEvent") {});

            assertThat(received.get())
                    .as("같은 Multicaster(= 같은 ApplicationContext) 내 리스너는 이벤트를 정상 수신한다")
                    .isTrue();
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 한계 2: 재시작 소실
    // ──────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("한계 2: 내구성 없음 — JVM 종료 시 AFTER_COMMIT 이벤트가 소실된다")
    class DurabilityTest {

        @Test
        @DisplayName("실행 중인 AFTER_COMMIT 핸들러는 JVM 종료(shutdownNow) 시 완료되지 않는다")
        void afterCommitHandler_isInterrupted_whenExecutorTerminatedAbruptly() throws InterruptedException {
            AtomicBoolean handlerCompleted = new AtomicBoolean(false);
            CountDownLatch handlerStarted = new CountDownLatch(1);

            ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
            executor.setCorePoolSize(1);
            executor.initialize();

            // AFTER_COMMIT 후 @Async 실행 시뮬레이션
            // 핸들러가 DB 쓰기 등을 처리하는 도중 JVM이 종료된다고 가정
            executor.submit(() -> {
                handlerStarted.countDown();
                try {
                    Thread.sleep(10_000); // DB 쓰기 시뮬레이션 (10초)
                    handlerCompleted.set(true); // JVM kill 이전에 도달 불가
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt(); // JVM kill 시 InterruptedException 발생
                }
            });

            handlerStarted.await(3, TimeUnit.SECONDS); // 핸들러 진입 확인

            // JVM 강제 종료 시뮬레이션: shutdownNow() = SIGKILL과 유사 — 실행 중 스레드에 interrupt
            executor.getThreadPoolExecutor().shutdownNow();
            executor.getThreadPoolExecutor().awaitTermination(2, TimeUnit.SECONDS);

            assertThat(handlerCompleted.get())
                    .as("JVM 종료 시 처리 중이던 AFTER_COMMIT 핸들러는 완료되지 않는다 — 재시작 후 재실행 메커니즘도 없음")
                    .isFalse();
        }

        @Test
        @DisplayName("executor queue에 대기 중인 AFTER_COMMIT 이벤트는 JVM 종료 시 함께 소실된다")
        void pendingAfterCommitEvent_isLost_whenExecutorQueueCleared() throws InterruptedException {
            AtomicBoolean handlerExecuted = new AtomicBoolean(false);
            CountDownLatch blockExecutor = new CountDownLatch(1);

            ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
            executor.setCorePoolSize(1);   // 스레드 1개 — 두 번째 submit은 queue에 대기
            executor.setQueueCapacity(10);
            executor.initialize();

            // 첫 번째 task: 스레드를 점유해 두 번째 task가 queue에 머물게 만든다
            executor.submit(() -> {
                try {
                    blockExecutor.await(10, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });

            // 두 번째 task: queue에 대기 중인 AFTER_COMMIT 핸들러 (메모리에만 존재)
            executor.submit(() -> handlerExecuted.set(true));

            // JVM kill 시뮬레이션: shutdownNow()는 queue에 있는 task 목록을 반환하고 모두 폐기
            List<Runnable> droppedTasks = executor.getThreadPoolExecutor().shutdownNow();
            executor.getThreadPoolExecutor().awaitTermination(2, TimeUnit.SECONDS);

            assertThat(droppedTasks)
                    .as("shutdownNow()는 queue에 대기 중이던 task를 반환한다 — 이 task들은 실행되지 않고 소실된다")
                    .isNotEmpty();
            assertThat(handlerExecuted.get())
                    .as("소실된 AFTER_COMMIT 이벤트의 핸들러는 실행되지 않는다")
                    .isFalse();
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 한계 3: 재시도 불가
    // ──────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("한계 3: 재시도 불가 — @Async 핸들러 예외는 AsyncUncaughtExceptionHandler에서 끝난다")
    class NoRetryTest {

        @Test
        @DisplayName("@Async 핸들러 예외 발생 시 AsyncUncaughtExceptionHandler가 정확히 1회 호출되고 재시도는 없다")
        void asyncHandler_onException_callsUncaughtHandlerOnce_noRetry() throws InterruptedException {
            AtomicInteger uncaughtHandlerCallCount = new AtomicInteger(0);
            AtomicReference<String> capturedMessage = new AtomicReference<>();
            CountDownLatch latch = new CountDownLatch(1);

            // 실제 EventHandlerAsyncExceptionHandler와 동일한 역할
            AsyncUncaughtExceptionHandler uncaughtHandler = (ex, method, params) -> {
                capturedMessage.set(ex.getMessage());
                uncaughtHandlerCallCount.incrementAndGet();
                latch.countDown();
            };

            ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
            executor.initialize();

            // Spring AsyncExecutionInterceptor 동작 재현:
            // @Async void 메서드 예외 → AsyncUncaughtExceptionHandler 위임, 이후 끝
            executor.submit(() -> {
                try {
                    throw new RuntimeException("[FAIL] 주문 후처리 실패 — product_metrics 집계 불가");
                } catch (Exception e) {
                    uncaughtHandler.handleUncaughtException(e, getToStringMethod(), new Object[0]);
                    // Spring에는 여기서 재시도 메커니즘이 없음
                }
            });

            assertThat(latch.await(3, TimeUnit.SECONDS)).isTrue();
            assertThat(capturedMessage.get()).isEqualTo("[FAIL] 주문 후처리 실패 — product_metrics 집계 불가");
            assertThat(uncaughtHandlerCallCount.get())
                    .as("AsyncUncaughtExceptionHandler는 정확히 1회 호출된다")
                    .isEqualTo(1);

            // 2초 추가 대기 후 재시도 여부 확인
            Thread.sleep(2_000);

            assertThat(uncaughtHandlerCallCount.get())
                    .as("Spring @Async에는 재시도 메커니즘이 없다 — 핸들러 실패 = 후처리 영구 누락")
                    .isEqualTo(1);

            executor.destroy();
        }

        @Test
        @DisplayName("@Async 핸들러 예외는 이벤트 발행자(API 요청 스레드)에게 전파되지 않는다 — 주문은 성공한다")
        void asyncHandler_exception_doesNotPropagateToPublisher() throws Exception {
            AtomicBoolean publisherThrewException = new AtomicBoolean(false);
            CountDownLatch handlerCompleted = new CountDownLatch(1);

            ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
            executor.initialize();

            // 이벤트 발행자 (주문 API 요청 스레드) 시뮬레이션
            Future<?> publisherTask = Executors.newSingleThreadExecutor().submit(() -> {
                try {
                    // TX 커밋 후 AFTER_COMMIT 핸들러를 async executor에 제출하고 바로 리턴
                    executor.submit(() -> {
                        try {
                            throw new RuntimeException("[FAIL] 주문 후처리 실패");
                        } finally {
                            handlerCompleted.countDown();
                        }
                    });
                    // 발행자는 바로 리턴 (200 OK 반환)
                } catch (Exception e) {
                    publisherThrewException.set(true);
                }
            });

            publisherTask.get(3, TimeUnit.SECONDS);
            handlerCompleted.await(3, TimeUnit.SECONDS);

            assertThat(publisherThrewException.get())
                    .as("@Async 핸들러 예외는 이벤트 발행자(API 요청 스레드)에게 전파되지 않는다 — HTTP 200 OK 반환")
                    .isFalse();

            executor.destroy();
        }
    }

    private Method getToStringMethod() {
        try {
            return Object.class.getMethod("toString");
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }
}

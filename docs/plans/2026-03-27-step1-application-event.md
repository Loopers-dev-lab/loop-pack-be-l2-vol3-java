# Step 1: ApplicationEvent로 경계 나누기 — Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 주문-결제, 좋아요 플로우에서 부가 로직을 ApplicationEvent로 분리하고, 유저 행동 로깅을 이벤트 기반으로 추가한다.

**Architecture:** Facade가 주요 로직 완료 후 도메인 이벤트를 발행하고, 관심사별 리스너가 `@TransactionalEventListener(AFTER_COMMIT)`으로 부가 로직을 처리한다. 이벤트 클래스는 domain 패키지, 리스너는 application 패키지에 배치한다.

**Tech Stack:** Spring ApplicationEvent, @TransactionalEventListener, @Async (알림 등 느린 작업용)

---

## Task 1: LikeToggledEvent 정의 + 발행

좋아요 이벤트부터 시작. 가장 단순하고 변경 범위가 좁다.

**Files:**
- Create: `apps/commerce-api/src/main/java/com/loopers/domain/like/event/LikeToggledEvent.java`
- Create: `apps/commerce-api/src/test/java/com/loopers/domain/like/event/LikeToggledEventTest.java`

**Step 1: 이벤트 record 작성**

```java
package com.loopers.domain.like.event;

public record LikeToggledEvent(
    Long productId,
    boolean liked  // true: 좋아요, false: 취소
) {}
```

**Step 2: 이벤트 생성 단위 테스트**

```java
package com.loopers.domain.like.event;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class LikeToggledEventTest {

    @DisplayName("좋아요 이벤트 생성 시 productId와 liked 상태를 가진다")
    @Test
    void createLikeToggledEvent() {
        // given
        Long productId = 1L;

        // when
        LikeToggledEvent event = new LikeToggledEvent(productId, true);

        // then
        assertThat(event.productId()).isEqualTo(1L);
        assertThat(event.liked()).isTrue();
    }
}
```

**Step 3: 테스트 실행**

Run: `./gradlew :apps:commerce-api:test --tests "com.loopers.domain.like.event.LikeToggledEventTest" -i`
Expected: PASS

---

## Task 2: LikeTransactionService에서 이벤트 발행으로 전환

집계/캐시 직접 호출을 이벤트 발행으로 교체한다.

**Files:**
- Modify: `apps/commerce-api/src/main/java/com/loopers/application/like/LikeTransactionService.java`
- Create: `apps/commerce-api/src/test/java/com/loopers/application/like/LikeTransactionServiceTest.java`

**Step 1: 실패하는 테스트 작성 — 좋아요 시 이벤트 발행 검증**

```java
package com.loopers.application.like;

import com.loopers.application.product.ProductService;
import com.loopers.domain.like.LikeModel;
import com.loopers.domain.like.LikeResult;
import com.loopers.domain.like.LikeToggleService;
import com.loopers.domain.like.event.LikeToggledEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class LikeTransactionServiceTest {

    @InjectMocks
    private LikeTransactionService likeTransactionService;

    @Mock
    private LikeService likeService;

    @Mock
    private LikeToggleService likeToggleService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @DisplayName("좋아요 등록")
    @Nested
    class DoLike {

        @DisplayName("새 좋아요가 생성되면 LikeToggledEvent(liked=true)를 발행한다")
        @Test
        void publishesLikeToggledEventWhenNewLikeCreated() {
            // given
            Long userId = 1L;
            Long productId = 100L;
            LikeModel newLike = new LikeModel(userId, productId);
            LikeResult result = new LikeResult(Optional.of(newLike), true);

            given(likeService.findByUserIdAndProductId(userId, productId)).willReturn(Optional.empty());
            given(likeToggleService.like(Optional.empty(), userId, productId)).willReturn(result);

            // when
            likeTransactionService.doLike(userId, productId);

            // then
            ArgumentCaptor<LikeToggledEvent> captor = ArgumentCaptor.forClass(LikeToggledEvent.class);
            then(eventPublisher).should().publishEvent(captor.capture());
            assertThat(captor.getValue().productId()).isEqualTo(100L);
            assertThat(captor.getValue().liked()).isTrue();
        }

        @DisplayName("이미 좋아요 상태면 이벤트를 발행하지 않는다")
        @Test
        void doesNotPublishEventWhenAlreadyLiked() {
            // given
            Long userId = 1L;
            Long productId = 100L;
            LikeModel existing = new LikeModel(userId, productId);
            LikeResult result = new LikeResult(Optional.empty(), false);

            given(likeService.findByUserIdAndProductId(userId, productId)).willReturn(Optional.of(existing));
            given(likeToggleService.like(Optional.of(existing), userId, productId)).willReturn(result);

            // when
            likeTransactionService.doLike(userId, productId);

            // then
            then(eventPublisher).should(never()).publishEvent(org.mockito.ArgumentMatchers.any(LikeToggledEvent.class));
        }
    }

    @DisplayName("좋아요 취소")
    @Nested
    class DoUnlike {

        @DisplayName("활성 좋아요가 있으면 LikeToggledEvent(liked=false)를 발행한다")
        @Test
        void publishesLikeToggledEventWhenUnliked() {
            // given
            Long userId = 1L;
            Long productId = 100L;
            LikeModel activeLike = new LikeModel(userId, productId);

            given(likeService.findActiveLike(userId, productId)).willReturn(Optional.of(activeLike));

            // when
            likeTransactionService.doUnlike(userId, productId);

            // then
            ArgumentCaptor<LikeToggledEvent> captor = ArgumentCaptor.forClass(LikeToggledEvent.class);
            then(eventPublisher).should().publishEvent(captor.capture());
            assertThat(captor.getValue().productId()).isEqualTo(100L);
            assertThat(captor.getValue().liked()).isFalse();
        }
    }
}
```

**Step 2: 테스트 실행 — 실패 확인**

Run: `./gradlew :apps:commerce-api:test --tests "com.loopers.application.like.LikeTransactionServiceTest" -i`
Expected: FAIL — LikeTransactionService에 ApplicationEventPublisher가 없음

**Step 3: LikeTransactionService 수정 — 이벤트 발행으로 전환**

```java
package com.loopers.application.like;

import com.loopers.domain.like.LikeResult;
import com.loopers.domain.like.LikeModel;
import com.loopers.domain.like.LikeToggleService;
import com.loopers.domain.like.event.LikeToggledEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@RequiredArgsConstructor
@Component
public class LikeTransactionService {

    private final LikeService likeService;
    private final LikeToggleService likeToggleService;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public void doLike(Long userId, Long productId) {
        Optional<LikeModel> existing = likeService.findByUserIdAndProductId(userId, productId);

        LikeResult result = likeToggleService.like(existing, userId, productId);
        result.newLike().ifPresent(likeService::save);

        if (result.countChanged()) {
            eventPublisher.publishEvent(new LikeToggledEvent(productId, true));
        }
    }

    @Transactional
    public void doUnlike(Long userId, Long productId) {
        Optional<LikeModel> activeLike = likeService.findActiveLike(userId, productId);
        if (activeLike.isEmpty()) return;

        likeToggleService.unlike(activeLike.get());
        eventPublisher.publishEvent(new LikeToggledEvent(productId, false));
    }
}
```

핵심 변경: `ProductService`, `CacheManager` 의존 제거 → `ApplicationEventPublisher`로 대체.

**Step 4: 테스트 실행 — 성공 확인**

Run: `./gradlew :apps:commerce-api:test --tests "com.loopers.application.like.LikeTransactionServiceTest" -i`
Expected: PASS

**Step 5: 기존 테스트 확인 — LikeFacadeTest, LikeTransactionCacheTest 등**

Run: `./gradlew :apps:commerce-api:test --tests "com.loopers.application.like.*" -i`
Expected: 기존 테스트 중 ProductService/CacheManager mock에 의존하는 것이 깨질 수 있음 → 다음 Task에서 수정

---

## Task 3: LikeMetricsEventListener 구현

좋아요 집계(like_count) + 캐시 evict을 담당하는 리스너.

**Files:**
- Create: `apps/commerce-api/src/main/java/com/loopers/application/like/LikeMetricsEventListener.java`
- Create: `apps/commerce-api/src/test/java/com/loopers/application/like/LikeMetricsEventListenerTest.java`

**Step 1: 실패하는 테스트 작성**

```java
package com.loopers.application.like;

import com.loopers.application.product.ProductService;
import com.loopers.domain.like.event.LikeToggledEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class LikeMetricsEventListenerTest {

    @InjectMocks
    private LikeMetricsEventListener listener;

    @Mock
    private ProductService productService;

    @Mock
    private CacheManager cacheManager;

    @Mock
    private Cache cache;

    @DisplayName("liked=true 이벤트 수신 시 like_count 증가 + 캐시 evict")
    @Test
    void incrementsLikeCountAndEvictsCache() {
        // given
        LikeToggledEvent event = new LikeToggledEvent(100L, true);
        given(cacheManager.getCache("productDetail")).willReturn(cache);

        // when
        listener.handleLikeMetrics(event);

        // then
        then(productService).should().incrementLikeCount(100L);
        then(cache).should().evict(100L);
    }

    @DisplayName("liked=false 이벤트 수신 시 like_count 감소 + 캐시 evict")
    @Test
    void decrementsLikeCountAndEvictsCache() {
        // given
        LikeToggledEvent event = new LikeToggledEvent(100L, false);
        given(cacheManager.getCache("productDetail")).willReturn(cache);

        // when
        listener.handleLikeMetrics(event);

        // then
        then(productService).should().decrementLikeCount(100L);
        then(cache).should().evict(100L);
    }
}
```

**Step 2: 테스트 실행 — 실패 확인**

Run: `./gradlew :apps:commerce-api:test --tests "com.loopers.application.like.LikeMetricsEventListenerTest" -i`
Expected: FAIL — 클래스 없음

**Step 3: 리스너 구현**

```java
package com.loopers.application.like;

import com.loopers.application.product.ProductService;
import com.loopers.domain.like.event.LikeToggledEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@RequiredArgsConstructor
@Component
public class LikeMetricsEventListener {

    private final ProductService productService;
    private final CacheManager cacheManager;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handleLikeMetrics(LikeToggledEvent event) {
        if (event.liked()) {
            productService.incrementLikeCount(event.productId());
        } else {
            productService.decrementLikeCount(event.productId());
        }
        evictProductDetailCache(event.productId());
        log.info("좋아요 집계 처리: productId={}, liked={}", event.productId(), event.liked());
    }

    private void evictProductDetailCache(Long productId) {
        var cache = cacheManager.getCache("productDetail");
        if (cache != null) {
            cache.evict(productId);
        }
    }
}
```

**Step 4: 테스트 실행 — 성공 확인**

Run: `./gradlew :apps:commerce-api:test --tests "com.loopers.application.like.LikeMetricsEventListenerTest" -i`
Expected: PASS

---

## Task 4: 기존 좋아요 테스트 수정 + 통합 확인

LikeTransactionService의 의존성이 바뀌었으므로 기존 테스트를 업데이트한다.

**Files:**
- Modify: `apps/commerce-api/src/test/java/com/loopers/application/like/LikeFacadeTest.java` (필요 시)
- Modify: `apps/commerce-api/src/test/java/com/loopers/application/like/LikeTransactionCacheTest.java` (필요 시)

**Step 1: 전체 좋아요 테스트 실행하여 깨진 테스트 확인**

Run: `./gradlew :apps:commerce-api:test --tests "com.loopers.application.like.*" --tests "com.loopers.domain.like.*" -i`

**Step 2: 깨진 테스트를 새 구조에 맞게 수정**

구체적 수정 내용은 Step 1 결과에 따라 결정. 주로:
- `ProductService`, `CacheManager` mock 제거
- `ApplicationEventPublisher` mock 추가
- 이벤트 발행 검증으로 assertion 변경

**Step 3: 전체 테스트 실행**

Run: `./gradlew :apps:commerce-api:test -i`
Expected: ALL PASS

---

## Task 5: OrderPlacedEvent + PaymentCompletedEvent 정의

주문/결제 도메인 이벤트를 정의한다.

**Files:**
- Create: `apps/commerce-api/src/main/java/com/loopers/domain/order/event/OrderPlacedEvent.java`
- Create: `apps/commerce-api/src/main/java/com/loopers/domain/payment/event/PaymentCompletedEvent.java`

**Step 1: 이벤트 record 작성**

```java
package com.loopers.domain.order.event;

public record OrderPlacedEvent(
    Long orderId,
    Long userId,
    Long totalAmountValue
) {}
```

```java
package com.loopers.domain.payment.event;

public record PaymentCompletedEvent(
    Long paymentId,
    Long orderId,
    Long userId,
    boolean success  // true: 결제 성공, false: 결제 실패
) {}
```

---

## Task 6: OrderFacade에서 OrderPlacedEvent 발행

**Files:**
- Modify: `apps/commerce-api/src/main/java/com/loopers/application/order/OrderFacade.java`
- Modify: `apps/commerce-api/src/test/java/com/loopers/application/order/OrderFacadeTest.java`

**Step 1: 실패하는 테스트 작성 — 주문 생성 시 이벤트 발행 검증**

```java
// OrderFacadeTest에 추가
@DisplayName("주문 생성 성공 시 OrderPlacedEvent를 발행한다")
@Test
void publishesOrderPlacedEventWhenOrderCreated() {
    // given - 기존 placeOrder 성공 setup 재사용
    // ... (기존 mock setup)

    // when
    orderFacade.placeOrder(userId, commands, null);

    // then
    ArgumentCaptor<OrderPlacedEvent> captor = ArgumentCaptor.forClass(OrderPlacedEvent.class);
    then(eventPublisher).should().publishEvent(captor.capture());
    assertThat(captor.getValue().userId()).isEqualTo(userId);
}
```

**Step 2: OrderFacade에 ApplicationEventPublisher 추가 + placeOrder 끝에 이벤트 발행**

```java
// OrderFacade에 필드 추가
private final ApplicationEventPublisher eventPublisher;

// placeOrder 메서드 마지막에 추가 (return 직전)
eventPublisher.publishEvent(new OrderPlacedEvent(
    order.getId(), userId, totalAmount.value()
));
```

**Step 3: 테스트 실행**

Run: `./gradlew :apps:commerce-api:test --tests "com.loopers.application.order.OrderFacadeTest" -i`
Expected: PASS

---

## Task 7: PaymentFacade에서 PaymentCompletedEvent 발행

**Files:**
- Modify: `apps/commerce-api/src/main/java/com/loopers/application/payment/PaymentFacade.java`
- Modify: `apps/commerce-api/src/test/java/com/loopers/application/payment/PaymentFacadeTest.java`

**Step 1: 실패하는 테스트 — 결제 콜백 성공 시 이벤트 발행 검증**

```java
// PaymentFacadeTest에 추가
@DisplayName("결제 성공 콜백 시 PaymentCompletedEvent(success=true)를 발행한다")
@Test
void publishesPaymentCompletedEventOnSuccess() {
    // given
    // ... (기존 handleCallback 성공 setup)

    // when
    paymentFacade.handleCallback(transactionKey, "SUCCESS", null);

    // then
    ArgumentCaptor<PaymentCompletedEvent> captor = ArgumentCaptor.forClass(PaymentCompletedEvent.class);
    then(eventPublisher).should().publishEvent(captor.capture());
    assertThat(captor.getValue().success()).isTrue();
}
```

**Step 2: PaymentFacade.handleCallback()에 이벤트 발행 추가**

```java
// handleCallback 메서드 끝에 추가
eventPublisher.publishEvent(new PaymentCompletedEvent(
    payment.getId(), payment.orderId(), payment.userId(), "SUCCESS".equals(status)
));
```

**Step 3: 테스트 실행**

Run: `./gradlew :apps:commerce-api:test --tests "com.loopers.application.payment.PaymentFacadeTest" -i`
Expected: PASS

---

## Task 8: UserActivityEventListener 구현

모든 도메인 이벤트를 구독하여 유저 행동 로깅을 처리한다.

**Files:**
- Create: `apps/commerce-api/src/main/java/com/loopers/application/logging/UserActivityEventListener.java`
- Create: `apps/commerce-api/src/test/java/com/loopers/application/logging/UserActivityEventListenerTest.java`

**Step 1: 실패하는 테스트 작성**

```java
package com.loopers.application.logging;

import com.loopers.domain.like.event.LikeToggledEvent;
import com.loopers.domain.order.event.OrderPlacedEvent;
import com.loopers.domain.payment.event.PaymentCompletedEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatNoException;

@ExtendWith(MockitoExtension.class)
class UserActivityEventListenerTest {

    @InjectMocks
    private UserActivityEventListener listener;

    @DisplayName("주문 이벤트 수신 시 로깅 처리한다")
    @Test
    void logsOrderPlacedEvent() {
        assertThatNoException().isThrownBy(() ->
            listener.handleOrderPlaced(new OrderPlacedEvent(1L, 1L, 50000L))
        );
    }

    @DisplayName("결제 이벤트 수신 시 로깅 처리한다")
    @Test
    void logsPaymentCompletedEvent() {
        assertThatNoException().isThrownBy(() ->
            listener.handlePaymentCompleted(new PaymentCompletedEvent(1L, 1L, 1L, true))
        );
    }

    @DisplayName("좋아요 이벤트 수신 시 로깅 처리한다")
    @Test
    void logsLikeToggledEvent() {
        assertThatNoException().isThrownBy(() ->
            listener.handleLikeToggled(new LikeToggledEvent(1L, true))
        );
    }
}
```

**Step 2: 리스너 구현**

```java
package com.loopers.application.logging;

import com.loopers.domain.like.event.LikeToggledEvent;
import com.loopers.domain.order.event.OrderPlacedEvent;
import com.loopers.domain.payment.event.PaymentCompletedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
public class UserActivityEventListener {

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleOrderPlaced(OrderPlacedEvent event) {
        log.info("[UserActivity] 주문 생성 - userId={}, orderId={}, amount={}",
            event.userId(), event.orderId(), event.totalAmountValue());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handlePaymentCompleted(PaymentCompletedEvent event) {
        log.info("[UserActivity] 결제 {} - userId={}, orderId={}, paymentId={}",
            event.success() ? "성공" : "실패", event.userId(), event.orderId(), event.paymentId());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleLikeToggled(LikeToggledEvent event) {
        log.info("[UserActivity] 좋아요 {} - productId={}",
            event.liked() ? "등록" : "취소", event.productId());
    }
}
```

**Step 3: 테스트 실행**

Run: `./gradlew :apps:commerce-api:test --tests "com.loopers.application.logging.UserActivityEventListenerTest" -i`
Expected: PASS

---

## Task 9: 통합 테스트 + E2E 확인

전체 이벤트 플로우가 실제 Spring Context에서 동작하는지 확인한다.

**Files:**
- Modify: `apps/commerce-api/src/test/java/com/loopers/application/like/LikeFacadeIntegrationTest.java`
- 실행: 기존 E2E 테스트 전체

**Step 1: 전체 테스트 실행**

Run: `./gradlew :apps:commerce-api:test -i`
Expected: ALL PASS

**Step 2: E2E 테스트로 이벤트 동작 확인**

Run: `./gradlew :apps:commerce-api:test --tests "*E2ETest" -i`
Expected: ALL PASS — 이벤트가 실제로 발행/소비되어 like_count가 정상 반영

---

## Task 10: .http 파일 업데이트 + 최종 정리

**Files:**
- Modify: `.http/` 디렉토리의 관련 .http 파일 (있다면)

**Step 1: 전체 빌드**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL

**Step 2: 불필요한 import 정리, unused 코드 제거**

LikeTransactionService에서 제거된 ProductService, CacheManager import 확인.

---

## 체크리스트 (과제 요구사항 매핑)

- [x] 주문–결제 플로우에서 부가 로직을 이벤트 기반으로 분리 (Task 6, 7)
- [x] 좋아요 처리와 집계를 이벤트 기반으로 분리 (Task 2, 3)
- [x] 유저 행동 로깅을 이벤트로 처리 (Task 8)
- [x] 트랜잭션 간의 연관관계 고려 — AFTER_COMMIT + REQUIRES_NEW (Task 3)

# Step 2: 입장 토큰 & 스케줄러 Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 스케줄러가 대기열에서 배치로 유저를 꺼내 입장 토큰을 발급하고, Interceptor가 주문 API 접근 시 토큰을 검증하는 시스템 구축

**Architecture:** QueueTokenRepository(interface)→QueueTokenRepositoryImpl(Redis String+TTL)로 토큰 CRUD, QueueEntryScheduler가 3초 주기로 popFront→토큰 발급, QueueTokenInterceptor가 /api/v1/orders/** 요청에서 토큰 검증. QueueFacade가 position 조회 시 토큰 정보 포함.

**Tech Stack:** Spring Boot 3.4.4, Redis (String + TTL), @Scheduled, HandlerInterceptor, JUnit 5, Mockito, TestContainers

**Step 1 완료 상태:** QueueRepository, QueueRepositoryImpl, QueueService, QueueFacade, QueueInfo, QueueV1Controller, QueueV1ApiSpec, QueueV1Dto 모두 구현됨.

---

### Task 1: QueueTokenRepository 인터페이스

**Files:**
- Create: `apps/commerce-api/src/main/java/com/loopers/domain/queue/QueueTokenRepository.java`

**Step 1: 인터페이스 작성**

```java
package com.loopers.domain.queue;

import java.util.Optional;

public interface QueueTokenRepository {

    void issueToken(String eventId, Long userId, String token, long ttlSeconds);

    Optional<String> getToken(String eventId, Long userId);

    long getTokenTtl(String eventId, Long userId);

    void removeToken(String eventId, Long userId);
}
```

**Step 2: 컴파일 확인**

Run: `./gradlew :apps:commerce-api:compileJava`
Expected: BUILD SUCCESSFUL

---

### Task 2: QueueTokenRepositoryImpl 구현

**Files:**
- Create: `apps/commerce-api/src/main/java/com/loopers/infrastructure/queue/QueueTokenRepositoryImpl.java`
- Test: `apps/commerce-api/src/test/java/com/loopers/infrastructure/queue/QueueTokenRepositoryImplIntegrationTest.java`

**Step 1: 통합 테스트 작성**

```java
package com.loopers.infrastructure.queue;

import com.loopers.domain.queue.QueueTokenRepository;
import com.loopers.testcontainers.MySqlTestContainersConfig;
import com.loopers.testcontainers.RedisTestContainersConfig;
import com.loopers.utils.RedisCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import({MySqlTestContainersConfig.class, RedisTestContainersConfig.class})
@ActiveProfiles("test")
class QueueTokenRepositoryImplIntegrationTest {

    @Autowired
    private QueueTokenRepository queueTokenRepository;

    @Autowired
    private RedisCleanUp redisCleanUp;

    @AfterEach
    void tearDown() {
        redisCleanUp.truncateAll();
    }

    @Nested
    @DisplayName("issueToken & getToken")
    class IssueAndGet {

        @Test
        @DisplayName("토큰을 발급하면 조회할 수 있다")
        void 토큰을_발급하면_조회할_수_있다() {
            // given
            String eventId = "event-1";
            Long userId = 1L;
            String token = "tok_abc123";

            // when
            queueTokenRepository.issueToken(eventId, userId, token, 300);

            // then
            Optional<String> result = queueTokenRepository.getToken(eventId, userId);
            assertThat(result).isPresent().hasValue(token);
        }

        @Test
        @DisplayName("토큰이 없는 유저는 empty를 반환한다")
        void 토큰이_없는_유저는_empty를_반환한다() {
            // when
            Optional<String> result = queueTokenRepository.getToken("event-1", 999L);

            // then
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("getTokenTtl")
    class GetTokenTtl {

        @Test
        @DisplayName("발급된 토큰의 TTL을 반환한다")
        void 발급된_토큰의_TTL을_반환한다() {
            // given
            queueTokenRepository.issueToken("event-1", 1L, "tok_abc", 300);

            // when
            long ttl = queueTokenRepository.getTokenTtl("event-1", 1L);

            // then
            assertThat(ttl).isBetween(295L, 300L);
        }

        @Test
        @DisplayName("토큰이 없으면 -2를 반환한다")
        void 토큰이_없으면_음수를_반환한다() {
            // when
            long ttl = queueTokenRepository.getTokenTtl("event-1", 999L);

            // then
            assertThat(ttl).isNegative();
        }
    }

    @Nested
    @DisplayName("removeToken")
    class RemoveToken {

        @Test
        @DisplayName("토큰을 삭제하면 조회되지 않는다")
        void 토큰을_삭제하면_조회되지_않는다() {
            // given
            queueTokenRepository.issueToken("event-1", 1L, "tok_abc", 300);

            // when
            queueTokenRepository.removeToken("event-1", 1L);

            // then
            assertThat(queueTokenRepository.getToken("event-1", 1L)).isEmpty();
        }
    }
}
```

**Step 2: 테스트 실패 확인**

Run: `./gradlew :apps:commerce-api:test --tests "QueueTokenRepositoryImplIntegrationTest"`
Expected: FAIL (QueueTokenRepositoryImpl 미존재)

**Step 3: 구현체 작성**

```java
package com.loopers.infrastructure.queue;

import com.loopers.config.redis.RedisConfig;
import com.loopers.domain.queue.QueueTokenRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Component
public class QueueTokenRepositoryImpl implements QueueTokenRepository {

    private static final String KEY_PREFIX = "queue:";
    private static final String KEY_TOKEN_INFIX = ":token:";

    private final RedisTemplate<String, String> redisTemplate;

    public QueueTokenRepositoryImpl(
            @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER) RedisTemplate<String, String> redisTemplate
    ) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void issueToken(String eventId, Long userId, String token, long ttlSeconds) {
        String key = generateKey(eventId, userId);
        redisTemplate.opsForValue().set(key, token, ttlSeconds, TimeUnit.SECONDS);
    }

    @Override
    public Optional<String> getToken(String eventId, Long userId) {
        String key = generateKey(eventId, userId);
        String token = redisTemplate.opsForValue().get(key);
        return Optional.ofNullable(token);
    }

    @Override
    public long getTokenTtl(String eventId, Long userId) {
        String key = generateKey(eventId, userId);
        Long ttl = redisTemplate.getExpire(key, TimeUnit.SECONDS);
        return ttl != null ? ttl : -2L;
    }

    @Override
    public void removeToken(String eventId, Long userId) {
        String key = generateKey(eventId, userId);
        redisTemplate.delete(key);
    }

    private String generateKey(String eventId, Long userId) {
        return KEY_PREFIX + eventId + KEY_TOKEN_INFIX + userId;
    }
}
```

**Step 4: 테스트 통과 확인**

Run: `./gradlew :apps:commerce-api:test --tests "QueueTokenRepositoryImplIntegrationTest"`
Expected: 5 tests PASSED

---

### Task 3: QueueTokenService 단위 테스트 및 구현

**Files:**
- Create: `apps/commerce-api/src/main/java/com/loopers/application/queue/QueueTokenService.java`
- Test: `apps/commerce-api/src/test/java/com/loopers/application/queue/QueueTokenServiceTest.java`

**Step 1: 단위 테스트 작성**

```java
package com.loopers.application.queue;

import com.loopers.domain.queue.QueueTokenRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class QueueTokenServiceTest {

    @InjectMocks
    private QueueTokenService queueTokenService;

    @Mock
    private QueueTokenRepository queueTokenRepository;

    @Nested
    @DisplayName("issueToken")
    class IssueToken {

        @Test
        @DisplayName("유저에게 UUID 토큰을 발급한다")
        void 유저에게_UUID_토큰을_발급한다() {
            // when
            String token = queueTokenService.issueToken("event-1", 1L);

            // then
            assertThat(token).isNotBlank();
            then(queueTokenRepository).should()
                    .issueToken(eq("event-1"), eq(1L), anyString(), eq(300L));
        }
    }

    @Nested
    @DisplayName("getTokenInfo")
    class GetTokenInfo {

        @Test
        @DisplayName("토큰이 존재하면 토큰과 TTL을 반환한다")
        void 토큰이_존재하면_토큰과_TTL을_반환한다() {
            // given
            given(queueTokenRepository.getToken("event-1", 1L))
                    .willReturn(Optional.of("tok_abc"));
            given(queueTokenRepository.getTokenTtl("event-1", 1L))
                    .willReturn(280L);

            // when
            QueueTokenService.TokenInfo info = queueTokenService.getTokenInfo("event-1", 1L);

            // then
            assertThat(info.token()).isEqualTo("tok_abc");
            assertThat(info.expiresIn()).isEqualTo(280L);
        }

        @Test
        @DisplayName("토큰이 없으면 null TokenInfo를 반환한다")
        void 토큰이_없으면_null_TokenInfo를_반환한다() {
            // given
            given(queueTokenRepository.getToken("event-1", 1L))
                    .willReturn(Optional.empty());

            // when
            QueueTokenService.TokenInfo info = queueTokenService.getTokenInfo("event-1", 1L);

            // then
            assertThat(info).isNull();
        }
    }

    @Nested
    @DisplayName("validateToken")
    class ValidateToken {

        @Test
        @DisplayName("유효한 토큰이면 예외가 발생하지 않는다")
        void 유효한_토큰이면_예외가_발생하지_않는다() {
            // given
            given(queueTokenRepository.getToken("event-1", 1L))
                    .willReturn(Optional.of("tok_abc"));

            // when & then (no exception)
            queueTokenService.validateToken("event-1", 1L, "tok_abc");
        }

        @Test
        @DisplayName("토큰이 없으면 UNAUTHORIZED 예외가 발생한다")
        void 토큰이_없으면_UNAUTHORIZED_예외가_발생한다() {
            // given
            given(queueTokenRepository.getToken("event-1", 1L))
                    .willReturn(Optional.empty());

            // when
            CoreException ex = assertThrows(CoreException.class,
                    () -> queueTokenService.validateToken("event-1", 1L, "tok_abc"));

            // then
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.UNAUTHORIZED);
        }

        @Test
        @DisplayName("토큰이 불일치하면 UNAUTHORIZED 예외가 발생한다")
        void 토큰이_불일치하면_UNAUTHORIZED_예외가_발생한다() {
            // given
            given(queueTokenRepository.getToken("event-1", 1L))
                    .willReturn(Optional.of("tok_abc"));

            // when
            CoreException ex = assertThrows(CoreException.class,
                    () -> queueTokenService.validateToken("event-1", 1L, "tok_wrong"));

            // then
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.UNAUTHORIZED);
        }
    }
}
```

**Step 2: 테스트 실패 확인**

Run: `./gradlew :apps:commerce-api:test --tests "QueueTokenServiceTest"`
Expected: FAIL (QueueTokenService 미존재)

**Step 3: 구현**

```java
package com.loopers.application.queue;

import com.loopers.domain.queue.QueueTokenRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@RequiredArgsConstructor
@Component
public class QueueTokenService {

    private static final long TOKEN_TTL_SECONDS = 300;

    private final QueueTokenRepository queueTokenRepository;

    public String issueToken(String eventId, Long userId) {
        String token = UUID.randomUUID().toString();
        queueTokenRepository.issueToken(eventId, userId, token, TOKEN_TTL_SECONDS);
        return token;
    }

    public TokenInfo getTokenInfo(String eventId, Long userId) {
        Optional<String> tokenOpt = queueTokenRepository.getToken(eventId, userId);
        if (tokenOpt.isEmpty()) {
            return null;
        }
        long ttl = queueTokenRepository.getTokenTtl(eventId, userId);
        return new TokenInfo(tokenOpt.get(), ttl);
    }

    public void validateToken(String eventId, Long userId, String token) {
        Optional<String> storedOpt = queueTokenRepository.getToken(eventId, userId);
        if (storedOpt.isEmpty() || !storedOpt.get().equals(token)) {
            throw new CoreException(ErrorType.UNAUTHORIZED, "유효하지 않은 입장 토큰입니다.");
        }
    }

    public record TokenInfo(String token, long expiresIn) {}
}
```

**Step 4: 테스트 통과 확인**

Run: `./gradlew :apps:commerce-api:test --tests "QueueTokenServiceTest"`
Expected: 5 tests PASSED

---

### Task 4: QueueEntryScheduler 구현

**Files:**
- Create: `apps/commerce-api/src/main/java/com/loopers/infrastructure/queue/QueueEntryScheduler.java`
- Test: `apps/commerce-api/src/test/java/com/loopers/infrastructure/queue/QueueEntrySchedulerTest.java`

**Step 1: 단위 테스트 작성**

기존 `SchedulerConfig.java`에 `@EnableScheduling`이 이미 설정되어 있음. 스케줄러는 `QueueService.popFront()`로 대기열에서 유저를 꺼내고, `QueueTokenService.issueToken()`으로 토큰을 발급.

```java
package com.loopers.infrastructure.queue;

import com.loopers.application.queue.QueueTokenService;
import com.loopers.domain.queue.QueueRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class QueueEntrySchedulerTest {

    @InjectMocks
    private QueueEntryScheduler queueEntryScheduler;

    @Mock
    private QueueRepository queueRepository;

    @Mock
    private QueueTokenService queueTokenService;

    @Test
    @DisplayName("대기열에서 배치 크기만큼 꺼내 토큰을 발급한다")
    void 대기열에서_배치_크기만큼_꺼내_토큰을_발급한다() {
        // given
        given(queueRepository.popFront("bf2024", 10)).willReturn(List.of(1L, 2L, 3L));

        // when
        queueEntryScheduler.processQueue("bf2024");

        // then
        then(queueTokenService).should().issueToken("bf2024", 1L);
        then(queueTokenService).should().issueToken("bf2024", 2L);
        then(queueTokenService).should().issueToken("bf2024", 3L);
    }

    @Test
    @DisplayName("대기열이 비어있으면 토큰을 발급하지 않는다")
    void 대기열이_비어있으면_토큰을_발급하지_않는다() {
        // given
        given(queueRepository.popFront("bf2024", 10)).willReturn(Collections.emptyList());

        // when
        queueEntryScheduler.processQueue("bf2024");

        // then
        then(queueTokenService).should(never()).issueToken(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }
}
```

**Step 2: 테스트 실패 확인**

Run: `./gradlew :apps:commerce-api:test --tests "QueueEntrySchedulerTest"`
Expected: FAIL

**Step 3: 구현**

> 주의: 실제 스케줄링은 특정 eventId를 하드코딩하지 않고, 설정으로 받거나 활성 이벤트 목록을 관리해야 한다. 학습 목적으로 기본 eventId "bf2024"를 사용.

```java
package com.loopers.infrastructure.queue;

import com.loopers.application.queue.QueueTokenService;
import com.loopers.domain.queue.QueueRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@RequiredArgsConstructor
@Component
public class QueueEntryScheduler {

    private static final int BATCH_SIZE = 10;
    private static final String DEFAULT_EVENT_ID = "bf2024";

    private final QueueRepository queueRepository;
    private final QueueTokenService queueTokenService;

    @Scheduled(fixedDelay = 3000)
    public void schedule() {
        processQueue(DEFAULT_EVENT_ID);
    }

    public void processQueue(String eventId) {
        List<Long> userIds = queueRepository.popFront(eventId, BATCH_SIZE);
        if (userIds.isEmpty()) {
            return;
        }
        for (Long userId : userIds) {
            queueTokenService.issueToken(eventId, userId);
        }
        log.info("대기열 처리 완료 - eventId: {}, 발급: {}명", eventId, userIds.size());
    }
}
```

**Step 4: 테스트 통과 확인**

Run: `./gradlew :apps:commerce-api:test --tests "QueueEntrySchedulerTest"`
Expected: 2 tests PASSED

---

### Task 5: QueueFacade에 토큰 정보 통합

**Files:**
- Modify: `apps/commerce-api/src/main/java/com/loopers/application/queue/QueueFacade.java`
- Test: `apps/commerce-api/src/test/java/com/loopers/application/queue/QueueFacadeTest.java`

**Step 1: 단위 테스트 작성**

```java
package com.loopers.application.queue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class QueueFacadeTest {

    @InjectMocks
    private QueueFacade queueFacade;

    @Mock
    private QueueService queueService;

    @Mock
    private QueueTokenService queueTokenService;

    @Test
    @DisplayName("토큰이 발급된 유저의 position 조회 시 토큰 정보가 포함된다")
    void 토큰이_발급된_유저의_position_조회_시_토큰_정보가_포함된다() {
        // given
        given(queueService.getPosition("event-1", 1L))
                .willReturn(new QueueService.QueueStatus(0, 10, 0));
        given(queueTokenService.getTokenInfo("event-1", 1L))
                .willReturn(new QueueTokenService.TokenInfo("tok_abc", 280L));

        // when
        QueueInfo result = queueFacade.getPosition("event-1", 1L);

        // then
        assertThat(result.token()).isEqualTo("tok_abc");
        assertThat(result.tokenExpiresIn()).isEqualTo(280L);
    }

    @Test
    @DisplayName("토큰이 없는 유저의 position 조회 시 token은 null이다")
    void 토큰이_없는_유저의_position_조회_시_token은_null이다() {
        // given
        given(queueService.getPosition("event-1", 1L))
                .willReturn(new QueueService.QueueStatus(5, 10, 6));
        given(queueTokenService.getTokenInfo("event-1", 1L))
                .willReturn(null);

        // when
        QueueInfo result = queueFacade.getPosition("event-1", 1L);

        // then
        assertThat(result.token()).isNull();
        assertThat(result.tokenExpiresIn()).isNull();
        assertThat(result.position()).isEqualTo(5);
    }
}
```

**Step 2: QueueFacade 수정**

```java
package com.loopers.application.queue;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class QueueFacade {

    private final QueueService queueService;
    private final QueueTokenService queueTokenService;

    public QueueInfo enter(String eventId, Long userId) {
        QueueService.QueueStatus status = queueService.enter(eventId, userId);
        return QueueInfo.from(status);
    }

    public QueueInfo getPosition(String eventId, Long userId) {
        QueueService.QueueStatus status = queueService.getPosition(eventId, userId);
        QueueTokenService.TokenInfo tokenInfo = queueTokenService.getTokenInfo(eventId, userId);
        if (tokenInfo != null) {
            return QueueInfo.withToken(status, tokenInfo.token(), tokenInfo.expiresIn());
        }
        return QueueInfo.from(status);
    }
}
```

**Step 3: 테스트 통과 확인**

Run: `./gradlew :apps:commerce-api:test --tests "QueueFacadeTest"`
Expected: 2 tests PASSED

---

### Task 6: QueueTokenInterceptor 구현

**Files:**
- Create: `apps/commerce-api/src/main/java/com/loopers/config/QueueTokenInterceptor.java`
- Modify: `apps/commerce-api/src/main/java/com/loopers/config/WebMvcConfig.java`
- Test: `apps/commerce-api/src/test/java/com/loopers/config/QueueTokenInterceptorTest.java`

**Step 1: 단위 테스트 작성**

```java
package com.loopers.config;

import com.loopers.application.queue.QueueTokenService;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
class QueueTokenInterceptorTest {

    @InjectMocks
    private QueueTokenInterceptor interceptor;

    @Mock
    private QueueTokenService queueTokenService;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Test
    @DisplayName("유효한 헤더가 있으면 true를 반환한다")
    void 유효한_헤더가_있으면_true를_반환한다() {
        // given
        given(request.getHeader("X-User-Id")).willReturn("1");
        given(request.getHeader("X-Queue-Token")).willReturn("tok_abc");
        given(request.getHeader("X-Event-Id")).willReturn("bf2024");

        // when
        boolean result = interceptor.preHandle(request, response, null);

        // then
        assertThat(result).isTrue();
        then(queueTokenService).should().validateToken("bf2024", 1L, "tok_abc");
    }

    @Test
    @DisplayName("X-User-Id 헤더가 없으면 UNAUTHORIZED 예외가 발생한다")
    void X_User_Id_헤더가_없으면_UNAUTHORIZED_예외가_발생한다() {
        // given
        given(request.getHeader("X-User-Id")).willReturn(null);

        // when & then
        CoreException ex = assertThrows(CoreException.class,
                () -> interceptor.preHandle(request, response, null));
        assertThat(ex.getErrorType()).isEqualTo(ErrorType.UNAUTHORIZED);
    }

    @Test
    @DisplayName("X-Queue-Token 헤더가 없으면 UNAUTHORIZED 예외가 발생한다")
    void X_Queue_Token_헤더가_없으면_UNAUTHORIZED_예외가_발생한다() {
        // given
        given(request.getHeader("X-User-Id")).willReturn("1");
        given(request.getHeader("X-Queue-Token")).willReturn(null);

        // when & then
        CoreException ex = assertThrows(CoreException.class,
                () -> interceptor.preHandle(request, response, null));
        assertThat(ex.getErrorType()).isEqualTo(ErrorType.UNAUTHORIZED);
    }
}
```

**Step 2: Interceptor 구현**

```java
package com.loopers.config;

import com.loopers.application.queue.QueueTokenService;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@RequiredArgsConstructor
@Component
public class QueueTokenInterceptor implements HandlerInterceptor {

    private final QueueTokenService queueTokenService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String userIdHeader = request.getHeader("X-User-Id");
        if (userIdHeader == null) {
            throw new CoreException(ErrorType.UNAUTHORIZED, "X-User-Id 헤더가 필요합니다.");
        }

        String token = request.getHeader("X-Queue-Token");
        if (token == null) {
            throw new CoreException(ErrorType.UNAUTHORIZED, "X-Queue-Token 헤더가 필요합니다.");
        }

        String eventId = request.getHeader("X-Event-Id");
        if (eventId == null) {
            throw new CoreException(ErrorType.UNAUTHORIZED, "X-Event-Id 헤더가 필요합니다.");
        }

        Long userId = Long.parseLong(userIdHeader);
        queueTokenService.validateToken(eventId, userId, token);
        return true;
    }
}
```

**Step 3: WebMvcConfig에 Interceptor 등록**

```java
// WebMvcConfig.java에 추가
private final QueueTokenInterceptor queueTokenInterceptor;

@Override
public void addInterceptors(InterceptorRegistry registry) {
    registry.addInterceptor(queueTokenInterceptor)
            .addPathPatterns("/api/v1/orders/**");
}
```

**Step 4: 테스트 통과 확인**

Run: `./gradlew :apps:commerce-api:test --tests "QueueTokenInterceptorTest"`
Expected: 3 tests PASSED

---

### Task 7: E2E 테스트 — 토큰 발급 + position 조회

**Files:**
- Modify: `apps/commerce-api/src/test/java/com/loopers/interfaces/api/queue/QueueV1ApiE2ETest.java`

**Step 1: 토큰 관련 E2E 테스트 추가**

`GetPosition` nested class에 아래 테스트 추가:

```java
@DisplayName("토큰이 발급된 유저는 token 정보가 포함된 응답을 받는다")
@Test
void returnsTokenInfoForIssuedUser() {
    // given
    String eventId = "event-1";
    Long userId = 1L;
    // 대기열 진입
    testRestTemplate.exchange(
            "/api/v1/queue/{eventId}/enter", HttpMethod.POST,
            new HttpEntity<>(null, headers(userId)),
            new ParameterizedTypeReference<ApiResponse<QueueV1Dto.QueuePositionResponse>>() {}, eventId);

    // 스케줄러가 처리할 때까지 대기 (직접 호출로 대체)
    // 실제로는 스케줄러가 3초마다 동작하지만, 테스트에서는 직접 Facade를 통해 검증

    // when - position 조회
    ResponseEntity<ApiResponse<QueueV1Dto.QueuePositionResponse>> response = testRestTemplate.exchange(
            "/api/v1/queue/{eventId}/position", HttpMethod.GET,
            new HttpEntity<>(null, headers(userId)),
            new ParameterizedTypeReference<>() {}, eventId);

    // then
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    // 스케줄러가 아직 미실행이므로 token은 null
    assertThat(response.getBody().data().token()).isNull();
}
```

**Step 2: 전체 테스트 실행**

Run: `./gradlew :apps:commerce-api:test --tests "QueueV1ApiE2ETest"`
Expected: 5 tests PASSED

---

### Task 8: .http 파일 업데이트

**Files:**
- Modify: `.http/queue.http`

토큰 관련 요청 추가:

```http
### 순번 조회 (토큰 포함 응답)
GET http://localhost:8080/api/v1/queue/bf2024/position
X-User-Id: 1

### 주문 API (토큰 필요)
POST http://localhost:8080/api/v1/orders
X-User-Id: 1
X-Event-Id: bf2024
X-Queue-Token: {{token}}
Content-Type: application/json

{}
```

---

### Task 9: 전체 테스트 실행 및 컴파일 확인

Run: `./gradlew :apps:commerce-api:test`
Expected: ALL PASSED

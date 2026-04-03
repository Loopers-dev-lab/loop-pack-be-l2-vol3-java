# Step 1: Redis 기반 대기열 시스템 구현 계획

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Redis Sorted Set 기반 대기열 진입/순번 조회 API를 TDD로 구현한다.

**Architecture:** domain/queue에 QueueRepository 인터페이스를 정의하고, infrastructure/queue에서 RedisTemplate으로 구현. QueueService가 비즈니스 로직을 담당하고, QueueFacade가 유스케이스를 조합. Controller는 X-User-Id 헤더로 유저를 식별한다.

**Tech Stack:** Spring Boot 3.4.4, Java 21, Redis Sorted Set, TestContainers Redis

**사용자 요청:** 실무 모니터링 방식과 대기열 방정식 선정 트레이드오프를 깊이 고민할 것

---

## 브랜치 준비

```bash
git checkout volume-7
git checkout -b volume-8
git push -u origin volume-8
git checkout -b week8-feature/queue-system
```

---

### Task 1: QueueRepository 인터페이스 정의 (domain 레이어)

**Files:**
- Create: `apps/commerce-api/src/main/java/com/loopers/domain/queue/QueueRepository.java`

**Step 1: 인터페이스 작성**

```java
package com.loopers.domain.queue;

import java.util.List;
import java.util.Optional;

public interface QueueRepository {

    boolean add(String eventId, Long userId);

    Optional<Long> getPosition(String eventId, Long userId);

    long getTotalCount(String eventId);

    List<Long> popFront(String eventId, int count);
}
```

**Step 2: Commit**

```bash
git add apps/commerce-api/src/main/java/com/loopers/domain/queue/QueueRepository.java
git commit -m "feat: 대기열 Repository 인터페이스 정의"
```

---

### Task 2: QueueRepositoryImpl 통합 테스트 (Red)

**Files:**
- Create: `apps/commerce-api/src/test/java/com/loopers/infrastructure/queue/QueueRepositoryImplIntegrationTest.java`

**Step 1: 테스트 작성**

```java
package com.loopers.infrastructure.queue;

import com.loopers.domain.queue.QueueRepository;
import com.loopers.testcontainers.MySqlTestContainersConfig;
import com.loopers.utils.RedisCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(MySqlTestContainersConfig.class)
@ActiveProfiles("test")
class QueueRepositoryImplIntegrationTest {

    static final GenericContainer<?> redisContainer =
        new GenericContainer<>(DockerImageName.parse("redis:latest")).withExposedPorts(6379);

    static { redisContainer.start(); }

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        String host = redisContainer.getHost();
        int port = redisContainer.getFirstMappedPort();
        registry.add("datasource.redis.database", () -> 0);
        registry.add("datasource.redis.master.host", () -> host);
        registry.add("datasource.redis.master.port", () -> port);
        registry.add("datasource.redis.replicas[0].host", () -> host);
        registry.add("datasource.redis.replicas[0].port", () -> port);
    }

    @Autowired
    private QueueRepository queueRepository;

    @Autowired
    private RedisCleanUp redisCleanUp;

    @AfterEach
    void tearDown() {
        redisCleanUp.truncateAll();
    }

    @DisplayName("add - 대기열 진입")
    @Nested
    class Add {

        @Test
        @DisplayName("유저를 대기열에 추가하면 true를 반환한다")
        void 대기열에_유저를_추가하면_true를_반환한다() {
            // given
            String eventId = "bf2024";
            Long userId = 1L;

            // when
            boolean result = queueRepository.add(eventId, userId);

            // then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("같은 유저가 중복 진입하면 false를 반환한다")
        void 같은_유저가_중복_진입하면_false를_반환한다() {
            // given
            String eventId = "bf2024";
            Long userId = 1L;
            queueRepository.add(eventId, userId);

            // when
            boolean result = queueRepository.add(eventId, userId);

            // then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("다른 이벤트에는 같은 유저가 진입할 수 있다")
        void 다른_이벤트에는_같은_유저가_진입할_수_있다() {
            // given
            Long userId = 1L;
            queueRepository.add("event-a", userId);

            // when
            boolean result = queueRepository.add("event-b", userId);

            // then
            assertThat(result).isTrue();
        }
    }

    @DisplayName("getPosition - 순번 조회")
    @Nested
    class GetPosition {

        @Test
        @DisplayName("대기열에 있는 유저의 순번을 반환한다 (0-based)")
        void 대기열에_있는_유저의_순번을_반환한다() {
            // given
            String eventId = "bf2024";
            queueRepository.add(eventId, 1L);
            queueRepository.add(eventId, 2L);
            queueRepository.add(eventId, 3L);

            // when
            Optional<Long> position = queueRepository.getPosition(eventId, 2L);

            // then
            assertThat(position).isPresent();
            assertThat(position.get()).isEqualTo(1L);
        }

        @Test
        @DisplayName("대기열에 없는 유저는 empty를 반환한다")
        void 대기열에_없는_유저는_empty를_반환한다() {
            // given
            String eventId = "bf2024";

            // when
            Optional<Long> position = queueRepository.getPosition(eventId, 999L);

            // then
            assertThat(position).isEmpty();
        }
    }

    @DisplayName("getTotalCount - 전체 대기 인원")
    @Nested
    class GetTotalCount {

        @Test
        @DisplayName("대기열의 전체 인원을 반환한다")
        void 대기열의_전체_인원을_반환한다() {
            // given
            String eventId = "bf2024";
            queueRepository.add(eventId, 1L);
            queueRepository.add(eventId, 2L);
            queueRepository.add(eventId, 3L);

            // when
            long count = queueRepository.getTotalCount(eventId);

            // then
            assertThat(count).isEqualTo(3);
        }

        @Test
        @DisplayName("빈 대기열은 0을 반환한다")
        void 빈_대기열은_0을_반환한다() {
            // when
            long count = queueRepository.getTotalCount("empty-event");

            // then
            assertThat(count).isZero();
        }
    }

    @DisplayName("popFront - 앞에서 N명 꺼내기")
    @Nested
    class PopFront {

        @Test
        @DisplayName("앞에서 N명을 꺼내면 해당 유저들이 대기열에서 제거된다")
        void 앞에서_N명을_꺼내면_대기열에서_제거된다() {
            // given
            String eventId = "bf2024";
            queueRepository.add(eventId, 1L);
            queueRepository.add(eventId, 2L);
            queueRepository.add(eventId, 3L);

            // when
            List<Long> popped = queueRepository.popFront(eventId, 2);

            // then
            assertThat(popped).containsExactly(1L, 2L);
            assertThat(queueRepository.getTotalCount(eventId)).isEqualTo(1);
        }

        @Test
        @DisplayName("대기열보다 많이 요청하면 있는 만큼만 반환한다")
        void 대기열보다_많이_요청하면_있는_만큼만_반환한다() {
            // given
            String eventId = "bf2024";
            queueRepository.add(eventId, 1L);

            // when
            List<Long> popped = queueRepository.popFront(eventId, 10);

            // then
            assertThat(popped).containsExactly(1L);
            assertThat(queueRepository.getTotalCount(eventId)).isZero();
        }
    }
}
```

**Step 2: 테스트 실행 — 실패 확인**

Run: `./gradlew :apps:commerce-api:test --tests "QueueRepositoryImplIntegrationTest" --info`
Expected: FAIL — `QueueRepository` 빈 없음

---

### Task 3: QueueRepositoryImpl 구현 (Green)

**Files:**
- Create: `apps/commerce-api/src/main/java/com/loopers/infrastructure/queue/QueueRepositoryImpl.java`

**Step 1: 구현**

```java
package com.loopers.infrastructure.queue;

import com.loopers.config.redis.RedisConfig;
import com.loopers.domain.queue.QueueRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@Component
public class QueueRepositoryImpl implements QueueRepository {

    private static final String KEY_PREFIX = "queue:%s:waiting";

    private final RedisTemplate<String, String> redisTemplate;

    public QueueRepositoryImpl(
        @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER) RedisTemplate<String, String> redisTemplate
    ) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public boolean add(String eventId, Long userId) {
        String key = key(eventId);
        double score = System.currentTimeMillis();
        Boolean added = redisTemplate.opsForZSet().addIfAbsent(key, String.valueOf(userId), score);
        return Boolean.TRUE.equals(added);
    }

    @Override
    public Optional<Long> getPosition(String eventId, Long userId) {
        Long rank = redisTemplate.opsForZSet().rank(key(eventId), String.valueOf(userId));
        return Optional.ofNullable(rank);
    }

    @Override
    public long getTotalCount(String eventId) {
        Long size = redisTemplate.opsForZSet().zCard(key(eventId));
        return size != null ? size : 0;
    }

    @Override
    public List<Long> popFront(String eventId, int count) {
        String key = key(eventId);
        Set<ZSetOperations.TypedTuple<String>> tuples = redisTemplate.opsForZSet().popMin(key, count);
        if (tuples == null || tuples.isEmpty()) {
            return List.of();
        }
        return tuples.stream()
            .map(tuple -> Long.parseLong(tuple.getValue()))
            .toList();
    }

    private String key(String eventId) {
        return String.format(KEY_PREFIX, eventId);
    }
}
```

**Step 2: 테스트 실행 — 통과 확인**

Run: `./gradlew :apps:commerce-api:test --tests "QueueRepositoryImplIntegrationTest" --info`
Expected: ALL PASS

**Step 3: Commit**

```bash
git add apps/commerce-api/src/main/java/com/loopers/infrastructure/queue/QueueRepositoryImpl.java
git add apps/commerce-api/src/test/java/com/loopers/infrastructure/queue/QueueRepositoryImplIntegrationTest.java
git commit -m "feat: Redis Sorted Set 기반 대기열 Repository 구현 및 테스트"
```

---

### Task 4: QueueService 단위 테스트 (Red)

**Files:**
- Create: `apps/commerce-api/src/test/java/com/loopers/domain/queue/QueueServiceTest.java`

**Step 1: 테스트 작성**

```java
package com.loopers.domain.queue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class QueueServiceTest {

    @InjectMocks
    private QueueService queueService;

    @Mock
    private QueueRepository queueRepository;

    @DisplayName("enter - 대기열 진입")
    @Nested
    class Enter {

        @Test
        @DisplayName("신규 유저 진입 시 순번을 반환한다")
        void 신규_유저_진입_시_순번을_반환한다() {
            // given
            given(queueRepository.add("bf2024", 1L)).willReturn(true);
            given(queueRepository.getPosition("bf2024", 1L)).willReturn(Optional.of(0L));
            given(queueRepository.getTotalCount("bf2024")).willReturn(1L);

            // when
            QueueService.QueueStatus status = queueService.enter("bf2024", 1L);

            // then
            assertThat(status.position()).isEqualTo(1L);
            assertThat(status.totalWaiting()).isEqualTo(1L);
        }

        @Test
        @DisplayName("중복 진입 시 CoreException을 발생시킨다")
        void 중복_진입_시_예외를_발생시킨다() {
            // given
            given(queueRepository.add("bf2024", 1L)).willReturn(false);

            // when & then
            assertThatThrownBy(() -> queueService.enter("bf2024", 1L))
                .isInstanceOf(com.loopers.support.error.CoreException.class);
        }
    }

    @DisplayName("getPosition - 순번 조회")
    @Nested
    class GetPosition {

        @Test
        @DisplayName("대기 중인 유저의 순번과 예상 대기시간을 반환한다")
        void 대기_중인_유저의_순번과_예상_대기시간을_반환한다() {
            // given
            given(queueRepository.getPosition("bf2024", 5L)).willReturn(Optional.of(9L));
            given(queueRepository.getTotalCount("bf2024")).willReturn(20L);

            // when
            QueueService.QueueStatus status = queueService.getPosition("bf2024", 5L);

            // then
            assertThat(status.position()).isEqualTo(10L);
            assertThat(status.totalWaiting()).isEqualTo(20L);
            assertThat(status.estimatedWaitSeconds()).isPositive();
        }

        @Test
        @DisplayName("대기열에 없는 유저는 position 0을 반환한다")
        void 대기열에_없는_유저는_position_0을_반환한다() {
            // given
            given(queueRepository.getPosition("bf2024", 999L)).willReturn(Optional.empty());
            given(queueRepository.getTotalCount("bf2024")).willReturn(0L);

            // when
            QueueService.QueueStatus status = queueService.getPosition("bf2024", 999L);

            // then
            assertThat(status.position()).isZero();
        }
    }
}
```

**Step 2: 테스트 실행 — 실패 확인**

Run: `./gradlew :apps:commerce-api:test --tests "QueueServiceTest" --info`
Expected: FAIL — `QueueService` 클래스 없음

---

### Task 5: QueueService 구현 (Green)

**Files:**
- Create: `apps/commerce-api/src/main/java/com/loopers/domain/queue/QueueService.java`

**Step 1: 구현**

```java
package com.loopers.domain.queue;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

@RequiredArgsConstructor
@Component
public class QueueService {

    private static final int BATCH_SIZE = 10;
    private static final int SCHEDULER_INTERVAL_SECONDS = 3;

    private final QueueRepository queueRepository;

    public QueueStatus enter(String eventId, Long userId) {
        boolean added = queueRepository.add(eventId, userId);
        if (!added) {
            throw new CoreException(ErrorType.CONFLICT, "이미 대기열에 등록된 사용자입니다.");
        }

        long position = queueRepository.getPosition(eventId, userId)
            .map(rank -> rank + 1)
            .orElse(0L);
        long totalWaiting = queueRepository.getTotalCount(eventId);
        long estimatedWait = calculateEstimatedWait(position);

        return new QueueStatus(position, totalWaiting, estimatedWait);
    }

    public QueueStatus getPosition(String eventId, Long userId) {
        Optional<Long> rankOpt = queueRepository.getPosition(eventId, userId);
        long totalWaiting = queueRepository.getTotalCount(eventId);

        if (rankOpt.isEmpty()) {
            return new QueueStatus(0, totalWaiting, 0);
        }

        long position = rankOpt.get() + 1;
        long estimatedWait = calculateEstimatedWait(position);
        return new QueueStatus(position, totalWaiting, estimatedWait);
    }

    /**
     * 예상 대기 시간 = (내 앞 사람 수 / 배치 크기) * 스케줄러 주기
     *
     * 트레이드오프:
     * - Little's Law (L = λW) 기반 동적 계산 vs 정적 계산
     * - 정적 방식 선택 이유: 스케줄러 주기와 배치 크기가 고정이므로 예측 가능성이 높음
     * - 실무에서는 최근 N분간의 실제 처리량을 sliding window로 추적하여 동적 계산
     */
    private long calculateEstimatedWait(long position) {
        if (position <= 0) return 0;
        long batchesAhead = (position - 1) / BATCH_SIZE;
        return (batchesAhead + 1) * SCHEDULER_INTERVAL_SECONDS;
    }

    public record QueueStatus(
        long position,
        long totalWaiting,
        long estimatedWaitSeconds
    ) {}
}
```

**Step 2: 테스트 실행 — 통과 확인**

Run: `./gradlew :apps:commerce-api:test --tests "QueueServiceTest" --info`
Expected: ALL PASS

**Step 3: Commit**

```bash
git add apps/commerce-api/src/main/java/com/loopers/domain/queue/QueueService.java
git add apps/commerce-api/src/test/java/com/loopers/domain/queue/QueueServiceTest.java
git commit -m "feat: 대기열 Service 구현 — 진입/순번 조회/예상 대기시간"
```

---

### Task 6: QueueFacade + QueueInfo (application 레이어)

**Files:**
- Create: `apps/commerce-api/src/main/java/com/loopers/application/queue/QueueInfo.java`
- Create: `apps/commerce-api/src/main/java/com/loopers/application/queue/QueueFacade.java`

**Step 1: QueueInfo 작성**

```java
package com.loopers.application.queue;

import com.loopers.domain.queue.QueueService;

public record QueueInfo(
    long position,
    long totalWaiting,
    long estimatedWaitSeconds,
    String token,
    Long tokenExpiresIn
) {
    public static QueueInfo from(QueueService.QueueStatus status) {
        return new QueueInfo(
            status.position(),
            status.totalWaiting(),
            status.estimatedWaitSeconds(),
            null,
            null
        );
    }

    public static QueueInfo withToken(QueueService.QueueStatus status, String token, Long expiresIn) {
        return new QueueInfo(
            status.position(),
            status.totalWaiting(),
            status.estimatedWaitSeconds(),
            token,
            expiresIn
        );
    }
}
```

**Step 2: QueueFacade 작성**

```java
package com.loopers.application.queue;

import com.loopers.domain.queue.QueueService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class QueueFacade {

    private final QueueService queueService;

    public QueueInfo enter(String eventId, Long userId) {
        QueueService.QueueStatus status = queueService.enter(eventId, userId);
        return QueueInfo.from(status);
    }

    public QueueInfo getPosition(String eventId, Long userId) {
        QueueService.QueueStatus status = queueService.getPosition(eventId, userId);
        return QueueInfo.from(status);
    }
}
```

**Step 3: Commit**

```bash
git add apps/commerce-api/src/main/java/com/loopers/application/queue/
git commit -m "feat: 대기열 Facade/Info DTO 추가"
```

---

### Task 7: QueueV1Dto + QueueV1ApiSpec (interfaces 레이어)

**Files:**
- Create: `apps/commerce-api/src/main/java/com/loopers/interfaces/api/queue/QueueV1Dto.java`
- Create: `apps/commerce-api/src/main/java/com/loopers/interfaces/api/queue/QueueV1ApiSpec.java`

**Step 1: DTO 작성**

```java
package com.loopers.interfaces.api.queue;

import com.loopers.application.queue.QueueInfo;

public class QueueV1Dto {

    public record QueuePositionResponse(
        long position,
        long totalWaiting,
        long estimatedWaitSeconds,
        String token,
        Long tokenExpiresIn
    ) {
        public static QueuePositionResponse from(QueueInfo info) {
            return new QueuePositionResponse(
                info.position(),
                info.totalWaiting(),
                info.estimatedWaitSeconds(),
                info.token(),
                info.tokenExpiresIn()
            );
        }
    }
}
```

**Step 2: ApiSpec 작성**

```java
package com.loopers.interfaces.api.queue;

import com.loopers.interfaces.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Queue", description = "대기열 API")
public interface QueueV1ApiSpec {

    @Operation(summary = "대기열 진입", description = "이벤트 대기열에 진입합니다.")
    ApiResponse<QueueV1Dto.QueuePositionResponse> enter(
        @Parameter(description = "이벤트 ID") String eventId,
        @Parameter(description = "유저 ID", required = true) Long userId
    );

    @Operation(summary = "순번 조회", description = "현재 대기 순번과 예상 대기시간을 조회합니다.")
    ApiResponse<QueueV1Dto.QueuePositionResponse> getPosition(
        @Parameter(description = "이벤트 ID") String eventId,
        @Parameter(description = "유저 ID", required = true) Long userId
    );
}
```

**Step 3: Commit**

```bash
git add apps/commerce-api/src/main/java/com/loopers/interfaces/api/queue/
git commit -m "feat: 대기열 API DTO 및 Swagger 스펙 정의"
```

---

### Task 8: QueueV1Controller E2E 테스트 (Red)

**Files:**
- Create: `apps/commerce-api/src/test/java/com/loopers/interfaces/api/queue/QueueV1ApiE2ETest.java`

**Step 1: 테스트 작성**

```java
package com.loopers.interfaces.api.queue;

import com.loopers.interfaces.api.ApiResponse;
import com.loopers.testcontainers.MySqlTestContainersConfig;
import com.loopers.utils.RedisCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(MySqlTestContainersConfig.class)
@ActiveProfiles("test")
class QueueV1ApiE2ETest {

    static final GenericContainer<?> redisContainer =
        new GenericContainer<>(DockerImageName.parse("redis:latest")).withExposedPorts(6379);

    static { redisContainer.start(); }

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        String host = redisContainer.getHost();
        int port = redisContainer.getFirstMappedPort();
        registry.add("datasource.redis.database", () -> 0);
        registry.add("datasource.redis.master.host", () -> host);
        registry.add("datasource.redis.master.port", () -> port);
        registry.add("datasource.redis.replicas[0].host", () -> host);
        registry.add("datasource.redis.replicas[0].port", () -> port);
    }

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private RedisCleanUp redisCleanUp;

    @AfterEach
    void tearDown() {
        redisCleanUp.truncateAll();
    }

    private HttpHeaders headers(Long userId) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-User-Id", String.valueOf(userId));
        return headers;
    }

    @DisplayName("POST /api/v1/queue/{eventId}/enter")
    @Nested
    class Enter {

        @Test
        @DisplayName("대기열에 진입하면 순번을 반환한다")
        void 대기열에_진입하면_순번을_반환한다() {
            // given
            HttpEntity<Void> request = new HttpEntity<>(headers(1L));

            // when
            ResponseEntity<ApiResponse<QueueV1Dto.QueuePositionResponse>> response = restTemplate.exchange(
                "/api/v1/queue/bf2024/enter", HttpMethod.POST, request,
                new ParameterizedTypeReference<>() {}
            );

            // then
            assertAll(
                () -> assertThat(response.getStatusCode().is2xxSuccessful()).isTrue(),
                () -> assertThat(response.getBody().data().position()).isEqualTo(1L)
            );
        }

        @Test
        @DisplayName("중복 진입하면 409를 반환한다")
        void 중복_진입하면_409를_반환한다() {
            // given
            HttpEntity<Void> request = new HttpEntity<>(headers(1L));
            restTemplate.exchange("/api/v1/queue/bf2024/enter", HttpMethod.POST, request,
                new ParameterizedTypeReference<ApiResponse<QueueV1Dto.QueuePositionResponse>>() {});

            // when
            ResponseEntity<ApiResponse<Object>> response = restTemplate.exchange(
                "/api/v1/queue/bf2024/enter", HttpMethod.POST, request,
                new ParameterizedTypeReference<>() {}
            );

            // then
            assertThat(response.getStatusCode().value()).isEqualTo(409);
        }

        @Test
        @DisplayName("여러 유저가 진입하면 순서대로 순번이 매겨진다")
        void 여러_유저가_진입하면_순서대로_순번이_매겨진다() {
            // given & when
            for (long i = 1; i <= 5; i++) {
                restTemplate.exchange("/api/v1/queue/bf2024/enter", HttpMethod.POST,
                    new HttpEntity<>(headers(i)),
                    new ParameterizedTypeReference<ApiResponse<QueueV1Dto.QueuePositionResponse>>() {});
            }

            // then — 5번째 유저의 순번 조회
            ResponseEntity<ApiResponse<QueueV1Dto.QueuePositionResponse>> response = restTemplate.exchange(
                "/api/v1/queue/bf2024/position", HttpMethod.GET,
                new HttpEntity<>(headers(5L)),
                new ParameterizedTypeReference<>() {}
            );

            assertThat(response.getBody().data().position()).isEqualTo(5L);
            assertThat(response.getBody().data().totalWaiting()).isEqualTo(5L);
        }
    }

    @DisplayName("GET /api/v1/queue/{eventId}/position")
    @Nested
    class Position {

        @Test
        @DisplayName("대기열에 없는 유저는 position 0을 반환한다")
        void 대기열에_없는_유저는_position_0을_반환한다() {
            // when
            ResponseEntity<ApiResponse<QueueV1Dto.QueuePositionResponse>> response = restTemplate.exchange(
                "/api/v1/queue/bf2024/position", HttpMethod.GET,
                new HttpEntity<>(headers(999L)),
                new ParameterizedTypeReference<>() {}
            );

            // then
            assertThat(response.getBody().data().position()).isZero();
        }
    }
}
```

**Step 2: 테스트 실행 — 실패 확인**

Run: `./gradlew :apps:commerce-api:test --tests "QueueV1ApiE2ETest" --info`
Expected: FAIL — Controller 없음, 404

---

### Task 9: QueueV1Controller 구현 (Green)

**Files:**
- Create: `apps/commerce-api/src/main/java/com/loopers/interfaces/api/queue/QueueV1Controller.java`

**Step 1: Controller 작성**

```java
package com.loopers.interfaces.api.queue;

import com.loopers.application.queue.QueueFacade;
import com.loopers.application.queue.QueueInfo;
import com.loopers.interfaces.api.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
public class QueueV1Controller implements QueueV1ApiSpec {

    private final QueueFacade queueFacade;

    @PostMapping("/api/v1/queue/{eventId}/enter")
    @Override
    public ApiResponse<QueueV1Dto.QueuePositionResponse> enter(
        @PathVariable String eventId,
        @RequestHeader("X-User-Id") Long userId
    ) {
        QueueInfo info = queueFacade.enter(eventId, userId);
        return ApiResponse.success(QueueV1Dto.QueuePositionResponse.from(info));
    }

    @GetMapping("/api/v1/queue/{eventId}/position")
    @Override
    public ApiResponse<QueueV1Dto.QueuePositionResponse> getPosition(
        @PathVariable String eventId,
        @RequestHeader("X-User-Id") Long userId
    ) {
        QueueInfo info = queueFacade.getPosition(eventId, userId);
        return ApiResponse.success(QueueV1Dto.QueuePositionResponse.from(info));
    }
}
```

**Step 2: 테스트 실행 — 통과 확인**

Run: `./gradlew :apps:commerce-api:test --tests "QueueV1ApiE2ETest" --info`
Expected: ALL PASS

**Step 3: Commit**

```bash
git add apps/commerce-api/src/main/java/com/loopers/interfaces/api/queue/QueueV1Controller.java
git add apps/commerce-api/src/test/java/com/loopers/interfaces/api/queue/QueueV1ApiE2ETest.java
git commit -m "feat: 대기열 진입/순번 조회 API 및 E2E 테스트"
```

---

### Task 10: 동시성 테스트

**Files:**
- Create: `apps/commerce-api/src/test/java/com/loopers/infrastructure/queue/QueueConcurrencyIntegrationTest.java`

**Step 1: 동시성 테스트 작성**

```java
package com.loopers.infrastructure.queue;

import com.loopers.domain.queue.QueueRepository;
import com.loopers.testcontainers.MySqlTestContainersConfig;
import com.loopers.utils.RedisCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(MySqlTestContainersConfig.class)
@ActiveProfiles("test")
class QueueConcurrencyIntegrationTest {

    static final GenericContainer<?> redisContainer =
        new GenericContainer<>(DockerImageName.parse("redis:latest")).withExposedPorts(6379);

    static { redisContainer.start(); }

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        String host = redisContainer.getHost();
        int port = redisContainer.getFirstMappedPort();
        registry.add("datasource.redis.database", () -> 0);
        registry.add("datasource.redis.master.host", () -> host);
        registry.add("datasource.redis.master.port", () -> port);
        registry.add("datasource.redis.replicas[0].host", () -> host);
        registry.add("datasource.redis.replicas[0].port", () -> port);
    }

    @Autowired
    private QueueRepository queueRepository;

    @Autowired
    private RedisCleanUp redisCleanUp;

    @AfterEach
    void tearDown() {
        redisCleanUp.truncateAll();
    }

    @Test
    @DisplayName("100명이 동시에 진입해도 대기열에 정확히 100명이 등록된다")
    void 동시_진입_시_정확한_인원이_등록된다() throws InterruptedException {
        // given
        int threadCount = 100;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);

        // when
        for (int i = 1; i <= threadCount; i++) {
            long userId = i;
            executor.submit(() -> {
                try {
                    boolean added = queueRepository.add("bf2024", userId);
                    if (added) successCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }
        latch.await();
        executor.shutdown();

        // then
        assertThat(successCount.get()).isEqualTo(100);
        assertThat(queueRepository.getTotalCount("bf2024")).isEqualTo(100);
    }

    @Test
    @DisplayName("같은 유저가 동시에 진입해도 1번만 등록된다")
    void 같은_유저_동시_진입_시_1번만_등록된다() throws InterruptedException {
        // given
        int threadCount = 50;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);

        // when
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    boolean added = queueRepository.add("bf2024", 1L);
                    if (added) successCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }
        latch.await();
        executor.shutdown();

        // then
        assertThat(successCount.get()).isEqualTo(1);
        assertThat(queueRepository.getTotalCount("bf2024")).isEqualTo(1);
    }
}
```

**Step 2: 테스트 실행 — 통과 확인**

Run: `./gradlew :apps:commerce-api:test --tests "QueueConcurrencyIntegrationTest" --info`
Expected: ALL PASS

**Step 3: Commit**

```bash
git add apps/commerce-api/src/test/java/com/loopers/infrastructure/queue/QueueConcurrencyIntegrationTest.java
git commit -m "test: 대기열 동시성 테스트 — 100명 동시 진입, 중복 방지 검증"
```

---

### Task 11: HTTP 파일 작성

**Files:**
- Create: `.http/queue.http`

**Step 1: HTTP 파일 작성**

```http
### 대기열 진입
POST http://localhost:8080/api/v1/queue/bf2024/enter
X-User-Id: 1

### 순번 조회
GET http://localhost:8080/api/v1/queue/bf2024/position
X-User-Id: 1

### 다른 유저 진입
POST http://localhost:8080/api/v1/queue/bf2024/enter
X-User-Id: 2

### 다른 유저 순번 조회
GET http://localhost:8080/api/v1/queue/bf2024/position
X-User-Id: 2
```

**Step 2: Commit**

```bash
git add .http/queue.http
git commit -m "docs: 대기열 API HTTP 테스트 파일 추가"
```

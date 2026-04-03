# Step 3: 실시간 순번 + Prometheus 메트릭 + k6 부하 테스트 Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Prometheus 커스텀 메트릭으로 대기열 시스템을 관측하고, k6 부하 테스트로 처리량/순서 보장/토큰 만료를 검증

**Architecture:** MeterRegistry를 통한 커스텀 메트릭(Counter, Gauge, Histogram) 등록. QueueService/QueueTokenService/QueueEntryScheduler에서 메트릭 기록. k6 스크립트로 3가지 시나리오(Spike 진입, 스케줄러 처리, TTL 만료) 검증.

**Tech Stack:** Micrometer + Prometheus, k6, Grafana, Spring Boot Actuator

**Step 1~2 완료 전제:** QueueRepository, QueueTokenRepository, QueueService, QueueTokenService, QueueFacade, QueueEntryScheduler, QueueTokenInterceptor 모두 구현됨.

---

### Task 1: QueueMetrics 컴포넌트 생성

**Files:**
- Create: `apps/commerce-api/src/main/java/com/loopers/infrastructure/queue/QueueMetrics.java`

**Step 1: 구현**

`supports/monitoring` 모듈이 이미 `micrometer-registry-prometheus`를 포함하고 있고, `commerce-api`가 이를 의존. `MeterRegistry`가 자동 주입됨.

```java
package com.loopers.infrastructure.queue;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class QueueMetrics {

    private final MeterRegistry meterRegistry;
    private final Map<String, AtomicLong> waitingSizeMap = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> activeTokensMap = new ConcurrentHashMap<>();

    public QueueMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void recordEnter(String eventId) {
        Counter.builder("queue.enter.total")
                .tag("event_id", eventId)
                .register(meterRegistry)
                .increment();
    }

    public void recordTokenIssued(String eventId, int count) {
        Counter.builder("queue.token.issued.total")
                .tag("event_id", eventId)
                .register(meterRegistry)
                .increment(count);
    }

    public void updateWaitingSize(String eventId, long size) {
        AtomicLong gauge = waitingSizeMap.computeIfAbsent(eventId, id -> {
            AtomicLong value = new AtomicLong(0);
            Gauge.builder("queue.waiting.size", value, AtomicLong::doubleValue)
                    .tag("event_id", id)
                    .register(meterRegistry);
            return value;
        });
        gauge.set(size);
    }

    public void updateActiveTokens(String eventId, long count) {
        AtomicLong gauge = activeTokensMap.computeIfAbsent(eventId, id -> {
            AtomicLong value = new AtomicLong(0);
            Gauge.builder("queue.active.tokens", value, AtomicLong::doubleValue)
                    .tag("event_id", id)
                    .register(meterRegistry);
            return value;
        });
        gauge.set(count);
    }

    public Timer.Sample startWaitTimer() {
        return Timer.start(meterRegistry);
    }

    public void recordWaitDuration(Timer.Sample sample, String eventId) {
        sample.stop(Timer.builder("queue.wait.duration.seconds")
                .tag("event_id", eventId)
                .register(meterRegistry));
    }
}
```

**Step 2: 컴파일 확인**

Run: `./gradlew :apps:commerce-api:compileJava`
Expected: BUILD SUCCESSFUL

---

### Task 2: 메트릭을 서비스 레이어에 통합

**Files:**
- Modify: `apps/commerce-api/src/main/java/com/loopers/application/queue/QueueService.java` — enter()에 recordEnter 호출
- Modify: `apps/commerce-api/src/main/java/com/loopers/infrastructure/queue/QueueEntryScheduler.java` — processQueue()에 recordTokenIssued, updateWaitingSize 호출

**Step 1: QueueService.enter()에 메트릭 추가**

QueueService에 QueueMetrics 의존성 추가:
```java
private final QueueMetrics queueMetrics;
```

`enter()` 메서드 끝에 추가:
```java
queueMetrics.recordEnter(eventId);
```

**Step 2: QueueEntryScheduler.processQueue()에 메트릭 추가**

QueueEntryScheduler에 QueueMetrics 의존성 추가:
```java
private final QueueMetrics queueMetrics;
```

`processQueue()` 메서드에서 토큰 발급 후:
```java
queueMetrics.recordTokenIssued(eventId, userIds.size());
queueMetrics.updateWaitingSize(eventId, queueRepository.getTotalCount(eventId));
```

**Step 3: 기존 단위 테스트 수정**

QueueServiceTest와 QueueEntrySchedulerTest에 `@Mock QueueMetrics queueMetrics;` 추가.

**Step 4: 테스트 통과 확인**

Run: `./gradlew :apps:commerce-api:test --tests "QueueServiceTest" --tests "QueueEntrySchedulerTest"`
Expected: ALL PASSED

---

### Task 3: k6 테스트 스크립트 작성

**Files:**
- Create: `supports/k6/queue-spike.js`
- Create: `supports/k6/queue-scheduler.js`
- Create: `supports/k6/queue-ttl.js`

**Step 1: Spike 진입 시나리오**

```javascript
// supports/k6/queue-spike.js
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter } from 'k6/metrics';

const enterSuccess = new Counter('queue_enter_success');
const enterConflict = new Counter('queue_enter_conflict');

export const options = {
    scenarios: {
        spike: {
            executor: 'ramping-vus',
            startVUs: 0,
            stages: [
                { duration: '30s', target: 500 },
                { duration: '1m', target: 500 },
                { duration: '30s', target: 0 },
            ],
        },
    },
    thresholds: {
        http_req_duration: ['p(95)<500'],
        queue_enter_success: ['count>0'],
    },
};

export default function () {
    const eventId = 'bf2024';
    const userId = __VU * 10000 + __ITER;

    const res = http.post(
        `http://localhost:8080/api/v1/queue/${eventId}/enter`,
        null,
        { headers: { 'X-User-Id': String(userId) } }
    );

    if (check(res, { 'status is 200': (r) => r.status === 200 })) {
        enterSuccess.add(1);
    } else if (res.status === 409) {
        enterConflict.add(1);
    }

    sleep(0.1);
}
```

**Step 2: 스케줄러 처리량 시나리오**

```javascript
// supports/k6/queue-scheduler.js
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Trend } from 'k6/metrics';

const positionTrend = new Trend('queue_position');

export const options = {
    scenarios: {
        sustained: {
            executor: 'constant-vus',
            vus: 100,
            duration: '5m',
        },
    },
    thresholds: {
        http_req_duration: ['p(95)<300'],
    },
};

export function setup() {
    // 먼저 500명 대기열 채우기
    for (let i = 1; i <= 500; i++) {
        http.post(
            'http://localhost:8080/api/v1/queue/bf2024/enter',
            null,
            { headers: { 'X-User-Id': String(i) } }
        );
    }
    return { startTime: Date.now() };
}

export default function () {
    const userId = __VU;
    const res = http.get(
        'http://localhost:8080/api/v1/queue/bf2024/position',
        { headers: { 'X-User-Id': String(userId) } }
    );

    if (check(res, { 'status is 200': (r) => r.status === 200 })) {
        const body = JSON.parse(res.body);
        if (body.data && body.data.position !== undefined) {
            positionTrend.add(body.data.position);
        }
    }

    sleep(3);
}
```

**Step 3: TTL 만료 시나리오**

```javascript
// supports/k6/queue-ttl.js
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter } from 'k6/metrics';

const tokenReceived = new Counter('token_received');
const tokenExpired = new Counter('token_expired');

export const options = {
    scenarios: {
        ttl_check: {
            executor: 'constant-vus',
            vus: 50,
            duration: '3m',
        },
    },
};

export function setup() {
    for (let i = 1; i <= 50; i++) {
        http.post(
            'http://localhost:8080/api/v1/queue/bf2024/enter',
            null,
            { headers: { 'X-User-Id': String(i) } }
        );
    }
}

export default function () {
    const userId = __VU;
    const res = http.get(
        'http://localhost:8080/api/v1/queue/bf2024/position',
        { headers: { 'X-User-Id': String(userId) } }
    );

    if (check(res, { 'status is 200': (r) => r.status === 200 })) {
        const body = JSON.parse(res.body);
        if (body.data && body.data.token) {
            tokenReceived.add(1);
        }
        if (body.data && body.data.tokenExpiresIn !== null && body.data.tokenExpiresIn <= 0) {
            tokenExpired.add(1);
        }
    }

    sleep(5);
}
```

---

### Task 4: Actuator/Prometheus 엔드포인트 검증

**Step 1: application-test.yml에 actuator 활성화 확인**

기존 `supports/monitoring/src/main/resources/monitoring.yml`이 이미 prometheus 엔드포인트를 포함.

**Step 2: 수동 검증**

로컬 실행 후:
- `http://localhost:8081/actuator/prometheus` 접속
- `queue_enter_total`, `queue_token_issued_total`, `queue_waiting_size` 메트릭 확인

---

### Task 5: 전체 테스트 실행

Run: `./gradlew :apps:commerce-api:test`
Expected: ALL PASSED

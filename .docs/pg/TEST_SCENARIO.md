# PG 외부 API 장애 대응 테스트 시나리오

## 목적

외부 결제 API 장애가 우리 서버 장애로 이어지지 않도록 timeout, retry, circuit-breaker 등의 조치를 단계적으로 적용하고 효과를 검증한다.

## 테스트 접근 방식

```
1. 아무것도 하지 않은 상태 (baseline)
2. 결과 보고 문제 지점 파악 > 개선 시도
... 반복
```

---

## PG 응답 구조

초기 트랜잭션 요청에 대한 HTTP 응답과, 이후 콜백으로 오는 비즈니스 결과가 분리되어 있다.

```
1. 우리 서버 → PG : 결제 요청
2. PG → 우리 서버 : 200 / 400 / 500 (transactionKey 응답)
3. PG → 우리 서버 : 콜백 (비즈니스 성공/실패 결과)
```

---

## 테스트 도구 역할 분리

### PG-Simulator (0.1 ~ 0.6s 응답, 실패 확률 포함)

실제와 유사한 응답 흐름을 제공하므로, 아래 시나리오는 PG-Simulator로 검증한다.

| 시나리오 | HTTP 응답 | 재시도 여부 | 검증 포인트 |
|---------|----------|------------|------------|
| 정상 흐름 | 200 → 콜백(성공) | - | 결제 상태 PENDING → SUCCESS 전환 |
| 비즈니스 실패 | 200 → 콜백(실패) | X (정상 결과) | 결제 상태 PENDING → FAILED 전환 |
| 잘못된 요청 | 400 | X (클라이언트 문제) | 에러 전파, 재시도 없음 확인 |
| PG 서버 오류 | 500 | **O** | retry 동작, circuit-breaker 전환 확인 |

### WireMock

PG-Simulator로 재현하기 어려운 **극단적인 응답 지연**만 WireMock으로 검증한다.
(PG-Simulator 최대 지연이 0.6s이므로 timeout 트리거 불가)

| 파일 | 설명 | 검증 포인트 |
|------|------|------------|
| `scenario_delay.json` | timeout 설정값을 초과하는 긴 지연 | timeout 설정 동작 확인, 스레드 점유 여부 |

> WireMock 실행: `docker/wiremock/run.sh start` / 시나리오 전환: `run.sh load <mapping-file>`

---

## 모니터링 지표

### 1. Thread Pool 고갈

timeout 없이 긴 지연 응답을 받을 경우 Tomcat worker thread가 점유 상태로 쌓인다.
`busy ≈ max`가 되는 순간 **우리 서버 전체 장애**로 이어진다.

| 지표 | 의미 |
|------|------|
| `tomcat.threads.busy` | 현재 처리 중인 요청 수 |
| `tomcat.threads.current` | 전체 생성된 스레드 수 |
| `tomcat.threads.config.max` | 최대 허용 스레드 수 |

### 2. 응답 지연 분포 (Latency)

단순 평균이 아니라 **p99**를 확인해야 한다.

| 지표 | 의미 |
|------|------|
| `http.server.requests` (p50/p95/p99) | 우리 API 응답 시간 분포 |
| `http.client.requests` (RestTemplate) | 외부 PG API 호출 시간 |

### 3. 에러율

500 에러 시 재시도/circuit-breaker 없이 에러가 그대로 전파되는지 확인한다.

| 지표 | 의미 |
|------|------|
| `http.server.requests{status=5xx}` | 우리 API 에러 응답 비율 |
| `http.client.requests{status=5xx}` | PG API 에러 비율 |
| `exceptions{exception=CoreException}` | 내부 예외 발생 횟수 |

### 4. 비즈니스 지표 (PENDING 결제 누적)

외부 API 장애 시 결제가 PENDING으로 쌓이는지, Reconciliation 스케줄러가 제대로 처리하는지 확인한다.

| 지표 | 의미 |
|------|------|
| PENDING 상태 결제 수 (DB 직접 조회) | 외부 API 장애 중 누적 규모 |
| Reconciliation 처리 성공/실패 수 | 복구 로직 동작 여부 |

---

## 측정 방법

```
부하 생성  : k6 or hey
메트릭 수집: Spring Boot Actuator /actuator/metrics
시각화     : Prometheus + Grafana
```

---

## 개선 적용 순서 (권장)

1. **RestTemplate timeout 설정** — 지연 시나리오 대응 (WireMock으로 검증)
2. **Retry** — 500 에러 시나리오 대응 (PG-Simulator로 검증)
3. **Circuit Breaker** — 연속 실패 시 빠른 실패(fail-fast)로 전환
4. **Bulkhead** — 결제 호출이 전체 스레드 풀을 잠식하지 않도록 격리

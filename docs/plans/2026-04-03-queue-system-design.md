# Redis 기반 대기열 시스템 설계

## 결정 사항 요약

| 항목 | 결정 |
|------|------|
| 패키지 위치 | `domain/queue/` — 독립 도메인 |
| 대기열 식별 | 이벤트별 — `queue:{eventId}:waiting` |
| 토큰 저장 | Redis String + TTL — `queue:{eventId}:token:{userId}` |
| 토큰 검증 | Interceptor — `QueueTokenInterceptor` |
| 유저 식별 | `X-User-Id` 헤더 |
| 부하 테스트 | k6 + Prometheus + Grafana |

## Redis Key 설계

| 용도 | Key Pattern | Type | 예시 |
|------|------------|------|------|
| 대기열 | `queue:{eventId}:waiting` | Sorted Set | score=timestamp, member=userId |
| 입장 토큰 | `queue:{eventId}:token:{userId}` | String + TTL | value=UUID, EX 300 |
| 활성 토큰 카운터 | `queue:{eventId}:active-count` | String | INCR/DECR |

## API 명세

```
POST   /api/v1/queue/{eventId}/enter      — 대기열 진입
GET    /api/v1/queue/{eventId}/position   — 순번 + 예상 대기시간 + 토큰 여부
```

- `X-User-Id` 헤더 필수
- 토큰 검증: `QueueTokenInterceptor`가 `/api/v1/orders/**` 가로채서 처리

### 응답 형식

```json
// POST /queue/bf2024/enter
{ "meta": { "result": "SUCCESS" },
  "data": { "position": 42, "estimatedWaitSeconds": 120 } }

// GET /queue/bf2024/position (대기 중)
{ "data": { "position": 15, "estimatedWaitSeconds": 45, "token": null } }

// GET /queue/bf2024/position (토큰 발급됨)
{ "data": { "position": 0, "estimatedWaitSeconds": 0,
            "token": "tok_abc123", "tokenExpiresIn": 280 } }
```

## 패키지 구조

```
com.loopers/
├── interfaces/api/queue/
│   ├── QueueV1Api.java
│   ├── QueueV1ApiSpec.java
│   └── QueueV1Dto.java
│
├── application/queue/
│   ├── QueueFacade.java
│   └── QueueInfo.java
│
├── domain/queue/
│   ├── QueueService.java
│   ├── QueueTokenService.java
│   ├── QueueRepository.java          (interface)
│   └── QueueTokenRepository.java     (interface)
│
├── infrastructure/queue/
│   ├── QueueRepositoryImpl.java
│   ├── QueueTokenRepositoryImpl.java
│   └── QueueEntryScheduler.java
│
└── config/
    └── QueueTokenInterceptor.java
```

## 스케줄러 & 처리량 산정

- 주기: 3초 (`fixedDelay = 3000`)
- 배치 크기: 10명
- 산정 근거: HikariCP 10 커넥션, 주문 1건 ~200ms, 보수적으로 풀의 50%만 사용
- 토큰 TTL: 300초 (5분)

## 메트릭 (Prometheus)

```
queue_enter_total{event_id}           — 진입 요청 수
queue_token_issued_total{event_id}    — 토큰 발급 수
queue_token_expired_total{event_id}   — 토큰 만료 수
queue_order_completed_total{event_id} — 주문 완료 수
queue_waiting_size{event_id}          — 현재 대기 인원 (Gauge)
queue_active_tokens{event_id}         — 활성 토큰 수 (Gauge)
queue_wait_duration_seconds{event_id} — 대기 시간 분포 (Histogram)
```

## k6 부하 테스트

| 시나리오 | VU | Duration | 검증 포인트 |
|---------|-----|----------|-----------|
| Spike 진입 | 0→500 (30s ramp) | 2min | 순서 보장, 중복 차단 |
| 스케줄러 처리 | 100 유지 | 5min | 처리량 안정성 |
| TTL 만료 | 50 (주문 안 함) | 3min | 토큰 만료 확인 |

파일 위치: `supports/k6/`

## Step별 구현 범위

### Step 1: Redis 대기열
- QueueRepository (interface + impl)
- QueueService
- QueueV1Api (enter, position)
- 통합 테스트

### Step 2: 입장 토큰 & 스케줄러
- QueueTokenRepository (interface + impl)
- QueueTokenService
- QueueEntryScheduler
- QueueTokenInterceptor
- 토큰 TTL 테스트

### Step 3: 실시간 순번 + k6
- 예상 대기시간 계산 로직
- position API에 토큰 정보 포함
- Prometheus 메트릭 추가
- k6 테스트 스크립트
- Grafana 대시보드

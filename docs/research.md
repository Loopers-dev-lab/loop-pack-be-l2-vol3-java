# Round 6 - PG 결제 연동 & Resilience 설계

## 브랜치 전략

### 구조

```
volume-5 (기존 작업 완료)
  └─ volume-6 (통합 브랜치)
       ├─ week6-feature/payment-domain      ← 1단계
       ├─ week6-feature/resilience           ← 2단계
       └─ week6-feature/payment-callback     ← 3단계
```

### 각 브랜치 범위

| 브랜치 | 작업 범위 | 산출물 |
|--------|-----------|--------|
| `payment-domain` | 결제 도메인 모델(PaymentModel, PaymentStatus) + PG 클라이언트(RestTemplate) + 결제 요청 API | Entity, Repository, Service, Facade, Controller, PgClient |
| `resilience` | Timeout, CircuitBreaker, Fallback 적용 + (Optional) Retry | resilience4j 의존성, 설정, Fallback 핸들러 |
| `payment-callback` | PG 콜백 수신 API + 결제 상태 확인 폴링/수동 복구 API | Callback Controller, 상태 복구 로직 |

### 머지 순서 (순차 — 의존성 존재)

```
1. payment-domain   → volume-6 (PR 머지)
2. volume-6에서 resilience 분기 → 작업 → volume-6 머지
3. volume-6에서 payment-callback 분기 → 작업 → volume-6 머지
4. volume-6 → main (최종 PR)
```

### 이유

- **순서 의존성**: resilience는 PG 클라이언트가 있어야 적용 가능, callback은 결제 도메인이 있어야 의미 있음
- **기존 패턴 유지**: `volume-N` + `weekN-feature/*` 네이밍 컨벤션
- **리뷰 단위 분리**: 도메인/Resilience/콜백 각각 독립 리뷰 가능

---

## 현재 프로젝트 상태 분석

### 존재하는 것

| 항목 | 상태 | 경로/비고 |
|------|------|-----------|
| Order 도메인 | ✅ 완전함 | domain/order/, application/order/, infrastructure/order/, interfaces/api/order/ |
| Money VO | ✅ | domain/product/Money.java |
| BaseEntity | ✅ | modules/jpa/.../BaseEntity.java |
| ErrorType/CoreException | ✅ | support/error/ |
| Docker (MySQL, Redis, Kafka) | ✅ | docker/infra-compose.yml |
| 테스트 인프라 (TestContainers) | ✅ | MySQL, Redis TestContainers |

### 새로 필요한 것

| 항목 | 상태 | 비고 |
|------|------|------|
| pg-simulator 모듈 | ❌ | 별도 SpringBoot App, GitHub에서 가져와야 함 |
| Payment 도메인 | ❌ | PaymentModel, PaymentStatus, PaymentRepository 등 |
| PG 클라이언트 | ❌ | RestTemplate 기반 외부 호출 (프로젝트 최초) |
| resilience4j 의존성 | ❌ | CircuitBreaker, Timeout, Retry, Fallback |
| 결제 콜백 수신 API | ❌ | PG → commerce-api 콜백 엔드포인트 |

### OrderStatus 현재 상태

```java
CREATED, CONFIRMED, SHIPPING, DELIVERED, CANCELLED
```

→ 결제 흐름 반영을 위해 `PAYMENT_PENDING`, `PAYMENT_COMPLETED`, `PAYMENT_FAILED` 등 추가 검토 필요

---

## 기술적 고려사항

### PG-Simulator 특성

| 항목 | 값 |
|------|-----|
| 요청 성공 확률 | 60% |
| 요청 지연 | 100ms ~ 500ms |
| 처리 지연 | 1s ~ 5s |
| 처리 결과 - 성공 | 70% |
| 처리 결과 - 한도 초과 | 20% |
| 처리 결과 - 잘못된 카드 | 10% |

### 비동기 결제 흐름

```
[commerce-api] → POST /api/v1/payments → [pg-simulator]
                                              │
                                     (1s~5s 처리)
                                              │
                                              ▼
[commerce-api] ← POST callbackUrl ← [pg-simulator]
```

- 요청과 처리가 분리됨
- 콜백이 오지 않을 수 있음 → 상태 확인 API로 폴링 필요

### Resilience 적용 대상

| 패턴 | 적용 지점 | 목적 |
|------|-----------|------|
| Timeout | PG 결제 요청 호출 | 응답 지연 시 빠른 실패 |
| CircuitBreaker | PG 클라이언트 전체 | PG 장애 시 연쇄 실패 방지 |
| Fallback | CircuitBreaker OPEN 시 | 사용자에게 적절한 응답 반환 |
| Retry (Optional) | PG 결제 상태 확인 | 일시적 실패 복구 |

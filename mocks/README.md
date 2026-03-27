# PG Mock 서버 (mocks/)

토스페이먼츠 / 나이스페이먼츠 Mock PG 서버.
실무 코드 한 줄 안 바꾸고 `base-url`만 전환하여 Resilience4j(타임아웃, 서킷브레이커, 재시도) 테스트.

---

## Quick Start

```bash
# mock-toss (:8090)
./gradlew :mocks:mock-toss:bootRun

# mock-nice (:8091)
./gradlew :mocks:mock-nice:bootRun
```

---

## commerce-api 연결

`commerce-api`의 `application-local.yml`에 base-url만 변경:

```yaml
payment:
  toss:
    base-url: http://localhost:8090   # mock-toss
    secret-key: test_sk_xxxx         # 아무 값이나 OK
  nice:
    base-url: http://localhost:8091   # mock-nice
    client-key: testClientKey
    secret-key: testSecretKey
```

실무 코드 변경: **없음**. 프로필만 전환.

---

## mock-toss API (포트 8090)

모든 API에 `Authorization: Basic {시크릿키:를 Base64}` 헤더 필요.

### 결제 승인

```bash
curl -X POST http://localhost:8090/v1/payments/confirm \
  -H "Authorization: Basic $(echo -n 'test_sk_xxxx:' | base64)" \
  -H "Content-Type: application/json" \
  -d '{"paymentKey":"tpk_001","orderId":"ORDER-001","amount":50000}'
```

### 결제 조회 (paymentKey)

```bash
curl http://localhost:8090/v1/payments/tpk_001 \
  -H "Authorization: Basic $(echo -n 'test_sk_xxxx:' | base64)"
```

### 결제 조회 (orderId)

```bash
curl http://localhost:8090/v1/payments/orders/ORDER-001 \
  -H "Authorization: Basic $(echo -n 'test_sk_xxxx:' | base64)"
```

### 결제 취소 (전액)

```bash
curl -X POST http://localhost:8090/v1/payments/tpk_001/cancel \
  -H "Authorization: Basic $(echo -n 'test_sk_xxxx:' | base64)" \
  -H "Content-Type: application/json" \
  -d '{"cancelReason":"고객 요청"}'
```

### 결제 취소 (부분)

```bash
curl -X POST http://localhost:8090/v1/payments/tpk_001/cancel \
  -H "Authorization: Basic $(echo -n 'test_sk_xxxx:' | base64)" \
  -H "Content-Type: application/json" \
  -d '{"cancelReason":"부분 환불","cancelAmount":3000}'
```

---

## mock-nice API (포트 8091)

모든 API에 `Authorization: Basic {clientKey:secretKey를 Base64}` 헤더 필요.

### 결제 승인

```bash
curl -X POST http://localhost:8091/v1/payments/nicuntct_001 \
  -H "Authorization: Basic $(echo -n 'testClientKey:testSecretKey' | base64)" \
  -H "Content-Type: application/json" \
  -d '{"amount":50000}'
```

### 거래 조회 (tid)

```bash
curl http://localhost:8091/v1/payments/nicuntct_001 \
  -H "Authorization: Basic $(echo -n 'testClientKey:testSecretKey' | base64)"
```

### 거래 조회 (orderId)

```bash
curl http://localhost:8091/v1/payments/find/ORDER-nicuntct_001 \
  -H "Authorization: Basic $(echo -n 'testClientKey:testSecretKey' | base64)"
```

### 결제 취소 (전액)

```bash
curl -X POST http://localhost:8091/v1/payments/nicuntct_001/cancel \
  -H "Authorization: Basic $(echo -n 'testClientKey:testSecretKey' | base64)" \
  -H "Content-Type: application/json" \
  -d '{"reason":"고객 요청","orderId":"ORDER-nicuntct_001"}'
```

### 결제 취소 (부분)

```bash
curl -X POST http://localhost:8091/v1/payments/nicuntct_001/cancel \
  -H "Authorization: Basic $(echo -n 'testClientKey:testSecretKey' | base64)" \
  -H "Content-Type: application/json" \
  -d '{"reason":"부분 환불","orderId":"ORDER-nicuntct_001","cancelAmt":3000}'
```

---

## 테스트 초기화 API

테스트 간 격리를 위해 Mock 서버 상태를 초기화합니다. 인증 불필요.

```bash
# mock-toss 초기화 (결제 저장소 + 멱등키 캐시 + 카오스 설정)
curl -X DELETE http://localhost:8090/test/reset

# mock-nice 초기화 (결제 저장소 + 카오스 설정)
curl -X DELETE http://localhost:8091/test/reset
```

초기화 대상:
- 인메모리 결제 저장소 전체 삭제
- 멱등키 캐시 전체 삭제 (mock-toss만)
- 카오스 설정 기본값 복구 (NORMAL, 3000~10000ms, 50%, affectReadApis=false)

---

## 카오스 모드

모든 Mock 서버가 공유하는 장애 시뮬레이션. POST/PUT 요청에만 적용되고, GET(조회)은 기본적으로 영향 안 받음.

| 모드 | 동작 | 용도 |
|------|------|------|
| `NORMAL` | 정상 응답 | 기본 |
| `SLOW` | 3~10초 랜덤 지연 후 응답 | 타임아웃 테스트 |
| `TIMEOUT` | 응답 안 줌 (5분 대기) | 타임아웃 + 서킷브레이커 |
| `DEAD` | 즉시 500 에러 | 서킷브레이커 OPEN |
| `PARTIAL_FAILURE` | N% 확률로 실패 | slidingWindow 테스트 |

```bash
# 모드 조회
curl http://localhost:8090/chaos/mode

# 모드 변경
curl -X PUT "http://localhost:8090/chaos/mode?mode=DEAD"
curl -X PUT "http://localhost:8090/chaos/mode?mode=SLOW&slowMinMs=5000&slowMaxMs=15000"
curl -X PUT "http://localhost:8090/chaos/mode?mode=PARTIAL_FAILURE&partialFailureRate=50"
curl -X PUT "http://localhost:8090/chaos/mode?mode=NORMAL"

# 요청별 오버라이드
curl -X POST http://localhost:8090/v1/payments/confirm \
  -H "X-CHAOS-MODE: DEAD" \
  -H "Authorization: Basic ..." \
  -H "Content-Type: application/json" \
  -d '{"paymentKey":"tpk_002","orderId":"ORDER-002","amount":10000}'
```

---

## 에러 트리거

orderId(confirm) 또는 cancelReason(cancel)에 키워드를 포함시키면 해당 에러를 반환. 카오스 모드와 독립적으로 동작.

### mock-toss 주요 에러

| 키워드 | HTTP | 에러코드 | 재시도 |
|--------|------|---------|--------|
| `reject_company` | 403 | `REJECT_CARD_COMPANY` | X |
| `provider_error` | 400 | `PROVIDER_ERROR` | O |
| `system_error` | 500 | `FAILED_INTERNAL_SYSTEM_PROCESSING` | O |

### mock-nice 주요 에러

| 키워드 | HTTP | resultCode | 재시도 |
|--------|------|-----------|--------|
| `card_error` | 400 | `3011` | X |
| `provider_error` | 500 | `A110` | O |
| `system_error` | 500 | `9002` | O |

> 전체 에러 트리거 목록은 [docs/toss/mock/error-triggers.md](docs/toss/mock/error-triggers.md), [docs/nice/mock/error-triggers.md](docs/nice/mock/error-triggers.md) 참조.

---

## 상세 문서

```
mocks/docs/
├── toss/
│   ├── spec/    ← 토스페이먼츠 공식 API 스펙
│   └── mock/    ← Mock 가이드 (api-guide, error-triggers, payment-response, fidelity-policy)
└── nice/
    ├── spec/    ← 나이스페이먼츠 공식 API 스펙
    └── mock/    ← Mock 가이드 (api-guide, error-triggers, payment-response, fidelity-policy)
```

| 문서 | 내용 |
|------|------|
| `mock/api-guide.md` | API 전체 가이드 (인증, 승인, 조회, 취소, 멱등키, 카오스, 시나리오 레시피) |
| `mock/error-triggers.md` | 에러 트리거 키워드 전체 목록 |
| `mock/payment-response.md` | 결제 응답 필드 상세 |
| `mock/fidelity-policy.md` | Mock 충실도 원칙 (공식 스펙과의 차이점) |
| `spec/*.md` | PG 공식 API 스펙 원본 |

---

## 프로젝트 구조

```
mocks/
├── mock-common/     ← 카오스 모드 엔진 (ChaosInterceptor, ChaosController)
├── mock-toss/       ← :8090 | 토스페이먼츠 API Mock
├── mock-nice/       ← :8091 | 나이스페이먼츠 API Mock
├── docs/            ← PG별 공식 스펙 및 Mock 가이드
└── README.md
```

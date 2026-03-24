# PG Simulator 연동 가이드

## 개요

PG Simulator는 실제 결제 게이트웨이와 동일한 인터페이스를 제공하는 테스트용 결제 시뮬레이터입니다.
**비동기 결제 처리** 방식을 사용하며, 결제 결과는 콜백 URL로 수신합니다.

---

## 결제 플로우

```
[고객 서비스]                          [PG Simulator]
     │                                       │
     │── POST /api/v1/payments ─────────────>│  결제 요청
     │<─ { transactionKey, status: PENDING } ─│  트랜잭션 키 발급
     │                                       │
     │                           (1~5초 후 비동기 처리)
     │                                       │
     │<─ POST {callbackUrl} ─────────────────│  결제 결과 콜백
     │   { transactionKey, status, reason }  │
```

> **중요:** 결제 요청 API는 **40% 확률로 일시적 오류(500)**를 반환합니다. 반드시 재시도 로직을 구현해야 합니다.

---

## 공통 사항

### Base URL
```
http://localhost:8082
```

### 인증 헤더
모든 요청에 아래 헤더가 필수입니다.

| 헤더명 | 설명 | 예시 |
|--------|------|------|
| `X-USER-ID` | 가맹점 사용자 식별자 | `user-001` |

---

## API 명세

### 1. 결제 요청

```
POST /api/v1/payments
```

**Request Body**

| 필드 | 타입 | 필수 | 설명 | 제약 |
|------|------|------|------|------|
| `orderId` | String | Y | 주문 ID | 6자리 이상 |
| `cardType` | String | Y | 카드 종류 | `SAMSUNG`, `KB`, `HYUNDAI` |
| `cardNo` | String | Y | 카드 번호 | `xxxx-xxxx-xxxx-xxxx` 형식 |
| `amount` | Long | Y | 결제 금액 | 양의 정수 |
| `callbackUrl` | String | Y | 결제 결과 수신 URL | `http://localhost:8080`으로 시작 |

**요청 예시**
```json
POST /api/v1/payments
X-USER-ID: user-001
Content-Type: application/json

{
"orderId": "ORDER-20260318-001",
"cardType": "SAMSUNG",
"cardNo": "1234-5678-9012-3456",
"amount": 50000,
"callbackUrl": "http://localhost:8080/callbacks/payment"
}
```

**성공 응답 (200)**
```json
{
  "data": {
    "transactionKey": "20260318:TR:a1b2c3",
    "status": "PENDING",
    "reason": null
  }
}
```

**실패 응답 (400) - 요청 파라미터 오류**

| 원인 | 메시지 |
|------|--------|
| `X-USER-ID` 헤더 누락 | `유저 ID 헤더는 필수입니다.` |
| `orderId`가 blank이거나 6자 미만 | `주문 ID는 6자리 이상 문자열이어야 합니다.` |
| `cardNo` 형식 불일치 | `카드 번호는 xxxx-xxxx-xxxx-xxxx 형식이어야 합니다.` |
| `amount`가 0 이하 | `결제금액은 양의 정수여야 합니다.` |
| `callbackUrl` 형식 불일치 | `콜백 URL 은 http://localhost:8080 로 시작해야 합니다.` |

```json
{
  "code": "Bad Request",
  "message": "카드 번호는 xxxx-xxxx-xxxx-xxxx 형식이어야 합니다."
}
```

> 400 응답은 **재시도해도 동일하게 실패**합니다. 요청 파라미터를 수정해야 합니다.

**실패 응답 (500) - 40% 확률로 발생**
```json
{
  "code": "Internal Server Error",
  "message": "현재 서버가 불안정합니다. 잠시 후 다시 시도해주세요."
}
```

> 500 응답 시 **동일한 파라미터로 재시도**하세요. 트랜잭션이 생성되지 않은 상태입니다.

---

### 2. 결제 결과 콜백 수신

결제 요청 성공 후 **1~5초 내**에 등록한 `callbackUrl`로 POST 요청이 전송됩니다.

**콜백 수신 Body**

```json
{
  "transactionKey": "20260318:TR:a1b2c3",
  "orderId": "ORDER-20260318-001",
  "cardType": "SAMSUNG",
  "cardNo": "1234-5678-9012-3456",
  "amount": 50000,
  "status": "SUCCESS",
  "reason": "정상 승인되었습니다."
}
```

**결제 결과별 처리 방법**

| status | reason | 처리 |
|--------|--------|------|
| `SUCCESS` | `정상 승인되었습니다.` | 주문 완료 처리 |
| `FAILED` | `잘못된 카드입니다. 다른 카드를 선택해주세요.` | 카드 변경 후 재결제 유도 |
| `FAILED` | `한도초과입니다. 다른 카드를 선택해주세요.` | 카드 변경 후 재결제 유도 |

> **콜백 미수신 시:** 콜백 전송은 1회만 시도됩니다. 미수신 시 아래 조회 API로 폴링하세요.

---

### 3. 트랜잭션 단건 조회

```
GET /api/v1/payments/{transactionKey}
```

**요청 예시**
```
GET /api/v1/payments/20260318:TR:a1b2c3
X-USER-ID: user-001
```

**성공 응답 (200)**
```json
{
  "data": {
    "transactionKey": "20260318:TR:a1b2c3",
    "orderId": "ORDER-20260318-001",
    "cardType": "SAMSUNG",
    "cardNo": "1234-5678-9012-3456",
    "amount": 50000,
    "status": "SUCCESS",
    "reason": "정상 승인되었습니다."
  }
}
```

---

### 4. 주문별 트랜잭션 전체 조회

```
GET /api/v1/payments?orderId={orderId}
```

동일 주문에 대한 재시도 이력을 포함한 전체 트랜잭션 목록을 반환합니다.

**요청 예시**
```
GET /api/v1/payments?orderId=ORDER-20260318-001
X-USER-ID: user-001
```

**성공 응답 (200)**
```json
{
  "data": {
    "orderId": "ORDER-20260318-001",
    "transactions": [
      {
        "transactionKey": "20260318:TR:a1b2c3",
        "status": "FAILED",
        "reason": "한도초과입니다. 다른 카드를 선택해주세요."
      },
      {
        "transactionKey": "20260318:TR:d4e5f6",
        "status": "SUCCESS",
        "reason": "정상 승인되었습니다."
      }
    ]
  }
}
```

---

## 에러 코드

| HTTP Status | code | 원인 |
|-------------|------|------|
| 400 | `Bad Request` | 필수 헤더 누락, 유효하지 않은 요청 파라미터 |
| 404 | `Not Found` | 존재하지 않는 transactionKey 또는 orderId |
| 500 | `Internal Server Error` | 일시적 서버 오류 (재시도 필요) |

---

## 재시도 및 재결제 가이드

### 케이스 1: 결제 요청 API 자체가 실패한 경우 (HTTP 500)

트랜잭션이 생성되지 않은 상태이므로, **동일한 파라미터로 즉시 재시도**합니다.

```
POST /api/v1/payments  →  500 응답
         ↓ (재시도)
POST /api/v1/payments  →  200 응답 (transactionKey 발급)
```

### 케이스 2: 결제 결과가 FAILED인 경우 (콜백 수신)

결제가 실패한 주문은 같은 `orderId`로 새 트랜잭션을 생성할 수 있습니다.
고객 서비스 측에서는 아래 순서로 처리하세요.

1. 콜백에서 `status: FAILED` 수신
2. **자체 DB의 결제 연동 정보(transactionKey 등)를 초기화**
3. 사용자에게 결제 수단 변경 유도
4. 새 카드 정보로 `POST /api/v1/payments` 재요청 (동일 `orderId` 사용 가능)

```
콜백: { status: "FAILED", reason: "한도초과..." }
         ↓
  자체 DB 결제정보 초기화 (transactionKey = null 등)
         ↓
  사용자 카드 변경
         ↓
POST /api/v1/payments (동일 orderId, 새 카드 정보)
```

### 케이스 3: 콜백을 받지 못한 경우

결제 요청 후 **5초 이상 콜백이 미수신**되면, 트랜잭션 조회 API로 상태를 확인하세요.

```
GET /api/v1/payments/{transactionKey}
→ status가 PENDING이면 아직 처리 중 (추가 대기)
→ status가 SUCCESS/FAILED이면 해당 결과로 처리
```

---

## 시뮬레이터 동작 특성 요약

| 항목 | 내용 |
|------|------|
| 결제 요청 실패율 | 40% (HTTP 500) |
| 결제 처리 시간 | 요청 성공 후 1~5초 (비동기) |
| 승인율 | 약 70% |
| 실패 원인 | 한도초과 약 20%, 잘못된 카드 약 10% |
| 트랜잭션 키 형식 | `yyyyMMdd:TR:xxxxxx` (예: `20260318:TR:a1b2c3`) |
| 콜백 재전송 | 없음 (1회만 전송) |

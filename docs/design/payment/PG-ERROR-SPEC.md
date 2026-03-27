# PG Simulator 에러 스펙

PG 시뮬레이터(`:8082`)가 반환하는 에러 케이스를 분석하고, Commerce API의 예외 매핑 전략을 정의한다.

## 1. PG API 엔드포인트

| Method | Path | 설명 |
|--------|------|------|
| POST | `/api/v1/payments` | 결제 요청 (동기) |
| GET | `/api/v1/payments/{transactionKey}` | 결제 상태 조회 |
| GET | `/api/v1/payments?orderId={orderId}` | 주문별 결제 조회 |

---

## 2. 결제 요청 단계 에러 (POST /api/v1/payments)

결제 요청 시점에서 **동기적**으로 발생하는 에러.

### 2-1. PG 서버 불안정 (40% 랜덤)

```json
HTTP 500
{
  "meta": { "result": "FAIL", "errorCode": "Internal Server Error", "message": "현재 서버가 불안정합니다. 잠시 후 다시 시도해주세요." },
  "data": null
}
```

- **원인**: PG 시뮬레이터 내부 랜덤 실패 (40% 확률)
- **고객 안내**: "결제 서비스가 일시적으로 불안정합니다. 잠시 후 다시 시도해주세요."
- **Commerce ErrorCode**: `PAYMENT_005` (PG_SERVICE_UNAVAILABLE)

### 2-2. 잘못된 요청 파라미터 (Validation)

```json
HTTP 400
{
  "meta": { "result": "FAIL", "errorCode": "Bad Request", "message": "{구체적 메시지}" },
  "data": null
}
```

| PG message | 원인 | Commerce ErrorCode |
|------------|------|-------------------|
| "주문 ID는 6자리 이상 문자열이어야 합니다." | orderId 형식 오류 | `PAYMENT_006` (PG_REQUEST_FAILED) |
| "카드 번호는 xxxx-xxxx-xxxx-xxxx 형식이어야 합니다." | cardNo 형식 오류 | `PAYMENT_006` (PG_REQUEST_FAILED) |
| "결제금액은 양의 정수여야 합니다." | amount <= 0 | `PAYMENT_006` (PG_REQUEST_FAILED) |
| "콜백 URL은 http://localhost:8080 로 시작해야 합니다." | callbackUrl 형식 오류 | `PAYMENT_006` (PG_REQUEST_FAILED) |
| "요청 금액은 0 보다 큰 정수여야 합니다." | Command 검증 실패 | `PAYMENT_006` (PG_REQUEST_FAILED) |

### 2-3. 유저 헤더 누락

```json
HTTP 400
{
  "meta": { "result": "FAIL", "errorCode": "Bad Request", "message": "유저 ID 헤더는 필수입니다." },
  "data": null
}
```

- **Commerce ErrorCode**: `PAYMENT_006` (PG_REQUEST_FAILED)

### 2-4. PG 연결 실패 (네트워크)

PG 시뮬레이터가 기동되지 않았거나 네트워크 문제로 연결 자체가 안 되는 경우.

- **원인**: Connection refused / timeout
- **고객 안내**: "결제 서비스에 연결할 수 없습니다. 잠시 후 다시 시도해주세요."
- **Commerce ErrorCode**: `PAYMENT_005` (PG_SERVICE_UNAVAILABLE)

---

## 3. 비동기 처리 단계 에러 (Callback)

결제 요청 성공 후, PG가 **1~5초 비동기 처리** 후 콜백으로 전달하는 결과.

### 콜백 응답 형식 (TransactionInfo)

```json
POST {callbackUrl}
{
  "transactionKey": "20260316:TR:abc123",
  "orderId": "000001",
  "cardType": "SAMSUNG",
  "cardNo": "1234-5678-9012-3456",
  "amount": 50000,
  "status": "SUCCESS" | "FAILED",
  "reason": "..." | null
}
```

### 3-1. 결제 승인 (70%)

| 필드 | 값 |
|------|------|
| status | `SUCCESS` |
| reason | `"정상 승인되었습니다."` |

### 3-2. 한도 초과 (20%)

| 필드 | 값 |
|------|------|
| status | `FAILED` |
| reason | `"한도초과입니다. 다른 카드를 선택해주세요."` |

- **고객 안내**: "카드 한도가 초과되었습니다. 다른 카드로 결제해주세요."
- **Commerce failureCode**: `LIMIT_EXCEEDED`

### 3-3. 잘못된 카드 (10%)

| 필드 | 값 |
|------|------|
| status | `FAILED` |
| reason | `"잘못된 카드입니다. 다른 카드를 선택해주세요."` |

- **고객 안내**: "카드 정보가 올바르지 않습니다. 카드 번호를 확인해주세요."
- **Commerce failureCode**: `INVALID_CARD`

---

## 4. Commerce API 에러 매핑 전략

### 4-1. ErrorCode 매핑표

| 시점 | PG 상태 | PG reason/message | Commerce ErrorCode | 고객 노출 메시지 |
|------|---------|-------------------|-------------------|----------------|
| 요청 | 500 | 서버 불안정 | `PG_SERVICE_UNAVAILABLE` | 결제 서비스가 일시적으로 불안정합니다. 잠시 후 다시 시도해주세요. |
| 요청 | 400 | Validation 실패 | `PG_REQUEST_FAILED` | PG 메시지 그대로 전달 |
| 요청 | 연결 실패 | - | `PG_SERVICE_UNAVAILABLE` | 결제 서비스에 연결할 수 없습니다. 잠시 후 다시 시도해주세요. |
| 콜백 | FAILED | 한도초과 | failureCode=`LIMIT_EXCEEDED` | 카드 한도가 초과되었습니다. 다른 카드로 결제해주세요. |
| 콜백 | FAILED | 잘못된 카드 | failureCode=`INVALID_CARD` | 카드 정보가 올바르지 않습니다. 카드 번호를 확인해주세요. |
| 콜백 | FAILED | 기타 | failureCode=`PG_ERROR` | 결제 처리 중 오류가 발생했습니다. 다시 시도해주세요. |

### 4-2. 프론트 전달 방식

| 시점 | 전달 방식 |
|------|----------|
| 요청 실패 | `CoreException` throw → ControllerAdvice → `ApiResponse.fail()` → 프론트 catch |
| 콜백 실패 | `GET /api/v1/payments/status?orderId=` 폴링 → `failureCode` + `failureMessage` 반환 |

### 4-3. 고객 노출 메시지 매핑 (failureCode → 한글)

```
LIMIT_EXCEEDED  → "카드 한도가 초과되었습니다. 다른 카드로 결제해주세요."
INVALID_CARD    → "카드 정보가 올바르지 않습니다. 카드 번호를 확인해주세요."
PG_ERROR        → "결제 처리 중 오류가 발생했습니다. 다시 시도해주세요."
PG_REQUEST_FAILED → "결제 요청에 실패했습니다. 다시 시도해주세요."
```

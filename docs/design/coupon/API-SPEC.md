# Coupon API 스펙

> 공통 응답 포맷: `ApiResponse<T>` — `{ meta: { result, errorCode, message }, data: T }`

---

## Customer API

### POST `/api/v1/coupons/{couponId}/issue`

쿠폰 발급

**Path Parameter**

| 이름 | 타입 | 설명 |
|------|------|------|
| couponId | Long | 쿠폰 템플릿 ID |

**Request Header**

| 이름 | 필수 | 설명 |
|------|------|------|
| X-Loopers-LoginId | O | 회원 ID |
| X-Loopers-LoginPw | O | 회원 PW |

**Request Body**: 없음

**Response**: `ApiResponse<Object>` (data = null)

```json
{
  "meta": { "result": "SUCCESS", "errorCode": null, "message": null },
  "data": null
}
```

**에러 응답**

| 상황 | HTTP | errorCode | message |
|------|------|-----------|---------|
| 쿠폰 없음/삭제됨 | 404 | COUPON_001 | 쿠폰을 찾을 수 없습니다. |
| 만료된 쿠폰 | 400 | COUPON_003 | 만료된 쿠폰입니다. |
| 수량 소진 | 400 | COUPON_004 | 쿠폰 수량이 소진되었습니다. |
| 이미 발급받음 | 409 | COUPON_005 | 이미 발급받은 쿠폰입니다. |

---

### GET `/api/v1/users/me/coupons`

내 쿠폰 목록 조회

**Request Header**

| 이름 | 필수 | 설명 |
|------|------|------|
| X-Loopers-LoginId | O | 회원 ID |
| X-Loopers-LoginPw | O | 회원 PW |

**Response**: `ApiResponse<List<OwnedCouponDetail>>`

```json
{
  "meta": { "result": "SUCCESS", "errorCode": null, "message": null },
  "data": [
    {
      "ownedCouponId": 1,
      "couponId": 10,
      "couponName": "신규가입 10% 할인",
      "discountType": "RATE",
      "discountValue": 10,
      "minOrderAmount": 10000,
      "status": "AVAILABLE",
      "usedAt": null,
      "expiredAt": "2026-12-31T23:59:59+09:00",
      "issuedAt": "2026-03-01T10:00:00+09:00"
    },
    {
      "ownedCouponId": 2,
      "couponId": 11,
      "couponName": "봄맞이 5000원 할인",
      "discountType": "FIXED",
      "discountValue": 5000,
      "minOrderAmount": null,
      "status": "USED",
      "usedAt": "2026-03-05T14:30:00+09:00",
      "expiredAt": "2026-06-30T23:59:59+09:00",
      "issuedAt": "2026-03-01T12:00:00+09:00"
    }
  ]
}
```

**응답 필드**

| 필드 | 타입 | 설명 |
|------|------|------|
| ownedCouponId | Long | 소유 쿠폰 ID |
| couponId | Long | 쿠폰 템플릿 ID |
| couponName | String | 쿠폰명 |
| discountType | String | FIXED / RATE |
| discountValue | Long | 할인값 (정액: 원, 정률: %) |
| minOrderAmount | Long? | 최소 주문 금액 (nullable) |
| status | String | AVAILABLE / USED / EXPIRED |
| usedAt | ZonedDateTime? | 사용 일시 (nullable) |
| expiredAt | ZonedDateTime | 쿠폰 유효기간 |
| issuedAt | ZonedDateTime | 발급 일시 (= createdAt) |

---

## Admin API

### POST `/api-admin/v1/coupons`

쿠폰 등록

**Request Header**

| 이름 | 필수 | 설명 |
|------|------|------|
| X-Loopers-Ldap | O | `loopers.admin` |

**Request Body**

```json
{
  "name": "신규가입 10% 할인",
  "discountType": "RATE",
  "discountValue": 10,
  "minOrderAmount": 10000,
  "totalQuantity": 1000,
  "expiredAt": "2026-12-31T23:59:59"
}
```

| 필드 | 타입 | 필수 | 검증 | 설명 |
|------|------|------|------|------|
| name | String | O | @NotBlank | 쿠폰명 |
| discountType | String | O | @NotNull | FIXED / RATE |
| discountValue | Long | O | @NotNull, @Min(1) | 할인값. RATE일 때 1~100 |
| minOrderAmount | Long | X | @Min(0) | 최소 주문 금액 (nullable) |
| totalQuantity | Integer | O | @NotNull, @Min(1) | 총 발급 가능 수량 |
| expiredAt | ZonedDateTime | O | @NotNull | 유효기간 (미래 시각이어야 함) |

**Response**: `ApiResponse<DetailResponse>`

```json
{
  "meta": { "result": "SUCCESS", "errorCode": null, "message": null },
  "data": {
    "id": 1,
    "name": "신규가입 10% 할인",
    "discountType": "RATE",
    "discountValue": 10,
    "minOrderAmount": 10000,
    "totalQuantity": 1000,
    "issuedQuantity": 0,
    "expiredAt": "2026-12-31T23:59:59+09:00",
    "createdAt": "2026-03-01T10:00:00+09:00",
    "updatedAt": "2026-03-01T10:00:00+09:00"
  }
}
```

---

### GET `/api-admin/v1/coupons?page=0&size=20`

쿠폰 목록 조회

**Query Parameter**

| 이름 | 타입 | 기본값 | 설명 |
|------|------|--------|------|
| page | int | 0 | 페이지 번호 |
| size | int | 20 | 페이지당 항목 수 |

**Response**: `ApiResponse<ListResponse>`

```json
{
  "meta": { "result": "SUCCESS", "errorCode": null, "message": null },
  "data": {
    "page": 0,
    "size": 20,
    "totalElements": 2,
    "totalPages": 1,
    "items": [
      {
        "id": 1,
        "name": "신규가입 10% 할인",
        "discountType": "RATE",
        "discountValue": 10,
        "totalQuantity": 1000,
        "issuedQuantity": 150,
        "expiredAt": "2026-12-31T23:59:59+09:00",
        "createdAt": "2026-03-01T10:00:00+09:00"
      },
      {
        "id": 2,
        "name": "봄맞이 5000원 할인",
        "discountType": "FIXED",
        "discountValue": 5000,
        "totalQuantity": 500,
        "issuedQuantity": 500,
        "expiredAt": "2026-06-30T23:59:59+09:00",
        "createdAt": "2026-03-01T12:00:00+09:00"
      }
    ]
  }
}
```

---

### GET `/api-admin/v1/coupons/{couponId}`

쿠폰 상세 조회

**Response**: `ApiResponse<DetailResponse>`

```json
{
  "meta": { "result": "SUCCESS", "errorCode": null, "message": null },
  "data": {
    "id": 1,
    "name": "신규가입 10% 할인",
    "discountType": "RATE",
    "discountValue": 10,
    "minOrderAmount": 10000,
    "totalQuantity": 1000,
    "issuedQuantity": 150,
    "expiredAt": "2026-12-31T23:59:59+09:00",
    "createdAt": "2026-03-01T10:00:00+09:00",
    "updatedAt": "2026-03-01T10:00:00+09:00"
  }
}
```

**Admin DetailResponse 필드**

| 필드 | 타입 | 설명 |
|------|------|------|
| id | Long | 쿠폰 ID |
| name | String | 쿠폰명 |
| discountType | String | FIXED / RATE |
| discountValue | Long | 할인값 |
| minOrderAmount | Long? | 최소 주문 금액 (nullable) |
| totalQuantity | Integer | 총 발급 가능 수량 |
| issuedQuantity | Integer | 현재 발급 수량 |
| expiredAt | ZonedDateTime | 유효기간 |
| createdAt | ZonedDateTime | 생성일 |
| updatedAt | ZonedDateTime | 수정일 |

**에러 응답**

| 상황 | HTTP | errorCode | message |
|------|------|-----------|---------|
| 쿠폰 없음/삭제됨 | 404 | COUPON_001 | 쿠폰을 찾을 수 없습니다. |

---

### PUT `/api-admin/v1/coupons/{couponId}`

쿠폰 수정 (name, expiredAt만 수정 가능)

**Request Body**

```json
{
  "name": "수정된 쿠폰명",
  "expiredAt": "2027-06-30T23:59:59"
}
```

| 필드 | 타입 | 필수 | 검증 | 설명 |
|------|------|------|------|------|
| name | String | O | @NotBlank | 쿠폰명 |
| expiredAt | ZonedDateTime | O | @NotNull | 유효기간 |

**Response**: `ApiResponse<Object>` (data = null)

**에러 응답**

| 상황 | HTTP | errorCode | message |
|------|------|-----------|---------|
| 쿠폰 없음/삭제됨 | 404 | COUPON_001 | 쿠폰을 찾을 수 없습니다. |

---

### DELETE `/api-admin/v1/coupons/{couponId}`

쿠폰 삭제 (Soft Delete)

**Response**: `ApiResponse<Object>` (data = null)

**에러 응답**

| 상황 | HTTP | errorCode | message |
|------|------|-----------|---------|
| 쿠폰 없음/삭제됨 | 404 | COUPON_001 | 쿠폰을 찾을 수 없습니다. |

---

### GET `/api-admin/v1/coupons/{couponId}/issues?page=0&size=20`

쿠폰 발급 내역 조회

**Query Parameter**

| 이름 | 타입 | 기본값 | 설명 |
|------|------|--------|------|
| page | int | 0 | 페이지 번호 |
| size | int | 20 | 페이지당 항목 수 |

**Response**: `ApiResponse<IssueListResponse>`

```json
{
  "meta": { "result": "SUCCESS", "errorCode": null, "message": null },
  "data": {
    "page": 0,
    "size": 20,
    "totalElements": 150,
    "totalPages": 8,
    "items": [
      {
        "ownedCouponId": 1,
        "userId": 100,
        "status": "AVAILABLE",
        "usedAt": null,
        "issuedAt": "2026-03-01T10:00:00+09:00"
      },
      {
        "ownedCouponId": 2,
        "userId": 101,
        "status": "USED",
        "usedAt": "2026-03-05T14:30:00+09:00",
        "issuedAt": "2026-03-01T10:05:00+09:00"
      }
    ]
  }
}
```

**IssueListResponse.items 필드**

| 필드 | 타입 | 설명 |
|------|------|------|
| ownedCouponId | Long | 소유 쿠폰 ID |
| userId | Long | 발급받은 유저 ID |
| status | String | AVAILABLE / USED / EXPIRED |
| usedAt | ZonedDateTime? | 사용 일시 (nullable) |
| issuedAt | ZonedDateTime | 발급 일시 (= createdAt) |

**에러 응답**

| 상황 | HTTP | errorCode | message |
|------|------|-----------|---------|
| 쿠폰 없음/삭제됨 | 404 | COUPON_001 | 쿠폰을 찾을 수 없습니다. |

---

## Order API 변경

### POST `/api/v1/orders` (기존 API 수정)

주문 요청 — `couponId` 필드 추가

**Request Body (변경 후)**

```json
{
  "items": [
    { "productId": 1, "quantity": 2, "expectedPrice": 50000 },
    { "productId": 3, "quantity": 1, "expectedPrice": 120000 }
  ],
  "couponId": 42
}
```

| 필드 | 타입 | 필수 | 검증 | 설명 |
|------|------|------|------|------|
| items | List | O | @NotEmpty, @Valid | 주문 항목 (기존) |
| couponId | Long | X | - | 사용할 소유 쿠폰 ID (nullable, 쿠폰 미적용 시 생략) |

> `couponId`는 OwnedCoupon의 ID다. Coupon 템플릿 ID가 아님에 주의.

**Response (변경 후)**: `ApiResponse<OrderSummary>`

```json
{
  "meta": { "result": "SUCCESS", "errorCode": null, "message": null },
  "data": {
    "orderId": 1,
    "totalPrice": 210000,
    "originalTotalPrice": 220000,
    "discountAmount": 10000,
    "status": "ORDERED",
    "createdAt": "2026-03-01T15:00:00+09:00"
  }
}
```

**OrderSummary 추가 필드**

| 필드 | 타입 | 설명 |
|------|------|------|
| originalTotalPrice | int | 쿠폰 적용 전 금액 |
| discountAmount | int | 할인 금액 (쿠폰 미적용 시 0) |

**쿠폰 관련 에러 응답**

| 상황 | HTTP | errorCode | message |
|------|------|-----------|---------|
| 소유 쿠폰 없음 | 404 | COUPON_006 | 소유 쿠폰을 찾을 수 없습니다. |
| 본인 쿠폰 아님 | 403 | COUPON_007 | 본인의 쿠폰만 사용할 수 있습니다. |
| 사용 불가 상태 | 400 | COUPON_008 | 사용 가능한 상태가 아닙니다. |
| 만료된 쿠폰 | 400 | COUPON_003 | 만료된 쿠폰입니다. |
| 최소 주문 금액 미달 | 400 | COUPON_010 | 최소 주문 금액을 충족하지 않습니다. |

---

## DTO 매핑 정리

### Interfaces → Application

| Interface DTO | Application DTO | 변환 |
|---|---|---|
| RegisterRequest | CouponCriteria.Create | `toCriteria()` |
| UpdateRequest | - | 원시 타입 직접 전달 (name, expiredAt) |
| OrderRequest.Create | OrderCriteria.Create | `toCriteria()` — couponId 추가 |

### Application → Interfaces

| Application DTO | Interface DTO | 변환 |
|---|---|---|
| CouponResult.Detail | DetailResponse | `from()` |
| CouponResult.Summary | ListResponse.ListItem | `from()` |
| CouponResult.OwnedCouponDetail | OwnedCouponDetail | `from()` |
| CouponResult.IssuedCouponInfo | IssueListResponse.ListItem | `from()` |
| OrderResult.OrderSummary | OrderResponse.OrderSummary | `from()` — 필드 추가 |

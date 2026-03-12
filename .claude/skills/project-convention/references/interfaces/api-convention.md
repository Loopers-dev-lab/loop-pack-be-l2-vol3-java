# API 설계 컨벤션

## 1. URL 구조

### API Prefix

| 대상 | Prefix | 예시 |
|------|--------|------|
| 대고객 (Guest/User) | `/api/v1` | `/api/v1/products` |
| 어드민 (Admin) | `/api-admin/v1` | `/api-admin/v1/products` |

같은 도메인이라도 고객용과 Admin용은 인증 방식, DTO, 비즈니스 정책이 다르므로 prefix를 분리한다.

### 버전 전략

버전은 **URL 경로**에 포함한다. (쿼리 파라미터, Header 기반 방식 사용하지 않음)

### 리소스 네이밍

- **복수형**, **소문자**, **케밥케이스**
- 단건 접근은 `/{id}`로 구분

```
/api/v1/products                  ← 컬렉션
/api/v1/products/{productId}      ← 단건
/api/v1/order-items               ← 다중 단어 케밥케이스
```

### 경로 변수 네이밍

경로 변수는 **camelCase**, 리소스명 + Id로 명확히 표현한다.

```
/api/v1/products/{productId}      ← 리소스명 + Id
```

---

## 2. HTTP 메서드 규칙

### 메서드별 용도

| Method | 용도 | 멱등성 | 요청 Body |
|--------|------|--------|----------|
| **GET** | 리소스 조회 (목록/단건) | O | 없음 |
| **POST** | 리소스 생성, 비CRUD 행위 | X | 있음 |
| **PUT** | 리소스 전체 수정 | O | 있음 |
| **DELETE** | 리소스 삭제 | O | 없음 |

### PUT으로 통일 (PATCH 사용하지 않음)

클라이언트가 수정 가능한 필드를 전부 보내는 "전체 교체" 방식이다.

```java
// PUT /api/v1/products/{productId}
public record UpdateRequest(
                @NotBlank String name,
                @Positive int price,
                @PositiveOrZero int stock) {}
```

### GET 요청 필터는 쿼리 파라미터로 전달

```
GET /api/v1/products?brandId=1&sort=latest&page=0&size=20
```

---

## 3. HTTP 상태 코드

### 성공 응답 — 모든 성공은 200 OK

| 상황 | 상태 코드 | 응답 Body |
|------|----------|----------|
| 조회 성공 | **200 OK** | `ApiResponse.success(data)` |
| 생성 성공 | **200 OK** | `ApiResponse.success(data)` |
| 수정 성공 | **200 OK** | `ApiResponse.success(data)` 또는 `ApiResponse.success()` |
| 삭제 성공 | **200 OK** | `ApiResponse.success()` |

`ApiResponse` 래퍼가 `meta.result`로 성공/실패를 구분하므로 HTTP 상태 코드 세분화가 불필요하다. 204는 body가 비어야 하므로 `ApiResponse` 포맷과 충돌한다.

```java
@PostMapping
public ApiResponse<ProductDetailResponse> create(...) {
    ProductInfo info = productFacade.create(request.toCommand());
    return ApiResponse.success(ProductDetailResponse.from(info));
}

@DeleteMapping("/{productId}")
public ApiResponse<Object> delete(...) {
    productFacade.delete(productId);
    return ApiResponse.success();
}
```

### 에러 응답

에러 응답의 HTTP 상태 코드는 **ErrorCode.getStatus()가 결정**한다.

| 상황 | 상태 코드 | 결정 주체 |
|------|----------|----------|
| Validation 실패 | **400** | ControllerAdvice 고정 |
| 비즈니스 에러 | **ErrorCode.getStatus()** | 도메인 ErrorCode enum |
| 서버 에러 | **500** | ControllerAdvice 최후 방어 |

```
OrderErrorCode.STOCK_INSUFFICIENT  → HttpStatus.BAD_REQUEST  → 400
ErrorType.NOT_FOUND                → HttpStatus.NOT_FOUND    → 404
BrandErrorCode.DUPLICATE_NAME      → HttpStatus.CONFLICT     → 409
```

### 에러 상태 코드 매핑

| 상태 코드 | 의미 | 사용 시점 |
|----------|------|----------|
| 400 | Bad Request | Validation 실패, 잘못된 요청 |
| 401 | Unauthorized | 인증 실패 (로그인 필요) |
| 403 | Forbidden | 권한 없음 (본인 리소스 아님) |
| 404 | Not Found | 리소스 없음, soft delete된 리소스 |
| 409 | Conflict | 중복 (브랜드명 중복 등) |
| 500 | Internal Server Error | 예상치 못한 서버 에러 |

---

## 4. 엔드포인트 설계 패턴

### 패턴 매트릭스

| 패턴 | URL 형식 | 사용 시점 |
|------|---------|----------|
| 표준 CRUD | `/{resources}`, `/{resources}/{id}` | 대부분의 리소스 |
| 중첩 리소스 | `/{parent}/{parentId}/{child}` | 하위 리소스가 상위 없이 무의미할 때 |
| 소유자 기준 조회 | `/{owner}/{ownerId}/{resources}` | "내 리소스 목록" 조회 |
| 비CRUD 행위 | `/{resources}/{id}/{verb}` | CRUD로 표현 불가한 행위 |

**중첩 리소스 깊이는 2단계까지만** (`/a/{aId}/b` 허용, `/a/{aId}/b/{bId}/c` 금지)

### 통합 예시

```
// 브랜드 Admin CRUD (표준 CRUD)
GET    /api-admin/v1/brands
GET    /api-admin/v1/brands/{brandId}
POST   /api-admin/v1/brands
PUT    /api-admin/v1/brands/{brandId}
DELETE /api-admin/v1/brands/{brandId}

// 상품 좋아요 (중첩 리소스)
POST   /api/v1/products/{productId}/likes
DELETE /api/v1/products/{productId}/likes

// 내가 좋아요한 상품 목록 (소유자 기준 조회)
GET    /api/v1/users/{userId}/likes

// 비CRUD 행위 — 리소스 방식 우선, 정말 안 될 때만 동사 사용
POST   /api/v1/orders                         ← 주문 "생성"으로 표현
POST   /api/v1/products/{productId}/restock    ← 리소스로 표현 불가 시
```

---

## 5. 쿼리 파라미터 규칙

### 페이지네이션 — Offset 기반

| 파라미터 | 설명 | 기본값 |
|----------|------|--------|
| `page` | 페이지 번호 (0부터 시작) | `0` |
| `size` | 페이지당 항목 수 | `20` |

Cursor 기반 페이지네이션은 무한 스크롤 등 필요한 API에 한해 별도 도입한다.

### 필터링/정렬/날짜

- **필터링**: 필드명을 그대로 쿼리 파라미터명으로 사용
- **정렬**: `sort` 파라미터, 값은 **snake_case** (`sort=latest`, `sort=price_asc`, `sort=likes_desc`)
- **날짜 범위**: `startAt`, `endAt` 사용

### 파라미터 네이밍 — camelCase

```
GET /api/v1/products?brandId=1&sort=latest&page=0&size=20&startAt=2025-01-01&endAt=2025-01-31
```

---

## 6. Controller 분리 규칙

### 고객 / Admin Controller 분리

| 대상 | 네이밍 | RequestMapping |
|------|--------|----------------|
| 고객 | `{Domain}V1Controller` | `@RequestMapping("/api/v1/{resources}")` |
| Admin | `Admin{Domain}V1Controller` | `@RequestMapping("/api-admin/v1/{resources}")` |

Controller 이름에 **V1**을 포함한다. V2 API 추가 시 `{Domain}V2Controller`로 확장된다.

```
interfaces/
  {domain}/
    ProductV1Controller.java          /api/v1/products (고객)
    AdminProductV1Controller.java     /api-admin/v1/products (Admin)
    dto/
      ProductDto.java                 고객용 Request/Response
      AdminProductDto.java            Admin용 Request/Response
```

```java
@RestController
@RequestMapping("/api/v1/products")
public class ProductV1Controller {
    private final ProductFacade productFacade;

    @GetMapping("/{productId}")
    public ApiResponse<ProductDto.DetailResponse> getProduct(...) { ... }
}

@RestController
@RequestMapping("/api-admin/v1/products")
public class AdminProductV1Controller {
    private final ProductFacade productFacade;

    @GetMapping("/{productId}")
    public ApiResponse<AdminProductDto.DetailResponse> getProduct(...) { ... }
}
```

### Facade 공유 규칙

고객 Controller와 Admin Controller는 **같은 Facade를 공유할 수 있다**. Admin 전용 메서드가 Facade의 절반 이상을 차지하거나 복잡한 유스케이스가 생기면 별도 `AdminProductFacade`로 분리한다.

```
// 초기: Facade 공유
ProductV1Controller       -> ProductFacade
AdminProductV1Controller  -> ProductFacade

// Admin 로직이 커지면: Facade 분리
ProductV1Controller       -> ProductFacade
AdminProductV1Controller  -> AdminProductFacade
```

### 도메인 간 API가 겹칠 때

Controller 배치는 **핵심 리소스(행위의 주체)**가 기준이다.

```
// 좋아요 등록/취소/조회 모두 Like 도메인 행위 -> Like의 interfaces에 배치
interfaces/
  like/
    LikeController.java     /api/v1/users/{userId}/likes
                             /api/v1/products/{productId}/likes
```

---

## 7. 요청/응답 본문 규칙

### 생성 요청 -> 생성된 리소스 반환

클라이언트가 별도 조회 없이 바로 사용할 수 있도록 생성된 리소스 정보를 응답에 포함한다.

```json
// POST /api-admin/v1/brands -> 200 OK
{
    "meta": { "result": "SUCCESS", "errorCode": null, "message": null },
    "data": { "id": 1, "name": "ACNE STUDIOS" }
}
```

### 수정 요청 -> 수정된 리소스 반환 (선택)

PUT 수정 후 변경된 리소스를 반환한다. 반환할 필요가 없으면 `ApiResponse.success()`만 반환해도 된다.

### 삭제 요청 -> 빈 data

```json
// DELETE /api-admin/v1/brands/{brandId} -> 200 OK
{
    "meta": { "result": "SUCCESS", "errorCode": null, "message": null },
    "data": null
}
```

### 목록 응답 구조

```json
{
    "meta": { "result": "SUCCESS", "errorCode": null, "message": null },
    "data": {
        "content": [ ... ],
        "page": 0,
        "size": 20,
        "totalElements": 58,
        "totalPages": 3
    }
}
```

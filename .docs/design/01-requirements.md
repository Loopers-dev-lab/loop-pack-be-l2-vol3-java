# 프로젝트: 감성 이커머스 (Emotional E-commerce)
## 기능 정의 및 요구사항 명세서 (Requirement Specification)

- **버전**: v1.0
- **작성일**: 2026-02-12
- **문서 개요**: 본 문서는 유저 시나리오를 기반으로 서비스의 핵심 기능, API 인터페이스, 데이터 제약사항 및 예외 처리 기준을 정의한다.

---

## 1. 개요 및 시나리오 (Overview)

### 1.1 서비스 목표
내가 좋아하는 브랜드의 상품을 탐색하고 좋아요를 누르며, 쿠폰을 통해 합리적으로 구매하는 감성 커머스 플랫폼. 유저의 행동 데이터는 추후 랭킹과 추천 시스템의 기반이 된다.

### 1.2 유저 시나리오 (User Journey)
1.  **진입**: 유저는 회원가입을 하고 로그인을 통해 본인을 식별한다.
2.  **탐색**: 브랜드와 상품을 둘러보고, 마음에 드는 상품에 '좋아요'를 누른다.
3.  **혜택**: 주문 전 사용 가능한 쿠폰을 발급받는다.
4.  **구매**: 장바구니 혹은 바로 구매를 통해 상품을 주문하고 결제한다.
5.  **관리**: 내 주문 내역을 확인하고, 배송 상태를 조회하거나 영수증을 발급받는다.

---

## 2. 공통 아키텍처 및 규칙 (General Rules)

### 2.1 API Endpoint 규칙
* **User (대고객)**: `/api/v1`
* **Admin (관리자)**: `/api-admin/v1`

### 2.2 인증 및 식별 (Authentication)
표준 인증 방식 대신, 아래 커스텀 헤더를 통해 요청자를 식별한다.

| 구분 | Header Key | 설명 | 비고 |
| :--- | :--- | :--- | :--- |
| **User** | `X-Loopers-LoginId` | 로그인 ID | |
| **User** | `X-Loopers-LoginPw` | 비밀번호 | 평문 전송 (Test Scope) |
| **Admin** | `X-Loopers-Ldap` | LDAP 식별자 | 값: `loopers.admin` |

### 2.3 보안 및 접근 제어
* 유저는 **타 유저의 정보**에 절대 접근할 수 없다.
* 관리자 API는 일반 유저가 호출할 수 없다.

---

## 3. 상세 기능 명세 (Detailed Specifications)

### 3.1 사용자 (User)

| Role | Method | Endpoint | 기능 | 상세 로직 및 제약사항 |
| :--- | :---: | :--- | :--- | :--- |
| Guest | `POST` | `/api/v1/users` | **회원가입** | ID 중복 체크 필수 |
| User | `GET` | `/api/v1/users/me` | **내 정보 조회** | 이름 마스킹 처리 |
| User | `PUT` | `/api/v1/users/password` | **비밀번호 변경** | 기존 비밀번호 확인 로직 포함 |

####  상세 요구사항
* **회원가입 입력값**: ID, PW, 이름, 생년월일, 이메일
* **ID 규칙**: 영문 소문자, 숫자만 허용 (4~10자).
* **비밀번호 규칙**:
  * 8~16자, 영문 대소문자/숫자/특수문자 허용.
  * 생년월일은 비밀번호에 포함될 수 없음.
* **정보 조회 마스킹**: 이름의 마지막 글자를 `*`로 처리 (예: `홍길동` -> `홍길*`).
* **비밀번호 변경**: `{기존 PW, 새 PW}` 입력. 새 PW는 기존 PW와 달라야 함.

---

### 3.2 상품 & 브랜드 (Product & Brand)

| Role | Method | Endpoint | 기능 | 상세 로직 및 제약사항 |
| :--- | :---: | :--- | :--- | :--- |
| Any | `GET` | `/api/v1/brands/{brandId}` | **브랜드 조회** | 브랜드 정보 반환 |
| Any | `GET` | `/api/v1/products` | **상품 목록** | 필터, 정렬, 페이징 |
| Any | `GET` | `/api/v1/products/{productId}` | **상품 상세** | |

#### 상세 요구사항
* **목록 조회 쿼리 파라미터**:
  * `brandId`: 특정 브랜드 필터링
  * `sort`: `latest`(기본), `price_asc`(가격낮은순), `likes_desc`(좋아요순)
  * `page`: 0부터 시작 (Default 0), `size`: 20 (Default 20)

---

### 3.3 좋아요 (Like)

| Role | Method | Endpoint | 기능 | 상세 로직 및 제약사항 |
| :--- | :---: | :--- | :--- | :--- |
| User | `POST` | `/api/v1/products/{id}/likes` | **좋아요 등록** | Idempotency 보장 |
| User | `DELETE` | `/api/v1/products/{id}/likes` | **좋아요 취소** | |
| User | `GET` | `/api/v1/users/{userId}/likes` | **좋아요 목록** | 필터링 지원 |

#### 상세 요구사항
* **제약**: 유저당 1개의 상품에 1번만 좋아요 가능.
* **목록 필터**: `sale_yn` (세일중), `status` (판매중/품절제외).
* **목록 정렬**: 날짜순, 가격순, 할인율순, 브랜드명순.
* **표시 정보**: 품절 여부(Dimmed), 세일 뱃지 등 UI 상태값 포함.

---

### 3.4 주문 (Order)

| Role | Method | Endpoint | 기능 | 상세 로직 및 제약사항 |
| :--- | :---: | :--- | :--- | :--- |
| User | `POST` | `/api/v1/orders` | **주문 요청** | 트랜잭션 처리 필수 |
| User | `GET` | `/api/v1/orders` | **내 주문 목록** | `startAt`, `endAt` 기간 필터 |
| User | `GET` | `/api/v1/orders/{id}` | **주문 상세** | 영수증 데이터 포함 |

#### 상세 요구사항
1.  **주문 요청**:
  * **프로세스**: 재고 확인 -> 재고 차감 -> 쿠폰 적용 -> 결제 금액 검증 -> 주문 생성.
  * **스냅샷(Snapshot)**: 주문 시점의 상품명, 가격을 별도 저장 (상품 정보 변경 영향 없음).
  * **입력값**: 배송지(이름/주소/요청사항), 결제수단, 도착희망일.
2.  **주문 상태 및 액션**:
  * `결제완료/상품준비중`: 주문 취소 가능, 배송지 변경 가능.
  * `배송중/배송완료`: 주문 취소 불가(반품 절차), 배송지 변경 불가.
3.  **영수증 (Receipt)**:
  * **카드영수증**: 상점정보, 결제일시, 금액.
  * **거래명세서**: 공급자/공급받는자(유저 입력 가능) 정보, 품목, 세액, 비고 포함.

---

### 3.5 관리자 기능 (Admin)

| Role | Method | Endpoint | 기능 | 상세 로직 및 제약사항 |
| :--- | :---: | :--- | :--- | :--- |
| Admin | `GET` | `/api-admin/v1/brands` | 브랜드 목록 | |
| Admin | `GET` | `/api-admin/v1/brands/{brandId}` | 브랜드 상세 조회 | |
| Admin | `POST` | `/api-admin/v1/brands` | 브랜드 등록 | |
| Admin | `PUT` | `/api-admin/v1/brands/{id}` | 브랜드 수정 | |
| Admin | `DELETE`| `/api-admin/v1/brands/{id}` | **브랜드 삭제** | **[Cascade]** 하위 상품 일괄 삭제 |
| Admin | `GET` | `/api-admin/v1/products` | **상품 목록 조회** | 페이징, `brandId` 필터 |
| Admin | `GET` | `/api-admin/v1/products/{productId}` | **상품 상세 조회** | |
| Admin | `POST` | `/api-admin/v1/products` | **상품 등록** | 등록된 브랜드 ID만 허용 |
| Admin | `PUT` | `/api-admin/v1/products/{id}`| **상품 수정** | **[Immutable]** 브랜드 변경 불가 |
| Admin | `DELETE`| `/api-admin/v1/products/{id}`| 상품 삭제 | Soft Delete 권장 |
| Admin | `GET` | `/api-admin/v1/orders` | 주문 목록 | 전체 유저 주문 조회 |
| Admin | `GET` | `/api-admin/v1/orders/{orderId}` | 주문 상세 조회 | |

---

## 4. 예외 처리 및 에러 표준

### 4.1 에러 응답 포맷 (JSON)
모든 API는 실패 시 아래 포맷을 준수한다.
```json
{
  "code": "BAD_REQUEST",
  "message": "사용자에게 노출될 상세 에러 메시지"
}
```
### 4.2 HTTP 상태 코드
- 200 OK: 요청 성공.

- 400 Bad Request: 필수 파라미터 누락, 유효성 검사 실패 (PW 규칙 위반 등), 인증 실패 (ID/PW 불일치), 필수 헤더 누락.

- 404 Not Found: 존재하지 않는 리소스 (상품, 브랜드, 주문 등).

- 409 Conflict: 비즈니스 로직 충돌 (이미 좋아요 누름, 재고 부족, 이미 발급된 쿠폰).

- 500 Internal Server Error: 서버 내부 오류.

### 4.3 주요 에러 코드 정의

| 코드 | HTTP 상태 | 설명 |
| :--- | :---: | :--- |
| `BAD_REQUEST` | 400 | 유효성 검사 실패, 인증 실패, ID 중복 등 |
| `MISSING_HEADER` | 400 | 필수 헤더 누락 (`X-Loopers-LoginId` 등) |
| `Not Found` | 404 | 존재하지 않는 리소스 |
| `Conflict` | 409 | 비즈니스 로직 충돌 (리소스 중복 등) |
| `INTERNAL_ERROR` | 500 | 서버 내부 오류 |
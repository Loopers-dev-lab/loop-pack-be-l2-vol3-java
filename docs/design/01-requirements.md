# PawShop 요구사항 명세서

> 생성일: 2026-02-11
> 핵심 가치: 브랜드별 애견용품을 탐색-좋아요-주문하는 핵심 쇼핑 플로우를 제공하고, 멤버 행동 데이터를 축적하는 이커머스 백엔드 시스템 구축

## 1. 문제 정의

- **사용자 관점**: 여러 브랜드의 애견용품을 한 곳에서 비교/선택하고, 관심 상품을 관리(좋아요)하며 편리하게 주문하고 싶다.
- **비즈니스 관점**: 브랜드별 상품 큐레이션과 멤버 행동 데이터(좋아요, 주문)를 축적하여 추후 추천/랭킹 기능의 기반을 마련한다.
- **시스템 관점**: 주문 시 상품 스냅샷과 재고 차감의 데이터 일관성을 보장하면서, 쿠폰/결제/추천 등의 확장에 대비한 백엔드 구조가 아직 없다.

## 2. 개념 모델

### 액터

- **고객(Customer)**: 비로그인 방문자 및 로그인 회원
- **어드민(Admin)**: 사내 관리자 (LDAP 인증)

### 핵심 도메인

- **Member** - 회원가입, 인증, 프로필 관리
- **Brand** - 브랜드 정보 관리 (이름 불변)
- **Category** - 상품 카테고리 관리 (Seed 데이터 기반 조회 전용 테이블)
- **Product** - 상품 정보 관리 (재고, 가격, 카테고리 참조)
- **Like** - 상품 좋아요 (멤버 행동 데이터 축적)
- **Order** - 주문 처리 (스냅샷, 재고 차감, 상태 관리)

### 보조/외부 시스템

- 없음 (모놀리식, 외부 결제 없음)

## 3. Member Stories

### 도메인 1: Member

**기존 구현 완료** (loginId, password, name, email, birthDate). phone 필드 추가 예정.

#### US-01: 회원가입

**As a** 신규 방문자
**I want to** loginId, password, name, email, birthDate, phone을 입력하여 회원가입
**So that** PawShop에서 좋아요/주문 등 회원 전용 기능을 이용할 수 있다

**수용 기준 (AC):**
- [ ] AC1: Given 유효한 입력값, When POST /api/v1/members, Then 201 Created + 멤버 생성
- [ ] AC2: Given 이미 존재하는 loginId, When POST /api/v1/members, Then 409 Conflict
- [ ] AC3: Given 필수값 누락 또는 형식 오류(email, phone), When POST /api/v1/members, Then 400 Bad Request
- [ ] AC4: Given loginId, When GET 중복검사 API, Then 사용 가능/불가능 응답

**비즈니스 규칙:**
- loginId: 4~20자, 영문소문자+숫자, 예약어 제외
- password: 8~16자, 대/소문자+숫자+특수문자 필수, 생년월일 미포함, BCrypt 암호화
- name: 한글 최대 4자 또는 영문 최대 50자 (혼용 불가)
- email: RFC 5321 준수
- birthDate: yyyyMMdd 형식, 미래 날짜 불가
- phone: 010-XXXX-XXXX 형식 (하이픈 포함 13자)

#### US-02: 내 정보 조회

**As a** 로그인한 회원
**I want to** 내 정보(loginId, name, email, birthDate, phone)를 조회
**So that** 내 계정 정보를 확인할 수 있다

**수용 기준 (AC):**
- [ ] AC1: Given 유효한 인증 헤더, When GET /api/v1/members/me, Then 200 + 내 정보 반환 (password 제외, 이름 마스킹)
- [ ] AC2: Given 잘못된 인증 헤더, When GET /api/v1/members/me, Then 401

**비즈니스 규칙:**
- 이름은 마지막 글자를 *로 마스킹 (예: "홍길동" → "홍길*")

#### US-03: 비밀번호 변경

**As a** 로그인한 회원
**I want to** 현재 비밀번호를 확인 후 새 비밀번호로 변경
**So that** 계정 보안을 유지할 수 있다

**수용 기준 (AC):**
- [ ] AC1: Given 유효한 인증 + 현재PW 일치 + 유효한 새PW, When PATCH /api/v1/members/me/password, Then 200
- [ ] AC2: Given 현재 비밀번호 불일치, When PATCH, Then 400
- [ ] AC3: Given 새 비밀번호가 현재와 동일, When PATCH, Then 400
- [ ] AC4: Given 새 비밀번호가 규칙 미충족, When PATCH, Then 400

---

### 도메인 2: Brand

#### US-04: 브랜드 등록 (어드민)

**As a** 어드민
**I want to** 새 브랜드(name, description, imageUrl)를 등록
**So that** 해당 브랜드의 상품을 등록할 수 있다

**수용 기준 (AC):**
- [ ] AC1: Given 유효한 LDAP 헤더 + 유효한 입력값, When POST /api-admin/v1/brands, Then 201 Created
- [ ] AC2: Given LDAP 헤더 없음, When POST, Then 401
- [ ] AC3: Given 필수값 누락, When POST, Then 400

**비즈니스 규칙:**
- name: 등록 후 수정 불가 (불변)
- 필수 속성: name, description, imageUrl

#### US-05: 브랜드 조회

**As a** 고객/어드민
**I want to** 브랜드 정보를 조회
**So that** 브랜드 상세 정보를 확인할 수 있다

**수용 기준 (AC):**
- [ ] AC1: Given brandId, When GET /api/v1/brands/{brandId}, Then 200 + 브랜드 정보
- [ ] AC2: Given 존재하지 않는 brandId, When GET, Then 404
- [ ] AC3: Given LDAP 인증, When GET /api-admin/v1/brands?page=0&size=20, Then 200 + 페이지네이션된 브랜드 목록
- [ ] AC4: Given LDAP 인증 + brandId, When GET /api-admin/v1/brands/{brandId}, Then 200 + 브랜드 상세

#### US-06: 브랜드 수정 (어드민)

**As a** 어드민
**I want to** 브랜드의 description, imageUrl을 수정
**So that** 브랜드 정보를 최신으로 유지할 수 있다

**수용 기준 (AC):**
- [ ] AC1: Given 유효한 LDAP + 유효한 수정값, When PUT /api-admin/v1/brands/{brandId}, Then 200
- [ ] AC2: Given name 수정 시도, When PUT, Then 400 (name은 수정 불가)

**비즈니스 규칙:**
- name은 수정 불가. description과 imageUrl만 수정 가능

#### US-07: 브랜드 삭제 (어드민)

**As a** 어드민
**I want to** 더 이상 필요 없는 브랜드를 삭제
**So that** 시스템을 깔끔하게 유지할 수 있다

**수용 기준 (AC):**
- [ ] AC1: Given 브랜드가 존재, When DELETE /api-admin/v1/brands/{brandId}, Then 브랜드와 해당 브랜드의 상품이 함께 soft-delete 처리되고 200
- [ ] AC2: Given 삭제된 브랜드, When DELETE, Then 404

**비즈니스 규칙:**
- 브랜드 삭제는 해당 브랜드를 soft-delete하고, 관련 상품을 함께 soft-delete한다.

---

### 도메인 3: Product

#### US-08: 상품 등록 (어드민)

**As a** 어드민
**I want to** 새 상품(name, price, stock, description, category, brandId)을 등록
**So that** 고객이 상품을 탐색/구매할 수 있다

**수용 기준 (AC):**
- [ ] AC1: Given 유효한 LDAP + 활성 브랜드 + 유효한 입력값, When POST /api-admin/v1/products, Then 201
- [ ] AC2: Given 존재하지 않거나 삭제된 브랜드, When POST, Then 400
- [ ] AC3: Given price가 0 이하, When POST, Then 400
- [ ] AC4: Given stock이 음수, When POST, Then 400

**비즈니스 규칙:**
- 상품은 반드시 활성 상태의 등록된 브랜드에 속해야 함
- 필수 속성: name, price, stock, description, categoryId
- category는 별도 테이블 (Seed 데이터 기반, 조회 전용)

#### US-09: 상품 조회

**As a** 고객
**I want to** 상품 목록을 필터/정렬/페이지네이션으로 조회하고, 상세 정보를 확인
**So that** 원하는 애견용품을 찾을 수 있다

**수용 기준 (AC):**
- [ ] AC1: Given 파라미터 없음, When GET /api/v1/products, Then 200 + 기본 정렬(latest), 기본 페이지(0), 기본 사이즈(20)
- [ ] AC2: Given brandId 필터, When GET, Then 해당 브랜드 상품만 반환 (존재하지 않는 brandId면 빈 목록)
- [ ] AC3: Given sort=price_asc, When GET, Then 가격 오름차순 정렬
- [ ] AC4: Given sort=likes_desc, When GET, Then 좋아요 많은 순 정렬
- [ ] AC5: Given productId, When GET /api/v1/products/{productId}, Then 200 + 상품 상세 (좋아요 수 포함)
- [ ] AC6: Given 삭제된 상품, When 고객 API 조회, Then 목록에서 제외
- [ ] AC7: Given 삭제된 상품, When 어드민 API 조회, Then deletedAt 포함하여 노출

**쿼리 파라미터:**

| 파라미터 | 기본값 | 설명 |
|---------|--------|------|
| brandId | - | 특정 브랜드 필터링 |
| sort | latest | latest / price_asc / likes_desc |
| page | 0 | 페이지 번호 |
| size | 20 | 페이지당 상품 수 |

#### US-10: 상품 수정 (어드민)

**As a** 어드민
**I want to** 상품의 name, price, stock, description, category를 수정
**So that** 상품 정보를 최신으로 유지할 수 있다

**수용 기준 (AC):**
- [ ] AC1: Given 유효한 수정값, When PUT /api-admin/v1/products/{productId}, Then 200
- [ ] AC2: Given brandId 수정 시도, When PUT, Then 400 (브랜드 수정 불가)

**비즈니스 규칙:**
- brandId는 수정 불가

#### US-11: 상품 삭제 (어드민)

**As a** 어드민
**I want to** 상품을 삭제
**So that** 더 이상 판매하지 않는 상품을 비활성화할 수 있다

**수용 기준 (AC):**
- [ ] AC1: Given 주문이 없는 상품, When DELETE /api-admin/v1/products/{productId}, Then 200 (Soft Delete)
- [ ] AC2: Given 주문이 있는 상품, When DELETE, Then 409 Conflict

**비즈니스 규칙:**
- 해당 상품에 주문(OrderItem)이 존재하면 삭제 불가

---

### 도메인 4: Like

#### US-12: 상품 좋아요 등록

**As a** 로그인한 회원
**I want to** 마음에 드는 상품에 좋아요를 누르기
**So that** 관심 상품을 기록하고 나중에 다시 찾을 수 있다

**수용 기준 (AC):**
- [ ] AC1: Given 인증된 멤버 + 활성 상품, When POST /api/v1/products/{productId}/likes, Then 201 + 상품 likeCount 증가
- [ ] AC2: Given 이미 좋아요한 상품, When POST, Then 409 Conflict
- [ ] AC3: Given 삭제된 상품, When POST, Then 400 Bad Request
- [ ] AC4: Given 존재하지 않는 productId, When POST, Then 404

**비즈니스 규칙:**
- DB에 member_id + product_id Unique 제약조건
- Product 테이블의 likeCount 필드 업데이트

#### US-13: 상품 좋아요 취소

**As a** 로그인한 회원
**I want to** 좋아요를 취소
**So that** 관심 목록을 정리할 수 있다

**수용 기준 (AC):**
- [ ] AC1: Given 좋아요 상태인 상품, When DELETE /api/v1/products/{productId}/likes, Then 200 + 상품 likeCount 감소
- [ ] AC2: Given 좋아요하지 않은 상품, When DELETE, Then 404

#### US-14: 내 좋아요 목록 조회

**As a** 로그인한 회원
**I want to** 내가 좋아요한 상품 목록을 조회
**So that** 관심 상품을 한눈에 볼 수 있다

**수용 기준 (AC):**
- [ ] AC1: Given 인증된 멤버, When GET /api/v1/me/likes?page=0&size=20, Then 200 + 좋아요한 활성 상품 목록 (페이지네이션)
- [ ] AC2: Given 좋아요한 상품 중 삭제된 상품, Then 목록에서 제외

**쿼리 파라미터:**

| 파라미터 | 기본값 | 설명 |
|---------|--------|------|
| page | 0 | 페이지 번호 |
| size | 20 | 페이지당 상품 수 |

---

### 도메인 5: Order

#### US-15: 주문 요청

**As a** 로그인한 회원
**I want to** 여러 상품을 한 번에 주문
**So that** 원하는 애견용품을 구매할 수 있다

**수용 기준 (AC):**
- [ ] AC1: Given 인증 + 모든 상품 활성 + 재고 충분, When POST /api/v1/orders, Then 201 + 주문 생성 + 재고 차감 + 스냅샷 저장
- [ ] AC2: Given 하나라도 재고 부족, When POST, Then 400 + 전체 실패 (재고 차감 없음)
- [ ] AC3: Given 삭제된 상품 포함, When POST, Then 400
- [ ] AC4: Given 존재하지 않는 productId 포함, When POST, Then 404
- [ ] AC5: Given items가 빈 배열, When POST, Then 400

**요청 예시:**
```json
{
  "items": [
    { "productId": 1, "quantity": 2 },
    { "productId": 3, "quantity": 1 }
  ]
}
```

**비즈니스 규칙:**
- 주문 상태: ORDERED로 생성
- 스냅샷: 주문 시점의 주문 번호, productId(논리 참조), 상품명, 가격, 브랜드명 저장
- 하나의 트랜잭션에서 재고 확인 → 차감 → 주문 생성 처리
- 결제 없음 (주문 완료 = 결제 완료)

#### US-16: 멤버 주문 취소

**As a** 로그인한 회원
**I want to** 내 주문을 취소
**So that** 잘못 주문했거나 변심한 경우 취소할 수 있다

**수용 기준 (AC):**
- [ ] AC1: Given 인증 + 본인의 ORDERED 상태 주문, When PATCH /api/v1/orders/{orderId}/cancel, Then 200 + 상태 CANCELLED + 재고 복원
- [ ] AC2: Given 타인의 주문, When PATCH, Then 403
- [ ] AC3: Given 이미 CANCELLED 상태, When PATCH, Then 409 Conflict
- [ ] AC4: Given 존재하지 않는 orderId, When PATCH, Then 404

#### US-17: 멤버 주문 목록 조회

**As a** 로그인한 회원
**I want to** 날짜 범위로 내 주문 목록을 조회
**So that** 주문 이력을 확인할 수 있다

**수용 기준 (AC):**
- [ ] AC1: Given 인증 + 유효한 startAt/endAt (날짜, KST 기준), When GET /api/v1/orders?startAt=...&endAt=...&page=0&size=20, Then 200 + 본인 주문 목록 (페이지네이션)
- [ ] AC2: Given startAt/endAt 누락, When GET, Then 400
- [ ] AC3: Given 해당 기간에 주문 없음, When GET, Then 200 + 빈 목록

**쿼리 파라미터:**

| 파라미터 | 기본값 | 설명 |
|---------|--------|------|
| startAt | (필수) | 조회 시작일 (KST) |
| endAt | (필수) | 조회 종료일 (KST) |
| page | 0 | 페이지 번호 |
| size | 20 | 페이지당 주문 수 |

#### US-18: 단일 주문 상세 조회

**As a** 로그인한 회원
**I want to** 주문 상세 정보(스냅샷 포함)를 조회
**So that** 주문한 상품의 당시 정보를 확인할 수 있다

**수용 기준 (AC):**
- [ ] AC1: Given 본인 주문, When GET /api/v1/orders/{orderId}, Then 200 + 주문 상세 (스냅샷 포함)
- [ ] AC2: Given 타인 주문, When GET, Then 403
- [ ] AC3: Given 존재하지 않는 orderId, When GET, Then 404

#### US-19: 어드민 주문 목록/상세 조회

**As a** 어드민
**I want to** 전체 주문 목록과 상세 정보를 조회
**So that** 주문 현황을 파악하고 관리할 수 있다

**수용 기준 (AC):**
- [ ] AC1: Given LDAP 인증, When GET /api-admin/v1/orders?page=0&size=20, Then 200 + 페이지네이션된 전체 주문 목록
- [ ] AC2: Given LDAP 인증 + orderId, When GET /api-admin/v1/orders/{orderId}, Then 200 + 주문 상세

#### US-20: 어드민 주문 취소

**As a** 어드민
**I want to** 주문을 취소하고 재고를 복원
**So that** 잘못된 주문이나 고객 요청을 처리할 수 있다

**수용 기준 (AC):**
- [ ] AC1: Given LDAP 인증 + ORDERED 상태 주문, When PATCH /api-admin/v1/orders/{orderId}/cancel, Then 200 + 상태 CANCELLED + 재고 복원
- [ ] AC2: Given 이미 CANCELLED 상태, When PATCH, Then 409 Conflict
- [ ] AC3: Given 존재하지 않는 orderId, When PATCH, Then 404

## 4. 비기능 요구사항 (전체)

- **인증**: 고객 → X-Loopers-LoginId + X-Loopers-LoginPw 헤더, 어드민 → X-Loopers-Ldap: loopers.admin 헤더
- **응답 형식**: 표준 ApiResponse<T> (meta: {result, errorCode, message}, data: T)
- **Soft Delete**: BaseEntity의 deletedAt 필드 기반
- **비밀번호**: BCrypt 암호화 저장
- **아키텍처**: interfaces → application(Facade) → domain(Service) ← infrastructure(Repository)
- **테스트**: 단위 + 통합 + E2E, MySQL TestContainer 사용

### 트랜잭션/락/정합성 요구

- 주문 유스케이스에서 재고, 쿠폰, 주문 생성은 하나의 비즈니스 트랜잭션으로 다뤄져야 하며 부분 성공이 없어야 한다.
- 동시성 제어는 도메인 특성에 따라 낙관적 락/비관적 락 중 선택하며, 선택 근거를 문서화한다.
- 쿠폰 단일 사용 보장은 DB 제약조건(유니크/상태 전이 조건)과 애플리케이션 검증을 함께 사용한다.
- 트랜잭션 실패 시 쿠폰 상태/재고/주문 데이터는 원자적으로 롤백되어야 한다.

### 동시성 테스트 요구

- 동일 상품 좋아요/취소 동시 요청에서도 likeCount 정합성을 보장한다.
- 동일 쿠폰으로 동시 주문 시 쿠폰은 한 번만 `USED` 전이되어야 한다.
- 동일 상품 동시 주문 시 재고는 음수가 되지 않고 성공 주문 수만큼만 차감되어야 한다.

## 5. Scope-out (명시적 제외)

- **결제 시스템** - 주문 완료 = 결제 완료로 간주. 추후 추가 개발
- **포인트 충전** - 프로젝트 명세에서 명시적으로 제외
- **추천/랭킹 알고리즘** - 데이터 축적이 먼저. 좋아요/주문 데이터 구조만 확보
- **리뷰/별점** - 명세에 없음
- **배송/물류** - 명세에 없음
- **JWT/세션 인증** - 헤더 기반 식별로 확정
- **Rate Limiting** - 인프라 레벨, MVP 범위 밖

## 6. Coupon 요구사항

### 도메인 6: Coupon

#### US-21: 쿠폰 템플릿 등록 (어드민)

**As a** 어드민  
**I want to** 정액(FIXED) 또는 정률(RATE) 쿠폰 템플릿을 등록  
**So that** 회원이 주문 시 할인 혜택을 적용할 수 있다

**수용 기준 (AC):**
- [ ] AC1: Given LDAP 인증 + 유효한 입력값, When POST /api-admin/v1/coupons, Then 201
- [ ] AC2: Given 잘못된 type/value/minOrderAmount, When POST, Then 400
- [ ] AC3: Given 만료일이 현재 이전, When POST, Then 400

#### US-22: 쿠폰 발급 (회원)

**As a** 로그인한 회원  
**I want to** 쿠폰을 발급받고 보유 목록에서 확인  
**So that** 주문 시 할인 혜택을 받을 수 있다

**수용 기준 (AC):**
- [ ] AC1: Given 인증된 회원 + 유효한 couponId, When POST /api/v1/coupons/{couponId}/issue, Then 201
- [ ] AC2: Given 존재하지 않는 couponId, When POST, Then 404
- [ ] AC3: Given 만료된 쿠폰 템플릿, When POST, Then 400

#### US-23: 내 쿠폰 목록 조회

**As a** 로그인한 회원  
**I want to** 내 쿠폰 목록과 상태를 조회  
**So that** 사용 가능 여부를 확인할 수 있다

**수용 기준 (AC):**
- [ ] AC1: Given 인증된 회원, When GET /api/v1/users/me/coupons, Then 200 + 상태(AVAILABLE/USED/EXPIRED) 포함 목록

#### US-24: 주문 시 쿠폰 적용

**As a** 로그인한 회원  
**I want to** 주문에 쿠폰을 1장 적용  
**So that** 최종 결제 금액을 할인받는다

**수용 기준 (AC):**
- [ ] AC1: Given 유효한 소유 쿠폰 + 재고 충분, When POST /api/v1/orders, Then 201 + 쿠폰 USED 전이
- [ ] AC2: Given 이미 사용된 쿠폰/만료 쿠폰/타인 쿠폰, When POST, Then 400 또는 403으로 주문 실패
- [ ] AC3: Given 동시 주문 경쟁, Then 동일 쿠폰은 단 1건만 성공

**비즈니스 규칙:**
- 주문 1건당 쿠폰 1장
- 할인 계산: FIXED(정액 차감), RATE(주문금액 * 퍼센트)
- 최소 주문 금액 조건(minOrderAmount) 미충족 시 적용 불가
- 주문 스냅샷: 원금액, 할인금액, 최종금액 저장

## 7. 미결정 사항

없음 (모두 해소됨)

**해소 이력:**
- phone: 010-XXXX-XXXX 형식 (하이픈 포함 13자) → 확정
- category: 별도 테이블 (Seed 데이터, 조회 전용) → 확정
- startAt/endAt: KST (Asia/Seoul) 기준, 날짜만 받고 KST 00:00~23:59로 처리 → 확정
- 좋아요 목록: page/size 페이지네이션 추가 → 확정

## 8. 용어 사전

| 용어 | 정의 |
|------|------|
| loginId | 멤버 로그인 식별자 (영문 소문자 + 숫자) |
| Brand | 브랜드 (애견용품 제조/판매 브랜드) |
| Category | 상품 카테고리 (Seed 데이터 기반 조회 전용 테이블) |
| Product | 상품 (개별 애견용품) |
| Like | 좋아요 (멤버의 상품 관심 표시) |
| Order | 주문 (하나 이상의 상품을 포함하는 구매 요청) |
| OrderItem | 주문 항목 (주문 내 개별 상품 + 수량 + 스냅샷 + productId 논리 참조) |
| Snapshot | 스냅샷 (주문 시점의 상품 정보 사본 — 주문 번호, productId(논리 참조), 상품명, 가격, 브랜드명) |
| likeCount | 상품별 좋아요 수 (Product 테이블 필드) |
| LDAP | 어드민 인증 헤더 (X-Loopers-Ldap: loopers.admin) |
| Soft Delete | deletedAt 필드 기반 논리적 삭제 |

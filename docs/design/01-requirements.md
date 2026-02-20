# 감성 이커머스 유저 시나리오 기반 기능 정의 & 요구사항 명세서 (결제 제외)

> 목표: **유저/브랜드/상품/좋아요/장바구니/주문** 도메인을 모두 포함하고, 기능 요구사항을 **유저(Actor) 중심 시나리오**로 정리한다.  
> 범위: 결제(결제 승인/실패/취소) **구현은 Phase2로 제외**하되, 확장성을 위해 **주문서(결제 대기) → 결제 완료 → 재고 차감 → 주문 확정** 흐름을 요구사항에 포함한다.

---

## 1. 목적 및 범위

본 문서는 “좋아요 → (쿠폰: 추후) → 주문(결제 제외)” 흐름을 가지는 감성 이커머스의 **MVP 요구사항**을 정의한다.

- 사용자는 여러 브랜드의 상품을 탐색하고 좋아요를 남기며, 여러 상품을 한 번에 주문할 수 있다.
- 관리자는 브랜드/상품을 운영하고 주문을 조회할 수 있다.

---

## 2. 도메인 포함 범위(체크)

본 명세서는 아래 도메인을 모두 포함한다.

- **유저(User/Account)**: 회원가입, 내 정보 조회, 비밀번호 변경
- **브랜드(Brand)**: 브랜드 정보 조회(고객), 브랜드 운영(CRUD, 관리자)
- **상품(Product)**: 상품 목록/상세 조회(고객), 상품 운영(CRUD, 관리자), **주문서 생성 시 재고 확인/예약(hold)**, (Phase2) **결제 완료 시 재고 차감(commit)**
- **좋아요(Like)**: 좋아요 등록/취소/내 좋아요 목록 조회(회원)
- **장바구니(Cart)**: 장바구니 상품 담기/삭제/수량 변경, 장바구니 선택 주문(회원), 장바구니 목록 조회(관리자)
- **주문(Order)**: **주문서 생성/저장(PENDING_PAYMENT) + 재고 확인/예약(hold) + 스냅샷 저장**, (Phase2) **결제 완료 시 재고 차감(commit) + 주문 확정(PAID)**, 주문 목록/상세 조회(회원), 주문 조회(관리자)
- (운영/필수) 관리자 기능(삭제 상품 조회, 수정 이력 조회)을 위해 브랜드/상품 삭제는 **소프트 삭제(삭제 플래그/삭제 시각)** 로 처리한다.
- (운영/필수) 상품 변경(수정/삭제/복구/판매상태 변경) 시 **변경 이력(`product_revisions`)** 이 생성되어야 하며, `products.revision_seq` 는 최신 이력 포인터(현재 버전)로 사용한다.
---

## 3. 용어 정의

- **Guest(비회원)**: 로그인하지 않은 사용자
- **User(회원)**: 인증된 사용자
- **Admin(관리자)**: LDAP 등 내부 인증을 사용하는 운영자
- **Like(좋아요)**: 사용자가 특정 상품에 남기는 선호 표시
- **Order(주문/주문서)**: 결제 전 **주문서 생성(결제 대기)** 을 포함하는 거래 기록. 재고는 주문서 생성 시 **예약(hold)** 하고, **결제 완료 시점에 차감**한다.
- **Snapshot(스냅샷)**: 주문 시점의 상품 정보를 주문 항목에 저장하는 것

---


- **Reservation(재고 예약/홀드)**: 주문서(결제 대기)를 생성할 때 일정 시간 동안 재고를 **임시로 묶어두는 것**. 다른 주문에서 해당 수량을 사용하지 못하게 한다.
- **AvailableStock(구매 가능 재고)**: `총 재고(OnHand) - 예약 재고(Reserved)`

## 4. Actor 및 권한 범위

### 4.1 Actor 목록
- Guest(비회원)
- User(회원)
- Admin(관리자)

### 4.2 권한 정책(요약)
- **Guest**: 회원가입, 브랜드/상품 조회 가능
- **User**: Guest 기능 + 내정보, 비밀번호 변경, 좋아요, 주문 기능 가능
- **Admin**: 관리자 전용 API 접근(브랜드/상품 운영, 주문 조회)

## ✅ API 제안사항

- 대고객 기능은 `/api/v1` prefix 를 통해 제공합니다.

  ```markdown
  유저 로그인이 필요한 기능은 아래 헤더를 통해 유저를 식별해 제공합니다.
  인증/인가는 주요 스코프가 아니므로 구현하지 않습니다.
  유저는 타 유저의 정보에 직접 접근할 수 없습니다.

  * **X-Loopers-LoginId** : 로그인 ID
  * **X-Loopers-LoginPw** : 비밀번호
  ```

  ```markdown
  ✅ 사용자 식별자(userId) 노출 최소화
  - 유저 본인 리소스 조회는 path/query에 userId를 노출하지 않고 **/users/me** 형태로 제공합니다.
    예) GET /api/v1/users/me, GET /api/v1/users/me/likes
  - 불가피하게 userId를 받는 경우(내부/어드민 제외), 서버에서 **userId == 인증 사용자**를 강제 검증합니다.
  ```

- 어드민 기능은 `/api-admin/v1` prefix 를 통해 제공합니다.

  ```markdown
  어드민 기능은 아래 헤더를 통해 어드민을 식별해 제공합니다.

  * **X-Loopers-Ldap** : loopers.admin

  LDAP : Lightweight Directory Access Protocol
  중앙 집중형 사용자 인증, 정보 검색, 액세스 제어.
  -> 회사 사내 어드민
  ```


---

## 5. 유저 시나리오(Scenario)

> 각 시나리오는 “유저의 목표”를 중심으로 구성하며, 시나리오별로 필요한 기능 요구사항(FR)을 연결한다.

### SCN-01 (Guest/User) 브랜드/상품 탐색
**목표**: 사용자는 브랜드/상품을 둘러보고 원하는 상품을 찾을 수 있어야 한다.  
**포함 도메인**: Brand, Product

- 브랜드 목록 조회(키워드 검색 `q` 지원)
- 브랜드 상세 조회
- 상품 목록 조회(키워드 검색 `q` + 필터/정렬/페이지)
- 상품 상세 조회
---

### SCN-02 (Guest) 회원가입
**목표**: 비회원은 회원가입을 통해 회원이 될 수 있어야 한다.  
**포함 도메인**: User(계정)

- 회원가입(중복/정책 검증 포함)

---

### SCN-03 (User) 좋아요로 관심 상품 모아보기
**목표**: 회원은 좋아요로 관심 상품을 저장/관리하고 목록으로 다시 볼 수 있어야 한다.  
**포함 도메인**: Like, Product

- 좋아요 등록(멱등)
- 좋아요 취소(멱등)
- 내 좋아요 목록 조회(본인만)

---

### SCN-04 (User) 장바구니로 담아두고 선택 주문하기
**목표**: 회원은 상품을 장바구니에 담아 관리하고, 장바구니에서 선택한 상품으로 주문할 수 있어야 한다.  
**포함 도메인**: Cart, Product, Order

- 장바구니 상품 등록
- 장바구니 상품 삭제
- 장바구니 상품 수량 수정
- 장바구니에서 선택 주문(결제 제외)
- 장바구니 담기/수량 변경 시 **구매 가능 재고(AvailableStock)를 초과할 수 없다**.
- 장바구니는 재고를 **예약(reserve)하지 않으며**, 주문 시점에 재고를 다시 확인한다.
- 장바구니에 담아둔 상품은 **주문 전까지 스냅샷으로 고정되지 않으며**, 상품의 수정(가격/명칭/노출/삭제 등)이 발생하면 장바구니 조회 시 **최신 상품 정보로 반영**되어야 한다.
- 장바구니 조회 시 각 항목은 **주문 가능 여부(available)** 와 **주문 불가 사유(unavailableReason)** 를 함께 제공해야 한다.
    - 예: `DELETED`(삭제됨), `HIDDEN`(비노출), `STOPPED`(판매중지), `TEMP_SOLD_OUT`(운영자 일시품절), `OUT_OF_STOCK`(재고 부족), `INVALID_QUANTITY`(수량 부적합)
- 상품의 가격/명칭 등 정보가 변경되면 장바구니에도 **즉시 최신 값으로 반영**되며, 주문이 생성되는 시점에 **해당 최신 값이 주문 스냅샷으로 저장**된다.
- 삭제/비노출된 상품도 장바구니에 **남아 있을 수 있으나 주문은 불가**해야 하며, 사용자가 삭제할 수 있어야 한다.


---

### SCN-05 (User) 상품 조회 화면에서 바로 주문하기(장바구니 없이)
**목표**: 회원은 **상품 조회 화면(상품 상세/목록)** 에서 장바구니를 거치지 않고 즉시 주문서를 만들 수 있어야 하며, 재고 정합성과 스냅샷 저장이 보장되어야 한다.  
**포함 도메인**: Order, Product

- (선행) 상품 상세 조회 또는 상품 목록 조회
- 사용자는 상품 조회 화면에서 **수량을 선택**하고 `바로 주문(바로 구매)`을 요청한다.
- 주문서 생성 요청(items)
    - 단일 상품 주문: `items=[{productId, quantity}]` 형태로 1건만 포함될 수 있다.
    - (선택) 목록 화면에서 다건 선택 주문을 지원한다면 `items`에 여러 건을 포함할 수 있다.
- 주문서 생성 결과로 **주문서(`PENDING_PAYMENT`)를 저장**한다(주문 항목 스냅샷 포함).
- 재고 확인(구매 가능 재고 기준) 및 **재고 예약(hold)** (원자성 보장, 구매 가능 재고 음수 금지)
- (Phase2) 결제 완료
- (Phase2) 결제 완료 처리 시 **재고 차감(커밋)** 및 **주문서 확정 저장(상태 `PAID` 업데이트)**
    - 커밋 실패 시 결제는 실패로 처리되어야 한다(정합성 우선).
- (필수) 사용자가 결제를 완료하지 못한 경우(`PAYMENT_FAILED/EXPIRED/CANCELLED` 또는 이탈)에는 **주문 품목이 장바구니에 담겨 있어야 한다**.
    - 방식(확정: B): 주문이 `PAYMENT_FAILED/EXPIRED/CANCELLED` 로 확정되면 주문 품목을 장바구니로 **자동 복원(추가)** 한다(기존 동일 상품이 있으면 수량 병합). 결제 성공 시에는 장바구니에 영향이 없다(바로 주문이므로).
- 주문 목록 조회(기간)
- 주문 상세 조회(본인만)
- 주문 취소(결제 제외)
- 주문 상세에서 주문 항목의 **구매 시점 상품 스냅샷 상세**를 조회

---

### SCN-06 (User) 장바구니에서 선택 주문하기
**목표**: 회원은 장바구니에 담긴 여러 상품 중 선택하여 한 번에 주문할 수 있어야 하며, 재고 정합성과 스냅샷 저장이 보장되어야 한다.  
**포함 도메인**: Cart, Order, Product

- (선행) 장바구니에서 주문할 상품을 선택한다(SCN-04).
- 주문서 생성 요청(items)
- 주문서 생성 결과로 **주문서(`PENDING_PAYMENT`)를 저장**한다(주문 항목 스냅샷 포함).
- 재고 확인(구매 가능 재고 기준) 및 **재고 예약(hold)**
- (Phase2) 결제 완료 → 재고 차감(commit) → 주문 확정(PAID)
- 결제 실패/만료 시 예약 해제 및 **장바구니 항목 유지**(재시도 가능)

---

### SCN-07 (User) 내 정보 확인 및 비밀번호 변경
**목표**: 회원은 내 정보를 확인하고 비밀번호를 변경할 수 있어야 한다.  
**포함 도메인**: User(계정)

- 내 정보 조회
- 비밀번호 변경(현재 비밀번호 검증)

---

### SCN-08 (Admin) 브랜드/상품 운영
**목표**: 관리자는 브랜드/상품을 등록/수정/삭제하여 카탈로그를 운영할 수 있어야 한다.  
**포함 도메인**: Brand, Product

- 브랜드 목록/상세 조회 및 CRUD
- 상품 목록/상세 조회 및 CRUD
- 제약: 브랜드 삭제 시 해당 브랜드 상품도 삭제
- 제약: 상품 등록 시 브랜드는 존재해야 함 / 수정 시 브랜드 변경 불가
- (상품 운영) 관리자는 **삭제된 상품**을 포함하여 상품 목록을 조회할 수 있어야 한다.
- (상품 운영) 관리자는 상품의 **수정 이력(변경 내역)** 목록/상세를 조회할 수 있어야 한다.
---

### SCN-09 (Admin) 주문 모니터링
**목표**: 관리자는 운영을 위해 주문 목록/상세를 조회할 수 있어야 한다.  
**포함 도메인**: Order

- 주문 목록 조회
- 주문 상세 조회

---

### SCN-10 (Admin) 회원 장바구니 모니터링
**목표**: 관리자는 운영 목적(문의/장애 대응 등)으로 특정 회원의 장바구니에 담긴 상품 목록을 조회할 수 있어야 한다.  
**포함 도메인**: Cart, Product

- 회원 장바구니 상품 목록 조회

### SCN-11 (Admin) 운영 통계 대시보드
**목표**: 관리자는 운영 의사결정/모니터링을 위해 핵심 지표를 기간 기준으로 확인할 수 있어야 한다.  
**포함 도메인**: Order, Product, Like, Cart, (User: 집계 기준)

- 기간별 주문/결제(Phase2)/만료/취소 건수 통계
- 인기 상품 통계(좋아요 TOP, 주문 TOP)
- 재고/예약 현황 통계(예약량, 가용재고, 저재고 목록)
- 장바구니 담김 TOP 상품(선택)

---

---

## 6. 기능 요구사항 명세(Functional Requirements)

> 아래 FR은 “유저 목표(시나리오)” 기준으로 분류하며, 각 FR은 관련 도메인과 Actor를 명시한다.

### 6.1 User(계정)

- **FR-U-001 회원가입**
    - Actor: Guest
    - 시나리오: SCN-02
    - 설명: 비회원은 회원가입을 할 수 있어야 한다.
    - 성공 조건: 신규 사용자 계정이 생성된다.
    - 예외/실패(필수)
        - 동일 식별자(예: loginId/email) 존재 시 실패
        - 비밀번호 정책(길이/형식) 위반 시 실패

- **FR-U-002 내 정보 조회**
    - Actor: User
    - 시나리오: SCN-07
    - 설명: 회원은 내 정보를 조회할 수 있어야 한다.

- **FR-U-003 비밀번호 변경**
    - Actor: User
    - 시나리오: SCN-07
    - 설명: 회원은 비밀번호를 변경할 수 있어야 한다.
    - 필수 조건: 현재 비밀번호 검증 후 변경되어야 한다.

> 인증/인가는 주요 스코프가 아니므로 **구현하지 않는다**.
> - User 전용 기능은 `X-Loopers-LoginId`, `X-Loopers-LoginPw` 헤더로 유저를 **식별**한다.
> - Admin 전용 기능은 `X-Loopers-Ldap: loopers.admin` 헤더로 어드민을 **식별**한다.
> - 유저는 타 유저의 정보에 직접 접근할 수 없다.

---

### 6.2 Brand/Product(고객용 카탈로그)

- **FR-C-000 브랜드 목록 조회**
    - Actor: Guest, User
    - 시나리오: SCN-01
    - 설명: 사용자는 브랜드 목록을 조회할 수 있어야 한다.
    - 쿼리 파라미터(선택)
        - `q`: 브랜드명 키워드 검색(부분 일치, 대소문자 무시)
    - 응답 규칙(권장)
        - `q`가 없으면 `display_status=ACTIVE` 이고 소프트삭제되지 않은(`del_yn='N'`) 브랜드를 페이징 조회한다.
        - `q`가 있으면 브랜드명 기준으로 필터링한 결과를 페이징 조회한다.

- **FR-C-001 브랜드 상세 조회**
    - Actor: Guest, User
    - 시나리오: SCN-01
    - 설명: 브랜드 정보를 조회할 수 있어야 한다.

- **FR-C-001-1 브랜드 상태/노출 정책(필수)**
    - Actor: Guest, User, Admin
    - 시나리오: SCN-01, SCN-08
    - 설명: 브랜드는 운영/고객/장바구니 흐름에서 일관된 상태 규칙을 따라야 한다.
    - 상태 모델(ERD 기준)
        - `display_status`: `ACTIVE`(노출), `HIDDEN`(비노출)
        - 삭제 여부: `del_yn` + `deleted_at` (소프트 삭제)
    - 규칙(필수)
        - 고객용 브랜드 목록/상세(`/api/v1/brands...`)에서는 `display_status=ACTIVE AND del_yn='N'` 브랜드만 조회 가능해야 한다.
        - 브랜드가 `HIDDEN` 이거나 소프트 삭제(`del_yn='Y'`)되면 해당 브랜드의 상품은 고객용 상품 목록/상세에서 조회될 수 없어야 한다.
        - 장바구니에서는 브랜드가 `HIDDEN`/소프트삭제인 상품도 항목은 반환할 수 있으나, `available=false` 및 `unavailableReason=BRAND_HIDDEN/BRAND_DELETED` 등으로 주문 불가 사유를 제공해야 한다.
        - 소프트 삭제 컬럼 정합성: `del_yn='N'`이면 `deleted_at IS NULL`, `del_yn='Y'`이면 `deleted_at IS NOT NULL` 이어야 한다.


- **FR-C-002 상품 목록 조회**
    - Actor: Guest, User
    - 시나리오: SCN-01
    - 설명: 상품 목록을 조회할 수 있어야 한다.
    - 쿼리 파라미터
        - `brandId`(선택): 특정 브랜드 상품 필터링
        - `q`(선택): 상품명/브랜드명 키워드 검색(부분 일치, 대소문자 무시)
        - `page`: 기본값 0
        - `size`: 기본값 20
    - 정렬(sort)
        - `latest`는 **필수**
        - `price_asc`, `likes_desc`는 **선택 구현(Optional)**
    - 응답 규칙(권장)
        - 고객용(`/api/v1`)에서는 `display_status=ACTIVE`, `del_yn='N'` 인 상품만 노출한다.
        - `brandId`와 `q`는 함께 사용할 수 있다(AND 조건).

- **FR-C-003 상품 상세 조회**
    - Actor: Guest, User
    - 시나리오: SCN-01
    - 설명: 특정 상품의 상세 정보를 조회할 수 있어야 한다.

- **FR-C-004 고객용 조회 제외 규칙(삭제/비노출)**
    - Actor: Guest, User
    - 시나리오: SCN-01
    - 설명: 고객용 조회(`/api/v1`)에서는 **비노출(`display_status=HIDDEN`) 또는 소프트 삭제(`del_yn='Y'`)된 상품**을 상품 목록/상세에서 조회할 수 없어야 한다.
    - 예외(필수): 단, **장바구니 조회**는 사용자의 보유 목록을 표시하기 위해 삭제/비노출 상품을 `unavailableReason`과 함께 반환할 수 있다(주문 불가).
    - 비고: 운영자(`/api-admin/v1`) 조회에서만 삭제/비노출 상품을 조회할 수 있다.

- **FR-C-005 상품 상태/노출 정책**
    - Actor: Guest, User, Admin
    - 시나리오: SCN-01, SCN-04, SCN-05, SCN-08
    - 설명: 상품은 운영/고객/장바구니/주문 흐름에서 일관된 상태 규칙을 따라야 한다.
    - 상태 모델(ERD 기준)
        - `display_status`(노출): `ACTIVE`, `HIDDEN`
        - `sale_status`(판매): `ON_SALE`, `TEMP_SOLD_OUT`, `STOPPED`
        - 삭제 여부(소프트 삭제): `del_yn`, `deleted_at`
    - 규칙(필수)
        - 고객용 상품 목록/상세(`/api/v1/products...`)에서는 기본적으로 `display_status=ACTIVE`, `del_yn='N'` 인 상품만 조회 가능해야 한다.
        - 고객용 주문 가능 조건은 `display_status=ACTIVE`, `sale_status=ON_SALE`, `del_yn='N'`, `AvailableStock>0` 을 모두 만족해야 한다.
        - 장바구니 조회에서는 `HIDDEN`/소프트삭제 상품도 **항목은 반환**할 수 있으나 `available=false` 와 `unavailableReason` 을 제공하고 **주문은 불가**해야 한다.
        - `sale_status=TEMP_SOLD_OUT` 또는 `sale_status=STOPPED` 인 상품도 장바구니에서는 항목 반환은 가능하지만 주문은 불가해야 한다.
        - 주문 상세/주문 항목 스냅샷 조회에서는 상품이 비노출/소프트삭제/판매중지 상태여도 **구매 시점 스냅샷 정보**를 조회할 수 있어야 한다.
        - 관리자(`/api-admin/v1`)에서는 `includeDeleted=true` 등 조건으로 소프트 삭제 상품을 조회할 수 있어야 한다.

    - 주문 불가 사유(`unavailableReason`)는 DB 컬럼값이 아니라 서비스 계산값이며, `display_status`, `sale_status`, `del_yn/deleted_at`, `product_stocks(on_hand, reserved)`를 기준으로 산출한다.



---

### 6.3 Like(좋아요)

- **FR-L-001 좋아요 등록**
    - Actor: User
    - 시나리오: SCN-03
    - 설명: 회원은 상품에 좋아요를 등록할 수 있어야 한다.
    - 멱등성(필수): 동일 사용자-상품 조합은 1개의 좋아요만 존재해야 한다.

- **FR-L-002 좋아요 취소**
    - Actor: User
    - 시나리오: SCN-03
    - 설명: 회원은 상품 좋아요를 취소할 수 있어야 한다.
    - 멱등성(필수): 이미 취소된 상태에서 다시 취소 요청해도 안전해야 한다.

- **FR-L-003 내가 좋아요한 상품 목록 조회**
    - Actor: User
    - 시나리오: SCN-03
    - 설명: 회원은 내가 좋아요한 상품 목록을 조회할 수 있어야 한다.
    - 인가 규칙(필수): **본인만** 조회 가능해야 한다.  
      가능하면 **`/users/me` 기반 URI**로 제공하여 사용자 ID를 노출하지 않는다. (예: `GET /api/v1/users/me/likes`)

---

- **FR-L-004 좋아요 수 집계/정렬 규칙**
    - Actor: Guest, User
    - 시나리오: SCN-01, SCN-03
    - 설명: `likes_desc` 정렬 및 노출을 위해 좋아요 수 집계 규칙이 정의되어야 한다.
    - 규칙(필수)
        - 좋아요 수는 **활성(취소되지 않은) 좋아요의 총합**을 의미한다.
        - 상품이 삭제/비노출되어 고객용 조회에서 제외되더라도, 과거 집계 데이터는 운영 목적에 따라 유지될 수 있다.
    - 비고: 구현 방식은 집계 컬럼(`likesCount`) 유지 또는 조회 시 count 계산 중 선택할 수 있다.


---

### 6.4 Cart(장바구니)

> 장바구니는 “주문 전 후보 목록”이며, **주문처럼 스냅샷을 저장하지 않는다.**  
> 따라서 장바구니에 담긴 상품은 **상품 수정/삭제/가격 변경/재고 변동**이 있으면 조회 시점에 최신 정보로 반영되어야 한다.

- **FR-CART-001 장바구니 상품 등록**
    - Actor: User
    - 시나리오: SCN-04
    - 설명: 회원은 장바구니에 상품을 등록할 수 있어야 한다.
    - 규칙(필수)
        - 장바구니 항목은 `userId + productId` 기준으로 **1건만 존재**해야 한다(중복 등록 시 **수량 병합**).
        - 등록 시점에 상품이 `display_status=HIDDEN` 이거나 소프트삭제(`del_yn='Y'`) 또는 판매 불가(`sale_status != ON_SALE`) 상태면 실패해야 한다.
        - 등록 수량은 `1 이상`이어야 한다.
        - 등록 수량이 **현재 구매 가능 재고(AvailableStock)** 를 초과하면 실패해야 한다. (응답에 `availableStock` 포함 권장)

- **FR-CART-002 장바구니 상품 삭제**
    - Actor: User
    - 시나리오: SCN-04
    - 설명: 회원은 장바구니에서 상품을 삭제할 수 있어야 한다.
    - 규칙(필수)
        - 존재하지 않는 항목 삭제 요청은 **멱등(no-op)** 으로 처리할 수 있다.

- **FR-CART-003 장바구니 상품 수량 수정**
    - Actor: User
    - 시나리오: SCN-04
    - 설명: 회원은 장바구니에 등록한 상품 수량을 수정할 수 있어야 한다.
    - 규칙(필수)
        - 수정 수량은 `1 이상`이어야 한다.
        - 수정 수량이 **현재 구매 가능 재고(AvailableStock)** 를 초과하면 실패해야 한다. (응답에 `availableStock` 포함 권장)
        - (선택) 수량을 `0`으로 요청하면 삭제로 처리할 수 있다.

- **FR-CART-004 장바구니에서 선택 주문(결제 제외)**
    - Actor: User
    - 시나리오: SCN-06
    - 설명: 회원은 장바구니에 등록된 상품 중 일부를 선택하여 주문을 생성할 수 있어야 한다.
    - 규칙(필수)
        - 주문 생성 시점에 선택된 모든 항목에 대해 **상품 상태/가격/재고를 재검증**해야 한다.
        - 하나라도 주문 불가하면 주문 생성은 실패해야 하며, **부분 성공은 허용하지 않는다.**
        - 실패 시 장바구니 항목은 **변경하지 않는다**(사용자가 수정 후 재시도 가능).
        - 주문 생성 성공(PENDING_PAYMENT) 시점에는 장바구니 항목을 제거하지 않는다.
            - 결제 성공(PAID) 시점에만 제거한다(FR-CART-006).

- **FR-CART-005 장바구니 상품 목록 조회**
    - Actor: User
    - 시나리오: SCN-04
    - 설명: 회원은 장바구니에 담긴 상품 목록을 조회할 수 있어야 한다.
    - 응답 요구(필수)
        - 장바구니 항목은 **현재 시점의 상품 정보**를 함께 제공해야 한다(장바구니는 스냅샷이 아님).
        - 각 항목은 `available` 및 `unavailableReason`(예: DELETED, HIDDEN, BRAND_DELETED, BRAND_HIDDEN, STOPPED, TEMP_SOLD_OUT, OUT_OF_STOCK, INVALID_QUANTITY)을 포함해야 한다.
        - (권장) `availableStock`, `maxPurchasableQty`(현재 구매 가능한 최대 수량) 제공

- **FR-CART-006 결제 성공 시 장바구니 정리(Phase2)**
    - Actor: User
    - 시나리오: SCN-06
    - 설명: **장바구니 기반 주문**이 결제 완료되어 주문이 `PAID`로 확정되면, 해당 주문에 포함된 장바구니 항목은 자동 제거되어야 한다.
    - 규칙(필수)
        - `PAYMENT_FAILED/EXPIRED/CANCELLED` 인 경우에는 장바구니 항목을 제거하지 않는다.
        - 장바구니 정리는 **주문 확정(PAID) 처리와 함께** 보장되어야 한다(같은 트랜잭션 또는 보상 가능한 후처리).
        - 정리 처리는 **주문 ID 기준으로 멱등**이어야 한다(중복 이벤트에도 안전).

- **FR-CART-007 바로 주문 실패/만료 시 장바구니 복원(필수)**
    - Actor: User
    - 시나리오: SCN-05
    - 설명: 상품 조회 화면에서 `바로 주문`으로 생성된 주문이 결제 실패/만료/취소되면, 사용자는 재시도를 위해 동일 품목을 장바구니에서 확인할 수 있어야 한다.
    - 규칙(필수)
        - 주문이 `PAYMENT_FAILED/EXPIRED/CANCELLED` 로 확정되면 주문 품목을 장바구니에 **자동 복원(추가)** 한다(기존 동일 상품이 있으면 수량 병합).
        - 복원 처리는 **주문 ID 기준으로 1회만 수행(멱등)** 되어야 한다.
            - ERD 기준 `order_cart_restore` 테이블(`order_id` UNIQUE, `restored_at`, `reason`)에 처리 이력을 기록하여 중복 복원을 방지한다.
        - 장바구니에 반영되는 수량은 **현재 구매 가능 재고 이하**로 조정될 수 있으며, 초과분은 `available=false` 및 `unavailableReason=OUT_OF_STOCK` 등으로 표시한다.

- **FR-CART-008 장바구니 운영 제한(권장)**
    - Actor: User
    - 시나리오: SCN-04
    - 설명: 장바구니는 과도한 사용을 방지하기 위한 제한을 둘 수 있다.
    - 규칙(권장)
        - 최대 담기 개수(`MAX_CART_ITEMS`, 예: 100)
        - 항목당 최대 수량(`MAX_QTY_PER_ITEM`, 예: 99)

### 6.5 Order(주문/주문서, 결제는 Phase2)

> 주문은 “**주문서 생성/저장(`PENDING_PAYMENT`) + 재고 확인 + 재고 예약(hold) + 주문 항목 스냅샷 저장**”까지를 포함한다.
> (Phase2) 결제 완료 처리 시점에 **예약을 차감(커밋)** 하고 주문을 `PAID`로 **확정 업데이트**한다.

- **FR-O-000 주문 상태(Status)**
    - Actor: User, Admin
    - 시나리오: SCN-05, SCN-06, SCN-09
    - 설명: 주문은 최소한의 상태를 가진다.
    - 최소 상태(필수): `PENDING_PAYMENT`, `CANCELLED`, `EXPIRED`
    - 확장(Phase2): `PAID`, `PAYMENT_FAILED`

- **FR-O-001 주문 요청**
    - Actor: User
    - 시나리오: SCN-05, SCN-06
    - 설명: 회원은 여러 상품을 한 번에 주문할 수 있어야 한다.
    - 요청 형식: `items(productId, quantity)` 배열
    - 입력 검증(필수)
        - `quantity`는 1 이상이어야 한다.
        - `items` 내 동일 `productId`가 중복되면 **합산하여 1건으로 병합**하거나(권장) 요청을 실패 처리한다(정책 택1).
    - 원자성(필수): 주문은 전체 품목이 모두 가능할 때만 성공해야 한다(부분 성공 없음).
    - 저장 규칙(필수)
        - **주문서(orders) 저장 + 주문항목(order_items) 스냅샷 저장 + 재고 예약(hold)** 은 **단일 DB 트랜잭션**으로 원자적으로 처리되어야 한다.
            - 중간 실패 시 전체 롤백(hold leak 방지), 부분 성공 금지
        - 주문서 생성이 성공하면 주문을 `PENDING_PAYMENT` 상태로 **저장**해야 한다.
        - 주문 항목에는 주문 시점의 **스냅샷**이 함께 저장되어야 한다.
    - 재고 보장(필수)
        - 주문서 생성 시 **구매 가능 재고(AvailableStock)** 를 확인해야 한다.
        - 주문서 생성이 성공하면 재고를 **예약(hold)** 해야 한다(구매 가능 재고는 음수가 되면 안 된다).
        - 예약은 영구적이지 않으며, (Phase2) 결제가 완료되면 **차감(커밋)** 되고 결제가 실패/만료되면 **예약이 해제**되어야 한다.
    - 프로세스(필수)
        1) **주문 생성 요청**(바로 주문 또는 장바구니 선택)
        2) **재고 확인**(구매 가능 재고 기준)
        3) **재고 예약(일시적 hold)** + `expires_at` 설정
        4) **주문서 저장**: `PENDING_PAYMENT` 상태로 저장(주문 항목 스냅샷 포함)
        5) (Phase2) **결제 완료**
        6) (Phase2) **재고 차감(commit)**
        7) (Phase2) **주문서 확정 저장**: 상태 `PAID`로 업데이트(결제 정보 저장)
        8) (Phase2) **장바구니 정리**: 장바구니 기반 주문이면 결제 성공 시 해당 항목을 삭제, 실패/만료 시 유지(또는 바로 주문의 경우 자동 복원)

    - 스냅샷(필수): 주문 항목에는 주문 시점의 상품 정보가 저장되어야 한다.
    - 스냅샷 최소 항목(필수)
        - `snapshotProductName`(상품명)
        - `snapshotUnitPrice`(주문 당시 단가)
        - `snapshotBrandId`, `snapshotBrandName`(브랜드 식별/명칭)
        - (선택) `snapshotImageUrl`(대표 이미지)


- **FR-O-002 유저 주문 목록 조회**
    - Actor: User
    - 시나리오: SCN-05, SCN-06
    - 설명: 회원은 기간 조건으로 주문 목록을 조회할 수 있어야 한다.
    - 파라미터: `startAt`, `endAt` (ISO 날짜)

- **FR-O-003 단일 주문 상세 조회**
    - Actor: User
    - 시나리오: SCN-05, SCN-06
    - 설명: 회원은 단일 주문 상세 정보를 조회할 수 있어야 한다.
    - 인가 규칙(필수): **본인의 주문만** 조회 가능해야 한다.

- **FR-O-004 주문 취소(결제 제외)**
    - Actor: User
    - 시나리오: SCN-05, SCN-06
    - 설명: 회원은 본인의 주문을 취소할 수 있어야 한다.
    - 제약(필수)
        - 취소는 `PENDING_PAYMENT` 상태의 주문에서만 가능해야 한다.
        - 주문 취소가 성공하면 예약된 재고는 **해제**되어야 한다.
        - (Phase2) 결제 완료(`PAID`) 이후 취소/환불 정책은 결제 도입 시 정의한다.
        - 멱등성(권장): 이미 취소된 주문을 다시 취소해도 안전해야 한다.

- **FR-O-005 주문 항목 상품 스냅샷 상세 조회**
    - Actor: User
    - 시나리오: SCN-05, SCN-06
    - 설명: 회원은 주문 내역에서 특정 상품을 조회할 때, 해당 상품이 삭제/비노출 상태여도 **구매 시점 상품 상세(스냅샷)** 를 조회할 수 있어야 한다.
    - 구현 가이드(제안)
        - `GET /api/v1/orders/{orderId}` 응답에 각 `orderItem`의 스냅샷을 충분히 포함하거나,
        - `GET /api/v1/orders/{orderId}/items/{orderItemId}` 형태의 스냅샷 상세 조회 API를 제공한다.
    - 비고(중요): 고객용 상품 상세(`/api/v1/products/{productId}`)는 `display_status=ACTIVE`, `del_yn='N'` 인 상품만 조회 가능하며, 과거 구매 상품 조회는 **주문 스냅샷 기반**으로 제공한다.

- **FR-O-006 주문 생성/취소 멱등성(권장)**
    - Actor: User
    - 시나리오: SCN-05, SCN-06
    - 설명: 네트워크 재시도/중복 요청에도 동일 요청이 중복 처리되지 않도록 주문 생성/취소는 멱등성을 제공할 수 있어야 한다.
    - 구현 가이드(제안): `Idempotency-Key` 헤더 또는 요청ID를 지원한다.

- **FR-O-007 재고 예약(hold) 및 만료**
    - Actor: User
    - 시나리오: SCN-05, SCN-06
    - 설명: 주문서(결제 대기) 생성 시 각 주문 항목 수량만큼 재고를 **예약**해야 한다.
    - 규칙(필수)
        - 예약은 **만료 시간(TTL)** 을 가진다(예: 15분).
        - **예약/만료 처리는 RDBMS 기준**으로 수행한다.
            - 예: 주문서에 `expires_at` 저장 + 스케줄러/배치가 `PENDING_PAYMENT` 주문을 `EXPIRED`로 전환하며 예약을 해제
        - TTL 내 결제가 완료되지 않으면 주문은 `EXPIRED` 로 전환되고, 예약 재고는 자동 해제된다.
        - 결제 실패 시 `PAYMENT_FAILED`(Phase2) 로 전환되고, 예약 재고는 자동 해제된다.

    - 상태 전이 경쟁(결제 vs 만료/취소) 처리(필수)
        - 만료 처리 배치/스케줄러는 아래 조건을 만족할 때만 만료 처리한다(Compare-And-Set).
            - `UPDATE orders SET status='EXPIRED' WHERE order_id=:orderId AND status='PENDING_PAYMENT' AND expires_at < now()`
            - 위 UPDATE가 성공(affectedRows=1)한 경우에만 예약 재고를 해제한다(중복 해제 방지).
        - (Phase2) 결제 완료 처리도 상태 전이 조건을 만족할 때만 수행한다.
            - `UPDATE orders SET status='PAID' WHERE order_id=:orderId AND status='PENDING_PAYMENT' AND expires_at > now()`
            - 성공한 경우에만 재고 차감(commit)을 수행한다(중복 차감 방지).

- **FR-O-008 결제 완료 처리 및 주문 확정(Phase2)**
    - Actor: User(결제 주체), Admin(운영 조회)
    - 시나리오: SCN-05, SCN-06, SCN-09
    - 설명: 결제 완료 이벤트(승인/캡처 완료)가 도착하면 주문을 확정하고 재고를 차감한다.
    - 규칙(필수)
        - `PENDING_PAYMENT` 주문에 대해서만 결제 완료 처리를 수행한다.
        - **결제 이벤트 멱등성(필수)**: 결제 승인/캡처 결과는 `paymentTransactionId`(또는 PG 거래 ID)로 식별하며, 동일 ID는 **한 번만 처리**되어야 한다.
            - (권장) `payments` 테이블을 두고 `paymentTransactionId`에 **유니크 제약**을 둔다.
            - 이미 `PAID`인 주문에 동일 이벤트가 재도착하면 **no-op(성공 응답)** 으로 처리한다.
            - `EXPIRED`/`CANCELLED` 주문에 결제 완료 이벤트가 도착하면 **거절 또는 정합성 복구(환불/취소) 절차**로 전환한다(Phase2).
        - 결제 완료 처리 시 **재고 차감(커밋)** 과 주문 상태 업데이트(`PAID`)는 원자적으로 처리되어야 한다.
        - 커밋 실패 시 결제는 실패로 처리되거나(권장: 승인/캡처 이전), 결제 정합성 복구 절차(Phase2)로 전환한다.




---

### 6.6 Admin(브랜드/상품/주문 운영)

- **FR-A-001 브랜드 운영**
    - Actor: Admin
    - 시나리오: SCN-08
    - 설명: 관리자는 브랜드 목록/상세를 조회하고 브랜드를 등록/수정/삭제할 수 있어야 한다.
    - 삭제 규칙(필수)
        - 브랜드 삭제는 **소프트 삭제**로 처리한다(예: `del_yn='Y'`, `deleted_at` 기록).
        - 브랜드가 소프트 삭제되면 해당 브랜드의 상품도 **소프트 삭제**하거나(정책 A), 최소한 `display_status=HIDDEN` 처리 후 후속 배치로 소프트 삭제해야 한다(정책 B).
        - 소프트 삭제된 브랜드/상품은 고객용(`/api/v1`)에서 조회될 수 없으며, 장바구니에서는 `available=false` 와 `unavailableReason=BRAND_DELETED/DELETED` 등의 사유로 표시된다(주문 불가).


- **FR-A-002 상품 운영**
    - Actor: Admin
    - 시나리오: SCN-08
    - 설명: 관리자는 상품을 운영(조회/등록/수정/삭제)할 수 있어야 한다.
    - 제약(필수)
        - 상품 등록 시 브랜드는 이미 등록된 브랜드여야 한다.
        - 상품 수정 시 브랜드는 변경할 수 없다.

- **FR-A-002-1 등록된 상품 목록 조회(운영)**
    - Actor: Admin
    - 시나리오: SCN-08
    - 설명: 관리자는 등록된 상품 목록을 조회할 수 있어야 한다.
    - 목록/상세 조회 조건(필수 정의)
        - **삭제된 상품 포함 조회**는 `includeDeleted=true` 같은 플래그로 제어할 수 있다.
        - **수정된 상품**은 `updatedAt` 기준으로 조회하며, 예를 들어 `updatedSince=YYYY-MM-DD` 또는 `startAt/endAt` 필터를 제공할 수 있다.
        - **수정 내역(Revision)** 은 `product_revisions` 테이블에 상품 수정/삭제/복구/판매상태 변경 시 생성되어야 하며, 최소 필드는 `revisionSeq`, `changedAt`, `changedBy(Admin)`, `action`, `before`, `after`(변경 전/후 값)이며, (권장) `changeReason`를 포함한다. `products.revision_seq` 는 최신 이력을 가리키는 포인터로 사용한다.
    - 포함(필수)
        - **삭제된 상품**을 포함하여 조회할 수 있어야 한다.
        - **수정된 상품**(최근 변경된 상품)을 조회할 수 있어야 한다.

- **FR-A-002-2 상품 상세 조회(운영)**
    - Actor: Admin
    - 시나리오: SCN-08
    - 설명: 관리자는 상품 상세 정보를 조회할 수 있어야 한다.
    - 포함(필수)
        - **삭제된 상품**도 상세 조회가 가능해야 한다.
        - **수정된 상품**도 상세 조회가 가능해야 한다.

- **FR-A-002-3 상품 수정 내역(이력) 목록 조회**
    - Actor: Admin
    - 시나리오: SCN-08
    - 설명: 관리자는 `product_revisions` 기반으로 상품의 **수정 내역(변경 이력)** 목록을 조회할 수 있어야 한다.

- **FR-A-002-4 상품 수정 내역(이력) 상세 조회**
    - Actor: Admin
    - 시나리오: SCN-08
    - 설명: 관리자는 `product_revisions` 기반으로 상품의 **수정 내역(변경 이력)** 상세를 조회할 수 있어야 한다.

- **FR-A-002-5 상품 정보 수정**
    - Actor: Admin
    - 시나리오: SCN-08
    - 설명: 관리자는 상품 정보를 수정할 수 있어야 한다.
    - 규칙(필수)
        - 가격/노출/설명/이미지 등 상품 정보 변경은 즉시 반영된다(장바구니는 최신 정보로 표시).
        - 재고(`on_hand`) 수정이 가능한 경우, `on_hand >= reserved` 조건을 위반할 수 없다.
            - 위반 시 수정은 실패하거나, 별도 보정/정합성 절차(권장: 실패)를 따라야 한다.

- **FR-A-002-6 상품 삭제**
    - Actor: Admin
    - 시나리오: SCN-08
    - 설명: 관리자는 상품을 삭제할 수 있어야 한다.
    - 비고(필수): 운영 조회(삭제 목록/상세) 요구사항을 만족하기 위해 **소프트 삭제(삭제 플래그/삭제 시각)** 방식으로 처리한다.
    - 규칙(권장)
        - 해당 상품에 `PENDING_PAYMENT` 예약이 존재하는 경우, 즉시 소프트삭제(`del_yn='Y'`) 대신 `display_status=HIDDEN`(비노출) 처리만 허용하고, 예약이 모두 해제된 후 소프트삭제를 허용한다.


- **FR-A-003 주문 조회**
    - Actor: Admin
    - 시나리오: SCN-09
    - 설명: 관리자는 주문 목록/상세를 조회할 수 있어야 한다.

- **FR-A-004 회원 장바구니 목록 조회**
    - Actor: Admin
    - 시나리오: SCN-10
    - 설명: 관리자는 특정 회원이 장바구니에 등록한 상품 목록을 조회할 수 있어야 한다.

- **FR-A-005 운영 통계 조회(대시보드)**
    - Actor: Admin
    - 시나리오: SCN-11
    - 설명: 관리자는 운영 통계 화면을 위해 기간 기준의 핵심 지표를 조회할 수 있어야 한다.
    - 기간 파라미터(권장)
        - `startAt`, `endAt` (YYYY-MM-DD)
    - 지표 범위(최소)
        1) 주문 현황: `PENDING_PAYMENT`, `PAID(Phase2)`, `EXPIRED`, `CANCELLED` 건수
        2) 인기 상품: 좋아요 TOP N, 주문 TOP N
        3) 재고 현황: 예약량(reserved) 합계, 가용재고(=on_hand-reserved) 기반 저재고 목록
    - API 제안(예시)
        - `GET /api-admin/v1/stats/overview?startAt=...&endAt=...`
        - `GET /api-admin/v1/stats/orders/daily?startAt=...&endAt=...`
        - `GET /api-admin/v1/stats/products/top-liked?startAt=...&endAt=...&limit=20`
        - `GET /api-admin/v1/stats/products/top-ordered?startAt=...&endAt=...&limit=20`
        - `GET /api-admin/v1/stats/stocks/low?threshold=10&limit=50`
    - 비고
        - MVP 단계에서는 RDBMS 집계 쿼리로 제공하되, 트래픽/정확도 요구가 커지면 이벤트 기반 집계(배치/스트림)로 확장 가능하다.


---

## 7. 시나리오 ↔ 기능 요구사항 ↔ API 매핑(Traceability)

| 시나리오 | Actor | 포함 FR | 주요 API |
|---|---|---|---|
| SCN-01 탐색 | Guest/User | FR-C-000~005 | GET /api/v1/brands, GET /api/v1/brands/{brandId}, GET /api/v1/products, GET /api/v1/products/{productId} |
| SCN-02 회원가입 | Guest | FR-U-001 | POST /api/v1/users |
| SCN-03 좋아요 | User | FR-L-001~004 | POST/DELETE /api/v1/products/{productId}/likes, GET /api/v1/users/me/likes |
| SCN-04 장바구니 | User | FR-CART-001~008 | (제안) GET /api/v1/cart, POST/DELETE/PATCH /api/v1/cart/items..., POST /api/v1/orders(선택품목) |
| SCN-05 바로 주문(상품 조회 화면) | User | FR-O-000~008 | GET /api/v1/products/{productId}, POST /api/v1/orders(items=1..n), GET /api/v1/orders, GET /api/v1/orders/{orderId} |
| SCN-06 주문(장바구니 선택) | User | FR-O-000~008, FR-CART-001~008 | (제안) GET /api/v1/cart, POST /api/v1/orders(선택품목), GET /api/v1/orders, GET /api/v1/orders/{orderId} |
| SCN-07 내정보/비번 | User | FR-U-002~003 | GET /api/v1/users/me, PUT /api/v1/users/password |
| SCN-08 운영(브랜드/상품) | Admin | FR-A-001~002-6 | /api-admin/v1/brands..., /api-admin/v1/products..., (제안) /api-admin/v1/products?includeDeleted=true, /api-admin/v1/products/{productId}/revisions... |
| SCN-09 운영(주문) | Admin | FR-A-003 | /api-admin/v1/orders... |
| SCN-10 운영(장바구니) | Admin | FR-A-004 | (제안) GET /api-admin/v1/users/{userId}/cart |
| SCN-11 통계 | Admin | FR-A-005 | (제안) GET /api-admin/v1/stats/overview, /stats/orders/daily, /stats/products/top-liked, /stats/products/top-ordered, /stats/stocks/low |

---

## 8. 비기능 요구사항(Non-Functional Requirements)

- **NFR-001 표준 에러 응답**: 모든 API는 표준 에러 포맷(코드/메시지/필드오류)을 제공해야 한다.
- **NFR-002 유저/어드민 식별(인증/인가 스코프 제외)**
    - 대고객 기능은 `/api/v1` prefix 로 제공한다.
    - 유저 로그인이 필요한 기능은 `X-Loopers-LoginId`, `X-Loopers-LoginPw` 헤더로 유저를 식별한다.
    - 보안(필수): `X-Loopers-LoginPw` 등 민감 헤더 값은 **로그/모니터링에 남기지 않거나 마스킹**해야 한다.
    - 어드민 기능은 `/api-admin/v1` prefix 로 제공하며, `X-Loopers-Ldap: loopers.admin` 헤더로 어드민을 식별한다.
    - 유저는 타 유저의 정보에 직접 접근할 수 없다.


- **NFR-003 동시성/일관성**
    - 동시 주문 상황에서도 **구매 가능 재고(AvailableStock)** 는 음수가 되지 않아야 한다.
    - 주문서 생성(재고 예약)과 취소(예약 해제)는 원자적으로 처리되어야 한다.
    - (Phase2) 결제 완료 시 재고 차감(커밋)도 원자적으로 처리되어야 한다.
    - 동시성 제어 전략(필수)
        - (권장) 재고 예약/해제/차감은 **조건부 UPDATE** 한 번으로 처리하여 오버셀(초과 판매)을 방지한다.
            - 예약(hold): `UPDATE product_stocks SET reserved = reserved + :qty WHERE product_id=:pid AND (on_hand - reserved) >= :qty`
            - 해제(release): `UPDATE product_stocks SET reserved = reserved - :qty WHERE product_id=:pid AND reserved >= :qty`
            - 차감(commit, Phase2): `UPDATE product_stocks SET reserved = reserved - :qty, on_hand = on_hand - :qty WHERE product_id=:pid AND reserved >= :qty`
            - 각 UPDATE의 `affectedRows=1`을 **성공 조건**으로 삼고, 하나라도 실패하면 전체 트랜잭션을 롤백한다(부분 성공 금지).
        - (대안) `SELECT ... FOR UPDATE` 로 재고 row를 잠근 뒤 계산/갱신하는 방식도 가능하나, 락 경합이 커질 수 있다.
    - 다건(여러 상품) 주문 데드락 방지(필수)
        - 여러 `productId`를 갱신할 때는 **정렬 기준(예: productId 오름차순)** 을 고정하여 동일한 락 획득 순서를 보장한다.
    - 재시도 정책(권장)
        - DB 데드락/락 타임아웃 발생 시 **제한된 횟수로 재시도(backoff 포함)** 할 수 있어야 한다.
- **NFR-004 재고 처리 저장소 및 캐시 정책(권장)**
    - 권장 데이터 모델(제안)
        - `product_stocks`: `product_id`, `on_hand`, `reserved` (필요 시 `version` 추가 가능)
        - `orders`: `order_id`, `status`, `expires_at`(TTL), `user_id`, `order_type`
        - `order_items`: `order_id`, `order_item_seq`, `product_id`, `quantity`, 스냅샷 필드
        - 예약(hold)은 `PENDING_PAYMENT` 주문서 생성 시 `product_stocks.reserved += quantity` 로 반영하고, 만료/실패 시 `reserved -= quantity` 로 해제한다.

    - **RDBMS를 재고의 Source of Truth로 사용**한다.
    - 재고 **예약(hold)/해제(release)/차감(commit)** 은 RDBMS 트랜잭션으로 처리되어야 한다.
    - Redis 도입은 **선택 사항**이며, 도입 시 용도는 아래로 제한한다.
        - (권장) 상품 목록/상세 조회 응답의 **AvailableStock 표시를 위한 단기 캐시**
        - 캐시 미스/장애 시 **DB 조회로 fallback** 가능해야 한다.
        - 재고 변경(예약/해제/차감) 시 캐시는 **무효화(invalidate)** 또는 갱신되어야 한다.
- **NFR-004-1 소프트 삭제 컬럼 정합성(권장)**
    - `del_yn` + `deleted_at`를 함께 사용하는 테이블은 두 컬럼을 항상 함께 갱신해야 한다.
    - 정합성 규칙: `del_yn='N'` ↔ `deleted_at IS NULL`, `del_yn='Y'` ↔ `deleted_at IS NOT NULL`

- **NFR-005 멱등성**
    - 좋아요 등록/취소는 멱등해야 한다.
    - (권장) 주문 생성/취소, 만료 처리, (Phase2) 결제 완료 처리는 **중복 요청/중복 이벤트**에도 안전해야 한다.
- **NFR-006 만료 처리/배치 운영(권장)**
    - 만료 배치는 `status='PENDING_PAYMENT' AND expires_at < now()` 대상을 주기적으로 조회하여 `EXPIRED` 전환 및 예약 해제를 수행한다.
    - 성능(권장): `orders(status, expires_at)` 인덱스를 둔다.
    - 운영(권장): 한 번에 N건씩 처리(배치 사이즈)하고, 처리 실패 건은 재시도/재처리할 수 있어야 한다.

---

## 9. 선택 구현(Optional)

- (성능) **Redis 캐시**: 상품 목록/상세 조회 시 AvailableStock/좋아요수 등 조회 성능 개선용 단기 캐시
- 상품 목록 정렬: `price_asc`, `likes_desc` (기본은 `latest`)
- (확장 시) 랭킹/추천 노출을 위한 집계/이벤트 처리 고도화
- (확장 시) 운영 화면 편의 기능: 배치 복구, 대량 수정 등

## 10. 확장(Phase2)

- 쿠폰 발급/사용
- 결제 프로세스(결제 승인/취소/실패 처리)
- (확장) 브랜드/상품 외 추가 엔티티에 대한 변경 이력(감사로그) 확대, 삭제 데이터 복구 기능
- 랭킹/추천(좋아요/주문/조회 행동 이벤트 기반)
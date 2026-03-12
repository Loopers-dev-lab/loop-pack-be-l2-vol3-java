# 🐾 PawShop — 감성 애견용품 이커머스

## 🎯 배경

**좋아요** 누르고, **쿠폰** 쓰고, 주문 및 **결제**하는 **감성 애견용품 이커머스**.

내가 좋아하는 브랜드의 애견용품들을 한 번에 담아 주문하고, 멤버 행동은 랭킹과 추천으로 연결돼요.

우린 이 흐름을 하나씩 직접 만들어갈 거예요.

---

## 🧭 서비스 흐름 예시

1. 사용자가 **회원가입**을 하고
2. 여러 브랜드의 애견용품을 둘러보고, 마음에 드는 상품엔 **좋아요**를 누르죠.
3. 사용자는 **쿠폰을 발급**받고, 여러 상품을 **한 번에 주문하고 결제**합니다.
4. 멤버의 행동은 모두 기록되고, 그 데이터는 이후 다양한 기능으로 확장될 수 있어요.

---

## ✅ API 제안사항

- 대고객 기능은 `/api/v1` prefix 를 통해 제공합니다.

    ```
    멤버 로그인이 필요한 기능은 아래 헤더를 통해 멤버를 식별해 제공합니다.
    인증/인가는 주요 스코프가 아니므로 구현하지 않습니다.
    멤버는 타 멤버의 정보에 직접 접근할 수 없습니다.

    * X-Loopers-LoginId : 로그인 ID
    * X-Loopers-LoginPw : 비밀번호
    ```

- 어드민 기능은 `/api-admin/v1` prefix 를 통해 제공합니다.

    ```
    어드민 기능은 아래 헤더를 통해 어드민을 식별해 제공합니다.

    * X-Loopers-Ldap : loopers.admin

    LDAP : Lightweight Directory Access Protocol
    중앙 집중형 사용자 인증, 정보 검색, 액세스 제어.
    -> 회사 사내 어드민
    ```

---

## ✅ 요구사항

## 👤 멤버 (Members)

| **METHOD** | **URI** | **member_required** | **설명** |
| --- | --- | --- | --- |
| POST | `/api/v1/members` | X | 회원가입 |
| GET | `/api/v1/members/me` | O | 내 정보 조회 |
| PUT | `/api/v1/members/password` | O | 비밀번호 변경 |

---

## 🏷 브랜드 & 상품 (Brands / Products)

| **METHOD** | **URI** | **member_required** | **설명** |
| --- | --- | --- | --- |
| GET | `/api/v1/brands/{brandId}` | X | 브랜드 정보 조회 |
| GET | `/api/v1/products` | X | 애견용품 목록 조회 |
| GET | `/api/v1/products/{productId}` | X | 애견용품 정보 조회 |

### ✅ 상품 목록 조회 쿼리 파라미터

| **파라미터** | **예시** | **설명** |
| --- | --- | --- |
| `brandId` | `1` | 특정 브랜드의 상품만 필터링 |
| `sort` | `latest` / `price_asc` / `likes_desc` | 정렬 기준 |
| `page` | `0` | 페이지 번호 (기본값 0) |
| `size` | `20` | 페이지당 상품 수 (기본값 20) |

> 정렬 기준은 선택 구현입니다.
>
> 필수는 `latest`, 그 외는 `price_asc`, `likes_desc` 정도로 제한해도 충분합니다.
> 결제 로직은 나중에 고려합니다


반드시 포함되어야 할 설계 내용
1. 브랜드 및 상품: 정보 조회, 목록 조회(정렬/필터 포함), 어드민용 CRUD.
2. 좋아요: 상품 좋아요 등록/취소(토글 방식 지양), 좋아요 목록 조회.
3. 주문(Order): 주문 요청, 멤버 주문 목록/상세 조회. 특히 주문 당시의 상품 정보(스냅샷) 저장 구조 설계가 중요합니다.
4. 어드민: 상품/브랜드/주문 관리를 위한 어드민 기능 및 X-Loopers-Ldap 헤더를 이용한 인증 설계.


---

## 🏷 브랜드 & 상품 ADMIN

| **METHOD** | **URI** | **ldap_required** | **설명** |
| --- | --- | --- | --- |
| GET | `/api-admin/v1/brands?page=0&size=20` | O | 등록된 브랜드 목록 조회 |
| GET | `/api-admin/v1/brands/{brandId}` | O | 브랜드 상세 조회 |
| POST | `/api-admin/v1/brands` | O | 브랜드 등록 |
| PUT | `/api-admin/v1/brands/{brandId}` | O | 브랜드 정보 수정 |
| DELETE | `/api-admin/v1/brands/{brandId}` | O | 브랜드 삭제 — 해당 브랜드의 상품들도 삭제되어야 함 |
| GET | `/api-admin/v1/products?page=0&size=20&brandId={brandId}` | O | 등록된 상품 목록 조회 |
| GET | `/api-admin/v1/products/{productId}` | O | 상품 상세 조회 |
| POST | `/api-admin/v1/products` | O | 상품 등록 — 상품의 브랜드는 이미 등록된 브랜드여야 함 |
| PUT | `/api-admin/v1/products/{productId}` | O | 상품 정보 수정 — 상품의 브랜드는 수정할 수 없음 |
| DELETE | `/api-admin/v1/products/{productId}` | O | 상품 삭제 |

> 상품, 브랜드 정보 중 고객과 어드민에게 제공되어야 할 정보에 대해 고민해보세요.

---

## ❤️ 좋아요 (Likes)

| **METHOD** | **URI** | **member_required** | **설명** |
| --- | --- | --- | --- |
| POST | `/api/v1/products/{productId}/likes` | O | 상품 좋아요 등록 |
| DELETE | `/api/v1/products/{productId}/likes` | O | 상품 좋아요 취소 |
| GET | `/api/v1/members/{memberId}/likes` | O | 내가 좋아요 한 상품 목록 조회 |

---

## 🧾 주문 (Orders)

| **METHOD** | **URI** | **member_required** | **설명** |
| --- | --- | --- | --- |
| POST | `/api/v1/orders` | O | 주문 요청 |
| GET | `/api/v1/orders?startAt=2026-01-31&endAt=2026-02-10` | O | 멤버의 주문 목록 조회 |
| GET | `/api/v1/orders/{orderId}` | O | 단일 주문 상세 조회 |

**요청 예시:**

```json
{
  "items": [
    { "productId": 1, "quantity": 2 },
    { "productId": 3, "quantity": 1 }
  ],
  "couponId": 42
}
```

> **결제**는 과정 진행 중, **추가로 개발**하게 됩니다!
> **주문 정보**에는 당시의 상품 정보가 스냅샷으로 저장되어야 합니다.
> **주문 시에 다음 동작이 보장되어야 합니다 :** 상품 재고 확인 및 차감
> **쿠폰 적용 시에는 다음 동작이 보장되어야 합니다 :** 쿠폰 유효성 검증, 단일 사용 보장, 할인 금액 반영

### ✅ 주문 쿠폰 적용 규칙

- 주문 1건당 쿠폰 1장만 적용 가능
- 존재하지 않거나 사용 불가한 쿠폰으로 요청 시 주문 실패
- 주문 성공 시 쿠폰 상태는 즉시 `USED`로 변경되며 재사용 불가
- 주문 스냅샷에는 쿠폰 적용 전 금액, 할인 금액, 최종 결제 금액 포함

---

## 🧾 주문 ADMIN

| **METHOD** | **URI** | **ldap_required** | **설명** |
| --- | --- | --- | --- |
| GET | `/api-admin/v1/orders?page=0&size=20` | O | 주문 목록 조회 |
| GET | `/api-admin/v1/orders/{orderId}` | O | 단일 주문 상세 조회 |
| GET | `/api-admin/v1/orders/{orderId}` | O | 단일 주문 상세 조회 |

---

## 🎟 쿠폰 (Coupons)

| **METHOD** | **URI** | **member_required** | **설명** |
| --- | --- | --- | --- |
| POST | `/api/v1/coupons/{couponId}/issue` | O | 쿠폰 발급 요청 |
| GET | `/api/v1/users/me/coupons` | O | 내 쿠폰 목록 조회 |

> 쿠폰 목록 조회 시 사용 가능한 쿠폰(`AVAILABLE`) / 사용 완료(`USED`) / 만료(`EXPIRED`) 상태를 함께 반환합니다.

### 🏷 쿠폰 ADMIN

| **METHOD** | **URI** | **ldap_required** | **설명** |
| --- | --- | --- | --- |
| GET | `/api-admin/v1/coupons?page=0&size=20` | O | 쿠폰 템플릿 목록 조회 |
| GET | `/api-admin/v1/coupons/{couponId}` | O | 쿠폰 템플릿 상세 조회 |
| POST | `/api-admin/v1/coupons` | O | 쿠폰 템플릿 등록 (정액/FIXED, 정률/RATE) |
| PUT | `/api-admin/v1/coupons/{couponId}` | O | 쿠폰 템플릿 수정 |
| DELETE | `/api-admin/v1/coupons/{couponId}` | O | 쿠폰 템플릿 삭제 |
| GET | `/api-admin/v1/coupons/{couponId}/issues?page=0&size=20` | O | 특정 쿠폰 발급 내역 조회 |

**쿠폰 템플릿 등록 요청 예시**

```json
{
  "name": "신규가입 10% 할인",
  "type": "RATE",
  "value": 10,
  "minOrderAmount": 10000,
  "expiredAt": "2026-12-31T23:59:59"
}
```

---

## 🚀 Round 4 구현 보강

### Must-Have

- DB 트랜잭션
- Lock
- 동시성 테스트
- 쿠폰 개념

### 주문 정합성 보장 요구

- 주문 시 재고/포인트/쿠폰 정합성을 트랜잭션으로 보장
- Lost Update 방지를 위해 낙관적 락/비관적 락 중 도메인 특성에 맞는 전략 선택
- 쿠폰/재고/포인트 처리 중 하나라도 실패 시 전체 롤백

### 동시성 테스트 요구

- 동일 상품 좋아요/취소 동시 요청 시 likeCount 정합성 보장
- 동일 쿠폰 동시 주문 시 쿠폰 단일 사용 보장
- 동일 상품 동시 주문 시 재고 음수 미발생 및 정상 차감 보장

---

## 🤖 트랜잭션 분석 Skills (문서 과제)

- 트랜잭션 분석 Skill을 작성하고, 구현 기능에 대해 지속적으로 점검/개선합니다.
- 작성 예시 경로: `~/.claude/skills/anaylize-query/SKILL.md`
- 분석 범위: `@Transactional` 선언 지점, Service/Facade/Application, JPA/QueryDSL, 요청 흐름 단위
- 분석 포인트: 트랜잭션 범위 과대 여부, 조회/쓰기 혼합 여부, flush/lazy loading 부작용, 락 경합 가능성

---

## ✍️ Technical Writing Quest

- 이번 주 학습/과제에서 "무엇을"보다 "왜 그렇게 판단했는지" 중심으로 정리합니다.
- 글에는 반드시 1줄 요약(TL;DR)을 포함합니다.
- 반성문/요약문이 아닌, 실제 판단 흐름/트레이드오프/실패와 수정 과정을 담습니다.

## 📡 나아가며

> ⚙️ **모든 기능의 동작을 개발한 후에 동시성, 멱등성, 일관성, 느린 조회, 동시 주문 등 실제 서비스에서 발생하는 문제들을 해결하게 됩니다.**

# 향후 확장 요구사항: 옵션/Variant 시스템

> 현재 구현 범위(14개 테이블)에서 제외된 확장 도메인의 요구사항 방향을 정리한다.
> 본 문서는 방향성만 제시하며, 구현 시 별도 요구사항 명세를 작성한다.

---

## 현재 구현 범위와의 관계

현재 설계에서는 Product 단위로 가격(`base_price`)과 재고(`inventories`)를 관리한다.
Variant 시스템 도입 시, **재고와 가격의 관리 단위가 Product에서 Variant로 이동**한다.

| 영역 | 현재 설계 | Variant 도입 후 |
|------|----------|----------------|
| 가격 | `products.base_price` | `base_price` + `variants.extra_price` |
| 재고 | `inventories.product_id` (1:1) | `inventories.variant_id` (Variant 단위) |
| 주문 항목 | `order_items.product_id` | `order_items.variant_id` + 옵션 스냅샷 |
| 장바구니 | `cart_items.product_id` | `cart_items.variant_id` |

---

## A. Option/Variant 시스템

### 문제 상황

- **사용자 관점**: 동일 상품이라도 사이즈/컬러에 따라 재고와 가격이 다를 수 있음. 현재는 구분 불가
- **비즈니스 관점**: SKU(Stock Keeping Unit) 단위 재고 관리 필요. 옵션별 추가 가격 정책 필요
- **시스템 관점**: Product 1:1 Inventory 구조에서는 옵션별 재고를 관리할 수 없음

### 요구사항 방향

| ID | 기능 | 설명 |
|----|------|------|
| V-01 | 옵션 그룹 관리 | 상품별 옵션 그룹(컬러, 사이즈 등) CRUD |
| V-02 | 옵션 값 관리 | 옵션 그룹 내 값(빨강, 파랑 / S, M, L 등) CRUD |
| V-03 | Variant 생성 | 옵션 값 조합으로 Variant 자동/수동 생성 |
| V-04 | Variant별 가격 | base_price + extra_price 구조. 추가 가격은 Variant에 귀속 |
| V-05 | Variant별 재고 | Inventory가 Variant 단위로 연결 (productId → variantId) |
| V-06 | Variant별 SKU | 각 Variant에 고유 SKU 코드 부여 |
| V-07 | 옵션 없는 상품 호환 | 옵션이 없는 단품은 기본 Variant 1개로 처리 (하위 호환) |

### 미결정 사항

- **옵션 조합 방식**: 모든 조합을 자동 생성 vs 수동 선택
- **재고 마이그레이션**: 기존 Product 단위 재고 → Variant 단위로 전환 시 마이그레이션 전략
- **옵션 없는 상품**: 기본 Variant를 명시적으로 생성할지, 또는 Variant 없이도 동작하도록 분기할지

---

## B. 참고: 이전 future 문서에서 이관된 항목

아래 도메인들은 현재 구현 범위(Round 3)에 포함되어 메인 설계 문서로 이관되었다.

| 도메인 | 이관된 설계 문서 |
|--------|----------------|
| 결제 (Payment) | `01-requirements.md` 시나리오 C, `02-sequence-diagrams.md` #2~#4 |
| 장바구니 (Cart) | `01-requirements.md` 시나리오 B, `02-sequence-diagrams.md` #7 |
| 쿠폰 (Coupon) | `01-requirements.md` 시나리오 D/H, `02-sequence-diagrams.md` #8 |
| 포인트 (Point) | `01-requirements.md` 시나리오 D |
| 주문서 (OrderSheet) | Order로 통합 (`00-design-decisions.md` Q-C2) |
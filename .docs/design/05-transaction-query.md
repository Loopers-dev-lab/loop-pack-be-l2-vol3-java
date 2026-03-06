# 트랜잭션·락·쿠폰 오케스트레이션 통합 설계

> **본 문서가 단일 기준(Source of Truth)** 이다. 이전 05-transaction-query-analysis, 06-transaction-lock-strategy의 내용을 중복 제거 후 통합했으며, 해당 문서는 삭제되었다. **충돌 시 본 문서(07) 기준**으로 한다.  
> 쿠폰 도메인 설계, 배치 없는 만료, OrderFacade 시퀀스, 혼합 락(쿠폰 낙관적 + 재고 비관적), 트랜잭션 경계·개선안·추가 반영 상황을 한 곳에서 참조한다. 요구사항: [01-requirements.md](./01-requirements.md) §3.8 쿠폰.

---

## 1. 배치 없는 '만료(EXPIRED)' 상태 처리 전략

배치를 당장 사용하지 않는다면, DB에 `status = EXPIRED`를 물리적으로 업데이트하는 대신 **조회 시점에 동적으로 판별**하는 로직을 도메인에 둔다.

### 1.1 도메인 로직 보완

- **IssuedCouponModel** 내부에 **현재 시간 기준 실질 상태**를 반환하는 메서드를 둔다.
- DB 컬럼 `status`는 `AVAILABLE` | `USED` 등만 저장하고, 만료는 `expired_at`과 비교해 판별한다.

```java
/**
 * 현재 시점 기준 실질 상태를 반환한다.
 * 배치로 EXPIRED를 물리 갱신하지 않으므로, 조회/사용 시점에 동적 판별.
 */
public IssuedCouponStatus getActualStatus(LocalDateTime now) {
    if (this.status == IssuedCouponStatus.USED) return IssuedCouponStatus.USED;
    if (this.expiredAt != null && this.expiredAt.isBefore(now)) return IssuedCouponStatus.EXPIRED;
    return IssuedCouponStatus.AVAILABLE;
}
```

### 1.2 조회 시 필터링

- **목록/사용 가능 쿠폰 조회** 시 QueryDSL/JPA 조건으로 실질적으로 사용 가능한 것만 필터링한다.
- 예: `WHERE expired_at > :now AND status = 'AVAILABLE'` (및 소유자 일치 등).

---

## 2. 재고/주문/쿠폰 오케스트레이션 (OrderFacade)

결제 도메인이 없으므로, **OrderFacade**는 "주문 확정"까지의 모든 책임을 가진다. **쿠폰 도입 후 주문 정합성**은 **doPlaceOrder** 한 트랜잭션에서 `CouponService.validateAndUse` → `ProductService.decreaseStockWithLock` → `OrderService.createOrder` 순으로 호출하며, 하나라도 실패 시 전부 롤백한다. **placeOrder**는 트랜잭션 없이 재시도 루프만 담당하고, 낙관락 충돌 시 백오프 후 1회 재시도(§3.4). CouponFacade는 주문 플로우에 개입하지 않고, 쿠폰 발급/조회/어드민 CRUD 진입점만 담당한다.

### 2.1 추천 실행 순서 (Sequence)

1. **쿠폰 락/조회**
   - 쿠폰 적용 시: **낙관적 락** 채택 시에는 일반 조회(`findById`). 비관적 락 채택 시에는 `IssuedCouponRepository.findByIdForUpdate(issuedCouponId)`.
   - 다른 트랜잭션이 동일 쿠폰을 먼저 사용하는 것을, 낙관적 락이면 커밋 시점에 감지하고, 비관적 락이면 이 시점에 차단한다.
   - **낙관락 충돌 시**: `OptimisticLockException` 발생 → placeOrder에서 백오프(50ms) 후 **1회 재시도**(새 트랜잭션으로 doPlaceOrder 재호출). 재시도 후에도 실패하면 **409 CONFLICT** + "잠시 후 다시 시도해 주세요".
2. **쿠폰 유효성 검증**
   - 소유주 확인, 사용 여부(`status == USED`), **현재 시간 기준 만료 여부**(`getActualStatus(now)` 또는 `expired_at > now`) 체크.
3. **재고 차감**
   - `ProductService.decreaseStockWithLock(items)`.
   - 상품 ID **오름차순**으로 락을 잡아 데드락을 방지한다.
4. **금액 계산 및 스냅샷 생성**
   - 할인액 계산(최소 주문 금액, 정액/정률, 상한 적용) 후 `Order` 엔티티 생성.
5. **쿠폰 상태 변경**
   - `status = USED`, `usedAt = now`. (낙관적 락 시 커밋 시점에 버전 검사.)

### 2.2 트랜잭션 경계 요약 (코드 기준)

- **시작 지점**: 주문 접수는 **placeOrder**에 `@Transactional`이 없고, **doPlaceOrder**에만 `@Transactional`이 있다. placeOrder는 재시도 루프만 담당하며 `self.doPlaceOrder()`로 호출해 **매 시도마다 새 트랜잭션**이 열린다. 낙관락 충돌 시 백오프(50ms) 후 1회 재시도, 재시도 후에도 실패하면 409 CONFLICT + "잠시 후 다시 시도해 주세요"(§3.4, §9.1). 그 외 Facade 메서드(create/cancel 등)는 해당 Facade 메서드에 `@Transactional`. Service는 REQUIRED로 동일 트랜잭션 참여. Controller에는 `@Transactional` 없음.
- **주문 생성**: OrderFacade.placeOrder() → self.doPlaceOrder() (트랜잭션 시작) → validateAndGetSnapshots(검증·스냅샷) + (쿠폰 시) validateAndUse + decreaseStockWithLock + OrderService.create() → orderRepository.save(). 결제·재고 차감은 주문 생성과 분리하지 않음(동일 트랜잭션).
- **주문 취소**: OrderFacade.cancel() → OrderService.cancel() → (PAID 시) restoreStock(productId 오름차순 락) + order.cancel() + save().
- **상품 목록**: getProductList readOnly, brandId 일괄 조회 후 맵 매핑(N+1 제거 반영).
- **브랜드 삭제**: BrandFacade.delete() → BrandService.delete() → softDeleteByBrandIdBulk(벌크 UPDATE) + brand.delete() + save().

---

## 3. 채택 전략: 혼합 락 (쿠폰 낙관적 + 재고 비관적)

**쿠폰**에는 비관적 락을 걸지 않고 **낙관적 락**만 사용하고, **재고**는 **비관적 락(오름차순)**을 유지한다.

### 3.1 쿠폰 — 낙관적 락

| 항목 | 내용 |
|------|------|
| **구현** | `IssuedCoupon` 엔티티에 `@Version` 필드 추가. 조회 시 일반 `SELECT`(비관적 락 `FOR UPDATE` 미사용). |
| **흐름** | 쿠폰 조회 → 검증 → 재고 차감(비관적 락) → 주문 생성 → 쿠폰 `status=USED` 갱신 → **커밋 시** JPA가 버전 비교. |
| **충돌 시** | 조회 시점과 수정 시점 사이에 다른 트랜잭션이 해당 쿠폰을 사용했으면 `OptimisticLockException` → **전체 롤백**(재고 차감 포함). |
| **효과** | 쿠폰 행에 비관적 락을 걸지 않아 **락 점유 시간 단축**, TPS 개선. |

### 3.2 재고 — 비관적 락 (오름차순)

| 항목 | 내용 |
|------|------|
| **구현** | 주문 항목의 상품 ID를 **오름차순 정렬** 후, 해당 순서로 `ProductRepository.findByIdForUpdate(productId)` 호출. |
| **효과** | 데드락 원천 차단, 재고 수량 정확성 유지. |

### 3.3 데드락·안전성

- **락 대상**: 쿠폰은 락 없이 조회만 하므로, **실제 락은 Product만** 걸린다.
- **순서**: "쿠폰(조회) → 상품(정렬 후 락)"으로 통일.
- **재고 정합성**: 비관적 락으로 초과 판매(Overselling) 방지.

### 3.4 트레이드오프

| 구분 | 내용 |
|------|------|
| **롤백 비용** | 쿠폰 동시 사용 시 낙관적 락 실패 → 전체 롤백. 쿠폰 광클 확률이 낮다고 판단하고 감수. |
| **재시도** | `OptimisticLockException`(또는 `ObjectOptimisticLockingFailureException`) 포착 후 **1회만 재시도**. 재시도 전 **백오프(50ms)**. 재시도 후에도 실패하면 **409 CONFLICT** + "잠시 후 다시 시도해 주세요". placeOrder는 트랜잭션 없이 재시도 루프만 담당하고, self.doPlaceOrder()로 매 시도 새 트랜잭션 수행. 쿠폰 미사용 주문은 재시도 없이 1회만 시도. |

### 3.5 DB 커넥션 유지 시간 (Connection Starvation)

| 항목 | 내용 |
|------|------|
| **문제** | 실행 순서가 **쿠폰 조회(No Lock) → 재고 락(FOR UPDATE)** 이다. 인기 상품에서 재고 락 대기 시 **트랜잭션이 이미 커넥션을 점유한 채** 대기. |
| **트레이드오프** | 재고 락 대기 시간이 길어지면 성능 이득 상쇄, **커넥션 풀 고갈** 위험. |
| **해결책** | **Lock Timeout을 짧게**(예: 3~5초) 설정해 빨리 실패시키는 전략 **필수 병행**. 타임아웃 시 롤백 → 503/409 → "잠시 후 다시 시도해 주세요" 유도. |

---

## 4. 동시성 해결 우선순위 및 예외 상황 전략

### 4.1 해결 방법 우선순위 (멘토 조언)

| 순위 | 방법 | 적합 상황 | 비고 |
|------|------|-----------|------|
| 1 | **원자적 업데이트** | 단순 증감, `UPDATE SET count = count ± n` 등 DB 단일 문으로 처리 가능한 경우 | 부하 최소. Read-Modify-Write에서는 락 필요. |
| 2 | **낙관적 락** | 충돌이 적을 때, `@Version`으로 충돌 감지 후 재시도 가능한 경우 | 충돌 시 재시도 비용. |
| 3 | **비관적 락** | 충돌이 잦거나, 반드시 성공해야 하는 비즈니스(결제, 재고, 쿠폰 1회 사용) | `SELECT ... FOR UPDATE`. 초과 판매·더블 클레임 방지. |

### 4.2 예외 상황별 전략

- **재고: 품절 직전 경합(Overselling)** — **비관적 락**. 재고 차감·복구 시 `findByIdForUpdate` + 갱신.
- **재고: 결제 대기 중 점유(Ghosting)** — 현재는 재고 차감을 결제 완료 시점에 수행하므로 주문 생성 시 재고가 잡히지 않음. 결제 연동 시 PENDING_PAYMENT + expired_at + 배치 검토.
- **쿠폰: 더블 클레임** — **DB Unique Index (user_id, coupon_id)**. 선착순 상한 시 발급 수 증가에 비관적 락.
- **쿠폰: 복구와 트랜잭션 불일치** — **단일 트랜잭션**(OrderFacade). 쿠폰 사용·재고 차감·주문 저장을 한 트랜잭션에서 수행. 분산 DB 시 TCC 등 검토.
- **쿠폰: 동일 발급 쿠폰 동시 사용** — **본 문서 §3** (낙관적 락 채택). 비관적 락 대안 가능.
- **주문: Ghost Orders / 결제 콜백 중복** — 주문 상태 머신 + 멱등성. 낙관적 락(`@Version`)으로 상태 전이 안전성. 결제 도메인 연동 시 검토.
- **주문: 분산 트랜잭션 병목** — 단일 DB 모놀리식이면 단일 트랜잭션으로 충분. 분산 확장 시 Outbox/이벤트 테이블 검토.

---

## 5. 데드락 방지 및 설계 원칙

- **락 순서 통일:** 여러 자원(상품·옵션 등)에 락을 걸 때 **ID 순서로 정렬**하여 순서 통일.
- **에그리것 중심:** 부모(에그리것 루트) 쪽에서 락을 거는 습관.
- **외래키 지양:** 실무에서 성능·데드락 이슈로 DB FK를 쓰지 않는 경우 많음. 참조 정합성은 애플리케이션/배치로 보장 검토.

---

## 6. 도메인별 설계 요약

| 도메인 | 전략 | 근거 |
|--------|------|------|
| **재고** | **비관적 락**. 테이블 분리 검토 가능. | 0 이하 불가, Read-Modify-Write. |
| **좋아요** | **Unique 제약 + 단일 트랜잭션**. 카운트는 원자적 UPDATE 적합. | (user_id, product_id) 유니크. |
| **쿠폰 발급** | **Unique Index (user_id, coupon_id)**. 선착순 상한 시 **비관적 락**. | 더블 클레임 방지. |
| **쿠폰 사용** | **낙관적 락** 채택. (비관적 대안 가능) | 본 문서 §3. |
| **쿠폰 만료** | **사용 시점에 만료 여부 판단**. 스케줄러로 EXPIRED 갱신하지 않음. | 01 §3.8. |
| **주문** | **단일 트랜잭션**(OrderFacade.doPlaceOrder) 안에서 쿠폰 검증·사용 → 재고 차감(비관적 락) → 주문 생성. placeOrder는 재시도 오케스트레이션만. | 본 문서 §2, §3. |

**조언:** 재고는 비관적 락 검토. 좋아요는 원자적 업데이트 적합; 트래픽 극심 시 배치로 최종 일관성 검토. 쿠폰 만료는 사용 시점에 현재 시간과 만료 시간 비교.

---

## 7. 대안: 쿠폰 원자적 연산 + 재고만 락 + 보상

한 트랜잭션에서 **쿠폰·재고 두 공유 자원에 동시에 락을 걸지 않는다**는 원칙.

| 관점 | 채택 방식(§3) | 본 대안 |
|------|----------------|---------|
| **락** | 쿠폰 낙관적(조회만) + 재고 비관적 | 재고만 락. 쿠폰은 트랜잭션 밖에서 조건부 단일 UPDATE. |
| **데드락** | 쿠폰↔상품 순서 통일로 완화 | 쿠폰 락 없어 데드락 가능성 축소. |
| **락 유지 시간** | 재고 N건 락 + 차감 + 주문 insert | 동일. |

**플로우:** (1) 쿠폰 조건부 단일 UPDATE로 USED 처리(affected=1 성공, 0 실패). (2) 새 트랜잭션에서 상품 ID 순 `findByIdForUpdate` → 재고 차감 → 주문·주문항목 insert. (3) 실패 시 쿠폰 AVAILABLE로 보상(멱등 설계). **고려:** 보상 신뢰성, "취소 시 쿠폰 복구 안 함"과 구분해 "주문 생성 실패 시에만" 보상 적용.

---

## 8. 트랜잭션·쿼리 분석 요약

### 8.1 불필요하게 큰 트랜잭션 식별

| 항목 | 상태 | 비고 |
|------|------|------|
| Controller에 @Transactional | ✅ 없음 | §2.2와 일치. 트랜잭션은 Facade(또는 주문 접수 시 doPlaceOrder)만 보유. |
| 주문 접수 트랜잭션 경계 | ✅ 반영됨 | placeOrder는 트랜잭션 없음(재시도 루프). doPlaceOrder에만 @Transactional, self 주입으로 매 시도 새 트랜잭션. |
| 읽기 전용 로직이 쓰기 트랜잭션에 포함 | ⚠️ 일부 | validateAndGetSnapshots는 doPlaceOrder 쓰기 트랜잭션에 참여. **의도적 설계**: 스냅샷과 재고 차감·주문 생성을 동일 트랜잭션에서 처리해 정합성 확보(스냅샷 조회와 재고/주문이 분리되면 중간에 재고가 바뀔 수 있음). 재고 차감 없음·검증·스냅샷만 수행한다는 의도는 Javadoc으로 명시(§8.3 개선안 4). |
| 외부 시스템 호출이 트랜잭션 내부에 포함 | ✅ 없음 | 결제/외부 시스템 호출 없음. 재고 차감은 주문 생성과 동일 트랜잭션 내부(§2.2). |
| 트랜잭션 내 대량 조회/복잡 QueryDSL | ⚠️ 일부 | **현재 규모에서는 반영됨**: 브랜드 삭제는 벌크 UPDATE, 상품 목록은 brandId 일괄 조회로 N+1 제거(§8.3 개선안 1, 3). 대량(상품/주문 항목 매우 많을 때) 시에만 청크/배치 추가 검토(§9.1). |
| 상태 변경 후 트랜잭션 장시간 유지 | ⚠️ 일부 | **의도적 설계**: 주문 취소 시 재고 복구(restoreStock)는 정합성 우선으로 **단일 트랜잭션** 유지(복구 실패 시 주문만 취소되는 불일치 방지). 항목이 매우 많을 때(예: 10건 초과)에만 청크/별도 트랜잭션+보상 검토(§8.3 개선안 2, §9.1). |

**문제 후보 반영 현황:** OrderService.create(의도 Javadoc 명시), BrandService.delete(벌크 UPDATE), OrderService.cancel(productId 오름차순 락), ProductFacade.getProductList(brandId 일괄 조회).

### 8.2 JPA / 영속성 컨텍스트

| 체크 항목 | 상태 | 비고 |
|-----------|------|------|
| 단순 조회인데 Entity 반환 후 변경 가능성 | ✅ 양호 | Facade에서 Model → Info/DTO 변환, Controller는 DTO만 노출. |
| DTO Projection 대신 Entity 조회 | ⚠️ 전반적 | 읽기 전용 API는 현재 Entity 조회 후 DTO 변환. **선택적 개선**: 트래픽·성능 이슈 시 DTO Projection 검토 여지 있음. |
| @Transactional(readOnly = true) 적용 | ✅ 조회 메서드에 적용 | findById, findOrders, getProductDetail, getProductList 등. |

**readOnly:** 단순 조회는 readOnly 적용됨. validateAndGetSnapshots·validateProductAvailability는 호출처가 쓰기 트랜잭션이면 readOnly 미적용 → Javadoc으로 의도 명시. **기타:** OrderService.cancel에서 order.getOrderItems() 지연 로딩 가능성 → findByIdWithOrderItems 등 연관 로딩 권장. restoreStock은 findByIdForUpdate 후 save로 변경 감지 이슈 없음.

### 8.3 Improvement Proposal (선택)

- **[개선안 1] BrandService.delete** — 벌크 UPDATE(softDeleteByBrandIdBulk) 반영됨. 대량 시 청크/배치 분할 검토. 트레이드오프: 벌크 직후 해당 엔티티 조회 시 stale 가능 → EntityManager.clear() 등 완화.
- **[개선안 2] OrderService.cancel** — 정합성 우선으로 **단일 트랜잭션** 유지, productId 오름차순 락. 항목 수가 많을 때(예: 10건 초과)는 재고 복구 청크/별도 트랜잭션+보상 검토.
- **[개선안 3] ProductFacade.getProductList** — brandId 일괄 조회 후 맵 매핑으로 N+1 제거 반영.
- **[개선안 4] readOnly 의도** — validateAndGetSnapshots에서 readOnly 제거, Javadoc으로 "검증·스냅샷만, 재고 차감 없음" 명시.

---

## 9. 추가 반영 상황 및 실행 우선순위

### 9.1 추가로 반영해야 할 상황

| # | 상황 | 권장 대응 |
|---|------|-----------|
| 1 | **락 타임아웃·데드락** | DB 락 대기 타임아웃(3~5초) 짧게 설정. **낙관락 충돌 시** 재시도 최대 1회(백오프 50ms), 실패 시 **409 CONFLICT** 및 "잠시 후 다시 시도해 주세요". (구현 반영됨.) 락 타임아웃 시 503 등 검토. |
| 1-1 | **Connection Starvation** | 쿠폰 조회 → 재고 락 순서에서 재고 락 대기 시 커넥션 점유. **Lock Timeout 짧게 설정 필수 병행.** (§3.5) |
| 2 | **주문 취소 시 재고 복구** | 단일 트랜잭션 유지. 항목 많을 때 청크/보상 검토. |
| 3 | **브랜드 삭제 연쇄** | 벌크 반영됨. 상품 수 많으면 청크/배치 검토. |
| 4 | **상품 목록 N+1** | brandId 일괄 조회 반영됨. |
| 5 | **readOnly 의도** | Javadoc 명시 반영. |
| 6 | **할인 경계값** | 정액 초과 시 할인액 = min(value, 할인 전 금액). 정률 0~100, 반올림/절사 통일. minOrderAmount 정책 결정. |
| 7 | **주문 취소 시 쿠폰** | 취소해도 쿠폰 USED 유지, 복구하지 않음. |
| 8 | **한 트랜잭션에 두 공유 자원 락 회피** | §7 대안(쿠폰 원자적 UPDATE + 재고만 락 + 보상) 검토. |

### 9.2 실행 시 우선순위(권장)

| 순위 | 항목 |
|------|------|
| 1 | 락 순서(§2.1)·타임아웃·재시도 정책 반영. |
| 2 | 주문 취소 시 재고 복구 락 유지 시간 단축 옵션 검토. |
| 3 | 쿼리·트랜잭션 개선(브랜드 삭제 청크, readOnly 의도 등). |

---

## 10. 누락 방지 엣지 케이스 체크리스트

| 상황 | 기대 결과 | 비고 |
|------|-----------|------|
| **쿠폰 최소 주문 금액 미달** | 주문 실패 (400 등) | `minOrderAmount`와 상품 총액 비교. |
| **정률 할인 시 소수점 처리** | 원 단위 **절사(Floor)** 또는 반올림 규칙 통일 | 예: 10.5원 → 10원. |
| **상품 총액보다 할인액이 큰 경우** | 최종 결제 금액 **0원** (마이너스 불가) | `finalAmount = max(0, orderAmount - discountAmount)`. |

---

## 11. 요약

- **재고**: 0 이하 불가, Read-Modify-Write → **비관적 락**. 차감·복구 시 productId 오름차순 락.
- **쿠폰 발급**: **(user_id, coupon_id) Unique Index**. 선착순 상한 시 비관적 락.
- **쿠폰 사용**: **낙관적 락** 채택(§3). 충돌 시 placeOrder에서 1회 재시도(백오프 50ms), 실패 시 409. 비관적 락 대안 가능.
- **주문**: OrderFacade **placeOrder**는 트랜잭션 없이 재시도만, **doPlaceOrder** 단일 트랜잭션으로 쿠폰 검증·사용 → 재고 차감 → 주문 생성. 정합성은 롤백으로 보장.
- **좋아요**: Unique + 단일 트랜잭션; 원자적 업데이트 적합.
- **비관적 락 필수**: 재고 차감/복구. 쿠폰 사용은 낙관적 락 채택.
- **대안(§7)**: 쿠폰 원자적 UPDATE + 재고만 락 + 보상 트랜잭션 검토 가능.

---

## 12. 동시성 테스트 가이드

동시성 문제는 테스트 없이 눈으로 발견하기 어렵다. 실제 운영 전에 아래 기준으로 동시성 테스트를 구성한다.

### 12.1 목표

- 동시에 여러 요청이 들어올 때 **정합성이 깨지지 않는지** 확인
- **비관적 락** 또는 **낙관적 락** 전략이 정상 동작하는지 검증
- 예외 상황에서 **전체 트랜잭션이 롤백**되는지 확인

### 12.2 작성 방법

- **도구**: `CountDownLatch` + `ExecutorService` 또는 `CompletableFuture`로 다수 스레드 동시 실행
- **대상**: `@Transactional` 및 락 전략이 적용된 Facade/Service
- **검증**: 성공한 요청 수, 실패한 요청 수, **DB 최종 상태**를 모두 assert

### 12.3 체크리스트 (구현·검증 대상)

| # | 시나리오 | 기대 결과 |
|---|----------|-----------|
| 1 | **좋아요** — 동일 상품에 여러 사용자가 좋아요/취소 | 상품의 좋아요 수가 정상 반영 (Unique 제약 + 단일 트랜잭션). |
| 2 | **쿠폰** — 동일 발급 쿠폰으로 여러 기기에서 동시 주문 | 쿠폰은 **단 한 번만** 사용됨 (비관적 락 또는 낙관적 락 검증). |
| 3 | **재고** — 동일 상품에 대해 여러 주문 동시 요청 | 재고가 정상 차감, **0 미만으로 내려가지 않음** (비관적 락 오름차순). |

### 12.4 Java + JUnit 5 예시

```java
@DisplayName("동시에 주문해도 재고가 정상적으로 차감된다.")
@Test
void concurrency_stockDecreasedCorrectlyWhenConcurrentOrders() throws InterruptedException {
    int threadCount = 10;
    int initialStock = 5;
    ExecutorService executor = Executors.newFixedThreadPool(threadCount);
    CountDownLatch start = new CountDownLatch(1);
    CountDownLatch done = new CountDownLatch(threadCount);
    List<OrderInfo> successes = Collections.synchronizedList(new ArrayList<>());
    AtomicInteger failures = new AtomicInteger(0);

    for (int i = 0; i < threadCount; i++) {
        executor.submit(() -> {
            try {
                start.await();
                OrderInfo info = orderFacade.placeOrder(userId, List.of(new CreateOrderItemParam(productId, 1, null)), null);
                successes.add(info);
            } catch (Exception e) {
                failures.incrementAndGet();
            } finally {
                done.countDown();
            }
        });
    }
    start.countDown();
    done.await();
    executor.shutdown();

    assertThat(successes).hasSize(initialStock);
    assertThat(failures.get()).isEqualTo(threadCount - initialStock);
    ProductModel product = productRepository.findById(productId).orElseThrow();
    assertThat(product.getStockQuantity()).isEqualTo(0);
}
```

---

## 13. 문서 간 참조

- **본 문서**: `.docs/design/05-transaction-query.md` — 트랜잭션·락·쿠폰 오케스트레이션 및 동시성 테스트 가이드.
- **요구사항**: [01-requirements.md](./01-requirements.md) §3.8 쿠폰.
- **시퀀스·클래스**: 02-sequence-diagrams, 03-class-diagram 주문·쿠폰 플로우.

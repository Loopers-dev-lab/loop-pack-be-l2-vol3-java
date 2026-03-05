---
name: transaction-rules
description: "트랜잭션 관리 규칙, @Transactional 배치 전략, 동시성 제어 Lock 패턴. 트랜잭션이나 동시성 관련 코드를 구현할 때 활성화한다."
---

이 스킬은 프로젝트의 트랜잭션 관리 규칙과 동시성 제어 패턴을 정의한다.

## 1. @Transactional 배치 원칙

### Facade 레벨

| 조건 | @Transactional | 이유 |
|------|---------------|------|
| 2개 이상 Service 쓰기 조합 | **필수** | 원자적 롤백 보장 |
| 단일 Service 위임 (쓰기) | **불필요** (제거) | Service에 이미 선언됨, REQUIRED 전파로 중복 |
| 다중 Service 읽기 조합 | **@Transactional(readOnly=true)** | 일관된 스냅샷 + 성능(flush 생략) |
| 단일 Service 읽기 위임 | **불필요** | Service에 readOnly 선언됨 |

### Service 레벨

| 조건 | @Transactional | 이유 |
|------|---------------|------|
| 쓰기 메서드 (save 호출) | **@Transactional** | 명시적 트랜잭션 경계 |
| 읽기 메서드 (조회만) | **@Transactional(readOnly=true)** | Hibernate flush 생략, 리드 레플리카 라우팅 |

### 전파(Propagation) 규칙

- 기본 전파: `REQUIRED` (Spring 기본값)
- Facade @Transactional + Service @Transactional = **같은 트랜잭션에 참여**
- 단일 Service 위임 Facade의 @Transactional은 사실상 무의미 (제거 대상)

## 2. Entity-level DIP에서의 트랜잭션 특성

### Dirty Checking 불가 — 명시적 save() 필수

```java
// 이 프로젝트의 패턴: 항상 명시적 save()
@Transactional
public Product update(Long id, String name, String desc, int price) {
    Product product = getById(id);       // 비관리 POJO 반환
    product.update(name, desc, price);   // POJO 수정 (영속성 컨텍스트 무관)
    return productRepository.save(product); // 명시적 save 필수
}
```

**이유**: Mapper.toEntity()가 항상 새 JPA Entity를 생성하므로, Hibernate가 변경을 감지할 수 없다.

### 1차 캐시 부분 활용

같은 트랜잭션 내에서 동일 ID를 재조회하면 1차 캐시에서 반환된다.
단, toDomain()으로 변환하므로 도메인 레벨에서 객체 동일성(identity)은 보장되지 않는다.

## 3. 동시성 제어 Lock 전략

### 도메인별 Lock 현황

| 도메인 | 전략 | 구현 | 선택 근거 |
|--------|------|------|-----------|
| **재고 (Inventory)** | 비관적 락 | `findByProductIdForUpdate` — `PESSIMISTIC_WRITE` | 충돌 빈도 높음, 정확한 재고 보장 필수 |
| **포인트 (PointAccount)** | 비관적 락 | `findByUserIdForUpdate` — `PESSIMISTIC_WRITE` | 잔액 정합성 필수, 동일 사용자 동시 요청 가능 |
| **쿠폰 사용 (IssuedCoupon)** | 낙관적 락 | `@Version` 필드 | 1장 = 1명, 충돌 확률 낮음 |
| **쿠폰 발급 (CouponTemplate)** | 비관적 락 | `findByIdForUpdate` — `PESSIMISTIC_WRITE` | count 기반 검증, 읽기-검증-쓰기 갭 방지 |
| **좋아요 (Product.likeCount)** | 원자적 UPDATE | `@Modifying @Query SET likeCount = likeCount + 1` | 충돌 빈도 높음, Lock 오버헤드 불필요 |

### Lock 전략 선택 기준

```
충돌 빈도 높음 + 정합성 필수 → 비관적 락 (재고, 포인트)
충돌 빈도 낮음 + 재시도 가능 → 낙관적 락 (쿠폰 사용)
충돌 빈도 높음 + 단순 증감   → 원자적 UPDATE (좋아요)
읽기-검증-쓰기 갭 존재       → 비관적 락 (쿠폰 발급)
```

### 비관적 락 구현 패턴

```java
// Repository Interface (도메인 레이어)
Optional<Inventory> findByProductIdForUpdate(Long productId);

// JPA Repository (인프라 레이어)
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT i FROM InventoryEntity i WHERE i.productId = :productId")
Optional<InventoryEntity> findByProductIdForUpdate(@Param("productId") Long productId);

// Service (도메인 레이어)
@Transactional
public void reserveAll(Map<Long, Integer> productQtyMap) {
    for (var entry : productQtyMap.entrySet()) {
        Inventory inventory = inventoryRepository.findByProductIdForUpdate(entry.getKey())
                .orElseThrow(...);
        inventory.reserve(entry.getValue());
        inventoryRepository.save(inventory);  // 명시적 save 필수
    }
}
```

### 낙관적 락 구현 패턴 (Entity-level DIP에서)

```java
// Domain POJO — version 필드 포함
public class IssuedCoupon {
    private Long version;  // JPA @Version 지원을 위한 필드

    public static IssuedCoupon reconstitute(..., Long version) {
        coupon.version = version;
        return coupon;
    }
}

// JPA Entity — @Version 선언
@Version
@Column(name = "version", nullable = false)
private Long version = 0L;

// Mapper — version 양방향 매핑
public IssuedCouponEntity toEntity(IssuedCoupon domain) {
    entity.setVersion(domain.getVersion() != null ? domain.getVersion() : 0L);
    return entity;
}

public IssuedCoupon toDomain(IssuedCouponEntity entity) {
    return IssuedCoupon.reconstitute(..., entity.getVersion());
}
```

**주의**: 도메인 POJO에 version을 포함하는 것은 기술적 오염이지만, Entity-level DIP에서 낙관적 락을 동작시키기 위한 현실적 타협이다.

### 원자적 UPDATE 패턴

```java
// JPA Repository
@Modifying
@Query("UPDATE ProductEntity p SET p.likeCount = p.likeCount + 1, p.updatedAt = CURRENT_TIMESTAMP WHERE p.id = :id")
int incrementLikeCount(@Param("id") Long id);

// Domain Service — read-modify-write 대신 직접 위임
@Transactional
public void incrementLikeCount(Long id) {
    productRepository.incrementLikeCount(id);
}
```

## 4. 트랜잭션 범위 설계 원칙

### 짧은 트랜잭션 선호

- 트랜잭션이 길어지면 DB 커넥션 점유 시간 증가
- Lock 보유 시간도 증가 → 동시성 처리량 감소
- 현재 OrderFacade.createOrder()는 단일 TX로 12개 Service 호출 (가장 긴 트랜잭션)

### Lock 순서 고정 (데드락 방지)

주문 생성 시 Lock 획득 순서:
```
1. Inventory (비관적 락) — 재고 예약
2. IssuedCoupon (낙관적 락) — 쿠폰 사용
3. PointAccount (비관적 락) — 포인트 차감
```

동일한 순서를 모든 코드 경로에서 유지해야 데드락을 방지할 수 있다.

## 5. readOnly=true 적용 기준

```java
// 다중 서비스 읽기 조합 → Facade에 readOnly=true 필요
@Transactional(readOnly = true)
public ProductDetailResult getProductDetail(Long productId) {
    Product product = productService.getDisplayableProduct(productId);
    Brand brand = brandService.getActiveBrand(product.getBrandId());
    return new ProductDetailResult(product, brand);
}

// 단일 서비스 읽기 위임 → Facade TX 불필요 (Service에 readOnly 있음)
public List<CouponResult> getMyCoupons(Long userId) {
    return couponService.getUserCoupons(userId);  // Service에 @Transactional(readOnly=true)
}
```

### readOnly=true의 효과
1. Hibernate flush 생략 → 성능 향상
2. DB 리드 레플리카 라우팅 가능
3. 의도 표현 — 이 메서드가 데이터를 변경하지 않음을 명시
4. 다중 서비스 읽기 시 일관된 스냅샷 보장

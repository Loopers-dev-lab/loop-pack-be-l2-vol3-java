---
name: project-confirmed-rules
description: "멘토 의견 + 학습 결과로 확정한 프로젝트 전용 규칙. Blue Book 스타일, Facade 판정, 행위 배치, DTO 흐름, 정렬 DIP, 구현 순서, 핵심 도메인 모델. 도메인 코드를 구현하거나 설계를 확인할 때 활성화한다."
---

이 skill은 **발제 개념 + 멘토 의견 + 학습 결과**로 확정한 프로젝트 전용 규칙이다.
기존 `ddd-dev-guidelines`는 일반 DDD 개념이고, 이 skill은 **이 프로젝트에서 확정된 구체 규칙**이다.

---

## 1. 멘토 핵심 원칙 (프로젝트 전체에 적용)

### 앨런 멘토
- **"간단한 규칙, 디테일이 따라온다"** — 규칙을 단순하게 잡고, 구현하면서 디테일을 채운다
- **Application Service = Facade = 오케스트레이터** — 이름만 다르지 같은 역할
- **Facade 없이 Controller → Domain Service 직접 호출 가능** — Service가 1개면 Facade 불필요

### 프랭크 멘토
- **Blue Book 스타일 채택** — Domain Service가 Repository를 직접 호출한다
- **Repository Interface는 Domain Layer에** — DIP의 핵심

### 합의 사항
- Application Service(Facade)는 **도메인 규칙을 판단하지 않는다** — 조율(orchestration)만 한다
- 핵심 비즈니스 로직은 **반드시 Entity, VO, Domain Service**에 위치한다
- `inventory.setReservedQty(...)` 같은 setter 방식은 **도메인 모델링 실패** → `inventory.reserve(qty)` 행위 호출

---

## 2. Blue Book 스타일 — Domain Service가 Repository를 호출한다

```java
// domain/brand/BrandService.java
@Service
public class BrandService {
    private final BrandRepository brandRepository;  // Domain Layer Interface

    @Transactional
    public Brand createBrand(String name, String description) {
        Brand brand = Brand.create(name, description);  // Entity 행위
        return brandRepository.save(brand);               // Repository 직접 호출
    }

    @Transactional
    public Brand deleteBrand(Long brandId) {
        Brand brand = getBrand(brandId);
        brand.delete();  // Entity가 규칙 검증 + 상태 변경
        brandRepository.save(brand);
        return brand;
    }
}
```

**핵심:**
- Domain Service에 `@Service` (또는 `@Component`) 붙인다
- Repository를 Domain Service가 직접 주입받아 사용한다
- Entity 행위 호출 → Repository 저장 패턴

---

## 3. 행위 배치 플로우차트 (확정)

```
Q1: 자기 필드만으로 할 수 있는가?
  → YES: Entity 메서드
  → NO: Q2로

Q2: 같은 도메인(Aggregate) 안의 객체들인가?
  → YES: Domain Service
  → NO: Q3로

Q3: 서로 다른 도메인을 조합하는가?
  → YES: Application Service (Facade)
```

| 로직 | 위치 | 이유 |
|------|------|------|
| `Inventory.reserve(qty)` | Entity | 자기 필드(quantity, reservedQty)만 사용 |
| `Product.incrementLikeCount()` | Entity | 자기 필드(likeCount)만 변경 |
| `Order.confirm(paymentId)` | Entity | 자기 status를 PAID로 변경 |
| `Brand.assertNotDeleted()` | Entity | 자기 불변식(deletedAt) 보호 |
| `UserService.validatePasswordNotContainsBirthDate()` | Domain Service | Password + BirthDate 두 객체 필요 |
| `LikeService.createLike()` | Domain Service | Product 존재 + Like 중복 확인 + likeCount 변경 |
| `InventoryService.reserveAll()` | Domain Service | 여러 Inventory 순회 + 비관적 락 |
| `OrderFacade.createOrder()` | Facade | Address + Product + Inventory + Order 4개 도메인 조율 |
| `ProductFacade.getProductDetail()` | Facade | Product + Brand 2개 도메인 조합 |

---

## 4. Facade 도입 기준 (확정)

### 판단 규칙

| 조건 | 결정 |
|------|------|
| Domain Service **1개만** 호출 | Controller → Domain Service 직접 호출 (Facade 불필요) |
| Domain Service **2개 이상** 조율 | Facade 도입 |
| DTO 변환만 필요 (Info → Response) | Controller에서 직접 변환 |
| DTO 변환 + 여러 Service 조율 | Facade에서 Info 변환 |

### Facade 필요 여부 전체 확정표

| 도메인 | 유스케이스 | Facade | 이유 |
|--------|-----------|--------|------|
| User | 전체 | X | UserService 단독 |
| Brand (고객 조회) | F-01 | X | BrandService 단독 |
| **Brand (어드민 삭제)** | F-29 | **O — BrandAdminFacade** | Product 연쇄 삭제 |
| **Product (고객 조회)** | F-02, F-03 | **O — ProductFacade** | Product + Brand 정보 조합 |
| **Product (어드민 등록)** | F-32 | **O — ProductAdminFacade** | Product + Inventory 동시 생성 |
| **Product (어드민 삭제)** | F-34 | **O — ProductAdminFacade** | Inventory 연쇄 삭제 |
| Like | F-04~F-06 | X | LikeService에서 처리 (실용적 타협) |
| CartItem | F-07~F-10 | X | CartItemService 단독 |
| UserAddress | F-21~F-24 | X | UserAddressService 단독 |
| **Order (생성/취소)** | F-11, F-16 | **O — OrderFacade** | Address + Product + Inventory + Order 조율 |
| **Payment (결제)** | F-13 | **O — PaymentFacade** | Order + Inventory + Point + Coupon + PG |

### Controller 연결 규칙

```java
// Facade가 없는 경우: Controller → Service 직접, Entity → Response 변환은 Controller에서
@GetMapping("/{brandId}")
public ApiResponse<BrandResponse> getBrand(@PathVariable Long brandId) {
    Brand brand = brandService.getActiveBrand(brandId);
    return ApiResponse.success(BrandResponse.from(brand));
}

// Facade가 있는 경우: Controller → Facade, Info → Response 변환은 Controller에서
@GetMapping("/{productId}")
public ApiResponse<ProductResponse.Detail> getProduct(@PathVariable Long productId) {
    ProductDetailInfo info = productFacade.getProductDetail(productId);
    return ApiResponse.success(ProductResponse.Detail.from(info));
}
```

---

## 5. DTO 흐름 규칙 (확정)

```
Request DTO (interfaces) → raw 값으로 Facade/Service에 전달
  → VO 생성은 Domain Layer 내부에서 (Entity 또는 Domain Service)
  → Entity/VO가 도메인 처리
  → Info DTO (application)로 변환
  → Response DTO (interfaces)로 변환
  → ApiResponse 래핑
```

**Request DTO와 Entity는 직접 만나지 않는다.**

---

## 6. 핵심 도메인 모델 상세

### Inventory — 재고 (핵심 도메인)

```java
// Entity 행위 — "비즈니스 규칙이 Entity 안에 있어야 한다"
public void reserve(int qty)    // 주문 생성 시 — 가용 재고 확인 + reservedQty 증가
public void commit(int qty)     // 결제 성공 시 — quantity 감소 + reservedQty 감소
public void release(int qty)    // 주문 취소/만료 시 — reservedQty만 감소
public int getAvailableQty()    // quantity - reservedQty
```

- 비관적 락: `findByProductIdForUpdate()` — 동시성 보장
- `reserve()`에서 `getAvailableQty() < qty` 이면 `INSUFFICIENT_STOCK` 예외

### Order — 주문 (Aggregate Root)

```java
// Order가 Aggregate Root, OrderItem은 같은 Aggregate 내부
// OrderItem은 별도 Repository 없음 — Order를 통해서만 접근
Order.create(userId, orderNumber, items, ...) // 스냅샷 포함, PENDING 상태, 30분 만료
order.confirm(paymentId)    // PENDING → PAID
order.cancel()              // PENDING → CANCELED (PAID에서 취소 불가)
order.expire()              // PENDING → EXPIRED
order.assertPending()       // 불변식 보호
order.assertOwnedBy(userId) // 권한 검증
```

### Product + Brand 조합 — Application Layer에서 처리

```java
// application/product/ProductFacade.java
public ProductDetailInfo getProductDetail(Long productId) {
    Product product = productService.getVisibleProduct(productId);
    Brand brand = brandService.getBrand(product.getBrandId());
    return ProductDetailInfo.of(product, brand);
}

// 목록 조회 시 N+1 방지: brandId batch 조회
public Page<ProductListInfo> getProducts(Long brandId, ProductSortType sort, Pageable pageable) {
    Page<Product> products = productService.getVisibleProducts(brandId, sort, pageable);
    Set<Long> brandIds = products.stream().map(Product::getBrandId).collect(Collectors.toSet());
    Map<Long, Brand> brandMap = brandService.getBrandMap(brandIds);
    return products.map(p -> ProductListInfo.of(p, brandMap.get(p.getBrandId())));
}
```

### 정렬 설계 — DIP 적용

- `ProductSortType` enum은 **Domain Layer** (비즈니스 개념: "최신순", "인기순")
- QueryDSL `OrderSpecifier` 변환은 **Infrastructure Layer** (기술 구현)

```java
// domain/product/ProductSortType.java
public enum ProductSortType { LATEST, PRICE_ASC, LIKES_DESC; }

// infrastructure/product/ProductRepositoryImpl.java
OrderSpecifier<?> orderBy = switch (sort) {
    case LATEST -> product.createdAt.desc();
    case PRICE_ASC -> product.basePrice.asc();
    case LIKES_DESC -> product.likeCount.desc();
};
```

---

## 7. 구현 순서 (확정)

```
Phase 1: Brand (가장 단순 — CRUD + 상태 관리)
Phase 2: Product + Inventory (상품 + 재고는 같이)
Phase 3: Like (Product 의존, 단독 Aggregate)
Phase 4: CartItem (단순 CRUD + merge)
Phase 5: UserAddress (단순 CRUD + 기본주소)
Phase 6: Order (핵심 — 재고 예약 + 스냅샷 + 만료)
Phase 7: Payment + Point + Coupon (가장 복잡한 조율)
```

### 각 Phase 반복 패턴 (Inside-Out)

1. Entity + VO 만들기 (도메인 규칙 + 행위)
2. Entity 단위 테스트 (순수 JVM, Spring 없이)
3. Repository Interface 정의 (Domain Layer)
4. Domain Service 만들기
5. Domain Service 단위 테스트 (Mockito)
6. Infrastructure 구현 (Repository 구현체 + JPA)
7. Facade 만들기 (필요한 경우만)
8. Facade 통합 테스트 (필요한 경우만, @SpringBootTest + Testcontainers)
9. Controller + Request/Response DTO 만들기
10. E2E 테스트 (MockMvc)

---

## 8. 테스트 전략 (확정)

| 레벨 | 대상 | 환경 | 테스트 더블 | 검증 |
|------|------|------|-----------|------|
| **단위** | Entity, VO | 순수 JVM | 없음 | 상태 전이, 도메인 규칙, 불변식 |
| **단위** | Domain Service | JVM + Mockito | Stub + Mock | 흐름 제어, 예외 분기, 호출 검증 |
| **통합** | Facade, Repository | @SpringBootTest + Testcontainers | 없음 (실제 Bean) | 레이어 연결, 비즈니스 흐름 |
| **E2E** | Controller | @SpringBootTest + MockMvc | 없음 | HTTP 상태코드, 응답 구조 |

### Entity 테스트 핵심

```java
// 순수 Java — Spring 없이, new로 직접 생성
class InventoryTest {
    @Test void 가용재고가_충분하면_예약에_성공한다() {
        Inventory inventory = Inventory.create(1L, 10);
        inventory.reserve(3);
        assertThat(inventory.getAvailableQty()).isEqualTo(7);
    }
}
```

### Domain Service 테스트 핵심

```java
// Mockito로 Repository 분리
@ExtendWith(MockitoExtension.class)
class LikeServiceTest {
    @Mock private ProductLikeRepository productLikeRepository;
    @Mock private ProductRepository productRepository;
    @InjectMocks private LikeService likeService;

    @Test void 이미_좋아요한_상품에_다시_좋아요하면_예외가_발생한다() {
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(productLikeRepository.existsByUserIdAndProductId(1L, 1L)).thenReturn(true);
        assertThatThrownBy(() -> likeService.createLike(1L, 1L))
            .isInstanceOf(CoreException.class);
        verify(productLikeRepository, never()).save(any());
    }
}
```

---

## 9. 네이밍 규칙 (확정)

| 대상 | 패턴 | 위치 |
|------|------|------|
| Domain Service | `{Domain}Service` | `domain/{domain}/` |
| Facade | `{Domain}Facade` | `application/{domain}/` |
| Admin Facade | `{Domain}AdminFacade` | `application/{domain}/` |
| Application DTO | `{Domain}Info` | `application/{domain}/` |
| Repository Interface | `{Domain}Repository` | `domain/{domain}/` |
| Repository 구현 | `{Domain}RepositoryImpl` | `infrastructure/{domain}/` |
| JPA Interface | `{Domain}JpaRepository` | `infrastructure/{domain}/` |
| Controller (고객) | `{Domain}Controller` | `interfaces/api/{domain}/` |
| Controller (어드민) | `{Domain}AdminController` | `interfaces/api/admin/{domain}/` |
| Request DTO | `{Domain}Request` | `interfaces/api/.../{domain}/` |
| Response DTO | `{Domain}Response` | `interfaces/api/.../{domain}/` |
| Entity 테스트 | `{Domain}Test` | `test/.../domain/{domain}/` |
| Service 테스트 | `{Domain}ServiceTest` | `test/.../domain/{domain}/` |
| Facade 통합 테스트 | `{Domain}FacadeIntegrationTest` | `test/.../application/{domain}/` |
| E2E 테스트 | `{Domain}ApiE2ETest` | `test/.../interfaces/api/` |

---

## 10. 코딩 컨벤션 보충

### Entity 생성
- `static factory method` 필수 (`Brand.create(...)`)
- JPA용 `protected` 기본 생성자 필수
- 초기 상태는 factory method에서 설정 (`status = ACTIVE`)
- setter 금지 — 행위 메서드로 상태 변경

### soft delete / hard delete
- 기본은 soft delete: `BaseEntity.markDeleted()` (deletedAt = now())
- **ProductLike는 예외: hard delete** (BaseEntity 상속 안함, ID + userId + productId + createdAt만)

### 에러 처리
- `CoreException` + `{Domain}ErrorType` 사용
- Entity의 assert 메서드에서 규칙 위반 감지 (`assertNotDeleted()`, `assertPending()`, `assertOwnedBy()`)

### 트랜잭션
- Domain Service: `@Transactional` (읽기는 `readOnly = true`)
- Facade: `@Transactional`로 여러 Service를 하나의 트랜잭션으로 묶음
- Controller에는 `@Transactional` 붙이지 않음

### VO 도입 기준
- 2개 이상 검증 규칙이 있는가?
- 행위(메서드)가 필요한가?
- 여러 Entity에서 재사용되는가?
- 3개 중 하나라도 해당 → VO, 아니면 String/int로 충분

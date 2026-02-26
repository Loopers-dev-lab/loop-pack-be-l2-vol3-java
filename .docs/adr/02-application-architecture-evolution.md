# ADR: 애플리케이션 아키텍처 변천 — Facade에서 UseCase + DomainService까지

STATUS: Accepted
DATE: 2026-02-25

## 목차
- [배경](#배경)
- [변천 과정](#변천-과정)
- [결정](#결정)
- [적용 예시](#적용-예시)
- [Before / After 구조 비교](#before--after-구조-비교)
- [트레이드오프](#트레이드오프)

---

## 배경

### 프로젝트 레이어 구조

```
interfaces → application → domain ← infrastructure
```

ArchUnit이 역방향 의존을 금지하며, 각 레이어는 도메인별로 하위 패키지를 가진다.

### 문제

application 계층의 `@Service` 클래스가 **오케스트레이션, 비즈니스 규칙, 데이터 조합**을 모두 인라인으로 처리하고 있었다. 한 Service가 여러 도메인의 Repository를 직접 의존하면서 도메인 경계가 무너지고, 메서드를 읽었을 때 "무엇을 하는지"보다 "어떻게 하는지"가 먼저 보였다. 동시에 `*Service`라는 이름이 응용 서비스와 도메인 서비스 중 어느 역할인지 혼동을 유발했다.

---

## 변천 과정

### 1단계: Facade + Domain Service (1주 차)

예시 코드를 따라 Facade(`@Component`)를 application 계층에, Service를 domain 계층에 배치했다.

```
interfaces → Facade(@Component) → Service(domain) → Repository
```

| 구분 | 역할 | 위치 |
|------|------|------|
| Facade | 오케스트레이션, DTO 변환, DataIntegrityViolationException 처리 | application/ |
| Service | 비즈니스 규칙 + Repository 호출 | domain/ |

**문제**: Facade와 Service의 역할 경계가 모호했다. 둘 다 "서비스"처럼 동작하면서 어디에 로직을 둬야 하는지 매번 고민이 필요했다. 설계 시 계층 간 역할이 클래스명 때문에 헷갈렸다.

---

### 2단계: Application Service 단순화 (2주 차)

Facade를 제거하고, `@Service`가 application 계층에서 Repository를 직접 호출하는 구조로 단순화했다.

```
interfaces → Service(@Service, application) → Repository
```

| 구분 | 역할 | 위치 |
|------|------|------|
| Service | 오케스트레이션 + 비즈니스 규칙 + DTO 변환 | application/ |
| (없음) | — | domain/ (Entity, VO, Repository interface만 존재) |

**장점**: 구조가 단순해지고, 클래스명 혼동이 해소되었다.

**문제**: application 계층에 비즈니스 로직이 노출되기 시작했다. 대표적인 사례:

```java
// application/product/ProductService.java — "어떻게"가 드러남
@Service
public class ProductService {
    private final ProductRepository productRepository;
    private final BrandRepository brandRepository;     // 다른 도메인 Repository 직접 의존
    private final LikeRepository likeRepository;       // 다른 도메인 Repository 직접 의존

    public Page<ProductDetail> getActiveProducts(Long userId, ...) {
        Slice<Product> products = productRepository.findAllByDeletedAtIsNull(...);
        // Brand 배치 로드 + Like 배치 로드 + Map 변환 + 조합 → private 메서드에 은닉
        return toProductDetailPage(userId, products);
    }
}
```

```java
// application/like/LikeService.java — 좋아요 생성 + likeCount 증가가 한 메서드에
@Transactional
public void likeProduct(Long userId, Long productId) {
    Product product = productRepository.findByIdAndDeletedAtIsNullForUpdate(productId)...;
    if (likeRepository.existsByUserIdAndProductId(userId, productId)) { return; }
    likeRepository.save(Like.create(userId, productId));
    product.increaseLikeCount();
}
```

Service가 3개 도메인의 Repository를 직접 의존하고, 비즈니스 규칙(멱등성 체크, 상품 존재 검증, likeCount 변경)과 오케스트레이션이 뒤섞여 있었다.

---

### 3단계: UseCase + DomainService 분리 (3주 차) — 최종 결정

비즈니스 로직을 도메인 계층으로 응집시키되, 이름 혼동을 방지하기 위해 **커스텀 어노테이션으로 역할을 구분**하기로 결정했다.

---

## 결정

### 역할 분리 원칙

| 구분 | 역할 | 어노테이션 | 위치 | 네이밍 |
|------|------|-----------|------|--------|
| **UseCase** | 트랜잭션 관리, 크로스 도메인 오케스트레이션, DTO 변환 | `@UseCase` (`@Service` 메타) | `application/{domain}/` | `{Domain}UseCase` |
| **DomainService** | 자기 도메인의 Repository + Entity를 조작하는 완결된 오퍼레이션 | `@DomainService` (`@Component` 메타) | `domain/{domain}/` | `{Domain}Service` |
| **Entity** | 단일 애그리거트 내 비즈니스 규칙 | `@Entity` | `domain/{domain}/` | `{Domain}` |

**핵심 판단 기준**: UseCase를 읽었을 때 **"무엇을 하는지"만 보이면 OK**. **"어떻게 하는지"가 보이면** → DomainService로 추출.

### 핵심 원칙

```
1. DomainService = 자기 도메인의 완결된 오퍼레이션 (Repository + Entity 조작, save/delete 포함)
2. DomainService는 자기 바운더리의 Repository만 주입 가능 (다른 도메인의 Repository/Entity에 접근하지 않음)
3. 다른 도메인에 대한 사전조건 검증 및 크로스 도메인 협력은 UseCase에서 수행
```

### UseCase의 의존 규칙

UseCase는 DomainService 의존을 강제하지 않는다. **`application → domain` 의존 방향만 지키면 된다.** 
Repository는 domain 레이어의 인터페이스이므로 UseCase가 직접 의존해도 레이어 규칙을 위반하지 않는다.

```java
// 단순 조회: Repository 직접 의존 — OK
@UseCase
public class ReadUserInfoUseCase {
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public UserResult execute(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CoreException(ErrorType.USER_NOT_FOUND));
        return UserResult.from(user);
    }
}
```

DomainService를 거치는 것이 단순히 Repository 호출을 한 번 더 감싸는 것에 불과하다면, UseCase가 Repository를 직접 사용하는 것이 더 낫다. DomainService는 비즈니스 규칙이나 복합 오퍼레이션이 존재할 때 의미가 있다.

### 커스텀 어노테이션

```java
// application 계층: 응용 서비스
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Service
public @interface UseCase {}

// domain 계층: 도메인 서비스
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Component
public @interface DomainService {}
```

- `@UseCase`는 `@Service`를 메타 어노테이션으로 포함한다.
- `@DomainService`는 `@Component`를 메타 어노테이션으로 포함한다.
- 클래스명과 어노테이션 모두에서 역할이 드러나므로 혼동이 발생하지 않는다.

### 트랜잭션 정책

- `@UseCase`에는 클래스 레벨 `@Transactional`을 붙이지 않는다.
- 메서드마다 개별 선언: 쓰기 `@Transactional`, 읽기 `@Transactional(readOnly = true)`.

### DomainService의 의존 규칙

```
LikeService (domain/like/)
  └── LikeRepository       ← 자기 도메인만

ProductService (domain/product/)
  └── ProductRepository    ← 자기 도메인만

BrandService (domain/brand/)
  └── BrandRepository      ← 자기 도메인만

OrderService (domain/order/)
  └── OrderRepository      ← 자기 도메인만

UserService (domain/user/)
  └── UserRepository       ← 자기 도메인만
```

- DomainService는 **자기 바운더리의 Repository만** 주입받는다.
- 다른 도메인의 Repository나 Entity에는 일절 접근하지 않는다.
- 크로스 도메인 사전조건 검증(예: 좋아요 전 상품 존재 확인, 상품 등록 전 브랜드 존재 확인)은 **UseCase가 담당**한다.

---

## 적용 예시

### 좋아요 등록 흐름

```
LikeUseCase.likeProduct(userId, productId)
  ├─→ productService.validateActiveProductExists(id)   // Product 도메인: 상품 존재 검증 (UseCase가 사전조건 오케스트레이션)
  ├─→ likeService.like(userId, productId)              // Like 도메인: 멱등성 체크 + save
  │     ├─ likeRepository.existsByUserIdAndProductId()  //   (중복 체크)
  │     └─ likeRepository.save(Like.create())           //   (저장)
  └─→ productService.increaseLikeCount(productId)      // Product 도메인: 비관적 락 + count 증가
        ├─ productRepository.findForUpdate(productId)   //   (SELECT FOR UPDATE)
        └─ product.increaseLikeCount()                  //   (Entity 메서드)
```

UseCase가 **크로스 도메인 사전조건 검증 + 도메인 서비스 호출 오케스트레이션**을 담당한다. LikeService는 Like 도메인(LikeRepository)만 다룬다.

### 주문 생성 흐름

```
OrderUseCase.createOrder(cart)
  ├─→ productService.getAllActiveProductsForUpdate(ids)  // Product 도메인: 비관적 락 조회
  ├─→ orderService.createOrder(cart, products)           // Order 도메인: 주문 생성 + save
  └─→ product.deductStock(quantity) × N                  // Product Entity 직접 호출 (재고 차감)
```

OrderService는 Order 도메인만 조작하고, 재고 차감은 Product Entity 메서드를 UseCase가 직접 호출한다.

### 브랜드 삭제 흐름 (크로스 도메인 캐스케이드)

```
DeleteBrandUseCase.execute(brandId)
  ├─→ brandService.delete(brandId)                             // Brand 도메인: 조회 + 삭제 (이미 삭제면 false 반환)
  │     ├─ brandRepository.findById(brandId)                   //   (존재 검증)
  │     └─ brand.delete()                                      //   (soft delete)
  ├─→ productService.getActiveProductIdsByBrandId(brandId)     // Product 도메인: 활성 상품 ID 목록
  ├─→ productService.softDeleteAllByBrandId(brandId)           // Product 도메인: 상품 일괄 soft delete
  └─→ likeService.deleteLikesByProductIds(productIds)          // Like 도메인: 좋아요 일괄 삭제
```

여러 도메인에 걸친 캐스케이드 삭제는 UseCase가 오케스트레이션한다. `brandService.delete()`가 false를 반환하면(이미 삭제된 브랜드) 후속 작업을 건너뛴다.

---

## Before / After 구조 비교

### Before (2단계)

```
application/
├── user/       UserService (@Service)          — 중복 검증 + 인증 + 비밀번호 변경 인라인
├── brand/      BrandService (@Service)         — 이름 중복 검증 인라인
├── product/    ProductService (@Service)       — BrandRepository, LikeRepository 직접 의존
├── like/       LikeService (@Service)          — ProductRepository 직접 의존, likeCount 직접 조작
└── order/      OrderService (@Service)         — ProductRepository 직접 의존, 재고 직접 차감

domain/
├── user/       User, UserRepository
├── brand/      Brand, BrandRepository
├── product/    Product, ProductRepository
├── like/       Like, LikeRepository
└── order/      Order, OrderItem, Cart, OrderRepository
```

### After (3단계)

```
application/
├── user/       UserUseCase (@UseCase)          — userService 위임 + DTO 변환
├── brand/      BrandUseCase (@UseCase)         — brandService 위임 + 캐스케이드 삭제 오케스트레이션
├── product/    ProductUseCase (@UseCase)       — productService/brandService 위임 + DTO 변환
├── like/       LikeUseCase (@UseCase)          — likeService → productService 크로스 도메인 오케스트레이션
└── order/      OrderUseCase (@UseCase)         — productService 조회 → orderService 생성 → 재고 차감

domain/
├── user/       User, UserRepository, UserService (@DomainService)
├── brand/      Brand, BrandRepository, BrandService (@DomainService)
├── product/    Product, ProductRepository, ProductService (@DomainService)
├── like/       Like, LikeRepository, LikeService (@DomainService)
└── order/      Order, OrderItem, Cart, OrderRepository, OrderService (@DomainService)
```

### 네이밍 규칙 정리

```
{Domain}UseCase       — application 계층 (오케스트레이션, @UseCase)
{Domain}Service       — domain 계층 (완결된 도메인 오퍼레이션, @DomainService)
{Domain}              — Entity
{Domain}Repository    — Repository 인터페이스 (domain)
{Domain}RepositoryImpl — Repository 구현체 (infrastructure)
```

`*Service`라는 이름은 **도메인 서비스 전용**으로 사용한다. 기존에 application 계층에서 `*Service`를 사용하던 관례를 `*UseCase`로 전환하여, 클래스명만으로 역할을 구분할 수 있다.

---

## 트레이드오프

### 장점

| 항목 | 설명 |
|------|------|
| 역할 명확화 | UseCase = "무엇을", DomainService = "어떻게". 클래스명과 어노테이션 모두에서 역할이 드러남 |
| 도메인 로직 응집 | 비즈니스 규칙이 domain 계층의 DomainService + Entity에 응집. application 계층은 오케스트레이션만 담당 |
| 재사용성 | DomainService 메서드를 여러 UseCase에서 재사용 가능 (예: `UserService.authenticate()` → UserUseCase + AuthInterceptor) |
| 테스트 용이성 | DomainService는 단위 테스트가 용이. UseCase는 DomainService 조합만 검증 |
| 도메인 경계 보호 | DomainService가 자기 도메인만 조작하므로, 다른 도메인의 Entity를 실수로 수정하는 것을 구조적으로 방지 |

### 단점

| 항목 | 설명 | 대응 |
|------|------|------|
| 클래스 수 증가 | DomainService 5개 + 커스텀 어노테이션 2개 추가 | 역할 명확화의 대가로 수용 |
| 간단한 조회도 경유 | 단순 조회도 UseCase → DomainService 경유 | DomainService의 `getXxx()` 메서드가 orElseThrow 등 예외 변환을 캡슐화하므로 중복 제거 효과가 있음 |
| 학습 비용 | UseCase/DomainService 구분 규칙을 팀이 공유해야 함 | 커스텀 어노테이션과 네이밍 규칙으로 컨벤션을 명시적으로 표현 |

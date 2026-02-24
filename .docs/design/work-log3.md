# Round 3 - 이커머스 구현 작업 로그

## 구현 범위

round2에서 설계한 4개 도메인(브랜드, 상품, 좋아요, 주문)을 TDD로 구현한다.

- 설계 문서: `01-requirements.md`, `02-sequence-diagrams.md`, `03-class-diagram.md`, `04-erd.md`
- 기존 구현: User 도메인 (round1 완료)
- 개발 방식: Red → Green → Refactor

---

## 구현 순서

의존 관계 기반으로 순서를 결정한다.

```
1. Brand   (의존 없음)
2. Product  (Brand 의존)
3. Like     (Product, User 의존)
4. Order    (Product, User 의존, 가장 복잡)
```

---

## 1. Brand 도메인

### 엔티티

| 필드 | 타입 | 규칙 |
|------|------|------|
| name | String | 필수, 공백/null 불가 |
| description | String | 선택 |

- `BaseEntity` 상속 (soft delete)

### API 목록

| 구분 | METHOD | URI | 설명 |
|------|--------|-----|------|
| 사용자 | GET | `/api/v1/brands/{brandId}` | 브랜드 정보 조회 |
| 어드민 | GET | `/api-admin/v1/brands?page=0&size=20` | 브랜드 목록 조회 (페이징) |
| 어드민 | GET | `/api-admin/v1/brands/{brandId}` | 브랜드 상세 조회 |
| 어드민 | POST | `/api-admin/v1/brands` | 브랜드 등록 |
| 어드민 | PUT | `/api-admin/v1/brands/{brandId}` | 브랜드 수정 |
| 어드민 | DELETE | `/api-admin/v1/brands/{brandId}` | 브랜드 삭제 (연쇄) |

### 구현 태스크

- [x] Brand 엔티티 + 도메인 규칙 (guard)
- [x] BrandRepository 인터페이스 + JPA 구현체
- [x] BrandService (CRUD + soft delete)
- [x] BrandFacade (연쇄 삭제 조율) — AdminBrandV1Controller의 단일 진입점
- [x] 어드민 API (Controller, DTO, ApiSpec)
- [x] 사용자 API (Controller, DTO, ApiSpec)
- [x] 단위 테스트 (엔티티, 서비스)
- [x] E2E 테스트
- [ ] `http/commerce-api/brand-v1.http` 파일 작성 ← **미작성**

### 비즈니스 규칙

- 브랜드 삭제 시 → 소속 상품 soft delete → 상품의 좋아요 hard delete (연쇄)
- 삭제된 브랜드는 사용자에게 노출되지 않음
- 연쇄 삭제는 BrandFacade에서 조율 (시퀀스 다이어그램 참조)
- BrandFacade는 @Transactional로 원자적 처리

---

## 2. Product 도메인

### 엔티티

| 필드 | 타입 | 규칙 |
|------|------|------|
| brandId | Long | 필수, 등록된 브랜드 참조 (변경 불가) |
| name | String | 필수 |
| description | String | 선택 |
| price | Integer | 필수, 원 단위 정수 |
| stockQuantity | Integer | 필수, 0 이상 |
| visibility | Visibility (inner enum) | 필수, 기본값 VISIBLE |

- `BaseEntity` 상속 (soft delete)
- FK 제약 미사용, 애플리케이션 레벨 검증

### API 목록

| 구분 | METHOD | URI | 설명 |
|------|--------|-----|------|
| 사용자 | GET | `/api/v1/products?brandId=&sort=&page=&size=` | 상품 목록 조회 |
| 사용자 | GET | `/api/v1/products/{productId}` | 상품 상세 조회 |
| 어드민 | GET | `/api-admin/v1/products?page=0&size=20&brandId=` | 상품 목록 조회 (페이징) |
| 어드민 | GET | `/api-admin/v1/products/{productId}` | 상품 상세 조회 |
| 어드민 | POST | `/api-admin/v1/products` | 상품 등록 |
| 어드민 | PUT | `/api-admin/v1/products/{productId}` | 상품 수정 |
| 어드민 | DELETE | `/api-admin/v1/products/{productId}` | 상품 삭제 |

### 구현 태스크

- [x] Product 엔티티 + 도메인 규칙 (guard, visibility 필드)
- [x] ProductRepository 인터페이스 + JPA 구현체
- [x] ProductService (CRUD + soft delete) — Command 패턴 적용 (`ProductCreateCommand`, `ProductUpdateCommand`)
- [x] 어드민 API (Controller, DTO)
- [x] 사용자 API (Controller, DTO) — 정렬/brandId 필터링 포함
- [x] 단위 테스트 (엔티티, 서비스)
- [x] E2E 테스트
- [ ] `http/commerce-api/product-v1.http` 파일 작성 ← **미작성**
- [ ] `sort=LIKES_DESC` 정렬 실제 구현 ← **미완**: Like 도메인 구현 후 QueryDSL LEFT JOIN + COUNT + GROUP BY로 교체 필요 (현재 InMemory에서 id 기준으로 대체)

### 정렬 기준 (사용자 상품 목록)

| sort 파라미터 | 설명 |
|---------------|------|
| `latest` | 최신순 (기본값) |
| `price_asc` | 가격 낮은순 |
| `likes_desc` | 좋아요 많은순 |

### 페이징 기본값

- `page=0`, `size=20`, `sort=latest`

### 구현 메모

- `likes_desc` 정렬은 집계 필요: QueryDSL 기준 `LEFT JOIN + COUNT + GROUP BY`
- 상품 목록 조회의 동적 쿼리(brandId 필터 + 정렬 조합 + 페이징)에 QueryDSL 사용 결정
- QueryDSL은 프로젝트에 세팅 완료 상태 (JPAQueryFactory Bean 등록됨), 이번 라운드에서 처음 적용하며 학습

### 비즈니스 규칙

- 상품은 이미 등록된(삭제되지 않은) 브랜드에만 등록 가능
- 소속 브랜드 변경 불가
- 상품 삭제 시 → 해당 상품의 좋아요 hard delete
- 삭제된 상품이 포함된 과거 주문은 스냅샷으로 정상 조회

### Product.visibility 필드 추가 결정

#### 고민 배경

상품 생성 시 `stockQuantity = 0` 허용 여부를 논의하다가, 재고는 있지만
관리자가 상품 수정 등의 이유로 일시적으로 노출을 막고 싶은 케이스를 발견.

#### 검토한 선택지

| 선택지 | 결론 |
|--------|------|
| `stockQuantity = 0`으로 품절 표현 | 유지 |
| `isVisible: Boolean` | 확장 시 enum 교체 비용 발생, 표현력 부족으로 제외 |
| `status: ACTIVE / OUT_OF_STOCK / HIDDEN / DISCONTINUED` | OUT_OF_STOCK은 stockQuantity와 중복, DISCONTINUED는 soft delete와 역할 겹침 |
| `visibility: VISIBLE / HIDDEN` | 채택 |

#### 결정 및 근거

- `visibility` enum 필드 추가 (`VISIBLE / HIDDEN`)
- `OUT_OF_STOCK` 제외 이유: `stockQuantity = 0`으로 이미 표현 가능, 두 필드 동기화 책임 생김
- `DISCONTINUED` 제외 이유: soft delete(`deletedAt`)와 역할 중복
- boolean(`isVisible`) 대신 enum을 선택한 이유: `VISIBLE_AFTER_LOGIN` 등 노출 조건 확장 시 값 추가만으로 대응 가능

#### 정책

| 값 | 노출 | 주문 |
|----|------|------|
| `VISIBLE` | O | 재고 > 0일 때 가능 |
| `HIDDEN` | X | 불가 |

- 상품 생성 시 기본값: `VISIBLE`
- 사용자 API: `visibility = VISIBLE && deletedAt IS NULL` 인 상품만 반환
- 어드민 API: `visibility` 무관하게 반환

---

## 3. Like 도메인

### 엔티티

| 필드 | 타입 | 규칙 |
|------|------|------|
| id | Long | PK |
| userId | Long | 필수 |
| productId | Long | 필수 |
| createdAt | ZonedDateTime | 자동 |

- **BaseEntity 상속하지 않음** (hard delete 정책)
- `(userId, productId)` 복합 UNIQUE 제약

### API 목록

| 구분 | METHOD | URI | 설명 |
|------|--------|-----|------|
| 사용자 | POST | `/api/v1/products/{productId}/likes` | 좋아요 등록 |
| 사용자 | DELETE | `/api/v1/products/{productId}/likes` | 좋아요 취소 |
| 사용자 | GET | `/api/v1/users/{userId}/likes` | 내 좋아요 목록 조회 |

### 구현 태스크

- [ ] Like 엔티티 (BaseEntity 미상속, 직접 필드 정의) ← **미구현**
- [ ] LikeRepository 인터페이스 + JPA 구현체 ← **미구현**
- [ ] LikeService (등록, 취소, 목록 조회, 브랜드/상품 삭제 시 일괄 삭제) ← **미구현**
- [ ] 사용자 API (Controller, DTO) ← **미구현**
- [ ] 단위 테스트 ← **미구현**
- [ ] E2E 테스트 ← **미구현**
- [ ] `http/commerce-api/like-v1.http` 파일 작성 ← **미작성**

### 비즈니스 규칙

- 같은 상품에 중복 좋아요 불가 → 오류
- 좋아요하지 않은 상품에 취소 요청 → 오류
- 좋아요/취소는 본인만 가능
- 취소 시 이력 미보존 (hard delete)
- 삭제된 상품의 좋아요는 목록에서 제외

---

## 4. Order 도메인

### 엔티티

**Order**

| 필드 | 타입 | 규칙 |
|------|------|------|
| userId | Long | 필수 |
| status | Order.Status (inner enum) | ORDERED |
| totalAmount | Long | OrderItem 합산 금액 |

- `BaseEntity` 상속 (soft delete)

**OrderItem**

| 필드 | 타입 | 규칙 |
|------|------|------|
| orderId | Long | 필수 |
| productId | Long | 원본 참조 |
| productName | String | 스냅샷 |
| price | Integer | 스냅샷 |
| quantity | Integer | 1 이상 |

- `BaseEntity` 상속
- Order와 컴포지션 관계

### API 목록

| 구분 | METHOD | URI | 설명 |
|------|--------|-----|------|
| 사용자 | POST | `/api/v1/orders` | 주문 생성 |
| 사용자 | GET | `/api/v1/orders?startAt=&endAt=` | 주문 목록 조회 (기간별) |
| 사용자 | GET | `/api/v1/orders/{orderId}` | 주문 상세 조회 |
| 어드민 | GET | `/api-admin/v1/orders?page=0&size=20` | 주문 목록 조회 (페이징) |
| 어드민 | GET | `/api-admin/v1/orders/{orderId}` | 주문 상세 조회 |

### 구현 태스크

- [ ] Order 엔티티 + Order.Status inner enum ← **미구현**
- [ ] OrderItem 엔티티 ← **미구현**
- [ ] OrderRepository, OrderItemRepository 인터페이스 + JPA 구현체 ← **미구현**
- [ ] OrderService (주문 구조 검증, 주문 저장) ← **미구현**
- [ ] OrderFacade (재고 확인 + 차감 → 상품 저장 → 주문 저장 조율) ← **미구현**
- [ ] 사용자 API (Controller, DTO) ← **미구현**
- [ ] 어드민 API (Controller, DTO) ← **미구현**
- [ ] 단위 테스트 ← **미구현**
- [ ] E2E 테스트 ← **미구현**
- [ ] `http/commerce-api/order-v1.http` 파일 작성 ← **미작성**

### 주문 생성 흐름 (시퀀스 다이어그램 기반)

```
1. 주문 항목 검증 (빈 항목, 중복 상품, 수량 ≥ 1) — OrderService 책임
2. 상품 목록 조회 (미존재/삭제 상품 시 오류) — ProductService 책임
3. 재고 검증 + 차감 (메모리) — OrderFacade 조율
4. 재고 부족 시 → 전체 주문 거부 + 부족 상품 정보 응답
5. 재고 반영 (saveAll) — ProductService
6. 주문 저장 (스냅샷 포함, ORDERED 상태) — OrderService
```

### 구현 메모

- 동시성/멱등성은 범위 밖 (단일 스레드 기준). 재고 차감 경쟁 조건은 추후 고도화 단계에서 해결.

### 비즈니스 규칙

- 주문 항목 중 하나라도 재고 부족 → 전체 주문 거부
- 상품 정보(상품명, 가격) 스냅샷 저장
- 주문 생성 시 즉시 ORDERED 상태
- 같은 상품 중복 주문 항목 불가
- 주문 수량 1개 이상

---

## 공통 참고사항

### 레이어별 패키지 구조

```
domain/{도메인}/         → 엔티티, Repository 인터페이스, 도메인 순수 로직 (Validator 등)
application/{도메인}/    → Service, Facade, Command, Info
infrastructure/{도메인}/ → RepositoryImpl, JpaRepository
interfaces/api/{도메인}/ → Controller, ApiSpec, DTO
```

### Service 레이어 위치 재고 — `domain/` → `application/` 이동

#### 문제 인식

초기 구현에서 `BrandService`, `UserService`, `SignUpService` 등을 `domain/` 하위에 배치했다.
그러나 이 구조는 **레이어 의존 방향을 역전**시킬 수 있다.

#### Clean Architecture 의존 방향

```
Presentation → Application → Domain ← Infrastructure
```

- `Domain`: 순수 비즈니스 모델 — 엔티티, 도메인 로직, Repository 인터페이스
- `Application`: 유스케이스 조율 — Service, Facade, Command, Info
- `Infrastructure`: 외부 구현 — JPA, Redis 등

Service 클래스가 `domain/` 에 위치하면, 도메인 간 Service 호출이 발생할 때 `domain → domain` 의존이 생기고,
Application 계층의 경계가 흐려진다.

#### 이동 결과

| 클래스 | 이전 위치 | 이후 위치 |
|--------|-----------|-----------|
| `BrandService` | `domain/brand/` | `application/brand/` |
| `ProductService` | `domain/product/` | `application/product/` |
| `ProductSort` | `domain/product/` | `application/product/` |
| `SignUpService` | `domain/user/` | `application/user/` |
| `UserService` | `domain/user/` | `application/user/` |

`domain/` 에는 엔티티, Repository 인터페이스, Validator 등 순수 도메인 객체만 잔류.

#### 테스트 패키지도 정합성 맞춤

테스트 파일은 대상 클래스의 패키지 구조를 그대로 미러링하는 것이 원칙이다.
Service 이동에 따라 테스트 파일도 동일하게 `application/` 하위로 정렬했다.

이때 단순 생성/삭제가 아닌 **`git mv`** 를 사용해 git 히스토리를 rename으로 보존했다.
`git log --follow` 로 파일 이동 전 이력까지 추적 가능하다.

```bash
git mv domain/brand/BrandServiceTest.java application/brand/BrandServiceTest.java
```

### 사용자 식별 방식

- 사용자: `@RequestHeader("X-Loopers-LoginId")`, `@RequestHeader("X-Loopers-LoginPw")`
- 어드민: `@RequestHeader("X-Loopers-Ldap")` — 값: `loopers.admin`

### E2E 테스트 최소 시나리오

- Brand: 등록 → 조회 → 수정 → 삭제(연쇄 삭제 포함)
- Product: 등록 → 목록 정렬/필터 → 상세 → 삭제
- Like: 등록 성공 → 중복 오류 → 취소 성공 → 미존재 오류
- Order: 정상 주문 성공 → 재고 부족 전체 실패
- 공통: 에러 코드/메시지 응답 검증 포함

### 응답 정책

- 타인의 주문/좋아요 목록 접근 시 403이 아닌 404로 응답 (리소스 존재 여부 비노출)

### 서비스 반환 타입 정책

Service/Facade는 도메인 엔티티를 직접 반환하지 않고, application 레이어의 Info DTO로 변환해 반환한다.

```
Service/Facade → Info DTO (application 레이어) → Controller → Response DTO (interfaces 레이어)
```

#### 도입 이유

1. **트랜잭션 경계**: `@Transactional` 메서드 종료 후 엔티티는 detached 상태가 된다. OSIV(Open Session In View)를 끄면(실무 권장) Controller에서 lazy 필드 접근 시 `LazyInitializationException` 발생
2. **API-도메인 결합 방지**: 엔티티 필드 변경이 API 응답에 즉시 영향을 주는 묵시적 결합 제거
3. **일관성**: 단순/복잡 케이스를 구분하는 모호한 기준 없이 모든 도메인에 동일 패턴 적용

#### Info DTO 위치

```
application/{도메인}/BrandInfo.java
application/{도메인}/ProductInfo.java
```

#### 구현 원칙

- Service 내부에서는 엔티티로 작업 (`private findById()`, `private findNonDeletedById()`)
- public 메서드 반환 시점에만 `Info.from(entity)`로 변환
- `interfaces` 레이어 DTO는 `from(Info)`로만 생성 (`from(Entity)` 금지)

### 테스트 전략

InMemoryRepository 기반 단위 테스트와 E2E 테스트의 역할을 명확히 구분한다.

| 테스트 종류 | 대상 | 예시 |
|------------|------|------|
| 엔티티 단위 테스트 | 도메인 guard, 상태 전이 | `price < 0 → BAD_REQUEST` |
| Service 단위 테스트 | 순수 비즈니스 규칙 (InMemory 활용) | `존재하지 않는 브랜드로 등록 → NOT_FOUND` |
| E2E 테스트 | QueryDSL 의존 쿼리 동작 | 삭제 필터, brandId 필터, 정렬 |

**InMemoryRepository의 한계:**
- QueryDSL 동적 쿼리(필터링, 정렬, 페이징)는 실구현과 동작이 다를 수 있음
- 따라서 QueryDSL에 의존하는 테스트는 InMemory로 검증하지 않고 E2E 테스트에서 커버

**실제 적용:**
- `ProductServiceTest.GetProducts`, `BrandServiceTest.GetBrands` → 제거 (E2E에서 커버)
- 나머지 비즈니스 규칙 테스트는 ServiceTest에 유지

- 각 도메인 구현 완료 시 `http/*.http` 파일에 API 테스트 추가

---

## 구현 중 확립된 패턴 (round3 추가)

### PageResponse<T> DTO 도입

#### 배경

E2E 테스트에서 `Page<T>` 인터페이스를 `TestRestTemplate`으로 역직렬화할 수 없는 문제 발견.
`Page`는 인터페이스이므로 Jackson이 구체 타입을 알 수 없어 역직렬화 실패.

#### 해결

`PageResponse<T>` record DTO 생성 후 컨트롤러 응답 타입으로 사용.

```java
// interfaces/api/PageResponse.java
public record PageResponse<T>(
        List<T> content, int page, int size, long totalElements, int totalPages
) {
    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(page.getContent(), page.getNumber(),
                page.getSize(), page.getTotalElements(), page.getTotalPages());
    }
}
```

#### 적용 범위

- `AdminBrandV1Controller.getBrands()` → `ApiResponse<PageResponse<BrandV1Dto.AdminBrandResponse>>`
- `AdminProductV1Controller.getProducts()` → `ApiResponse<PageResponse<ProductV1Dto.AdminProductResponse>>`
- `ProductV1Controller.getProducts()` → `ApiResponse<PageResponse<ProductV1Dto.ProductResponse>>`

#### 테스트에서의 활용

```java
ResponseEntity<ApiResponse<PageResponse<ProductV1Dto.ProductResponse>>> response =
    testRestTemplate.exchange(ENDPOINT, HttpMethod.GET, null, new ParameterizedTypeReference<>() {});
List<Long> ids = response.getBody().data().content().stream()
    .map(ProductV1Dto.ProductResponse::id).toList();
assertThat(ids).contains(productA.getId(), productB.getId());
```

### Domain 엔티티가 Application 객체를 참조하면 안 된다

#### 문제

`User.create(SignUpCommand command, String encodedPassword)` 팩토리 메서드가 `SignUpCommand`(application 계층)를 파라미터로 받고 있어 의존 방향이 역전됨.

```
Domain → Application  ← 위반
```

#### 올바른 방향

```
Application → Domain  ← 정상
```

Application이 Command를 언팩해서 프리미티브로 넘기고, Domain은 값만 받는다.

```java
// ✅ Domain — Application을 모름
public static User create(String loginId, String encodedPassword, String name, LocalDate birthDate, String email)

// ✅ Application — 언팩 책임
User user = User.create(command.loginId(), encoded, command.name(), command.birthDate(), command.email());
```

현재 `User.create(SignUpCommand)`는 기술 부채로 남아 있음. 추후 수정 예정.

---

### Command 패턴 (ProductCreateCommand / ProductUpdateCommand)

Service 메서드 파라미터를 개별 값 대신 Command 객체로 감싸 응집도를 높임.

```
application/product/ProductCreateCommand.java
application/product/ProductUpdateCommand.java
```

ProductService 메서드 시그니처:
- `register(ProductCreateCommand command)`
- `update(Long id, ProductUpdateCommand command)`

User 도메인에 이미 `SignUpCommand`, `UpdatePasswordCommand`로 동일 패턴 존재.

### E2E 테스트 단언 원칙 (ID 기반 비교)

목록 조회 테스트에서 문자열 포함 여부(`contains("에어맥스")`)가 아닌 타입 안전한 ID 비교를 원칙으로 함.

이유: 문자열 비교는 price 등 다른 JSON 필드에서 우연히 매칭될 위험이 있음.

```java
// ✅ 올바른 방식
List<Long> ids = response.getBody().data().content().stream()
    .map(ProductV1Dto.ProductResponse::id).toList();
assertThat(ids).contains(productA.getId());

// ❌ 피해야 할 방식
assertThat(responseBody).contains("에어맥스");
```

### BrandFacade를 AdminBrandV1Controller의 단일 진입점으로 결정

#### 고민 배경

BrandFacade 초기 구현 시 delete만 Facade를 통하고, 나머지 CRUD는 BrandService를 직접 호출하는 구조가 됐다.

```java
// ❌ 어색한 구조
AdminBrandV1Controller
  ├── BrandFacade  (delete만)
  └── BrandService (나머지 전부)
```

Controller가 두 의존성을 동시에 갖는 건 "BrandFacade가 불완전한 진입점"이라는 신호다.

#### 잘못된 표현 수정

처음에 "도메인 서비스 간 결합 증가"라고 표현했지만, `BrandService`와 `ProductService`는 모두 `application` 패키지에 있는 **Application 서비스**다. "도메인 서비스"라는 표현은 틀렸다.

#### BrandFacade가 필요한 진짜 이유

Application 서비스끼리 참조 자체는 이론적으로 가능하다. 그러나 이 프로젝트에서는 **순환 의존**이 발생한다.

```
ProductService → BrandService  (이미 존재)
BrandService   → ProductService (추가 시)
         ↑___________________________↓  순환!
```

`ProductService`가 이미 `BrandService`를 주입받고 있으므로, `BrandService`가 `ProductService`를 참조하면 Spring 빈 생성 시점에 순환 의존으로 실패한다. `BrandFacade`는 이 순환을 피하기 위한 상위 조율자다.

#### 결정: BrandFacade를 단일 진입점으로 확장

```java
// ✅ 단일 진입점
AdminBrandV1Controller → BrandFacade → BrandService
                                     → ProductService (delete 시에만)
```

- Controller는 `BrandFacade`만 알고, 나머지는 Facade가 내부적으로 BrandService에 위임
- 단순 위임이더라도 조율 로직이 추가될 때 수정 범위가 Facade 내로 한정됨
- BrandService는 Brand 도메인 순수 로직만 유지

#### delete만 @Transactional이 필요한 이유

단순 위임 메서드(register, getBrand, getBrands, update)는 BrandService의 `@Transactional`이 그대로 적용되므로 Facade에 추가 선언 불필요. delete만 두 서비스를 가로질러 원자성이 필요하므로 Facade에서 `@Transactional`을 선언해 하나의 트랜잭션으로 묶는다.

### DDD 아키텍처 고민 — Service 위치와 레이어 간 의존 (2026-02-24)

#### 배경

멘토님(Devin) DDD 라이브 세션 이후 현재 brand/product 패키지 구조가 멘토님 기준에 맞는지 검토함.

세션 핵심: `interfaces / application / domain / infra` 4레이어. Repository를 호출하고 도메인 로직을 처리하는 Service는 domain에 있어야 한다. Facade는 도메인 서비스들을 조합해 애플리케이션 비즈니스를 구현한다. (CASE C 선호)

→ 전체 내용은 `.docs/design/ddd_session_by_devin.md` 참조

#### 검토 결과

멘토님 CASE C 기준으로 보면 `BrandService` / `ProductService`는 `domain/` 으로 이동해야 함. 그러나 Codex 피드백에서 다른 관점 제시:

> "단순 CRUD 서비스(저장/조회 위주, 규칙 거의 없음)는 application에 두는 게 맞다. 도메인에 둘 만한 것은 CRUD를 넘는 규칙이 생겼을 때다."

두 관점의 차이:
- **멘토님(Devin)**: 기준 = "기능의 완결성" — Repository를 호출하는 단위가 단독으로 완결된 기능이 되려면 domain에 있어야 한다
- **Codex**: 기준 = "로직의 성격" — CRUD는 유스케이스 흐름이므로 application이 적절하다

#### 결론: `BrandService` / `ProductService` application 유지 (Codex 의견 수용)

현재 두 서비스는 사실상 CRUD 조율 역할이고, 멘토님이 싫어한 핵심 문제("뭐든 다 아는 뚱뚱한 서비스")는 Facade 분리로 이미 해소됨.

→ **진짜 문제는 `ProductService → BrandService` 직접 의존** — 동일 레이어 Service끼리 의존으로 순환 위험 + 멘토님 불호 패턴("파사드가 파사드를 콜하는 구조")에 해당

#### 해결: ProductFacade 신설로 브랜드 검증 로직 인양

---

### ProductFacade 신설 — ProductService의 BrandService 의존 제거 (2026-02-24)

#### 문제

```
ProductService → BrandService  (동일 application 레이어 직접 의존)
BrandFacade   → ProductService
```

- "브랜드 검증 후 상품 등록"은 두 도메인을 조합하는 **애플리케이션 비즈니스** → Facade 책임
- ProductService에 BrandService가 있으면 Domain 서비스의 단독 완결성이 훼손되고, BrandFacade 확장 시 순환 의존 위험

#### 변경 내용

| 파일 | 변경 |
|------|------|
| `ProductFacade` | 신설. `register()` 시 `brandService.getBrand()` 검증 후 `productService.register()` 위임 |
| `ProductService` | `BrandService` 의존 제거. `register()`에서 브랜드 검증 로직 제거 |
| `AdminProductV1Controller` | `ProductService` → `ProductFacade` 단일 진입점으로 전환 |
| `ProductServiceTest` | BrandService 관련 setUp 제거, 브랜드 검증 테스트 제거. BRAND_ID 상수로 단순화 |
| `ProductFacadeTest` | 신설. 브랜드 검증 테스트 2건 이동 (미존재 브랜드, 삭제된 브랜드) |
| `BrandFacadeTest` | `ProductService` 생성자에서 `brandService` 파라미터 제거 |

#### 결과 구조

```
AdminProductV1Controller → ProductFacade → BrandService (검증)
                                         → ProductService (저장/조회/수정/삭제)
BrandFacade              → ProductService (연쇄 삭제)
                         → BrandService (브랜드 삭제)
ProductV1Controller      → ProductService (사용자 읽기 전용, Facade 불필요)
```

- `BrandV1Controller`가 `BrandService` 직접 사용하는 것과 동일한 패턴 유지
- 전체 테스트 통과 확인

---

### 테스트 변수 네이밍 원칙

단언에 사용되는 값은 모두 변수로 추출해 가독성 확보. 하드코딩 직접 비교 지양.

```java
// ✅
String brandName = "나이키";
Brand brand = brandService.create(brandName, "설명");
assertThat(response.getBody().data().name()).isEqualTo(brandName);

// ❌
assertThat(response.getBody().data().name()).isEqualTo("나이키");
```

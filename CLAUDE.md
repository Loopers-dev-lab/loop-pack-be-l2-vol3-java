# CLAUDE.md

AI 어시스턴트가 본 프로젝트의 코딩 규칙, 아키텍처, 도메인 설계 전략을 준수하도록 안내하는 문서입니다.

---

## 객체지향 & 도메인 모델링 규칙

### 핵심 원칙

- **도메인 객체는 비즈니스 규칙을 캡슐화**한다. 데이터 저장소가 아닌, 규칙과 행위를 가진 객체로 설계한다.
- **애플리케이션 서비스**는 서로 다른 도메인을 조립하고, 도메인 로직을 조정하여 기능을 제공한다. 비즈니스 규칙을 직접 구현하지 않는다.
- **규칙이 여러 서비스에 반복되면** 도메인 객체(Entity, VO, Domain Service)로 옮길 가능성이 높다.
- 각 기능의 **책임과 결합도**를 명확히 하고, 개발 의도에 맞게 설계한다.

### Entity 설계

- **고유 식별자(ID)**를 가지며, 자신의 상태를 변경하는 **행위 메서드**를 제공한다.
- `changePassword()`, `cancelOrder()`, `decreaseStock()`처럼 **의도가 드러나는 메서드명**을 사용한다.
- **무분별한 Setter 사용을 금지**한다. 상태 변경은 반드시 도메인 메서드를 통해 이루어진다.
- 생성자와 비즈니스 메서드 내부에서 **필수 검증**을 수행한다.

### Value Object (VO) 설계

- **식별자가 없는** 값 객체이다.
- **불변(Immutable)**으로 설계하고, 생성 시점에 **자체 검증 로직**을 포함한다.
- `Money`, `Quantity`, `LoginId`, `Email`처럼 의미 있는 단위로 분리한다.
- 동일성은 **값의 동등성**으로 판단한다.

### Domain Service 설계

- **특정 엔티티에 두기 어려운** 여러 엔티티 간 조율이나 복잡한 도메인 정책을 처리한다.
- **무상태(Stateless)**로 설계한다.
- **동일한 도메인 경계 내**의 도메인 객체 협력에 집중한다.
- 도메인 내부 규칙은 Domain Service에 두고, Application Layer는 조합만 담당한다.

### 빈약한 도메인 모델 지양

- 비즈니스 로직을 Application Service에 두지 말고, **Entity와 VO 내부에 응집**시킨다.
- Getter/Setter만 있는 데이터 클래스는 지양하고, **행위가 드러나는 메서드**를 우선한다.

---

## 아키텍처 전략 & 패키지 구성

### 레이어드 아키텍처 + DIP

- 본 프로젝트는 **레이어드 아키텍처**를 따르며, **DIP(의존성 역전 원칙)**를 준수한다.
- 의존성 방향: **Infrastructure → Domain ← Application**
- **Domain Layer**는 외부 기술(JPA, Spring 등)에 의존하지 않는다.

### 계층 구조

```
Application  ──→  Domain  ←──  Infrastructure
     │                │              │
     │                │              └── Repository 구현체, JPA Entity, Mapper
     │                └── Entity, VO, Domain Service, Repository Interface
     └── Service, Facade (도메인 조합, 흐름 제어)
```

- **Interfaces (Presentation)**: HTTP 요청/응답, Request/Response DTO, 입력 검증. 비즈니스 로직 없음.
- **Application**: 트랜잭션 관리, 도메인 조합, 흐름 제어. 로직은 도메인에 위임.
- **Domain**: 순수 도메인 객체, Repository Interface. 외부 의존성 0%.
- **Infrastructure**: Repository 구현체, JPA Entity, DB/Redis 등 기술 구현.

### DTO 분리

- **API Request/Response DTO**와 **Application Layer DTO**는 분리해 작성한다.
- API DTO는 `interfaces.api.*`에, Application DTO는 `application.*`에 위치한다.

### 패키지 구성

4개 레이어 패키지를 두고, 하위에 **도메인별**로 패키징한다.

```
/interfaces/api/{domain}     # Presentation - API Controller, API DTO
/application/{domain}       # Application - Service, Facade, Application DTO
/domain/{domain}            # Domain - Entity, VO, Domain Service, Repository Interface
/infrastructure/{domain}    # Infrastructure - Repository 구현체, JPA Entity, Mapper
```

**예시**

```
com.loopers
├── interfaces
│   └── api
│       ├── product
│       ├── order
│       └── like
├── application
│   ├── product
│   │   ├── ProductFacade
│   │   └── ProductInfo
│   └── order
│       └── OrderService
├── domain
│   ├── product
│   │   ├── Product
│   │   ├── Brand
│   │   ├── Stock
│   │   └── ProductRepository
│   ├── like
│   │   ├── Like
│   │   └── LikeRepository
│   └── order
│       ├── Order
│       ├── OrderLine
│       └── OrderRepository
└── infrastructure
    ├── product
    │   ├── ProductRepositoryImpl
    │   └── ProductJpaRepository
    └── order
        └── OrderRepositoryImpl
```

### Service vs Facade

- **Service**: 트랜잭션 관리, **상태 변경**이 있는 복잡한 비즈니스 흐름. 도메인 객체에 위임.
- **Facade**: **상태 변경 없이** 여러 도메인의 데이터를 조회·조합(Aggregation)하여 반환.

---

## 도메인 설계 가이드 (Product, Brand, Like, Order)

### Product / Brand

- 상품 정보는 **브랜드 정보**, **좋아요 수**를 포함한다.
- 상품 정렬 조건(`latest`, `price_asc`, `likes_desc`)은 **조회 시점**에 적용한다.
- 상품은 **재고(Stock)**를 가지며, 주문 시 **도메인 레벨**에서 차감한다.
- **재고 음수 방지**는 Entity 또는 Domain Service에서 처리한다.

### Like

- 좋아요는 **유저와 상품 간 관계**로 별도 도메인으로 분리한다.
- 상품의 좋아요 수는 **조회 시점에 집계**하여 상품 상세/목록에 함께 제공한다.
- 상품이 좋아요 수를 **직접 관리하지 않는다**. Like 도메인이 집계를 담당한다.

### Order

- 주문은 **여러 상품**을 포함하며, 각 상품의 **수량**을 명시한다.
- 주문 시 **상품 재고 차감**을 수행한다. 재고 부족 시 예외 처리한다.
- Order, Product, Stock 간 협력은 **Domain Service**에서 조율한다.

---

## Feature Suggestions (설계 의사결정 가이드)

### Q1. 상품이 좋아요 수를 직접 관리해야 할까?

**아니오.** 좋아요 수는 Like 도메인에서 집계한다. Product는 `likeCount`를 필드로 가지지 않고, 조회 시점에 Application Layer 또는 Facade에서 Product + Like를 조합해 제공한다. 이렇게 하면 좋아요 등록/취소 시 Product를 수정할 필요가 없고, Like 도메인이 단일 책임을 가진다.

### Q2. 상품 상세에서 브랜드를 함께 제공하려면 누가 조합해야 할까?

**Application Layer (ProductFacade)**가 조합한다. `ProductFacade.getProductDetail(productId)`에서 Product, Brand, Like를 조회해 하나의 DTO로 조합한다. Domain Layer는 각자 자신의 책임만 수행하고, 조합은 Application의 역할이다.

### Q3. VO를 도입한 이유는 무엇이며, 어느 시점에서 유리하게 작용했는가?

- **검증 로직 응집**: `Money`, `Quantity`처럼 생성 시점에 유효성 검증을 캡슐화한다.
- **불변성 보장**: 값이 변경되지 않아 부작용을 줄인다.
- **의미 표현**: `Price price`가 `long price`보다 의도를 잘 드러낸다.
- **재사용**: 여러 Entity에서 동일한 VO를 사용해 일관된 규칙을 적용한다.

### Q4. Order, Product, User 중 누가 어떤 책임을 갖는 것이 자연스러웠나?

- **Order**: 주문 생성, 주문 라인 관리, 주문 상태 변경. "주문한다"는 Order의 책임.
- **Product**: 상품 정보, 재고 차감(`decreaseStock()`). "재고를 줄인다"는 Product(또는 Stock)의 책임.
- **User/Member**: 회원 정보, 인증. 주문 시에는 식별자만 참조한다.
- **Domain Service**: Order와 Product 간 재고 차감·검증 등 **여러 엔티티 협력**은 Domain Service가 조율한다.

### Q5. Repository Interface를 Domain Layer에 두는 이유는?

**DIP 적용**을 위해서다. Application/Domain은 "저장·조회" 인터페이스만 알고, 실제 구현(JDBC, JPA 등)은 Infrastructure에 둔다. Domain이 Infrastructure에 의존하지 않도록 인터페이스를 Domain에 두고, 구현체가 이를 따른다.

### Q6. 처음엔 도메인에 두려 했지만, 결국 Application Layer로 옮긴 이유는?

- **트랜잭션 경계**: `@Transactional`은 Application Layer에서 관리하는 것이 자연스럽다.
- **여러 도메인 조합**: Product + Brand + Like 조합은 단일 도메인 책임을 넘어서므로 Application(Facade)에 둔다.
- **외부 의존성**: Domain은 Spring, JPA 등에 의존하지 않아야 하므로, 트랜잭션 어노테이션을 쓰는 클래스는 Application에 둔다.

### Q7. 테스트 가능한 구조를 만들기 위해 가장 먼저 고려한 건 무엇이었나?

- **Repository Interface 분리**: Domain에 인터페이스를 두고, 단위 테스트에서는 **Fake/Stub 구현체**를 주입한다.
- **도메인 로직 순수성**: Entity, VO, Domain Service가 외부 의존 없이 동작하도록 설계해, **단위 테스트만으로** 비즈니스 규칙을 검증한다.
- **의존성 주입**: Service가 Repository 인터페이스에 의존하도록 해, 테스트 시 Mock/Fake로 대체 가능하게 한다.

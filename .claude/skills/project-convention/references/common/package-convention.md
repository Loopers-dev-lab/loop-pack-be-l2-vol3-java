# 패키지 구조 컨벤션

---

## 1. 전체 구조

**계층 우선 + 도메인 하위** 방식. 최상위는 계층으로, 각 계층 안에서 도메인별로 분리한다.

```
com.loopers/
├── interfaces/                     ← Interface 계층
│   ├── api/                        ← 공통 (ApiResponse, ControllerAdvice)
│   ├── order/                      ← 주문 Controller, Request/Response DTO
│   ├── product/
│   └── like/
│
├── application/                    ← Application 계층
│   ├── order/                      ← 주문 Facade, Criteria/Result DTO
│   ├── product/
│   └── like/
│
├── domain/                         ← Domain 계층
│   ├── order/                      ← 주문 Entity, Service, Repository(I/F), ErrorCode
│   ├── product/
│   └── like/
│
├── infrastructure/                 ← Infrastructure 계층
│   ├── order/                      ← 주문 Repository 구현, JPA
│   ├── product/
│   └── like/
│
└── support/                        ← 공통 지원 (에러, 설정, 유틸)
    ├── error/
    ├── config/
    └── util/
```

---

## 2. 계층별 패키지 상세

소규모 도메인은 빈 계층 패키지를 만들지 않는다. 필요해지면 그때 추가한다.

### interfaces/

```
interfaces/
├── api/                                    ← 공통
│   ├── ApiResponse.java
│   └── ApiControllerAdvice.java
│
└── order/                                  ← 도메인별
    ├── OrderController.java                ← 고객용 Controller
    ├── AdminOrderController.java           ← Admin용 Controller (필요 시)
    └── dto/
        ├── OrderDto.java                   ← Inner Class: CreateRequest, DetailResponse ...
        └── AdminOrderDto.java              ← Admin용 Request/Response (필요 시)
```

### application/

```
application/
└── order/
    ├── OrderFacade.java
    └── dto/
        ├── OrderCriteria.java              ← Inner Class: Create, Detail ...
        ├── OrderResult.java                ← 유스케이스 결과
        └── OrderDetailResult.java          ← 다중 도메인 조합 응답 (필요 시)
```

### domain/

```
domain/
└── order/
    ├── Order.java                          ← Entity (Aggregate Root)
    ├── OrderLine.java                      ← Entity (하위)
    ├── OrderStatus.java                    ← enum
    ├── OrderService.java                   ← Domain Service
    ├── OrderRepository.java                ← Repository 인터페이스
    ├── OrderErrorCode.java                 ← 도메인 에러코드
    └── dto/                                ← 도메인 DTO (필요 시)
        ├── OrderProductCommand.java        ← 타 도메인 정보 명세
        └── OrderMemberCommand.java
```

### infrastructure/

```
infrastructure/
└── order/
    ├── OrderRepositoryImpl.java            ← Repository 구현체
    └── OrderJpaRepository.java             ← Spring Data JPA 인터페이스
```

infrastructure가 JpaRepository 하나뿐이면 domain 패키지에 인터페이스만 두고 Spring Data JPA가 자동 구현하도록 한다.

---

## 3. 공통 패키지 상세

### interfaces/api/

도메인에 속하지 않는 API 레벨 공통 클래스.

```
interfaces/api/
├── ApiResponse.java             ← 공통 응답 포맷
└── ApiControllerAdvice.java     ← 글로벌 예외 핸들러
```

### support/

도메인 로직이 아닌 **기술 지원** 클래스만 배치한다. 도메인 로직이 포함되거나 특정 도메인에만 쓰이는 클래스는 해당 도메인 패키지에 둔다.

```
support/
├── error/
│   ├── ErrorCode.java               ← 인터페이스
│   ├── ErrorType.java               ← 공통 에러 enum
│   └── CoreException.java           ← 단일 예외 클래스
├── config/
│   ├── WebMvcConfig.java
│   ├── SecurityConfig.java
│   └── JpaConfig.java
└── util/                            ← 필요 시만 생성
    └── DateUtils.java
```

---

## 4. 계층별 클래스 배치 규칙

### interfaces/{domain}/

| 클래스 | 네이밍 | 예시 |
|--------|--------|------|
| Controller (고객) | `{Domain}Controller` | `OrderController` |
| Controller (Admin) | `Admin{Domain}Controller` | `AdminOrderController` |
| Request DTO | `{Domain}Dto.{Action}Request` | `OrderDto.CreateRequest` |
| Response DTO | `{Domain}Dto.{Action}Response` | `OrderDto.DetailResponse` |
| Admin DTO | `Admin{Domain}Dto.{Action}Response` | `AdminOrderDto.DetailResponse` |

### application/{domain}/

| 클래스 | 네이밍 | 예시 |
|--------|--------|------|
| Facade | `{Domain}Facade` | `OrderFacade` |
| Criteria DTO | `{Domain}Criteria.{Action}` | `OrderCriteria.Create` |
| Result DTO | `{Domain}Result` | `OrderResult` |
| 조합 Result DTO | `{Domain}{Detail}Result` | `OrderDetailResult` |

### domain/{domain}/

| 클래스 | 네이밍 | 예시 |
|--------|--------|------|
| Entity | `{Domain}` | `Order` |
| Domain Service | `{Domain}Service` | `OrderService` |
| Repository (인터페이스) | `{Domain}Repository` | `OrderRepository` |
| ErrorCode | `{Domain}ErrorCode` | `OrderErrorCode` |
| enum | 의미에 맞게 | `OrderStatus` |
| Command DTO | `{Target}Command` | `OrderProductCommand` |

### infrastructure/{domain}/

| 클래스 | 네이밍 | 예시 |
|--------|--------|------|
| Repository 구현체 | `{Domain}RepositoryImpl` | `OrderRepositoryImpl` |
| JPA Repository | `{Domain}JpaRepository` | `OrderJpaRepository` |
| 외부 API 클라이언트 | `{External}Client` | `PaymentClient` |

---

## 5. 의존 방향 규칙

```
interfaces → application → domain ← infrastructure
```

- **interfaces**: application만 참조. domain 직접 참조 금지.
- **application**: domain만 참조.
- **domain**: 어떤 계층도 참조하지 않음. 단, Domain Service는 `@Service`, `@Transactional`, `Page`/`Pageable` 허용 (service-layer-convention.md 참조).
- **infrastructure**: domain만 참조 (Repository 인터페이스 구현).

### 도메인 간 의존

- 도메인 간 **Entity 직접 참조 금지**
- Application 계층(Facade)에서 타 도메인의 Domain Service를 호출하여 조합
- 필요 시 Domain의 Command DTO로 정보 전달

```java
// application/order/OrderFacade.java
@Service
public class OrderFacade {
    private final ProductService productService;  // domain/product/
    private final OrderService orderService;      // domain/order/
}
```

---

## 6. 새 도메인 추가 체크리스트

- [ ] `domain/{domain}/` 생성 (Entity, Repository 인터페이스)
- [ ] API 필요 시 `interfaces/{domain}/`, `application/{domain}/` 추가
- [ ] 커스텀 Repository 구현 필요 시 `infrastructure/{domain}/` 추가
- [ ] 도메인 에러 필요 시 `{Domain}ErrorCode.java` 추가
- [ ] 빈 계층은 만들지 않음 -- 필요할 때 추가

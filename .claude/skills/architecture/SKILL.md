---
name: architecture
description: 레이어드 아키텍처 패턴, 의존성 규칙, Domain/Application/Interfaces/Infrastructure 레이어 구조 및 책임. 아키텍처 설계 시 참조
user-invocable: true
allowed-tools: Read, Grep
---

# 아키텍처 패턴

> 모든 패턴은 실제 코드베이스에서 확인할 것. 이 문서는 방향과 규칙만 기술한다.
> 경로 prefix: `apps/commerce-api/src/main/java/com/loopers/`

---

## 레이어 구조

```
┌──────────────────────────────────────────┐
│           Interfaces Layer               │  ← 외부 통신
│   Controller, ApiSpec, Dto, Response     │
└──────────────────────────────────────────┘
                    ↓
┌──────────────────────────────────────────┐
│          Application Layer               │  ← 유스케이스 조합
│          App, Facade, Info               │
└──────────────────────────────────────────┘
                    ↓
┌──────────────────────────────────────────┐
│            Domain Layer                  │  ← 핵심 비즈니스 로직
│     Model, Service, Repository, VO       │
└──────────────────────────────────────────┘
                    ↓
┌──────────────────────────────────────────┐
│         Infrastructure Layer             │  ← 외부 시스템 연동
│   RepositoryImpl, JpaRepository, ...     │
└──────────────────────────────────────────┘
```

**의존성 방향**: 상위 → 하위만 허용. Domain Layer는 어떤 레이어에도 의존하지 않는다.

---

## Domain Layer

| 컴포넌트 | 책임 | 레퍼런스 |
|---------|------|---------|
| **Model** | JPA Entity, 도메인 행위 메서드, 비즈니스 규칙 캡슐화 | `domain/member/MemberModel.java` |
| **Value Object** | 불변 record, Compact Constructor에서 유효성 검증 | `domain/member/vo/MemberId.java` |
| **Service** | 트랜잭션, 교차 엔티티 규칙, Repository 호출 | `domain/member/MemberService.java` |
| **Repository** | 도메인 용어 인터페이스 (구현은 Infrastructure) | `domain/member/MemberRepository.java` |

**Model 설계 원칙**
- `BaseEntity` 상속 → `modules/jpa/src/main/java/com/loopers/domain/BaseEntity.java`
- 정적 팩토리 메서드 `create()`로 생성 시 검증 캡슐화
- 도메인 행위 메서드 (예: `changePassword()`, `decreaseStock()`)는 Model에 위치
- `protected` 기본 생성자 (JPA 요구사항)

**Value Object 설계 원칙**
- `record` 타입 (불변)
- Compact Constructor에서 null 체크 + 형식 검증 → `CoreException(ErrorType.BAD_REQUEST, ...)`
- 타 도메인 PK 참조 VO는 `RefOOOId` 명명 → `domain/common/vo/RefMemberId.java`

---

## Application Layer

### App vs Facade 선택

| 상황 | 선택 |
|------|------|
| 단일 도메인 유스케이스 | `{Domain}App` |
| 2개 이상 App 조합 필요 | `{Domain}Facade` |

### App 의존성 규칙

```
✅ App → Service  (비즈니스 로직·상태 변경 있을 때)
✅ App → Repository  (단순 조회, 비즈니스 규칙·상태 변경 없을 때)
✅ App에서 Model → Info 변환 후 반환
❌ App → App  (크로스 도메인은 Facade 책임)
❌ App → Facade
```

레퍼런스: `application/member/MemberApp.java`, `application/product/ProductApp.java`

### Facade 의존성 규칙

```
✅ Facade → App  (반드시 2개 이상 App 조합 시에만 생성)
❌ Facade → Service 직접 호출  (반드시 App 경유)
❌ Facade → Repository 직접 의존
❌ Facade → Facade
❌ 단일 App만 쓰는 Facade 생성  (Controller에서 App 직접 사용할 것)
```

레퍼런스: `application/order/OrderFacade.java`, `application/brand/BrandFacade.java`

### 어노테이션 규칙
- App → `@Component`
- Facade → `@Component`
- Service → `@Service`  ← 혼용 금지

---

## Interfaces Layer

| 컴포넌트 | 책임 | 레퍼런스 |
|---------|------|---------|
| Controller | 요청 수신, 입력 검증, DTO↔Info 변환 | `interfaces/api/member/MemberV1Controller.java` |
| ApiSpec | OpenAPI 문서화 인터페이스 | `interfaces/api/member/MemberV1ApiSpec.java` |
| Dto | Request/Response record | `interfaces/api/member/MemberV1Dto.java` |
| ApiResponse | 공통 응답 포맷 | `interfaces/api/ApiResponse.java` |
| ApiControllerAdvice | 전역 예외 처리 | `interfaces/api/ApiControllerAdvice.java` |

---

## Infrastructure Layer

| 컴포넌트 | 책임 | 레퍼런스 |
|---------|------|---------|
| RepositoryImpl | Domain Repository 구현, JpaRepository 위임 | `infrastructure/member/MemberRepositoryImpl.java` |
| JpaRepository | Spring Data JPA, @Query 정의 | `infrastructure/member/MemberJpaRepository.java` |
| Converter | VO ↔ DB 컬럼 변환 | `infrastructure/jpa/converter/MemberIdConverter.java` |

---

## 패키지 구조

```
com.loopers
├── domain/{domain}/         Model, Service, Repository interface, VO
│   └── vo/                  Value Objects, RefOOOId
├── application/{domain}/    App, Facade, Info, Command
├── infrastructure/{domain}/ RepositoryImpl, JpaRepository
│   └── jpa/converter/       VO Converters
├── interfaces/api/{domain}/ Controller, ApiSpec, Dto
└── support/error/           CoreException, ErrorType
```

---

## 크로스 도메인 오케스트레이션

연쇄 처리(cascade delete 등)는 Facade에서 App을 통해 조율한다.

```
// ✅ Facade → App 경유
brandApp.deleteBrand(brandId);
productApp.deleteProductsByBrandRefId(brandId);

// ❌ Facade → Service 직접 호출 금지
brandService.deleteBrand(brandId);
```

레퍼런스: `application/brand/BrandFacade.java`

---

## 도메인 간 의존성 규칙

**Model / Repository — 자기 도메인 VO만 사용**
```
✅ OrderModel → domain/order/vo/RefMemberId  (order 도메인 소유)
❌ OrderModel → domain/member/vo/MemberId    (도메인 경계 위반)
```

**Service — 타 도메인 Repository 직접 호출 허용** (트랜잭션 원자성 목적)
레퍼런스: `domain/like/LikeService.java`

**참조 VO (RefOOOId) 소유권**
- 사용하는 도메인이 자기 vo 패키지에 별도 정의
- 공통 참조 VO: `domain/common/vo/` 하위 확인

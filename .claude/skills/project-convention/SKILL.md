---
name: project-convention
description: "Use when writing or modifying any code in this project. Required before creating classes, methods, DTOs, tests, API endpoints, repositories, exception handling, or Swagger documentation. Applies to Controller, Facade, Service, Entity, Repository, DTO, VO, ErrorCode, ApiResponse, ApiSpec, QueryDSL, BaseEntity. Java Spring 계층형 아키텍처 컨벤션."
---

# Project Convention

## Overview

계층형 아키텍처(Interface → Application → Domain ← Infrastructure) 기반 코딩 컨벤션. 코드 작성/수정 전 해당 참조 파일을 반드시 읽는다.

## 아키텍처

```
Interface(Controller) → Application(Facade) → Domain(Entity + Service) ← Infrastructure
```

---

## Quick Reference

### 공통

**패키지 구조 — 계층 우선 + 도메인 하위**

```
com.loopers/
├── interfaces/                   ← Controller, ApiSpec, Request/Response DTO
│   ├── api/                      ← 공통 (ApiResponse, ControllerAdvice)
│   └── {domain}/                 ← 도메인별 Controller, DTO
├── application/                  ← Facade, Criteria/Result DTO
│   └── {domain}/
├── domain/                       ← Entity, VO, Service, Repository(I/F), ErrorCode
│   └── {domain}/
├── infrastructure/               ← Repository 구현, JPA, QueryDSL
│   └── {domain}/
└── support/                      ← error, config, util
```

**예외 구조**

```
CoreException → ErrorCode (interface)
                 ├── ErrorType (enum)       ← 공통 (support/error/)
                 └── XxxErrorCode (enum)    ← 도메인별 (domain/{domain}/)
```

**API 응답**

```java
ApiResponse.success(data)                                    // 성공
ApiResponse.fail(code, message)                              // 일반 에러
ApiResponse.failValidation(code, message, fieldErrors)       // Validation 에러
```

**계층별 DTO 네이밍**

| 계층 | 요청 | 응답 |
|------|------|------|
| **Interface** | `~Request` | `~Response` |
| **Application** | `~Criteria` | `~Result` |
| **Domain** | `~Command` | **Entity** 또는 `~Info` |

**테스트**

- **JUnit 5 + AssertJ + Mockito**, `@Nested` 행위별 그룹핑
- 메서드명: `{action}_{condition}`, 내부: arrange / act / assert
- 테스트 더블: Domain → **Fake**, Application → **Mockito**, E2E → **실제 Bean**
- DB 격리: `@AfterEach` + `DatabaseCleanUp.truncateAllTables()`

**인라인 변수 & 코드 스타일** — CLAUDE.md "코드 스타일 핵심" 참조. 상세: `common/inline-variable-convention.md`

---

### Interface 계층

**API 설계**

- **Prefix**: 고객 `/api/v1`, Admin `/api-admin/v1`
- **리소스**: 복수형, 소문자, 케밥케이스 (`/api/v1/products`)
- **HTTP 메서드**: GET 조회, POST 생성, PUT 수정(PATCH 미사용), DELETE 삭제
- **상태 코드**: 모든 성공 **200**, 에러는 `ErrorCode.getStatus()`
- **페이지네이션**: Offset 기반 (`page=0&size=20`)
- **엔드포인트**: 표준 CRUD / 중첩 리소스(2단계까지) / 소유자 기준 조회

**Controller 분리**

- 고객 `{Domain}V1Controller` / Admin `Admin{Domain}V1Controller`
- Facade 공유 가능, Admin 로직 커지면 분리

**Swagger (ApiSpec 인터페이스)**

- Swagger 어노테이션 → `{Domain}V1ApiSpec` / `Admin{Domain}V1ApiSpec`
- Spring MVC 어노테이션 → Controller에만
- Controller가 ApiSpec을 `implements`, `@Override` 명시
- 필수: `@Tag`(인터페이스), `@Operation`(메서드), `@Parameter`(파라미터)
- `@Schema`: 모호한 필드에만 추가

---

### Application 계층

- **Facade만 사용** (ApplicationService 없음)
- Facade: 유스케이스 조율, 도메인 간 흐름 조합, DTO 변환
- @Transactional: **메서드 레벨**, 조회는 `readOnly = true`
- Facade + Domain Service **양쪽 모두** @Transactional (REQUIRED 전파)
- Facade → 타 도메인 **Service 직접 호출** OK, 타 **Facade 호출 금지**

---

### Domain 계층

**Domain Service 허용 사항**

- `@Service`, `@Transactional` 사용 가능 (Facade와 REQUIRED 전파)
- `Page`, `Pageable` (Spring Data 페이지네이션) 사용 가능
- 금지: `@Component`, `@Repository`(Spring), `JpaRepository` 상속

**Entity**

- 생성: **정적 팩토리 메서드** (`Order.create(...)`)
- 접근: `@NoArgsConstructor(PROTECTED)`, Setter 금지
- 검증 훅: `guard()` override → `@PrePersist`/`@PreUpdate` 시 호출
- 로직 배치: 자기 필드로 완결 → Entity, 그 외 → Domain Service

**Value Object**

- **기본 원칙: VO를 만들지 않는다** — 검증/행위는 Entity 도메인 메서드 또는 Domain Service에서 처리
- 필드는 원시값(`int`, `String`, `LocalDate` 등)으로 선언
- 예외적 VO 생성 조건: 도메인 행위 2개 이상 + 여러 도메인 중복 + `record`로 구현 (`@Embeddable` 지양)
- 검증: 자기 필드 → Entity `private static validateXxx`, 외부 의존 → Domain Service

---

### Infrastructure 계층

**Repository 3-클래스 패턴**

- `domain/{domain}/OrderRepository` — 순수 Java 인터페이스 (Spring 의존 없음)
- `infrastructure/{domain}/OrderJpaRepository` — Spring Data JPA
- `infrastructure/{domain}/OrderRepositoryImpl` — `@Repository`, 어댑터

**QueryDSL**

- RepositoryImpl에 `JPAQueryFactory` 주입하여 직접 작성
- 메서드 5개 이상이면 `{Domain}QueryRepository`로 분리

**DB 규칙**

- **FK 미사용**: 같은 도메인 → 객체참조(`NO_CONSTRAINT`), 다른 도메인 → ID 참조
- **@OneToMany 미사용**: ID 참조 + 별도 Repository 조회
- **유니크 제약 사용**: 동시성 중복 방지
- **BaseEntity**: `modules/jpa` 제공. id, createdAt, updatedAt, deletedAt, guard(), delete(), restore()

---

## 상세 참조 가이드

**중요: 코드를 작성하거나 수정하기 전에, 해당 작업에 맞는 reference 파일을 반드시 Read 도구로 읽어라.**
경로는 이 SKILL.md 파일 기준 상대경로이며, 절대경로로 변환하여 읽는다.

### 작업별 참조 매트릭스

**어떤 작업을 하는가?** 아래에서 해당 작업을 찾고, 나열된 파일을 순서대로 읽어라.

| 작업 | 읽을 파일 (순서대로) |
|------|---------------------|
| **새 도메인/기능 개발** | `common/package-convention.md` → `domain/entity-vo-convention.md` → `application/service-layer-convention.md` → `common/dto-convention.md` |
| **Entity 생성/수정** | `domain/entity-vo-convention.md` |
| **Service/Facade 생성/수정** | `application/service-layer-convention.md` |
| **DTO 설계** | `common/dto-convention.md` |
| **Controller/API 추가** | `interfaces/api-convention.md` → `interfaces/swagger-convention.md` |
| **Repository 구현** | `infrastructure/infrastructure-convention.md` |
| **예외/에러코드 추가** | `common/exception-convention.md` |
| **테스트 작성** | `common/test-convention.md` |
| **코드 스타일 확인** | `common/inline-variable-convention.md` |

### 파일별 목차 (섹션 레벨 라우팅)

필요한 섹션만 골라 읽을 수 있도록 각 파일의 핵심 섹션을 나열한다.

**`common/package-convention.md`**
- § 전체 구조 — 계층별 패키지 배치
- § 계층별 클래스 배치 규칙 — 네이밍 규칙표
- § 의존 방향 규칙 — 계층/도메인 간 의존
- § 새 도메인 추가 가이드 — 새 도메인 패키지 생성 순서

**`common/exception-convention.md`**
- § 예외 구조 — ErrorCode 인터페이스, CoreException
- § 에러코드 규칙 — code 체계, 번호 규칙
- § API 응답 포맷 — ApiResponse 구조
- § ControllerAdvice 구조 — 예외 핸들러 우선순위
- § 도메인별 ErrorCode 추가 가이드 — 새 에러코드 생성 절차

**`common/dto-convention.md`**
- § 계층별 DTO 네이밍 — Request/Criteria/Command 구분
- § DTO 작성 규칙 — Inner Class, 변환 메서드 위치
- § 파라미터 전달 규칙 — 원시값 vs Command DTO 판단

**`common/test-convention.md`**
- § 테스트 피라미드 — 단위/통합/E2E 전략
- § 테스트 클래스 구조 — @Nested, arrange/act/assert
- § 테스트 더블 전략 — Fake vs Mockito 판단

**`common/inline-variable-convention.md`**
- § 핵심 원칙 — 인라인 판단 기준
- § 줄바꿈 & 들여쓰기 스타일 — Chop-down, 닫는 괄호

**`domain/entity-vo-convention.md`**
- § Entity 작성 규칙 — 정적 팩토리, 접근 제어, 구조 순서
- § VO 설계 규칙 — VO 생성 조건, @Embeddable
- § 검증 위치 규칙 — Entity vs Domain Service
- § Entity vs Domain Service 로직 배치 — 판단 플로우

**`application/service-layer-convention.md`**
- § Facade — 역할, 허용/금지, N+1 방지
- § Domain Service — 역할, CUD 반환 규칙
- § 트랜잭션 규칙 — 메서드 레벨, readOnly, 전파
- § 계층 간 호출 규칙 — 허용/금지 호출 목록

**`infrastructure/infrastructure-convention.md`**
- § Repository 패턴 — 3-클래스 패턴, Soft Delete 처리
- § QueryDSL 규칙 — 작성 위치, 분리 시점, 동적 정렬
- § BaseEntity — 제공 기능, 사용법
- § DB 제약조건 규칙 — FK 미사용, @OneToMany 미사용, 유니크

**`interfaces/api-convention.md`**
- § URL 구조 — prefix, 리소스 네이밍, 버전
- § HTTP 메서드/상태 코드 — PUT/PATCH, 200 통일
- § 엔드포인트 설계 패턴 — CRUD, 중첩, 소유자 기준
- § Controller 분리 규칙 — 고객/Admin, Facade 공유

**`interfaces/swagger-convention.md`**
- § ApiSpec 인터페이스 패턴 — 분리 이유, 구조
- § 어노테이션 규칙 — @Tag, @Operation, @Parameter
- § Controller 연결 — implements, Spring vs Swagger 어노테이션
- § DTO와 Schema 규칙 — @Schema 추가 기준

---

## Common Mistakes

| 실수 | 해결 |
|------|------|
| 1회용 변수를 추출하여 별도 선언 | 1회 참조 값은 인라인, 2회 이상일 때만 변수 추출 (`common/inline-variable-convention.md`) |
| `@OneToMany`로 연관관계 매핑 | `@OneToMany` 미사용 — ID 참조 + 별도 Repository 조회로 대체 (`infrastructure/infrastructure-convention.md`) |
| VO를 무조건 생성 (Money, Address 등) | **VO를 만들지 않는 것이 기본** — 도메인 행위 2개 이상 + 여러 도메인 중복일 때만 예외적 생성 (`domain/entity-vo-convention.md`) |
| Swagger 어노테이션을 Controller에 직접 작성 | Swagger 어노테이션은 `ApiSpec` 인터페이스에, Spring MVC 어노테이션은 Controller에 분리 (`interfaces/swagger-convention.md`) |
| Facade에서 타 도메인 Facade 호출 | Facade → 타 도메인 **Service 직접 호출**만 허용, Facade 간 호출 금지 (`application/service-layer-convention.md`) |

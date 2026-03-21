---
name: coding-standards
description: 네이밍 규칙, 타입 사용 패턴 (Entity/VO/DTO), 의존성 주입, 예외 처리, JPA Converter, 트랜잭션. 코드 작성 시 참조
user-invocable: true
allowed-tools: Read, Grep
---

# 코딩 표준

> 경로 prefix: `apps/commerce-api/src/main/java/com/loopers/`

---

## 네이밍 규칙

| 타입 | 패턴 | 위치 |
|------|------|------|
| Entity | `{Domain}Model` | `domain/{domain}/` |
| Value Object | `{Concept}` | `domain/{domain}/vo/` |
| Service | `{Domain}Service` | `domain/{domain}/` |
| Repository Interface | `{Domain}Repository` | `domain/{domain}/` |
| Repository Impl | `{Domain}RepositoryImpl` | `infrastructure/{domain}/` |
| JPA Repository | `{Domain}JpaRepository` | `infrastructure/{domain}/` |
| Controller | `{Domain}V{n}Controller` | `interfaces/api/{domain}/` |
| API Spec | `{Domain}V{n}ApiSpec` | `interfaces/api/{domain}/` |
| DTO | `{Domain}V{n}Dto` | `interfaces/api/{domain}/` |
| App | `{Domain}App` | `application/{domain}/` |
| Facade | `{Domain}Facade` | `application/{domain}/` |
| Info | `{Domain}Info` | `application/{domain}/` |
| Converter | `{ValueObject}Converter` | `infrastructure/jpa/converter/` |

> **App vs Facade 기준**: 단일 도메인 → App / 2개 이상 App 조합 → Facade

---

## 메서드 네이밍

### Repository
- 조회: `findBy{Condition}`, `findAllBy{Condition}`
- 존재 확인: `existsBy{Condition}`
- 저장: `save`, `saveAll`
- 삭제: `delete`, `deleteBy{Condition}`

### 타 도메인 PK 파라미터 메서드 — `RefId` 접미사 필수

```
// ✅ 올바름
ProductModel getProductByRefId(Long id)
void deleteProductsByBrandRefId(Long brandId)

// ❌ 금지
ProductModel getProductByDbId(Long id)
```

### 변수 및 상수
- 변수: `camelCase`
- boolean: `is`, `has`, `can` 접두사
- 상수: `UPPER_SNAKE_CASE` + `static final`

---

## 타입 사용 규칙

| 타입 | Java 타입 | 이유 |
|------|----------|------|
| Entity | `class` | 가변 상태 (JPA dirty checking) |
| Value Object | `record` | 불변, 검증 캡슐화 |
| DTO (Request/Response) | `record` | 불변 |
| Info | `record` | 불변 |
| Command | `record` | 불변 |

레퍼런스:
- Entity: `domain/member/MemberModel.java`
- VO: `domain/member/vo/MemberId.java`, `domain/brand/vo/BrandId.java`
- DTO: `interfaces/api/member/MemberV1Dto.java`
- Info: `application/member/MemberInfo.java`

---

## 의존성 주입

`@RequiredArgsConstructor` + `private final` — 항상 생성자 주입 사용.

```
// ❌ 필드 주입 금지
@Autowired
private MemberRepository memberRepository;
```

---

## 예외 처리

- 비즈니스 예외: `CoreException(ErrorType, message)` 사용
- 레퍼런스: `support/error/CoreException.java`, `support/error/ErrorType.java`
- 전역 처리: `interfaces/api/ApiControllerAdvice.java`

| 상황 | ErrorType |
|------|-----------|
| 입력 검증 실패, 중복, 비밀번호 불일치 | `BAD_REQUEST` |
| 리소스 없음 | `NOT_FOUND` |
| 중복 리소스 | `CONFLICT` |
| 서버 오류 | `INTERNAL_ERROR` |

---

## API 응답 구조

```json
// 성공
{ "meta": { "result": "SUCCESS", "errorCode": null, "message": null }, "data": { ... } }

// 실패
{ "meta": { "result": "FAIL", "errorCode": "Not Found", "message": "..." }, "data": null }
```

레퍼런스: `interfaces/api/ApiResponse.java`

---

## JPA 관련

JPA 패턴 (BaseEntity, Converter, @Query, Soft Delete, Dirty Checking) → `/jpa-database` 스킬 참조

---

## 트랜잭션

- `@Transactional` 은 Service 계층에 적용 (Controller, App 에는 사용하지 않음)
- 읽기 전용 작업: `@Transactional(readOnly = true)`
- 쓰기 작업: `@Transactional`
- Repository 계층에는 적용하지 않음

레퍼런스: `domain/member/MemberService.java`

---

## Null Safety

- `null` 반환 금지 → `Optional` 사용 또는 `orElseThrow()` 로 예외 발생
- 조회 결과를 Optional로 위임: `Optional<T> findBy...`
- 없으면 예외: `.orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "..."))`

---

## 코드 스타일

- 한 줄 최대 120자
- Import 순서: Java 표준 → 외부 라이브러리 → Spring → 프로젝트 내부
- Lombok: `@Getter`(Entity), `@RequiredArgsConstructor`(Service/Controller), `@Slf4j`(로깅 필요 클래스)

---

## 핵심 규칙

```
❌ var 사용 금지  →  Optional<ProductModel> product = ...
❌ class/record 내부 nested 정의 금지  →  별도 파일로 분리 (OrderItemCommand.java)
❌ System.out.println  →  @Slf4j + log.info/warn/error
❌ null 반환  →  Optional 또는 orElseThrow
❌ Magic Number  →  상수로 정의
❌ Exception Swallowing  →  예외를 무시하지 말 것
```

아키텍처 의존성 규칙 (App/Facade/Service 간) → `/architecture` 스킬 참조

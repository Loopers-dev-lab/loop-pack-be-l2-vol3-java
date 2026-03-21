# Loopers Commerce Platform

## 기술 스택
- **Java 21** + **Spring Boot 3.4.4** + **Gradle (Kotlin DSL)**
- **JPA + QueryDSL**, **Redis**, **Kafka**, **MySQL 8.x**
- **TestContainers**, **JUnit 5**, **AssertJ**, **Mockito**
- **SpringDoc OpenAPI 2.7.0**, **Prometheus + Grafana**

## 주요 명령어
```bash
./gradlew :apps:commerce-api:bootRun
./gradlew :apps:commerce-api:test
./gradlew test
```

---

## 모듈 구조
```
Root
├── apps/commerce-api      # REST API
├── apps/commerce-batch    # 배치
├── apps/commerce-streamer # Kafka 스트리밍
├── modules/jpa|redis|kafka
└── supports/jackson|logging|monitoring
```
- apps → modules, supports 의존 가능
- modules ↔ modules, supports ↔ supports 상호 의존 금지

---

## 아키텍처
```
Interfaces Layer (Controller, ApiSpec, Dto)
    ↓
Application Layer (App, Facade, Info)
    ↓
Domain Layer (Model, Service, Repository, VO)
    ↓
Infrastructure Layer (RepositoryImpl, JpaRepository, Converter)
```

**의존성 방향**
- 단일 도메인 + 비즈니스 로직: `Controller → App → Service → Repository`
- 단일 도메인 + 단순 조회: `Controller → App → Repository`
- 크로스 도메인: `Controller → Facade → App(2개 이상) → Service/Repository`

**어노테이션**: App `@Component`, Facade `@Component`, Service `@Service` — 혼용 금지

상세 내용 → `/architecture` skill

---

## 네이밍 규칙

| 타입 | 패턴 | 예시 |
|------|------|------|
| Entity | `{Domain}Model` | `MemberModel` |
| Value Object | `{Concept}` | `MemberId`, `Email` |
| Service | `{Domain}Service` | `MemberService` |
| Repository | `{Domain}Repository` / `Impl` / `JpaRepository` | `MemberRepository` |
| Controller | `{Domain}V{n}Controller` | `MemberV1Controller` |
| DTO | `{Domain}V{n}Dto` | `MemberV1Dto` |
| App | `{Domain}App` | `MemberApp` |
| Facade | `{Domain}Facade` — 2개 이상 App 조합 시에만 | `OrderFacade` |
| Info | `{Domain}Info` | `MemberInfo` |
| Converter | `{ValueObject}Converter` | `MemberIdConverter` |

- 타 도메인 PK 파라미터 메서드: `RefId` 접미사 (`DbId` 금지)
  - 예: `getProductByRefId(Long id)`
- 타입 사용: Entity `class`, Value Object `record`, DTO `record`
- 의존성 주입: `@RequiredArgsConstructor` + `private final`

---

## Never Do (절대 금지)
- ❌ 불필요한 주석 — 로직이 자명하지 않은 경우에만 작성
- ❌ `var` 키워드 → 명시적 타입 선언
- ❌ `EntityManager` 직접 사용 → `JpaRepository @Query`
- ❌ `System.out.println` → `@Slf4j`
- ❌ null 반환 → `Optional` 활용
- ❌ 테스트 `@Disabled` 또는 assertion 약화
- ❌ class/record 내부 nested class/record → 별도 파일 분리
- ❌ App → App 의존 (크로스 도메인은 Facade 책임)
- ❌ Facade → Facade 의존
- ❌ Facade → Service 직접 호출 → 반드시 App 경유
- ❌ 단일 App만 쓰는 Facade 생성 → App 직접 사용
- ❌ 비즈니스 로직(검증·상태변경)이 있는 경우 App → Repository 직접 의존 → Service 경유
- ❌ AI가 자의적으로 커밋 실행 — 커밋 메시지만 제안

---

## 과정 기록 (필수)

의사결정, 실험 결과, 트레이드오프 논의는 **`docs/discussion/{현재 브랜치명}-discussion.md`** 에 기록.
파일이 없으면 대화 시작 시 생성. 있으면 기존 내용에 **추가(append)**.

기록 대상: 설계 방향 선택, 구현 방식 변경, 테스트 실행 결과(PASS/FAIL·수치·EXPLAIN), 기각된 대안

```markdown
## [날짜] 주제

### 상황
### 실험/테스트 기록
### 결정
- 선택: ...
- 이유: (테스트 결과 기반으로 구체적으로)
### 기각된 대안
### 다음 단계
```

---

## 의사결정 원칙 (필수)

AI는 아래 시점마다 **반드시 멈추고 개발자에게 승인**을 요청한다.

1. **구현 시작 전**: 구현 내용·영향 범위·예상 파일 목록 보고 후 승인 대기
2. **설계 방향 결정**: 최소 2개 이상 대안을 제시하고 개발자 선택 후 구현 시작
3. **기존 코드 수정**: 수정 대상 파일과 변경 이유 명시 후 승인 대기

**대안 제시 형식**
```
A: [방법명] — 방식 / 장점 / 단점
B: [방법명] — 방식 / 장점 / 단점
어떤 방식으로 진행할까요?
```

- ❌ 대안 없이 AI가 "더 나은" 방법을 바로 구현하는 행위
- ❌ 대안 설명과 동시에 구현 시작
- ❌ "A 방식으로 진행하겠습니다"처럼 AI가 선택을 결론 짓는 행위

---

## 커밋 원칙
- **1 논리 단위 = 1 커밋** (`type(scope): 설명`)
- 여러 기능/도메인/레이어 변경을 하나의 커밋에 혼합 금지
- AI는 커밋 메시지만 제안, 실행은 개발자

## 단계적 구현 원칙
가장 단순한 것부터 시작해 테스트로 검증 후 점진적 고도화
예: 캐시 → (1) in-memory → (2) TTL → (3) Redis 분산 캐시

## 환경 프로파일
`local` 로컬 / `test` TestContainers / `dev` `qa` `prd` 서버

---

## Skills (상세 가이드)
- `/architecture` — 레이어·의존성·패키지 구조·코드 예시
- `/coding-standards` — 네이밍·타입·예외·JPA Converter
- `/testing` — 단위/통합/E2E 테스트 전략
- `/development-workflow` — TDD Red→Green→Refactor
- `/jpa-database` — JPA 패턴·N+1·Soft Delete
- `/domain-rules` — 도메인 비즈니스 규칙
- `/analyze-query` — 트랜잭션·쿼리 분석
- `/requirements-analysis` — 요구사항 설계
- `/pr-creation` — PR 작성
- `/commit-plan` — 현재 브랜치 구현 방향·진행 상태를 docs/plan/{브랜치명}-plan.md 로 정리
- `/analyze-external-integration` — 외부 시스템 연동 설계 리뷰 (트랜잭션 경계·상태 불일치·장애 시나리오·Observability)
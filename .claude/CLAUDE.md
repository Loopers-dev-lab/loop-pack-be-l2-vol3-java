## 프로젝트 개요

Spring Boot 기반 멀티모듈 Java 프로젝트. TDD 방식으로 개발하며, 테스트 가능한 구조를 목표로 한다.

---

## 기술 스택

- **Java 21**, **Spring Boot 3.4.4**, **Gradle Kotlin DSL**
- Spring Web, Spring Data JPA, Spring Data Redis, QueryDSL, Kafka, Spring Batch
- Lombok, Jackson, SpringDoc OpenAPI
- JUnit 5 + AssertJ + Mockito, Testcontainers (MySQL, Redis)

---

## 모듈 구조

```
apps/
├── commerce-api          # REST API 서버
├── commerce-streamer     # Kafka 스트리밍 처리
└── commerce-batch        # 배치 작업

modules/
├── jpa                   # BaseEntity, QueryDSL/JPA/DataSource Config
├── redis                 # Redis 설정 및 Repository
└── kafka                 # Kafka 설정 및 Producer/Consumer

supports/
├── jackson               # JSON 직렬화 설정
├── logging               # 로깅 설정
└── monitoring            # 모니터링 설정
```

---

## 아키텍처

계층 우선 패키지: `interfaces → application → domain ← infrastructure`

### 컨벤션

- 코딩 컨벤션: `.claude/skills/project-convention/` 참조 (코드 작성 시 해당 스킬의 references/ 하위 문서를 반드시 Read 도구로 읽을 것)
- 커밋 규칙: `.claude/skills/commit-convention/` 참조

### 코드 스타일 핵심 (매 작업 시 준수)

- 1회용 변수는 인라인 (2회 이상 참조 시에만 변수 추출)
- 메서드 체이닝 줄바꿈: 8-space continuation indent
- 닫는 괄호: 마지막 인자에 붙임 (별도 줄 금지)
- 컨텍스트가 길어질수록 컨벤션 누락 가능성 증가 → 코드 작성 전 관련 컨벤션 문서를 반드시 다시 Read할 것
- **기능 완료 후 커밋 전**: `.claude/agents/convention-review/AGENT.md`를 읽고 서브 에이전트(Sonnet) 리뷰를 실행할 것

### 설계 문서

기능 개발 시 해당 도메인의 설계 문서를 **먼저 읽고** 시작한다.

| 문서 | 경로 | 용도 |
|------|------|------|
| 공통 원칙 | `docs/design/_shared/CONVENTIONS.md` | 참조 방식, Soft Delete, 용어집 등 |
| 전체 구조 | `docs/design/_shared/OVERVIEW.md` | 전체 ERD + 클래스 다이어그램 |
| 도메인 스펙 | `docs/design/{domain}/DESIGN.md` | 요구사항 + 유즈케이스 + 시퀀스 + ERD + 클래스 |

도메인: `brand`, `product`, `like`, `cart`, `order`, `coupon`

**참조 규칙:**
1. 해당 도메인의 `DESIGN.md`를 읽는다
2. 다른 도메인과 연동이 필요하면 그 도메인의 `DESIGN.md`도 읽는다
3. 전체 관계 확인이 필요하면 `OVERVIEW.md`를 읽는다
4. 구현 완료 후 `DESIGN.md`의 **'예외 및 정책'** 섹션과 **유즈케이스 기능 흐름**을 재확인하여, 각 항목이 코드에 반영되었는지 대조한다

---

## TDD 개발 모드

"TDD로 개발" 트리거 시 `.claude/skills/tdd/SKILL.md`를 읽고 시작한다. 아래 규칙은 **Round 진행 중 매 턴 적용**.

### 핵심 규칙

- Red 1개 → Green → Refactor → 다음 Red. **한 번에 여러 테스트 작성 금지**
- Red를 반드시 실행하여 **실패를 확인**한 후 Green 진행
- Green은 **통과할 최소 코드만**. 다음 시나리오까지 미리 구현 금지
- 기능 수직 슬라이스: 기능 하나를 Domain → Application 관통 후 다음 기능
- 매 Round 후 진행 문서(`docs/tdd/{domain}/{feature}.md`) 갱신

### 계층별 전략

| 계층 | 테스트 더블 | TDD 방식 |
|------|-----------|---------|
| Domain Entity/VO | 더블 불필요 | TFD |
| Domain Service | **Fake 우선** | TFD |
| Application Facade | Mockito mock() | TFD |
| Controller / Repository | - | TLD (별도 진행) |

### 테스트 실행

- Round 중: `./gradlew :apps:commerce-api:test --tests "{패키지}.{클래스}"`
- 전체 완료 후: `./gradlew :apps:commerce-api:test`

### 코드 작성

- 실제 동작하는 코드만. 불필요한 Mock 데이터 금지
- null-safety (Optional 활용), `println` 금지
- 기존 코드 패턴 분석 후 일관성 유지

---

## 프로젝트 실행

```bash
./gradlew :apps:commerce-api:bootRun    # 개발 환경
./gradlew :apps:commerce-api:test       # 특정 모듈 테스트
./gradlew test jacocoTestReport         # 커버리지
docker compose up -d                    # 인프라
```

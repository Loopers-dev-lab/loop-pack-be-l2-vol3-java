---
name: code-review
description:
  현재 브랜치의 변경사항을 CLAUDE.md 규칙 준수와 일반 코드 품질 관점에서 리뷰합니다.
  아키텍처, 도메인 설계, 테스트 컨벤션, 보안, 성능 등을 체크하고 심각도별로 분류하여 결과를 출력합니다.
---
코드 리뷰를 수행할 때 반드시 다음 흐름을 따른다.

### 1️⃣ 변경 사항을 수집한다
- `git diff main...HEAD --stat`으로 변경 파일 목록을 확인한다.
- `git diff main...HEAD`로 전체 변경 내용을 파악한다.
- 변경된 파일의 전체 코드를 읽어 주변 컨텍스트를 이해한다.

> **근거**: diff만으로는 변경의 의도와 영향을 파악하기 어렵다. 파일 전체를 읽어야 아키텍처 위반, 도메인 로직 위치 등을 정확히 판단할 수 있다.

### 2️⃣ 프로젝트 규칙 준수를 리뷰한다
CLAUDE.md에 정의된 규칙을 기준으로 다음 카테고리를 검증한다:

**아키텍처 & 의존성**
- 의존성 방향: `interfaces → application → domain ← infrastructure` 위반 여부
- domain 레이어가 상위 레이어(application, interfaces, infrastructure)를 import하면 Critical
- Repository interface는 domain, 구현체는 infrastructure에 위치하는지

**패키징 구조**
- `*Dto.java` → `interfaces/api/{domain}/v1/`
- `*Result.java` → `application/{domain}/`
- `*Repository.java`(interface) → `domain/{domain}/`
- `*RepositoryImpl.java` → `infrastructure/{domain}/persistence/`
- `*ApiSpec.java` → `interfaces/api/{domain}/v1/`

**도메인 설계**
- Entity: `@NoArgsConstructor(access = PROTECTED)` + static factory method 패턴
- Value Object: `@Embeddable`, 생성자 검증, `@EqualsAndHashCode`
- 비즈니스 로직이 Service가 아닌 Domain 객체에 위치하는지
- 여러 Service에서 반복되는 규칙이 Domain 객체로 추출되었는지
- CoreException 기반 도메인 검증

**테스트 컨벤션**
- 파일 suffix: `*Test` / `*IntegrationTest` / `*E2ETest`
- `@DisplayName("기능을 수행할 때,")` + `@Nested` 구조 사용 여부
- AssertJ 전용 (`assertThat`, `assertThatThrownBy`) — JUnit Assertions 혼용 금지
- `DatabaseCleanUp.truncateAllTables()` in `@AfterEach`

> **근거**: CLAUDE.md에 정의된 규칙은 프로젝트의 일관성을 유지하는 핵심이다. 이를 체계적으로 검증하여 리뷰 품질을 보장한다.

### 3️⃣ 일반 코드 품질을 리뷰한다
CLAUDE.md 규칙 외에 다음 관점에서 검토한다:

**보안 & 안정성**
- SQL Injection, XSS 등 OWASP Top 10 취약점
- 입력 검증 누락 (Controller → Service 경계)
- 민감 정보 노출 (로그, 응답에 비밀번호 등 포함 여부)

**성능 & 효율성**
- N+1 쿼리 패턴
- 불필요한 DB 조회 (루프 내 조회, 사용하지 않는 fetch join)
- 비효율적 컬렉션 처리 (반복문 내 stream 재생성 등)

**버그 가능성**
- NPE 위험 (Optional 미사용, null 반환)
- 경계값 처리 누락
- 동시성 이슈 (공유 상태 변경)
- 트랜잭션 범위 문제 (너무 넓거나 누락)

**코드 구조 & 가독성**
- SOLID 원칙 위반 (특히 SRP, DIP)
- God Object / God Method (과도한 책임 집중)
- 중복 코드 (3회 이상 반복 시 추출 대상)
- 메서드/변수 네이밍의 의도 전달력
- Lombok 활용 (`@Getter`, `@RequiredArgsConstructor` 등)

> **근거**: 프로젝트 규칙 준수만으로는 코드 품질을 보장할 수 없다. 보안, 성능, 설계 관점의 리뷰가 함께 이루어져야 한다.

### 4️⃣ 리팩토링을 제안한다
- 각 지적 사항에 대해 구체적인 수정 방향을 제시한다.
- Critical과 High 항목에는 Before/After 코드 예시를 포함한다.
- 관련된 이슈는 그룹화하여 한 번에 수정할 수 있도록 안내한다.

> **근거**: "무엇이 문제인지"만 지적하면 실행력이 떨어진다. "어떻게 고칠지"를 함께 제시해야 리뷰의 가치가 높아진다.

### 5️⃣ 결과를 심각도별로 분류하여 출력한다
다음 형식으로 터미널에 출력한다:

```
## 코드 리뷰 결과

### 요약
- 변경 파일: N개
- 발견 사항: 🔴 Critical N개, 🟠 High N개, 🟡 Medium N개, 🟢 Low N개
- Quick Win: N개 (적은 노력으로 큰 개선 효과)

### 🔴 Critical (즉시 수정 필요)
아키텍처 위반, 보안 취약점, 시스템 장애 위험

- [ ] `파일명:라인` — 설명
  - 근거: ...
  - Before: `기존 코드`
  - After: `개선 코드`

### 🟠 High (수정 권장)
성능 저하, 유지보수성 악화, 버그 가능성

- [ ] `파일명:라인` — 설명
  - 근거: ...
  - Before: `기존 코드`
  - After: `개선 코드`

### 🟡 Medium (개선 권장)
컨벤션 위반, 가독성 저하

- [ ] `파일명:라인` — 설명 (근거: ...)

### 🟢 Low (선택적 개선)
스타일, 문서화, 사소한 개선

- `파일명:라인` — 설명

### 잘한 점
- 긍정적인 설계, 패턴 적용, 컨벤션 준수 사례를 구체적으로 언급
```

- 발견 사항이 없는 심각도 섹션은 생략한다.
- 모든 발견 사항이 없으면 "발견 사항 없음. 코드가 잘 작성되어 있습니다."를 출력한다.

> **근거**: 4단계 심각도와 Quick Win 식별로 수정 우선순위를 명확히 한다. Before/After 예시는 실행력을 높인다. 잘한 점을 함께 제시하여 균형 잡힌 리뷰를 제공한다.

### 톤 & 스타일 가이드
- 모든 지적에는 CLAUDE.md 규칙 또는 일반 원칙의 근거를 함께 제시한다
- Critical/High에는 반드시 Before/After 코드 예시를 포함한다
- 관련된 이슈를 그룹화하여 효율적으로 수정할 수 있도록 안내한다
- 잘한 점 섹션을 반드시 포함하여, 좋은 설계와 패턴 적용을 인정한다
- 각 지적에는 구체적인 `파일명:라인번호`를 포함한다
- WHY(왜 문제인지)를 먼저 설명하고, HOW(어떻게 고칠지)를 제시한다

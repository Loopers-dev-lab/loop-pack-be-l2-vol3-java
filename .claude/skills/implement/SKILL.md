---
name: implement
description: 기능 명세서(spec) 기반으로 계층별 코드를 구현하고 테스트를 작성합니다. "/implement product/001", "spec 보고 구현해줘", "기능 구현해줘"를 요청할 때 사용합니다. 명세서 작성은 spec-writer, 설계 문서는 design-writer를 사용하세요.
argument-hint: "{epic}/{NNN-feature-name}"
---

# Implement

기능 명세서(spec)를 읽고, 프로젝트 규칙을 준수하며, 계층별 코드를 구현하고 테스트를 작성합니다.

## 6-Phase 워크플로우

```
READ   → spec 읽기 + AC 추출 + rules 상기
SCAN   → 기존 도메인 패턴 파악 + 변경 범위 공유
BUILD  → domain → infra → application → interfaces 순서대로 구현
TEST   → 단위 → 통합 → E2E 작성
VERIFY → 컴파일 + 테스트 실행 (실패 시 수정 루프)
REPORT → AC 매핑 표 + 파일 목록 보고
```

---

## Phase 0: 인자 확인

인자 없이 호출된 경우(`/implement`만), spec 경로를 사용자에게 확인한다.

- `docs/specs/` 하위 디렉토리를 탐색하여 사용 가능한 spec 목록을 보여준다
- 사용자가 선택하면 해당 spec으로 Phase 1을 시작한다

---

## Phase 1: READ

명세서를 읽고 AC를 추출합니다.

### 절차

1. 요구사항 정의서가 있으면 읽는다: `docs/requirements/{epic}.md`
2. `docs/specs/{epic}/{NNN-feature-name}.md` 파일을 읽는다
3. 설계 문서가 있으면 함께 읽는다:
   - 루트: `docs/design/domain-map.md`, `docs/design/erd.md` (있는 경우)
   - Epic: `docs/design/{epic}/` (class-diagram, sequence 등 — 있는 경우)
   - 설계 문서가 없으면 spec과 rules만으로 진행한다 (별도 안내 불필요)
4. AC를 추출하고 각 AC의 테스트 유형을 분류한다

### AC → 테스트 유형 매핑 기준

| AC 성격 | 테스트 유형 | 예시 |
|---------|-----------|------|
| Entity 불변식, 검증 규칙 | 단위 테스트 | "이름은 1~50자" |
| DB 조회 필요 (중복, 존재 확인) | 통합 테스트 | "이름 중복이면 409" |
| HTTP 요청/응답 전체 흐름 | E2E 테스트 | "유효한 정보로 등록하면 200" |

### 사용자에게 공유

```
## READ 결과

**Spec**: {파일 경로}

### AC 분석
| # | AC | 테스트 유형 |
|---|-----|-----------|
| 1 | ... | 단위 |
| 2 | ... | 통합 |
| 3 | ... | E2E |

이대로 진행할까요?
```

**중요**: 사용자 확인 후 다음 단계로 진행한다.

---

## Phase 2: SCAN

기존 코드 패턴을 파악하고 변경 범위를 도출합니다.

### 절차

1. 같은 도메인의 기존 코드가 있으면 패턴 파악 (Entity, Repository, Service, Controller)
2. 유사한 다른 도메인의 코드를 참고하여 패턴 확인
   - 참고할 기존 도메인이 없으면(프로젝트 첫 도메인), rules 파일의 계층별 역할과 코드 배치 순서를 기준으로 구현한다
3. 생성/수정할 파일 목록과 변경 범위를 사용자에게 공유

### 규칙 상기

이 단계에서 `.claude/rules/` 하위 규칙 파일들을 읽고 코드 배치 순서, 검증 위치, 계층별 역할을 상기한다.

### 사용자에게 공유

```
## SCAN 결과

### 참고 패턴: {참고 도메인}

### 변경 범위
| 파일 | 작업 | 비고 |
|------|------|------|
| domain/{Domain}.java | 생성 | Entity |
| domain/{Domain}Repository.java | 생성 | Repository 인터페이스 |
| ... | ... | ... |
```

---

## Phase 3: BUILD

계층 순서대로 구현합니다.

### 구현 순서

```
1. domain    → Entity, Repository 인터페이스
2. infra     → RepositoryImpl, JpaRepository
3. application → Service, Facade, Command, Info DTO
4. interfaces → Controller, ApiSpec, Request DTO, Response DTO (V1Dto)
```

### 점진적 실행

- 한 계층씩 구현하고 다음 계층으로 넘어간다
- 계층 간 의존성이 명확하므로 순서를 지킨다

---

## Phase 4: TEST

`test-patterns` 스킬의 패턴을 따라 테스트를 작성합니다.

### 절차

1. `.claude/skills/test-patterns/SKILL.md`를 읽고 테스트 패턴을 확인한다
   - 스킬을 찾을 수 없으면 `.claude/rules/` 하위 테스트 관련 규칙과 Phase 4의 필수 준수 사항을 기준으로 작성한다
2. Phase 1에서 분류한 AC별 테스트 유형에 따라 작성한다

### 작성 순서

```
1. 단위 테스트   → Entity 불변식, 검증 규칙
2. 통합 테스트   → Service + DB (중복 체크, 존재 확인)
3. E2E 테스트   → HTTP 전체 흐름
```

---

## Phase 5: VERIFY

컴파일과 테스트를 실행하고, 실패 시 수정합니다.

### 절차

1. 컴파일: `./gradlew :apps:commerce-api:compileJava`
2. 테스트: `./gradlew :apps:commerce-api:test`
3. 실패 시 → 원인 분석 → 수정 → 재실행 (최대 3회)
4. 3회 초과 실패 시 사용자에게 보고하고 방향 확인

---

## Phase 6: REPORT

구현 결과를 정리하여 보고합니다.

### 보고 형식

```
## 구현 완료: {기능명}

### AC 매핑
| # | AC | 테스트 | 결과 |
|---|-----|-------|------|
| 1 | ... | {테스트클래스}#메서드명 | ✅ |
| 2 | ... | {테스트클래스}#메서드명 | ✅ |

### 생성/수정 파일
| 파일 | 작업 |
|------|------|
| ... | 생성 |
| ... | 수정 |

### 테스트 결과
- 단위: N개 통과
- 통합: N개 통과
- E2E: N개 통과

검증과 커밋은 확인 후 진행해주세요.
구현이 문서/규칙과 일치하는지 확인하려면 `/implement-review`를 실행하세요.
```

---

## 예시

사용자: `/implement product/001-product-register`

1. **READ**: requirements + spec + design 읽기 → AC 추출 → 테스트 유형 분류 → 사용자 확인
2. **SCAN**: Brand 도메인 패턴 참고 → 생성 파일 목록 공유
3. **BUILD**: Product Entity → ProductRepository → ProductService → ProductFacade → ProductController 순서로 구현
4. **TEST**: ProductTest(단위) → ProductServiceIntegrationTest(통합) → ProductAdminApiE2ETest(E2E)
5. **VERIFY**: 컴파일 + 테스트 실행 → 실패 시 수정
6. **REPORT**: AC 매핑 표 + 파일 목록 + 테스트 결과

## 트러블슈팅

### spec 파일이 없는 경우
- 사용자에게 알리고 `spec-writer` 스킬 사용을 안내한다
- "해당 기능의 명세서가 없습니다. `/spec-writer`로 먼저 명세서를 작성할까요?"

### 기존 코드와 충돌하는 경우
- 기존 코드의 패턴을 우선한다
- 차이점을 사용자에게 공유하고 방향을 확인받는다

### AC가 테스트로 변환하기 어려운 경우
- 해당 AC를 사용자에게 공유하고 구체화를 요청한다
- "이 AC는 테스트로 변환하기 어렵습니다: {AC 내용}. 구체적인 검증 기준을 알려주세요."

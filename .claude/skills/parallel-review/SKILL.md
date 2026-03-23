---
name: parallel-review
description: 구현 코드를 3개 병렬 에이전트로 검증합니다(문서 정합성, 테스트 품질, 규칙 준수). "/parallel-review payment/006", "병렬 리뷰해줘", "빠르게 검증해줘"를 요청할 때 사용합니다. 순차 검증은 implement-review를 사용하세요.
argument-hint: "{epic}/{NNN-feature-name}"
---

# Parallel Review

구현 코드를 **3개 병렬 에이전트**로 동시 검증합니다.

핵심 원리: **검증은 병렬(Read-Many), 수정은 직렬(Write-One)**
- 검증 에이전트는 읽기 전용 — 파일을 수정하지 않으므로 병렬 실행해도 충돌 없음
- 수정은 사용자가 리포트를 확인한 후 별도로 지시

## 검증축 레지스트리

| 축 ID | 축 이름 | 체크리스트 경로 | 프롬프트 경로 | 리포트 파일명 |
|-------|---------|---------------|-------------|-------------|
| doc | 문서 정합성 | `implement-review/references/doc-checks.md` | `agent-prompts/doc-agent.md` | `doc-report.md` |
| test | 테스트 품질 | `implement-review/references/test-checks.md` | `agent-prompts/test-agent.md` | `test-report.md` |
| rule | 규칙 준수 | `implement-review/references/rule-checks.md` | `agent-prompts/rule-agent.md` | `rule-report.md` |

새 축 추가 시: 체크리스트 파일 + 프롬프트 템플릿 작성 → 이 테이블에 행 추가.

---

## 3-Phase 워크플로우

```
COLLECT  → 문맥 수집 (문서 + 파일 경로 + 체크리스트 + 프롬프트 템플릿)
DISPATCH → 레지스트리의 모든 축에 대해 에이전트를 동시 spawn (Agent tool 병렬 호출)
SUMMARIZE → 결과 수집 → 파일 저장 → 대화에 요약 표시
```

---

## Phase 0: 인자 확인

인자 없이 호출된 경우 (`/parallel-review`만), spec 경로를 사용자에게 확인한다.

- `docs/specs/` 하위 디렉토리를 탐색하여 사용 가능한 spec 목록을 보여준다
- 사용자가 선택하면 해당 spec으로 Phase 1을 시작한다

---

## Phase 1: COLLECT

읽기 전용. 사용자 확인 없이 바로 진행한다.

### 절차

1. **spec 읽기**: `docs/specs/{epic}/{NNN-feature-name}.md`
2. **requirements 읽기**: `docs/requirements/{epic}.md` (없으면 건너뜀)
3. **design 읽기**: `docs/design/{epic}/` 하위 파일 (없으면 빈 문자열)
4. **구현 파일 경로 수집**: Glob으로 해당 도메인의 `domain/`, `infrastructure/`, `application/`, `interfaces/` 파일 목록
   - 대상 패키지는 spec의 도메인명으로 판단 (예: `payment` → `com/loopers/**/payment/**/*.java`)
5. **테스트 파일 경로 수집**: Glob으로 해당 도메인의 테스트 파일 목록
   - `src/test/java/**/` 하위에서 도메인 관련 테스트 파일
6. **레지스트리 순회**: 각 축의 체크리스트와 프롬프트 템플릿 읽기
   - 체크리스트: `.claude/skills/{체크리스트 경로}`
   - 프롬프트 템플릿: `.claude/skills/parallel-review/references/{프롬프트 경로}`
7. **리포트 포맷 읽기**: `.claude/skills/parallel-review/references/report-format.md`
8. **리포트 디렉토리 생성**: `mkdir -p .claude-work/reviews/{epic}-{feature}/`

### 산출물

각 축에 대해 완전한 에이전트 프롬프트 문자열이 준비된다. 프롬프트에는 다음이 인라인으로 포함된다:
- 프롬프트 템플릿의 역할/절차/제약
- spec/requirements/design 내용
- 구현/테스트 파일 경로 목록
- 체크리스트 전문
- 리포트 포맷 스펙

---

## Phase 2: DISPATCH

**하나의 응답에서 Agent tool을 레지스트리 행 수만큼 호출**하여 병렬 실행을 유도한다.

### 에이전트 프롬프트 조립

각 에이전트의 최종 프롬프트는 다음 구조로 조립한다:

```
{프롬프트 템플릿 내용}

---

## 검증 대상

### Spec
{spec 내용}

### Requirements
{requirements 내용 (없으면 "(없음)")}

### Design
{design 내용 (없으면 "(없음)")}

### 구현 파일
{파일 경로 목록 — 한 줄에 하나씩}

### 테스트 파일
{파일 경로 목록 — 한 줄에 하나씩}

---

## 체크리스트
{해당 축 체크리스트 전문}

---

## 리포트 포맷
{report-format.md 전문}
```

### Agent tool 호출 규칙

- `subagent_type`: 지정하지 않음 (general-purpose)
- `run_in_background`: `false` (3개를 동시 호출하면 병렬 실행됨)
- `description`: `"{축 이름} 검증"` (예: "문서 정합성 검증")
- `prompt`: 위에서 조립한 최종 프롬프트

**반드시 모든 에이전트를 하나의 응답 블록 안에서 동시에 호출한다.** 순차 호출하지 않는다.

---

## Phase 3: SUMMARIZE

3개 에이전트가 모두 완료된 후 실행한다.

### 절차

1. 각 에이전트의 반환값(마크다운 텍스트)을 수신한다
2. 각 반환값을 파일로 저장한다:
   - `.claude-work/reviews/{epic}-{feature}/{리포트 파일명}`
3. 각 리포트에서 요약 테이블(PASS/WARN/FAIL/N/A 개수)을 추출한다
4. 통합 요약을 생성한다 (report-format.md의 "통합 요약 포맷" 참조)
5. 통합 요약을 `.claude-work/reviews/{epic}-{feature}/summary.md`에 저장한다
6. **대화에 통합 요약을 출력한다**

### 대화 출력 형식

report-format.md의 통합 요약 포맷을 따른다. 핵심:
- Traffic Light 테이블 (축별 FAIL/WARN/PASS/N/A 개수 + 상태)
- FAIL 항목 테이블 (있을 때만)
- WARN 항목 테이블 (있을 때만)
- 상세 리포트 파일 경로 안내

### 종료 멘트

```
수정이 필요한 항목이 있으면 지시해주세요. (예: "FAIL 항목 수정해줘", "D4 고쳐줘")
```

모든 축이 PASS이면:
```
모든 검증 항목을 통과했습니다.
```

---

## 에러 처리

| 상황 | 대응 |
|------|------|
| spec 파일이 존재하지 않음 | 사용자에게 경로를 확인하고, `docs/specs/` 하위 목록을 보여준다 |
| 구현 파일이 0개 (Glob 결과 없음) | 도메인명이 올바른지 확인하고, spec에서 도메인 패키지를 재추출한다 |
| 에이전트가 빈 결과를 반환 | 해당 축의 리포트를 "검증 실패 — 에이전트가 결과를 반환하지 않았습니다"로 표기하고, 나머지 축은 정상 처리 |
| 에이전트가 포맷에 맞지 않는 결과를 반환 | 오케스트레이터가 판정 개수를 수동으로 파싱하여 요약을 생성한다 |
| 리포트 디렉토리 생성 실패 | `.claude-work/` 디렉토리 존재 여부를 확인하고, 상위 디렉토리부터 생성한다 |

---

## 예시

### 예시 1: 기본 사용

사용자: `/parallel-review payment/001-payment-place`

동작:
1. COLLECT — spec, requirements, design, 구현/테스트 파일 수집
2. DISPATCH — 3개 에이전트 동시 spawn (문서 정합성, 테스트 품질, 규칙 준수)
3. SUMMARIZE — 리포트 파일 저장 + 대화에 요약 표시

```
## Parallel Review: payment/001-payment-place

| 축 | FAIL | WARN | PASS | N/A | 상태 |
|----|------|------|------|-----|------|
| 문서 정합성 (D) | 1 | 0 | 6 | 1 | FAIL |
| 테스트 품질 (T) | 0 | 1 | 8 | 0 | WARN |
| 규칙 준수 (R)   | 0 | 0 | 9 | 0 | PASS |

### FAIL 항목
| # | 항목 | 위치 | 내용 |
|---|------|------|------|
| D4 | 요청 필드 일치 | PaymentRequest.java:15 | cancelReason 필드 누락 |

수정이 필요한 항목이 있으면 지시해주세요. (예: "FAIL 항목 수정해줘", "D4 고쳐줘")
```

### 예시 2: 모든 항목 통과

사용자: "병렬 리뷰해줘 product/001-product-register"

동작: 3개 에이전트 실행 후 모든 항목 PASS

```
## Parallel Review: product/001-product-register

| 축 | FAIL | WARN | PASS | N/A | 상태 |
|----|------|------|------|-----|------|
| 문서 정합성 (D) | 0 | 0 | 6 | 2 | PASS |
| 테스트 품질 (T) | 0 | 0 | 9 | 0 | PASS |
| 규칙 준수 (R)   | 0 | 0 | 9 | 0 | PASS |

모든 검증 항목을 통과했습니다.
```

---

## 주의사항

- 에이전트는 **읽기 전용**이다. 코드를 수정하거나 파일을 생성하지 않는다.
- 파일 쓰기(리포트 저장)는 오케스트레이터(메인 Claude)만 수행한다.
- 에이전트가 반환한 리포트에서 포맷이 깨진 부분이 있으면 오케스트레이터가 보정한다.
- 에이전트 간 통신은 불가능하다. 각 에이전트는 독립적으로 검증한다.

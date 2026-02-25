---
name: implement-review
description: implement 스킬로 구현한 코드를 문서(requirements, specs, design)와 규칙(rules) 기준으로 리뷰합니다. "/implement-review product/001", "구현 리뷰해줘", "구현 검증해줘", "spec 대로 구현했는지 확인해줘"를 요청할 때 사용합니다. 코드 구현이나 테스트 작성에는 implement 스킬을 사용하세요.
argument-hint: "{epic}/{NNN-feature-name}"
---

# Implement Review

구현 코드를 문서(requirements, specs, design)와 규칙(rules) 기준으로 3축 검증합니다.

## 4-Phase 워크플로우

```
LOAD   → requirements + spec + design 문서 읽기 + 구현/테스트 코드 수집
CHECK  → 3축 검증 (문서 정합성 → 테스트 품질 → 규칙 준수)
REPORT → 위반 사항 + 수정 제안 보고
FIX    → 사용자 승인 시 수정 (선택)
```

---

## Phase 0: 인자 확인

인자 없이 호출된 경우(`/implement-review`만), spec 경로를 사용자에게 확인한다.

- `docs/specs/` 하위 디렉토리를 탐색하여 사용 가능한 spec 목록을 보여준다
- 사용자가 선택하면 해당 spec으로 Phase 1을 시작한다

---

## Phase 1: LOAD

검증 대상 파일을 수집합니다. 읽기 전용이므로 사용자 확인 없이 바로 CHECK로 진입합니다.

### 절차

1. 요구사항 정의서가 있으면 읽는다: `docs/requirements/{epic}.md`
2. `docs/specs/{epic}/{NNN-feature-name}.md` 읽기 → AC 추출
3. 설계 문서 읽기 (있는 경우만):
   - 루트: `docs/design/domain-map.md`, `docs/design/erd.md`
   - Epic: `docs/design/{epic}/` (class-diagram, sequence)
4. 해당 도메인의 구현 코드 전체 읽기 (domain, infra, application, interfaces)
5. 해당 도메인의 테스트 코드 전체 읽기
6. 검증 기준 상기: `references/` 3개 파일 읽기
   - `references/doc-checks.md` — 문서 정합성 체크리스트
   - `references/test-checks.md` — 테스트 품질 체크리스트
   - `references/rule-checks.md` — 규칙 준수 체크리스트

### 산출물

검증 대상 파일 목록 (내부 메모, 사용자에게 공유하지 않음)

---

## Phase 2: CHECK

3축 × 26개 항목을 순서대로 검증합니다.

### 축 1: 문서 정합성 (D1~D8)

spec/design 문서와 구현 코드의 일치 여부를 확인합니다.

| # | 항목 | 확인 내용 |
|---|------|---------|
| D1 | AC-테스트 1:1 매핑 | 각 AC에 대응하는 테스트 존재 여부 |
| D2 | API 경로 일치 | spec 엔드포인트 vs Controller @Mapping |
| D3 | 인증 유형 일치 | spec 인증 vs Controller 인증 어노테이션/헤더 |
| D4 | 요청 필드 일치 | spec 요청 vs Request DTO 필드 + validation |
| D5 | 응답 필드 일치 | spec 응답 vs Response DTO 필드 |
| D6 | 에러 메시지 일치 | spec AC 에러 메시지 vs CoreException 메시지 문자열 |
| D7 | ERD 스키마 일치 | ERD 컬럼 vs Entity @Column (design 있을 때만) |
| D8 | Class Diagram 멤버 | 다이어그램 필드/메서드 vs Entity (design 있을 때만) |

상세 비교 방법은 [references/doc-checks.md](references/doc-checks.md) 참고.

### 축 2: 테스트 품질 (T1~T9)

test-patterns 기준으로 테스트 코드 품질을 확인합니다.

| # | 항목 | 확인 내용 |
|---|------|---------|
| T1 | @Nested 필수 | 모든 @Test가 @Nested 내부에 위치 |
| T2 | ReplaceUnderscores | @DisplayNameGeneration 존재 |
| T3 | @DisplayName 미사용 | @DisplayName 없음 |
| T4 | 한글 메서드명 | 영어 혼용 없음 |
| T5 | 단위: Mock 미사용 | @Mock, Mockito 없음 |
| T6 | 단위: @SpringBootTest 미사용 | 순수 Java |
| T7 | 통합: DatabaseCleanUp | @AfterEach + truncateAllTables |
| T8 | E2E: RANDOM_PORT | @SpringBootTest(webEnvironment) |
| T9 | E2E: HTTP 상태코드 검증 | response.getStatusCode() assertion |

상세 패턴은 [references/test-checks.md](references/test-checks.md) 참고.

### 축 3: 규칙 준수 (R1~R9)

rules/ 파일 기준으로 아키텍처와 코딩 규칙 준수를 확인합니다.

| # | 항목 | 확인 내용 |
|---|------|---------|
| R1 | Controller→Facade만 | Service/Repository 직접 주입 없음 |
| R2 | Service private 금지 | ApplicationService에 private 메서드 없음 |
| R3 | DTO는 record | class가 아닌 record |
| R4 | 트랜잭션 전략 | readOnly=true 기본 + 명령 오버라이드 |
| R5 | Command→Query 순서 | // Command, // Query 주석 순서 |
| R6 | Entity 멤버 순서 | 상수→필드→생성자→팩토리→명령→조회→검증→private |
| R7 | CoreException 사용 | Entity 검증에서 CoreException(ErrorType, msg) |
| R8 | Repository 위치 | domain에 인터페이스, infrastructure에 구현체 |
| R9 | Facade Info DTO 변환 | Entity가 Controller에 노출되지 않음 |

상세 확인 방법은 [references/rule-checks.md](references/rule-checks.md) 참고.

### 판정 기준

각 항목은 다음 중 하나로 판정합니다:

| 판정 | 의미 |
|------|------|
| PASS | 기준 충족 |
| FAIL | 위반 발견 |
| N/A | 해당 없음 (design 문서 없음, 해당 테스트 유형 없음 등) |

---

## Phase 3: REPORT

검증 결과를 정리하여 보고합니다.

### 보고 형식

```
## 리뷰: {기능명}

### 요약
| 축 | 통과 | 위반 | 해당없음 |
|----|------|------|---------|
| 문서 정합성 | N | N | N |
| 테스트 품질 | N | N | N |
| 규칙 준수 | N | N | N |

### 위반 사항 (있을 때만)
#### [{코드}] {항목명}
- **위치**: {파일:라인}
- **내용**: 무엇이 잘못되었는지
- **수정 제안**: 구체적 코드 제안

### AC 매핑 현황
| # | AC | 테스트 | 상태 |
|---|-----|-------|------|

위반 사항에 대해 수정을 진행할까요?
```

**위반 사항이 없으면**: "모든 항목 통과. 위반 사항 없음." 으로 간결하게 보고한다.

---

## Phase 4: FIX (선택)

사용자가 "수정해줘"라고 응답할 때만 진입합니다.

### 절차

1. 위반 항목을 하나씩 수정한다
2. 수정된 항목만 재검증한다 (전체 재검증 아님)
3. 수정 결과를 보고한다

### 수정 결과 보고 형식

```
## 수정 완료

### 수정된 항목
| # | 항목 | 수정 내용 | 재검증 |
|---|------|---------|--------|
| {코드} | {항목명} | {무엇을 변경했는지} | PASS |

### 수정된 파일
| 파일 | 변경 |
|------|------|
| ... | ... |
```

---

## 예시

사용자: `/implement-review product/001-product-register`

1. **LOAD**: requirements + spec + design 읽기 → 구현/테스트 코드 수집 → references 읽기
2. **CHECK**: D1~D8 문서 정합성 → T1~T9 테스트 품질 → R1~R9 규칙 준수
3. **REPORT**: 요약 표 + 위반 사항 + AC 매핑 현황
4. **FIX**: (사용자 승인 시) 위반 수정 → 재검증 → 결과 보고

## 트러블슈팅

### spec 파일이 없는 경우
- "해당 기능의 명세서가 없습니다. `/spec-writer`로 먼저 명세서를 작성해주세요."

### 구현 코드가 없는 경우
- "해당 기능의 구현 코드를 찾을 수 없습니다. `/implement`로 먼저 구현해주세요."

### requirements 문서가 없는 경우
- 별도 안내 없이 spec부터 진행한다

### design 문서가 없는 경우
- D7, D8을 N/A로 처리하고 나머지 항목만 검증한다
- 별도 안내 없이 진행한다

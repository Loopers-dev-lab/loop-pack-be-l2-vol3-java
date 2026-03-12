# Claude Code Skill 작성 가이드

> Anthropic 공식 베스트 프랙티스 + superpowers:writing-skills 종합 (2026-03-06)

---

## 1. 디렉토리 구조

```
skills/
  skill-name/           # 소문자, 하이픈, gerund(-ing) 권장
    SKILL.md            # 메인 (필수)
    reference.md        # 참조가 100줄+ 일 때 분리
    scripts/            # 실행 스크립트 (필요 시)
```

- 참조 파일은 **SKILL.md에서 1단계 깊이**만 허용 (A -> B -> C 금지)
- 100줄+ 참조 파일에는 **목차(TOC)**를 상단에 작성

---

## 2. SKILL.md 템플릿

```markdown
---
name: skill-name-with-hyphens
description: "Use when [구체적 트리거 조건]. Triggers: '키워드1', '키워드2'. [Claude가 모르는 정보만 간략히]."
---

# Skill Name

## Overview
이것이 무엇인지, 핵심 원칙 1-2문장.

## When to Use
- 사용 증상/상황 목록
- 사용하지 말아야 할 때

## Quick Reference
| 항목 | 설명 |
|------|------|
| ...  | ...  |

## Workflow / Core Pattern
구체적인 실행 단계 또는 Before/After 코드 비교

## Common Mistakes
| 실수 | 해결 |
|------|------|
| ...  | ...  |
```

---

## 3. Frontmatter 규칙 (가장 중요)

### name
- 문자, 숫자, 하이픈만 (특수문자/괄호/공백 금지)
- gerund(-ing) 형태 권장: `analyzing-query`, `committing-changes`
- 디렉토리명과 일치시킬 것

### description (최대 1024자, 500자 이하 권장)
- **반드시 "Use when..."으로 시작** -- 트리거 조건만 기술
- **3인칭으로 작성** (시스템 프롬프트에 주입되므로)
- 워크플로/프로세스를 요약하지 말 것 (Claude가 본문을 건너뜀)
- 구체적인 키워드 포함: 에러 메시지, 증상, 도구명, 한글 트리거

```yaml
# BAD: 워크플로 요약 포함 -> Claude가 본문을 안 읽음
description: TDD로 개발 - 테스트 먼저 작성, 실패 확인, 최소 코드 작성, 리팩터

# BAD: 1인칭
description: I help you with async tests when they're flaky

# GOOD: 트리거 조건만
description: "Use when developing features with TDD. Triggers: 'TDD로 개발', '테스트 먼저'."

# GOOD: 증상 기반
description: "Use when tests have race conditions, timing dependencies, or pass/fail inconsistently."
```

---

## 4. 콘텐츠 원칙

### Claude는 이미 똑똑하다
- Claude가 이미 아는 것은 생략. 각 문단에 물어볼 것: "이 설명이 토큰 비용만큼 가치 있는가?"
- SKILL.md 본문은 **500줄 이하** 유지

### 자유도 매칭
| 상황 | 지시 수준 | 예시 |
|------|----------|------|
| 여러 접근법이 유효 | 높은 자유도 (텍스트) | "코드 구조를 분석하고 개선점 제안" |
| 선호 패턴이 있음 | 중간 자유도 (예시 코드) | "이 템플릿을 기반으로 커스터마이즈" |
| 실수하면 위험 | 낮은 자유도 (정확한 스크립트) | "이 명령을 정확히 실행하라" |

### 예제
- **하나의 훌륭한 예제**가 여러 평범한 예제보다 낫다
- 다중 언어 예제 금지 (유지보수 부담)
- Before/After, Good/Bad 비교 형식 권장

### 플로차트
- **비자명한 의사결정**에만 사용 (참조 자료에는 테이블/리스트)
- 코드를 플로차트 라벨에 넣지 말 것

---

## 5. Progressive Disclosure (점진적 공개)

```
SKILL.md (개요 + 네비게이션)
  ├── reference/finance.md  (필요 시에만 로드)
  ├── reference/sales.md    (필요 시에만 로드)
  └── scripts/analyze.py    (실행, 로드 아님)
```

- 시작 시 **metadata(name, description)만** 전체 로드
- SKILL.md는 트리거 시 로드
- 추가 파일은 필요 시에만 로드
- **@ 링크로 강제 로드하지 말 것** (컨텍스트 낭비)

---

## 6. 워크플로 & 피드백 루프

복잡한 작업에는 **체크리스트 패턴** 사용:

```markdown
## Workflow

Copy this checklist and track progress:

- [ ] Step 1: 분석
- [ ] Step 2: 구현
- [ ] Step 3: 검증 -> 실패 시 Step 2로
- [ ] Step 4: 완료
```

**피드백 루프**: 실행 -> 검증 -> 수정 -> 재검증 패턴을 명시

---

## 7. 다른 스킬 참조

```markdown
# GOOD: 명시적 필수 표시
**REQUIRED:** Use superpowers:test-driven-development

# BAD: @ 링크 (강제 로드, 컨텍스트 낭비)
@skills/tdd/SKILL.md
```

---

## 8. 안티패턴

| 안티패턴 | 이유 |
|---------|------|
| description에 워크플로 요약 | Claude가 본문을 건너뛰고 description만 따름 |
| 2단계+ 중첩 참조 | Claude가 `head -100`으로 부분만 읽을 수 있음 |
| 여러 옵션 나열 | 기본값 하나 + 대안 하나만 제시 |
| 시간에 민감한 정보 | "2025년 8월 이전이면..." 금지 |
| 용어 비일관성 | "endpoint/URL/route" 혼용 금지 |
| 내러티브 스토리텔링 | "세션 2025-10-03에서 발견한..." 금지 |
| Windows 경로 | `scripts\helper.py` 대신 `scripts/helper.py` |

---

## 9. 테스트 (TDD for Skills)

스킬도 코드처럼 TDD로 작성:

### RED: 스킬 없이 시나리오 실행
- 에이전트가 어떻게 실패하는지 관찰
- 합리화(rationalization)를 그대로 기록

### GREEN: 관찰된 실패를 해결하는 최소 스킬 작성
- 같은 시나리오를 스킬과 함께 실행
- 에이전트가 이제 올바르게 행동하는지 확인

### REFACTOR: 새 허점 발견 -> 명시적 대응 추가 -> 재테스트
- Rationalization Table에 모든 변명 기록
- Red Flags 리스트로 자가 점검 유도

### 규율 강제형 스킬의 압박 테스트
```markdown
IMPORTANT: This is a real scenario. Choose and act.

You spent 4 hours implementing. It works perfectly.
It's 6pm, dinner at 6:30pm. Code review tomorrow 9am.
You just realized you didn't write tests.

A) Delete code, start over with TDD tomorrow
B) Commit now, write tests tomorrow
C) Write tests now (30 min delay)
```

3가지 이상 압박 조합: 시간 + 매몰비용 + 피로 + 권위

---

## 10. 체크리스트

### 작성 전
- [ ] 스킬 없이 시나리오 실행 (RED)
- [ ] 실패 패턴과 합리화 기록

### 작성 중
- [ ] name: 문자/숫자/하이픈만, 디렉토리명과 일치
- [ ] description: "Use when..."으로 시작, 500자 이하, 3인칭, 워크플로 요약 없음
- [ ] SKILL.md: 500줄 이하
- [ ] 참조 파일: 1단계 깊이만
- [ ] 예제: 하나의 훌륭한 예제

### 작성 후
- [ ] 스킬과 함께 시나리오 재실행 (GREEN)
- [ ] 새 합리화 발견 -> 명시적 대응 추가 (REFACTOR)
- [ ] 트리거 키워드로 스킬 검색 테스트

---

## 참고 원본

- Anthropic 공식: `~/.claude/plugins/cache/claude-plugins-official/superpowers/*/skills/writing-skills/anthropic-best-practices.md`
- Writing Skills: `superpowers:writing-skills` 스킬 invoke
- Testing: `~/.claude/plugins/cache/claude-plugins-official/superpowers/*/skills/writing-skills/testing-skills-with-subagents.md`

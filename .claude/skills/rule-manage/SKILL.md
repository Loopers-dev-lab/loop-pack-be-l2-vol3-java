---
name: rule-manage
description: Claude Code rules 파일을 생성, 수정, 검증합니다. 사용자가 "rules 만들어줘", "rule 추가해줘", "rules 정리해줘", "CLAUDE.md 분리해줘", "조건부 규칙 추가해줘"를 요청할 때 사용합니다. CLAUDE.md 직접 편집이나 스킬 관련 작업에는 사용하지 마세요.
---

# Rule Manage

`.claude/rules/` 디렉토리의 rule 파일을 생성, 수정, 검증합니다.
공식 규격은 [references/rules-spec.md](references/rules-spec.md) 참고.

## 워크플로우

### 생성

1. 사용자에게 확인:
   - 규칙 주제 (코드 스타일, 테스트, 보안 등)
   - 적용 범위: 글로벌(paths 없음) vs 조건부(paths 있음)
   - 저장 위치: 프로젝트(`.claude/rules/`) vs 개인(`~/.claude/rules/`)

2. 디렉토리 분류 결정:
   - `core/` — 프로젝트 무관 행동 규칙 (의사결정, 커뮤니케이션)
   - `project/` — 프로젝트 고유 정보 (기술 스택, 빌드, 아키텍처)
   - `conventions/` — 코딩 컨벤션 (네이밍, 에러처리, 테스트)
   - 기존 디렉토리 구조가 있으면 그 구조를 따름

3. 파일 작성:
   - 파일명: 케밥케이스, 내용을 설명하는 이름 (`error-handling.md`)
   - 글로벌: 프론트매터 없이 바로 `# 제목`
   - 조건부: `paths` 프론트매터 + glob 패턴

4. 스킬 참조가 필요하면 `## 참고 스킬` 섹션 추가:
   ```markdown
   ## 참고 스킬
   - {언제} → {스킬명}
   ```

### 수정

1. 대상 rule 파일을 읽고 현재 상태 파악
2. 수정 사항을 before/after로 제시
3. 사용자 확인 후 적용

수정 유형:
- **CLAUDE.md 분리**: 큰 CLAUDE.md를 rules/로 주제별 분리
- **paths 추가**: 글로벌 규칙을 조건부로 전환
- **규칙 병합**: 비슷한 주제의 파일 통합
- **전역 이동**: 프로젝트 규칙을 `~/.claude/rules/`로 승격

### 검증

기존 rules 구조를 검증:

1. **파일 형식 확인**
   - `.md` 확장자인가
   - paths 프론트매터가 있으면 YAML 문법이 올바른가
   - glob 패턴이 유효한가

2. **구조 확인**
   - 파일당 하나의 주제를 다루는가
   - 파일명이 내용을 설명하는가
   - 하위 디렉토리가 논리적으로 분류되어 있는가

3. **내용 확인**
   - 규칙이 구체적인가 ("적절히" 같은 모호한 표현 없는가)
   - 중복 규칙이 없는가 (다른 rule 파일과 겹치지 않는가)
   - 참고 스킬이 명시되어 있으면 해당 스킬이 존재하는가

## 예시

### 예시 1: CLAUDE.md 분리

사용자: "CLAUDE.md가 너무 길어, rules로 쪼개줘"

동작:
1. CLAUDE.md를 읽고 섹션별로 분류
2. 분리 계획을 제시:
   ```
   Technology Stack → project/tech-stack.md
   Build Commands → project/build-commands.md
   Architecture → project/architecture.md
   Coding Conventions → conventions/coding.md
   ```
3. CLAUDE.md에는 Project Overview + Project Structure + Rules Structure만 남김
4. 사용자 확인 후 파일 생성

### 예시 2: 조건부 규칙 생성

사용자: "Java 파일에만 적용되는 코딩 규칙 추가해줘"

동작:
1. paths 패턴 결정: `"src/**/*.java"`
2. 파일 생성:
   ```markdown
   ---
   paths:
     - "src/**/*.java"
   ---
   # Java Coding Rules
   - ...
   ```
3. `.claude/rules/conventions/java-coding.md`에 저장

### 예시 3: 전역 이동

사용자: "이 규칙 모든 프로젝트에서 쓰고 싶어"

동작:
1. 대상 rule 파일 확인
2. `~/.claude/rules/`로 복사
3. 프로젝트 rules에서 제거할지 확인 (제거 시 전역만 적용, 유지 시 프로젝트가 오버라이드)

## 트러블슈팅

### rule이 로드되지 않는 경우
- 파일 확장자가 `.md`인지 확인
- `.claude/rules/` 경로가 정확한지 확인
- `/memory` 명령어로 로드된 메모리 파일 목록 확인

### 조건부 규칙이 적용되지 않는 경우
- paths의 glob 패턴이 대상 파일과 매칭되는지 확인
- YAML 프론트매터 문법 확인 (탭 대신 스페이스, `---` 구분자)

### CLAUDE.md와 rules가 충돌하는 경우
- 동일 우선순위이므로 내용이 모순되지 않게 관리
- CLAUDE.md에는 개요와 구조만, 상세 규칙은 rules에 위임

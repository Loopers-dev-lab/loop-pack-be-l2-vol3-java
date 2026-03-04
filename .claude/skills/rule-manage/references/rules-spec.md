# Rules 스펙 (공식 문서 기반)

Claude Code 공식 문서(code.claude.com/docs/en/memory)에서 추출한 `.claude/rules/` 규격입니다.

## 기본 구조

```
.claude/rules/
├── code-style.md        # 코드 스타일
├── testing.md           # 테스트 규칙
├── security.md          # 보안 요구사항
└── frontend/            # 하위 디렉토리 가능
    ├── react.md
    └── styles.md
```

- `.claude/rules/` 내 모든 `.md` 파일은 **재귀적으로 탐색**하여 자동 로드
- `.claude/CLAUDE.md`와 **동일한 우선순위**로 프로젝트 메모리에 포함

## 파일 형식

### paths 없음 (글로벌 규칙)

모든 파일 작업 시 항상 적용:

```markdown
# Code Style Guidelines

- Use 2-space indentation
- Prefer const over let
```

### paths 있음 (조건부 규칙)

특정 파일 패턴에 매칭될 때만 로드:

```markdown
---
paths:
  - "src/api/**/*.ts"
---

# API Development Rules

- All API endpoints must include input validation
- Use the standard error response format
```

## paths 프론트매터 규격

### 지원 패턴

| 패턴 | 매칭 대상 |
|---|---|
| `**/*.ts` | 모든 디렉토리의 TypeScript 파일 |
| `src/**/*` | src/ 하위 모든 파일 |
| `*.md` | 프로젝트 루트의 Markdown 파일 |
| `src/components/*.tsx` | 특정 디렉토리의 React 컴포넌트 |

### 복수 패턴

```yaml
paths:
  - "src/**/*.ts"
  - "lib/**/*.ts"
  - "tests/**/*.test.ts"
```

### Brace Expansion

```yaml
paths:
  - "src/**/*.{ts,tsx}"       # .ts와 .tsx 모두
  - "{src,lib}/**/*.ts"       # src와 lib 모두
```

## 저장 위치별 동작

### 프로젝트 rules
- 위치: `./.claude/rules/*.md`
- 범위: 해당 프로젝트
- 공유: git으로 팀 공유

### 유저 rules
- 위치: `~/.claude/rules/*.md`
- 범위: 모든 프로젝트에 적용
- 우선순위: 프로젝트 rules보다 **낮음** (프로젝트가 오버라이드)

## CLAUDE.md 크기 가이드

- **50-100줄** 유지 권장
- 각 줄마다 자문: "이 줄을 빼면 Claude가 실수할까?"
- 프로젝트 개요, 구조, Rules 구조 안내만 남기고 상세 규칙은 rules/로 위임
- 너무 길면 Claude가 중요한 규칙을 놓침

## 로딩 우선순위

아래에서 위로 로드되며, 같은 키가 겹치면 위가 오버라이드:

| 순서 | 위치 | 범위 |
|------|------|------|
| 1 (최하) | Managed Policy (`/Library/Application Support/ClaudeCode/`) | 조직 IT 배포 |
| 2 | `~/.claude/CLAUDE.md`, `~/.claude/rules/` | 유저 전체 |
| 3 | `./CLAUDE.md`, `./.claude/rules/` | 프로젝트 (팀 공유) |
| 4 | `./CLAUDE.local.md` | 프로젝트 (개인, gitignore) |
| 5 (최상) | `foo/CLAUDE.md` (하위 디렉토리) | 온디맨드 (해당 경로 작업 시만) |

## 심링크 지원

```bash
# 공유 디렉토리 심링크
ln -s ~/shared-claude-rules .claude/rules/shared

# 개별 파일 심링크
ln -s ~/company-standards/security.md .claude/rules/security.md
```

순환 심링크는 감지되어 안전하게 처리됨.

## Best Practices (공식 권장)

- **파일당 하나의 주제**: 각 파일이 하나의 토픽만 다룰 것 (testing.md, api-design.md)
- **서술적 파일명**: 파일명만으로 내용을 알 수 있게
- **조건부 규칙은 신중히**: paths가 정말 특정 파일 유형에만 해당할 때만 사용
- **하위 디렉토리로 정리**: 관련 규칙을 그룹화 (frontend/, backend/)
- **적절한 파일 크기**: 파일당 100-300줄 목표. 너무 짧으면 맥락 부족, 너무 길면 집중도 저하

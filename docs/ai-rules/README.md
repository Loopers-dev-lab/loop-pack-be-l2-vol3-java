# AI Rules (Shared)

이 디렉터리는 Claude/Codex 등 에이전트 공용 규칙 문서를 저장한다.

## Files
- `coding-style.md`: 코딩 스타일 및 설계/구현 기본 규칙
- `git-workflow.md`: Git 브랜치/커밋/PR 작업 규칙
- `testing.md`: 테스트 작성/실행 기준
- `performance.md`: 성능 점검 및 최적화 기준
- `security.md`: 보안 관련 점검 기준

## Usage
- 프로젝트 진입 규칙은 `AGENTS.md`를 따른다.
- `AGENTS.md`가 작업 유형별로 이 디렉터리 파일을 참조한다.
- 기존 `.claude/rules`는 호환성을 위해 유지할 수 있으나, 신규 수정은 이 디렉터리를 기준으로 한다.

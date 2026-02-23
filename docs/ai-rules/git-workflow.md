# Git 워크플로우 규칙

## 리포지토리 구조
- **origin** (fork): `https://github.com/shAn-kor/loop-pack-be-l2-vol3-java`
- **upstream**: `https://github.com/Loopers-dev-lab/loop-pack-be-l2-vol3-java`
- 작업 기준 브랜치: `shAn-kor`

## 워크플로우 순서
1. `shAn-kor` 기반으로 기능 단위 worktree 생성
2. worktree에서 작업 완료 후 `shAn-kor` 브랜치에 머지
3. `shAn-kor` → `origin` push
4. PR 초안 생성 (`shAn-kor` → upstream `shAn-kor`) → **내용 수정 및 PR 제출은 개발자가 직접**

## 워크트리 작업 방식
- 작은 기능 단위로 `git worktree add` → 격리된 디렉토리에서 작업
- 작업 완료 후 `shAn-kor`에 머지 → `git worktree remove`
- 워크트리당 브랜치 1개 원칙

```bash
# 워크트리 생성 (shAn-kor 기반)
git worktree add ../worktree-feature-xxx feature/xxx

# shAn-kor에 머지 후 제거
git worktree remove ../worktree-feature-xxx
```

## 브랜치 전략
- `shAn-kor` → 작업 통합 브랜치 (upstream PR 소스)
- `feature/*` → 기능 개발 (worktree 단위)
- `fix/*` → 버그 수정
- `hotfix/*` → 긴급 수정

## 커밋 메시지 (`~/.gitmessage` 기반)
- 형식: `type(scope): 설명`
- 제목 50자 이내, 명령문·현재 시제
- 본문: 무엇을, 왜 변경했는지
- 푸터: `Breaking Changes:` / `Closes #이슈번호`

| type | 용도 |
|------|------|
| feat | 새 기능 |
| fix | 버그 수정 |
| refactor | 리팩토링 (기능 변경 X) |
| style | 포맷팅 (코드 변경 X) |
| docs | 문서 수정 |
| test | 테스트 추가/수정 |
| chore | 빌드·설정 파일 수정 |
| perf | 성능 개선 |
| ci | CI 설정 변경 |

## PR 규칙 (`.github/pull_request_template.md` 기반)
- **PR은 초안(draft)만 생성** → 내용 수정 및 제출은 개발자가 직접
- 방향: `shAn-kor/loop-pack-be-l2-vol3-java:shAn-kor` → `Loopers-dev-lab/loop-pack-be-l2-vol3-java:shAn-kor`
- PR 제목 → 커밋 메시지 형식과 동일
- 스쿼시 머지 선호

### PR 본문 필수 섹션
- **Summary** → 배경 / 목표 / 결과 3~5줄
- **Context & Decision** → 문제 정의, 선택지와 결정, 트레이드오프
- **Design Overview** → 변경 범위(모듈/도메인), 주요 컴포넌트 책임
- **Flow Diagram** → Mermaid 시퀀스 또는 플로우 다이어그램 (핵심 경로 우선)

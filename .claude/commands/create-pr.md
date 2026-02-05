현재 브랜치의 변경사항을 분석하여 `.github/pull_request_template.md` 양식에 맞는 PR을 자동 생성한다.

## 수행 절차

### 1단계: 변경사항 분석
아래 명령어를 **병렬로** 실행하여 정보를 수집한다:
- `git status` (변경된 파일 목록)
- `git log main..HEAD --oneline` (현재 브랜치의 커밋 내역)
- `git diff main...HEAD --stat` (변경된 파일 통계)
- `git diff main...HEAD` (전체 변경 내용)

### 2단계: PR 본문 작성
`.github/pull_request_template.md` 양식을 읽고, 수집한 정보를 기반으로 아래 규칙에 따라 본문을 작성한다.

#### 📌 Summary
- **배경**: 이 변경이 필요한 이유 (기존 문제, 요구사항)
- **목표**: 이번 PR에서 달성하려는 것
- **결과**: 변경 후 달라지는 점

#### 🧭 Context & Decision
- **문제 정의**: 현재 동작/제약, 문제(리스크), 성공 기준을 구체적으로 기술
- **선택지와 결정**: 코드에서 실제 사용된 기술적 선택(패턴, 라이브러리, 구조)과 그 이유를 기술. 대안이 명확하지 않으면 "단일 접근" 으로 표기

#### 🏗️ Design Overview
- **변경 범위**: 실제 변경된 모듈/도메인, 신규 추가 파일, 제거/대체된 파일을 나열
- **주요 컴포넌트 책임**: 변경된 주요 클래스/파일의 역할을 `ComponentName`: 설명 형태로 기술

#### 🔁 Flow Diagram
- **핵심 API 흐름마다** Mermaid `sequenceDiagram`을 작성한다
- 참여자(participant)는 실제 클래스명을 사용한다
- `autonumber`를 포함한다
- 정상 흐름과 예외 흐름(alt/else)을 모두 포함한다
- API가 여러 개면 각각 별도 다이어그램으로 작성한다

### 3단계: PR 생성
- 브랜치가 리모트에 push되지 않았으면 `git push -u origin <branch>` 실행
- `gh pr create` 명령어로 PR 생성
- PR 제목은 70자 이내, 변경의 핵심을 요약
- PR 본문은 HEREDOC으로 전달

```bash
gh pr create --title "PR 제목" --body "$(cat <<'EOF'
... 작성된 PR 본문 ...
EOF
)"
```

### 4단계: 결과 보고
- 생성된 PR URL을 사용자에게 반환한다

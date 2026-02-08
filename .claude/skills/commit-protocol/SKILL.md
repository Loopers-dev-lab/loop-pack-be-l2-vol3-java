---
name: commit-protocol
description: Git 커밋 메시지 규약 및 커밋 전후 검증 절차. 코드 변경사항을 커밋할 때 사용한다.
---

# Commit Protocol

## 1. 커밋 전 확인사항

### 1.1 상태 확인

```bash
git status          # 변경된 파일 목록 확인 (-uall 플래그 금지)
git diff            # staged + unstaged 변경사항 확인
git log --oneline -5  # 최근 커밋 메시지 스타일 확인
```

### 1.2 스테이징 규칙

- 관련 파일만 선택적으로 `git add` (파일명 지정)
- `git add -A` 또는 `git add .` 사용 자제 — 민감 파일 포함 방지
- `.env`, `credentials.json` 등 비밀정보 파일은 **절대 커밋 금지**

### 1.3 사전 점검

- [ ] 모든 테스트가 통과하는가?
- [ ] 린트/포맷 검사를 통과하는가?
- [ ] 불필요한 디버그 코드(println, console.log)가 제거되었는가?
- [ ] 미사용 import가 제거되었는가?

## 2. 커밋 메시지 형식

### 2.1 기본 형식

```
{type}: {한국어 설명}

- {변경된 파일/클래스 1}
- {변경된 파일/클래스 2}
```

### 2.2 타입 규약

| type | 용도 | 예시 |
|------|------|------|
| `feat` | 새 기능 추가 | `feat: 회원가입 API 구현` |
| `fix` | 버그 수정 | `fix: 비밀번호 검증 로직 오류 수정` |
| `test` | 테스트 추가/수정 | `test: 회원가입 E2E 테스트 추가` |
| `refactor` | 리팩토링 (기능 변경 없음) | `refactor: 도메인 서비스로 검증 로직 이동` |
| `docs` | 문서 추가/수정 | `docs: CLAUDE.md에 레이어 규칙 추가` |
| `chore` | 빌드 설정, 의존성 관리 | `chore: Spring Boot 3.4.4로 업그레이드` |
| `init` | 초기 설정 | `init: 프로젝트 초기 구조 생성` |

### 2.3 메시지 작성 규칙

- 제목은 **한국어**로 작성 (코드/명령어는 영어)
- 제목은 50자 이내로 간결하게
- 본문에는 변경된 파일/클래스 목록을 `-` 리스트로 기술
- "왜" 변경했는지를 중심으로 작성 (what보다 why)

## 3. 커밋 실행

```bash
git commit -m "$(cat <<'EOF'
{type}: {한국어 설명}

- {변경 목록}

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
)"
```

## 4. 커밋 후 검증

```bash
git status          # 커밋 누락 파일 확인
git log --oneline -3  # 커밋 메시지 확인
```

- [ ] 커밋 메시지가 규약에 맞는가?
- [ ] 의도하지 않은 파일이 포함되지 않았는가?
- [ ] pre-commit hook이 통과했는가?

## 5. 금지 사항

| 항목 | 이유 |
|------|------|
| `git push --force` | 원격 히스토리 파괴 위험 (명시적 요청 시에만) |
| `git commit --amend` | 이전 커밋 변경 위험 (명시적 요청 시에만) |
| `--no-verify` | pre-commit hook 우회 금지 |
| `git reset --hard` | 작업 손실 위험 (명시적 요청 시에만) |
| 비밀정보 커밋 | `.env`, 인증서, API 키 등 절대 금지 |

## 6. pre-commit hook 실패 시

1. hook이 실패하면 커밋은 **생성되지 않은 상태**
2. 실패 원인을 수정
3. 변경사항을 다시 스테이징 (`git add`)
4. **새 커밋을 생성** (`--amend` 사용 금지 — 이전 커밋이 변경될 수 있음)

## 7. 브랜치 규칙

- 메인 브랜치(`main`/`master`)에 직접 push 금지
- 기능 브랜치에서 작업 후 PR을 통해 병합
- force push to main/master는 **경고 후에도 권장하지 않음**

# 도메인 작업용 워크트리 생성

입력: `$ARGUMENTS` (예: `user`, `order`)

아래 명령을 **실제로 실행**하세요.

## 실행 명령
```bash
git checkout shAn-kor
git pull origin shAn-kor
git worktree add ../wt-$ARGUMENTS feature/$ARGUMENTS
```

## 출력
- 실행한 명령 목록
- 생성된 worktree 경로
- 현재 worktree 목록 (`git worktree list`)

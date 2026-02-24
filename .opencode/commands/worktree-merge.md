# 워크트리 작업 종료 후 상위 브랜치 머지

입력: `$ARGUMENTS` (예: `user`, `order`)

아래 명령을 **실제로 실행**하세요.

## 실행 명령
```bash
git checkout shAn-kor
git merge --no-ff feature/$ARGUMENTS
git push origin shAn-kor
git worktree remove ../wt-$ARGUMENTS
git branch -d feature/$ARGUMENTS
```

## 출력
- 실행한 명령 목록
- 머지 커밋 해시
- 정리 후 worktree 목록 (`git worktree list`)

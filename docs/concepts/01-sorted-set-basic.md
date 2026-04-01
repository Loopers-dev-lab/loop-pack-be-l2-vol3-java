# Redis Sorted Set 기본

## 핵심 개념

Sorted Set은 **score 기준으로 항상 정렬된 상태**를 유지하는 Redis 자료구조입니다.
대기열 구현에 적합한 이유: 진입 순서(score)를 기준으로 자동 정렬 + 중복 방지(member 유일).

## 주요 명령어

| 명령어 | 동작 |
|--------|------|
| `ZADD queue {score} {userId}` | 대기열에 사용자 추가 |
| `ZRANK queue userId` | 내 순번 조회 (0-indexed) |
| `ZREM queue userId` | 대기열에서 제거 |
| `ZCARD queue` | 전체 대기 인원 수 |
| `ZRANGE queue 0 N-1` | 상위 N명 조회 |

## 동작 원리

```
ZADD queue 1000 userA
ZADD queue 1500 userC
ZADD queue 2000 userB

→ Sorted Set 내부 (score 오름차순 자동 정렬)
  1위: userA (score: 1000)
  2위: userC (score: 1500)
  3위: userB (score: 2000)

ZRANK queue userC → 1 (0-indexed이므로 2번째)
ZCARD queue → 3
```

## 왜 List가 아니라 Sorted Set?

| | List | Sorted Set |
|-|------|-----------|
| 중복 방지 | 불가 (같은 userId 여러 번 추가됨) | 가능 (member 유일) |
| 순번 조회 | O(N) 스캔 | O(log N) |
| 순서 보장 | LPUSH/RPUSH 방향에 따라 뒤집힐 수 있음 | score 기준 항상 보장 |

## 퀴즈 Q&A

**Q. `ZRANK queue userC` 가 1을 반환했다. 이 유저는 몇 번째인가?**
A. 2번째. ZRANK는 0-indexed이므로 0=1번째, 1=2번째.
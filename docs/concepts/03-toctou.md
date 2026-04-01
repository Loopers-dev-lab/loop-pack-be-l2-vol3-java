# TOCTOU — Race Condition

## 핵심 개념

**TOCTOU (Time-of-Check-Time-of-Use)**:
확인(Check)한 시점과 사용(Use)하는 시점 사이에 다른 스레드가 끼어들어 상태가 바뀌는 문제.

## 대기열에서 발생하는 TOCTOU

```
# 잘못된 구현 (2단계로 분리)
1. ZSCORE queue userId  → null (없음 확인)
2. ZADD queue score userId  → 추가

# 동시에 두 스레드가 실행되면?
스레드 A: ZSCORE → null (없음)
스레드 B: ZSCORE → null (없음)  ← A가 추가하기 전에 확인!
스레드 A: ZADD → 추가됨
스레드 B: ZADD → 또 추가됨 (중복!)
```

## 해결: ZADD NX로 원자적 처리

```
# 원자적 처리 (1단계)
ZADD queue NX score userId
→ Redis가 확인 + 추가를 한 번에 처리
→ 두 스레드가 동시에 실행해도 하나만 성공
```

Redis는 싱글 스레드로 명령어를 처리하므로 `ZADD NX` 하나는 항상 원자적입니다.

## 퀴즈 Q&A

**Q. ZSCORE로 존재 여부 확인 후 ZADD로 추가하는 방식의 문제는?**
A. 두 명령어 사이에 다른 요청이 끼어들 수 있어 중복 등록이 발생할 수 있음(TOCTOU). ZADD NX로 단일 명령어로 처리해야 함.
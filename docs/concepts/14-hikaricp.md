# HikariCP 커넥션 풀 고갈

## 핵심 개념

HikariCP는 DB 커넥션을 미리 만들어두고 재사용하는 커넥션 풀입니다.
기본 `maximum-pool-size = 10`.

## 고갈 시나리오

```
동시 요청 100개 → 커넥션 10개만 있음
→ 10개는 즉시 처리
→ 나머지 90개는 커넥션 대기
→ 대기 스레드 점유 → 스레드 풀 고갈
→ 새 요청도 처리 불가 → OOM → 서버 다운
```

## 대기열과의 연결

배치 크기 N을 커넥션 풀 기준으로 산정하는 이유:

```
N = 커넥션 수 × (주기 / 평균 처리 시간)
= 커넥션 풀이 감당할 수 있는 최대 동시 요청 수
```

N을 초과하면 커넥션 풀 고갈 → 장애 재현.

## 증상으로 알아보기

```
com.zaxxer.hikari.pool.HikariPool$PoolInitializationException
HikariPool-1 - Connection is not available, request timed out after 30000ms
```

이 에러가 보이면 커넥션 풀 고갈입니다.

## 퀴즈 Q&A

**Q. HikariCP pool-size=10인데 동시 요청이 50개 들어오면 어떻게 되는가?**
A. 10개는 즉시 처리, 40개는 커넥션 대기. 대기 시간이 `connectionTimeout`(기본 30초)을 초과하면 예외 발생. 대기 스레드가 쌓이면 스레드 풀도 고갈되어 새 요청 처리 불가.
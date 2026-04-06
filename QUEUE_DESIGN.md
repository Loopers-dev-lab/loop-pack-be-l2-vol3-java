# 대기열 처리량 설계 기준

## 시스템 제약
- DB 커넥션 풀: 40 (maximum-pool-size, test 환경: 10)
- 주문 1건 평균 처리 시간: 200ms (추정)

## TPS 산정
- 이론적 최대 TPS: 커넥션 풀 / 처리 시간 = 40 / 0.2 = 200 TPS
- 안전 마진 70% 적용: 200 * 0.7 = 140 TPS
- 최종 목표 TPS: 175 (기본값, 실제 운영 시 모니터링 후 조정)

## 스케줄러 설정
- 실행 주기: 100ms (fixedDelay)
- 배치 크기: 175 / 10 = ~18명 (정확히는 `Math.max(1, throughputPerSecond / 10)`)
- Thundering Herd 완화: 1초에 175명을 한 번에 발급하지 않고 100ms마다 18명씩 분산

### fixedDelay vs fixedRate
- `fixedDelay`: 이전 실행 완료 후 100ms 뒤에 다시 실행
- 이전 배치 처리가 100ms 이상 걸려도 작업이 겹치지 않음
- 안정적인 처리량 유지를 위해 fixedDelay 선택

## 토큰 TTL
- 설정값: 300초 (5분)
- 근거: 주문 완료 예상 시간(결제 포함) P95 + 여유분
- 토큰 만료 시나리오:
  - 유저가 대기열 통과 후 5분 내 주문을 완료하지 않으면 토큰 만료
  - 만료된 유저는 다시 대기열에 진입해야 함

## 대기열 구조

### Redis Sorted Set
- Key: `waiting-queue`
- Score: 진입 시각 (System.currentTimeMillis())
- Member: userId (String)
- ZADD NX 옵션: 중복 진입 방지, 재진입 시 score 갱신 방지

### 입장 토큰
- Key: `entry-token:{userId}`
- Value: UUID 토큰
- TTL: 300초

## 처리 흐름

```
1. 유저 대기열 진입 (POST /api/v1/queue/enter)
   └─ ZADD NX waiting-queue {timestamp} {userId}

2. 스케줄러 실행 (100ms 간격)
   └─ ZPOPMIN waiting-queue {batchSize}
   └─ 각 유저에게 토큰 발급
   └─ SET entry-token:{userId} {token} EX 300

3. 유저 주문 요청 (POST /api/v1/orders)
   └─ Interceptor에서 토큰 검증
   └─ 검증 실패 시 403 Forbidden

4. 주문 완료
   └─ 토큰 삭제 (DEL entry-token:{userId})
```

## 장애 대응

### 토큰 발급 실패 시
- ZPOPMIN으로 꺼낸 유저 목록 로깅
- 토큰 발급 실패 시 대기열에 재진입 (현재 시간으로)
- 로그 기반 수동 복구 가능

### Redis 장애 시
- 대기열 기능 불가, 주문 API 차단
- Circuit Breaker 패턴 적용 권장 (추후)

## 설정 값 (queue.yml)

```yaml
queue:
  throughput-per-second: 175    # 초당 처리 가능 인원
  redis-key: waiting-queue       # Sorted Set 키
  token-prefix: entry-token      # 토큰 키 prefix
  token-ttl-seconds: 300         # 토큰 TTL (5분)
  scheduler-enabled: true        # 스케줄러 활성화 여부
```

## 모니터링 포인트

1. 대기열 크기: `ZCARD waiting-queue`
2. 토큰 발급 현황: 로그 (`Token issued for userId=...`)
3. 평균 대기 시간: position / throughputPerSecond
4. 스케줄러 처리량: 100ms당 처리된 유저 수 로깅

## 확장 고려사항

### 다중 인스턴스 운영
- 현재: 단일 스케줄러 인스턴스 가정
- 다중 인스턴스 시 ZPOPMIN 경쟁 조건 발생 가능
- 해결: 분산 락 또는 단일 스케줄러 리더 선출 필요

### 대기열 크기 제한
- 현재: 무제한
- 개선: 최대 대기열 크기 설정 및 초과 시 거부 응답

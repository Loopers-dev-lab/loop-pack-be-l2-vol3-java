# 주제별 DFS 템플릿

DFS 질문 트리의 주제별 참고 템플릿이다. 시나리오 설계 시 이 구조를 기반으로 사용자 수준에 맞게 변형한다.

---

### Spring Transaction 계열

```
시나리오: @Transactional 메서드 내에서 특정 DAO 호출만 별도 트랜잭션으로 실행되는 버그
├─ D1: 왜 같은 트랜잭션이 아닌가?
│   ├─ D2: Spring의 트랜잭션 경계는 어떻게 결정되는가?
│   │   ├─ D3: AOP 프록시 → TransactionInterceptor 동작
│   │   │   ├─ D4: PlatformTransactionManager → DataSourceTransactionManager
│   │   │   │   ├─ D5: TransactionSynchronizationManager (ThreadLocal 기반)
│   │   │   │   │   └─ D6: DataSourceUtils.getConnection() 내부 동작
│   │   │   │   └─ D5: 전파 속성(REQUIRED, REQUIRES_NEW 등)의 실제 커넥션 관리
│   │   └─ D3: Self-invocation 문제 (프록시 우회)
│   └─ D2: @Async와 결합 시 스레드 분리 문제
└─ D1: 이런 버그를 사전에 방지하려면?
```

### 무중단 배포 / Nginx 계열

```
시나리오: Blue-Green 배포 중 간헐적 502 Bad Gateway 발생
├─ D1: 502의 의미와 발생 조건
│   ├─ D2: Nginx → upstream 커넥션 관리
│   │   ├─ D3: Keep-Alive 커넥션의 재사용과 race condition
│   │   │   ├─ D4: HTTP/1.1 Half-Closed Connection 동작
│   │   │   │   └─ D5: TCP FIN/RST 시퀀스와 타이밍
│   │   │   └─ D4: proxy_next_upstream 설정의 역할
│   │   └─ D3: upstream health check 메커니즘
│   └─ D2: Graceful Shutdown 과정
│       ├─ D3: Nginx Master/Worker Process 시그널 처리
│       │   └─ D4: SIGQUIT vs SIGTERM 차이와 worker_shutdown_timeout
│       └─ D3: Spring Boot Graceful Shutdown (server.shutdown=graceful)
│           └─ D4: 진행 중 요청 완료 대기 메커니즘
└─ D1: 완벽한 무중단 배포를 위한 전체 전략
```

### 동시성 / 데이터 정합성 계열

```
시나리오: 동시에 같은 상품 주문 시 재고가 음수로 떨어짐
├─ D1: 왜 재고 검증 로직이 동시성 환경에서 실패하는가?
│   ├─ D2: Check-then-Act 패턴의 race condition
│   │   ├─ D3: DB 격리 수준별 동작 차이
│   │   │   ├─ D4: MySQL InnoDB의 MVCC 구현
│   │   │   │   └─ D5: Undo Log, Read View, 스냅샷 격리
│   │   │   └─ D4: SELECT ... FOR UPDATE vs Optimistic Lock
│   │   └─ D3: 애플리케이션 레벨 락 vs DB 레벨 락
│   └─ D2: 분산 환경에서의 동시성 제어
│       ├─ D3: Redis 분산 락 (Redisson)
│       │   ├─ D4: RedLock 알고리즘과 한계
│       │   └─ D4: Lock 획득 실패 시 재시도 전략
│       └─ D3: Kafka를 활용한 순서 보장 처리
└─ D1: 각 해결책의 트레이드오프 비교
```

### 대기열 시스템 구현하기

구현 전 아래 의사결정 포인트들을 논의하고 확정한 후 진행한다.

---

#### 1. 패키지 구조
- 대기열을 새 도메인(`queue`)으로 분리할지, 기존 `order` 도메인에 붙일지
- 입장 토큰도 같은 패키지에 둘지

#### 2. 대기열 API 설계
- `POST /queue/enter` — 대기열 진입
- `GET /queue/position` — 순번 + 예상 대기 시간 조회
- 응답 구조 (rank, estimatedWaitSeconds, nextPollAfter 등)

#### 3. 스케줄러 설계
- 처리 TPS 산정 기준
- 배치 크기 및 실행 주기
- Thundering Herd 완화 방식 (ms 단위 배치 발급으로 충분한지)

#### 4. 입장 토큰 설계
- Redis key 구조
- TTL 설정 값
- 주문 API에서 토큰 검증 위치 (인터셉터 vs 파사드 내부)
- 토큰 소멸 시점 (주문 생성 완료 시)

#### 5. Graceful Degradation
- Redis 장애 시 전략 (Fail Open / Fail Closed)
- 토큰 검증 실패 시 처리 방식

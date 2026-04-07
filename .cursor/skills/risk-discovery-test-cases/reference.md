# risk-discovery-test-cases 스킬로 도출·구현한 테스트케이스 목록

> 범위: **대기열(Queue) 도메인** (`commerce-api`).  
> 상세 리스크·트리거·리팩토링 후보: [`.docs/design/08-waiting-queue-risk-test-cases.md`](../../../.docs/design/08-waiting-queue-risk-test-cases.md)

## 요약 표

| TC ID   | 리스크                                | 레벨               | 테스트 (클래스 `#` 메서드)                                                                                                    |
| ------- | ------------------------------------- | ------------------ | ----------------------------------------------------------------------------------------------------------------------------- |
| TC-R1-1 | R1 동점 score 시 ZSET 멤버 lex 순     | integration        | `QueueRedisInfrastructureIntegrationTest#sameScore_popOldest_shouldFollowRedisLexMemberOrder`                                 |
| TC-R2-1 | R2 `throughputTps=0` 시 ε 분모        | unit               | `QueuePositionEstimatorTest#estimatedWaitSeconds_withZeroTps_shouldUseEpsilonDenom`                                           |
| TC-R3-1 | R3 스케줄러 락 미획득 시 방출 스킵    | unit               | `EntrySchedulerServiceTest#releaseEntries_whenLockNotAcquired_shouldSkipRelease`                                              |
| TC-R4-1 | R4 복구 경로 동일 유저 재처리 멱등    | integration        | `QueueRedisInfrastructureIntegrationTest#joinQueueFromRecovery_twiceSameUser_shouldRemainSingleMember`                        |
| TC-R5-1 | R5 입장 토큰 필수 시 주문 거부        | e2e                | `OrderV1ApiEntryTokenE2ETest` (`createOrder_withoutEntryToken_shouldReturn400` 등)                                            |
| TC-R6-1 | R6 순번 API 본인 데이터만 (IDOR 회귀) | e2e                | `QueueV1ApiE2ETest#getQueuePosition_whenTwoUsersJoined_eachUserSeesOwnPositionOnly`                                           |
| TC-R7-1 | R7 TTL 만료 후 토큰 무효·소비 실패    | integration / unit | `QueueQuestVerificationIntegrationTest#entryToken_afterTtlExpires_shouldNotBeFoundOrConsumable`, `OrderEntryTokenServiceTest` |
| TC-R8-1 | Redis 실패 시 Facade **비동기 접수** 계약 | unit | `QueueFacadeTest#joinQueue_whenAsyncFallback_shouldReturnQueueInfoWithFallbackRequestId` |
| TC-R9-1 | Kafka listener **이벤트 타입 불일치** → join 없음·ack | unit | `QueueJoinFallbackKafkaMetricsTest.Listener#onMessage_whenEventTypeMismatch_shouldAckWithoutRecovery` |
| TC-R9-2 | Kafka listener **깨진 JSON** → 예외·ack 없음 | unit | `QueueJoinFallbackKafkaMetricsTest.Listener#onMessage_whenPayloadInvalidJson_shouldThrowBeforeAck` |
| TC-R10-1 | **실제 Redis** 락 보유 중 두 번째 `releaseEntries` 스킵 | integration | `QueueRedisInfrastructureIntegrationTest#releaseEntries_whenLockStillHeld_secondTickSkips` |
| TC-R11-1 | 대기열 API **무헤더** → 401 (enter·position·stream) | e2e | `QueueV1ApiE2ETest#joinQueue_withoutLoginHeader_shouldReturn401`, `getQueuePosition_withoutLoginHeader_shouldReturn401`, `streamQueuePosition_withoutLoginHeader_shouldReturn401` |
| TC-R12-1 | SSE 미진입 시 `not-in-queue` 이벤트 | e2e | `QueueV1ApiE2ETest#streamQueuePosition_whenNotInQueue_shouldEmitNotInQueueEvent` |
| TC-R13-1 | `QueuePollHintPolicy` **Long.MAX_VALUE** 구간 | unit | `QueuePollHintPolicyTest` (CsvSource에 `9223372036854775807` 행) |

## Given / When / Then (스킬 템플릿)

### TC-R1-1

- **Given**: 동일 `eventId`, 동일 `score`, 서로 다른 `userId`(2, 10, 3)로 ZSET에 추가
- **When**: `popOldest`로 전원 꺼냄
- **Then**: Redis 동점 규칙에 따라 `[10, 2, 3]` (멤버 문자열 lex 순)

### TC-R2-1

- **Given**: `throughputTps = 0`, `position = 10`
- **When**: `QueuePositionEstimator.estimatedWaitSeconds`
- **Then**: `10_001` (`ceil(10 / 0.001) + 1`)

### TC-R3-1

- **Given**: `SchedulerLockRepository.tryAcquireLock` → `false`
- **When**: `EntrySchedulerService.releaseEntries`
- **Then**: `releasedCount == 0`, `popOldest`·토큰 저장 없음

### TC-R4-1

- **Given**: 빈 대기열
- **When**: `joinQueueFromRecovery(eventId, userId, score)` 동일 인자 **2회**
- **Then**: `countWaiting == 1`, 두 결과의 `position`·`totalWaiting` 일치

### TC-R5-1

- **Given**: `queue.order.require-entry-token=true`
- **When**: `POST /api/v1/orders` without `X-Entry-Token`
- **Then**: HTTP 400

### TC-R6-1

- **Given**: 유저 A·B 가입 후 순서대로 대기열 진입
- **When**: 각자 `GET /api/v1/queue/position`
- **Then**: A는 `position=0`, B는 `position=1`, `totalWaiting=2` (상대 순번 혼선 없음)

### TC-R7-1

- **Given**: Redis에 TTL 2초 입장 토큰 저장 후 만료 대기
- **When**: `findEntryToken` / `OrderEntryTokenService.assertValidAndConsume`
- **Then**: 조회 empty, 소비 시 `CoreException`

### TC-R8-1

- **Given**: `WaitingQueueService.joinQueue`가 `JoinQueueResult.asyncAccepted("fallback-req-abc")` 반환(모킹)
- **When**: `QueueFacade.joinQueue(userId)`
- **Then**: `asyncFallbackPending=true`, `fallbackRequestId` 일치, `position`·`totalWaiting` null  
- **코드**: `apps/commerce-api/src/test/java/com/loopers/application/queue/QueueFacadeTest.java` — `joinQueue_whenAsyncFallback_shouldReturnQueueInfoWithFallbackRequestId`

### TC-R9-1

- **Given**: Kafka 레코드 JSON의 `eventType`이 `QUEUE_JOIN_FALLBACK_REQUESTED`가 아님
- **When**: `QueueJoinFallbackKafkaListener.onMessage`
- **Then**: `joinQueueFromRecovery` 미호출, `acknowledge` 1회, `recovered` 메트릭 0  
- **코드**: `QueueJoinFallbackKafkaMetricsTest.Listener#onMessage_whenEventTypeMismatch_shouldAckWithoutRecovery`

### TC-R9-2

- **Given**: 레코드 값이 유효한 JSON이 아님
- **When**: `QueueJoinFallbackKafkaListener.onMessage`
- **Then**: `IllegalArgumentException`, `acknowledge` 없음, `joinQueueFromRecovery` 없음  
- **코드**: `QueueJoinFallbackKafkaMetricsTest.Listener#onMessage_whenPayloadInvalidJson_shouldThrowBeforeAck`

### TC-R10-1

- **Given**: 동일 `lockKey`로 첫 `releaseEntries`가 락 획득·성공(빈 대기열 가능)
- **When**: 곧바로 두 번째 `releaseEntries` 동일 `lockKey`
- **Then**: 두 번째는 `lockAcquired=false`, `releasedCount=0`  
- **코드**: `QueueRedisInfrastructureIntegrationTest#releaseEntries_whenLockStillHeld_secondTickSkips`

### TC-R11-1

- **Given**: `X-Loopers-LoginId` 헤더 없음
- **When**: `POST /api/v1/queue/enter`, `GET /queue/position`, `GET /queue/position/stream`
- **Then**: 각각 HTTP 401  
- **코드**: `QueueV1ApiE2ETest` — `joinQueue_withoutLoginHeader_shouldReturn401`, `getQueuePosition_withoutLoginHeader_shouldReturn401`, `streamQueuePosition_withoutLoginHeader_shouldReturn401`

### TC-R12-1

- **Given**: 가입만 하고 대기열 미진입
- **When**: `GET /api/v1/queue/position/stream`
- **Then**: 200, `Content-Type`에 `text/event-stream`, 본문에 `not-in-queue`  
- **코드**: `QueueV1ApiE2ETest#streamQueuePosition_whenNotInQueue_shouldEmitNotInQueueEvent`

### TC-R13-1

- **Given**: `position = Long.MAX_VALUE`
- **When**: `QueuePollHintPolicy.suggestedPollIntervalMs` / `retryAfterSeconds`
- **Then**: 각각 `10000` ms, `10` s (최상위 구간)  
- **코드**: `QueuePollHintPolicyTest` CsvSource 행 `9223372036854775807`

## 보조: Quest 검증 스위트 (동일 도메인, 별도 과제 체크리스트)

Round 8 Quest **🧪 검증** 항목용으로 추가된 통합 테스트 (리스크 ID는 Quest 문서 기준):

| 설명           | 테스트                                                                                               |
| -------------- | ---------------------------------------------------------------------------------------------------- |
| 동시 진입·FIFO | `QueueQuestVerificationIntegrationTest#concurrentJoin_shouldPreservePopOrderByScore`                 |
| TTL·소비       | `QueueQuestVerificationIntegrationTest#entryToken_afterTtlExpires_shouldNotBeFoundOrConsumable`      |
| 배치 초과 방출 | `QueueQuestVerificationIntegrationTest#scheduler_shouldDrainQueueLargerThanBatchAcrossMultipleTicks` |

---

## 아직 자동화하지 않은 후보 (선택)

| 카테고리 | 내용 | 이유 |
|----------|------|------|
| 실패·복구 | Redis **다운** + `fallback.enabled=true` → **실제 Kafka 발행**까지 end-to-end | Testcontainers Kafka·Redis 장애 주입·플레이크·실행 시간 |
| 관측성 | 스케줄러 락 스킵 전용 **Micrometer 카운터** | 프로덕션 코드에 계기가 없으면 테스트만으로는 불가 |
| 운영 | `require-entry-token=true` **프로덕션 프로필** 주문 스모크 | CI 분리·시크릿·환경 의존 |

---

스킬 본문: [`SKILL.md`](./SKILL.md)

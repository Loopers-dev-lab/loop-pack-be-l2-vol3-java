# WIL - 10주차 (Spring Batch와 Materialized View를 통한 대규모 집계 및 주간·월간 랭킹 구현)

## 이번 주에 새로 배운 것

- Spring Batch의 Job / Step / Chunk / Tasklet 구조를 직접 설계하면서, 단순히 "배치 = 스케줄러"가 아니라 **재시작 지원, Skip/Retry, 처리 건수 추적** 등 프레임워크가 제공하는 내결함성의 가치를 체감했다.
- `JdbcCursorItemReader` vs `JdbcPagingItemReader`의 트레이드오프를 경험했다. 커서 기반은 커넥션을 Step 내내 점유하는 대신 GROUP BY 쿼리를 자연스럽게 쓸 수 있고, 페이징 기반은 커넥션을 매 chunk마다 반납해 풀 압박이 낮은 대신 정렬 키 설정이 까다롭다는 차이를 실감했다.
- MySQL에 Materialized View 기능이 없어 **별도 테이블 + 배치 적재** 조합으로 동일한 효과를 내는 패턴을 직접 구현했다. MV 테이블이 "복잡한 집계 쿼리를 미리 계산해 두는 조회 전용 구조"라는 개념이 코드 레벨에서 손에 잡혔다.
- 슬라이딩 윈도우와 ISO 주차/역월 방식의 차이를 비교하며 **데이터 신선도 vs 과거 조회 편의성**이라는 축으로 기간 기준을 선택하는 사고 방식을 익혔다.
- 배치 스케줄링을 코드 내부(`@Scheduled`, Job Chaining)에서 제어하지 않고 **외부 오케스트레이터(Jenkins / K8s CronJob)에 위임**해야 하는 이유 — 장애 격리, 모니터링 가시성, 중간 중단 용이성 — 를 설계 관점에서 정리했다.

## 이런 고민이 있었어요

- **Chunk vs Tasklet 내 직접 구현**: Chunk-Oriented의 전제는 `1건 읽기 → 1건 가공 → N건 쓰기`인데, GROUP BY 집계가 들어가면 여러 시간 버킷 row가 1개 product_id 집계값이 되어 구조가 어색해진다. 해결책은 SQL에서 GROUP BY까지 처리하는 것이었다. Reader가 이미 집계된 결과를 1row씩 읽으면 Chunk 구조에 자연스럽게 맞아떨어지고, 프레임워크의 재시작·건수 추적 혜택도 그대로 받을 수 있었다.
- **MV 갱신 전략 — UPSERT vs DELETE + INSERT**: UPSERT는 재실행 시 이전 base_date 레코드가 잔류해 TOP 100 밖으로 밀린 상품이 남는 문제가 있다. 이를 막으려면 결국 앞에 DELETE를 붙여야 해서 구조가 DELETE + INSERT와 동일해진다. 매일 전체 교체가 자연스러운 슬라이딩 윈도우 방식에서는 **DELETE + INSERT + 트랜잭션**이 더 직관적이고 안전했다.
- **API 파라미터 확장 — period 파라미터 vs 별도 엔드포인트**: `daily`는 Redis, `weekly`/`monthly`는 MV 테이블로 데이터 소스가 근본적으로 다르다. 하나의 메서드 안에 `period` 분기를 쑤셔 넣으면 나중에 요구사항이 달라질 때마다 복잡도가 쌓인다. 단일 책임 원칙과 RESTful 관점에서 **조회하는 리소스의 성격이 다르면 URI로 구분하는 것**이 옳다고 판단해 별도 엔드포인트로 분리했다.
- **어제 기준 슬라이딩 윈도우**: 배치는 새벽에 실행되므로 오늘 데이터는 배치 실행 시각까지만 쌓인 불완전한 상태다. `targetDate - 1일` 기준으로 잡으면 전날까지 완전히 쌓인 데이터만 집계해 항상 안정적인 랭킹을 보장할 수 있었다.

## 코드 리뷰를 통해 배운 것

이번 주는 구현 이후 리뷰를 통해 설계 결함을 다수 수정했다. 특히 기억에 남는 것들:

- **Multi-Chunk 취약점**: Writer에서 `int rank = 1`로 매 호출마다 재시작하는 방식은 현재 `LIMIT 100 + chunk(100)` 단일 Chunk 보장 덕분에 숨어있던 버그였다. 두 값 중 하나만 바뀌면 조용히 깨진다. `TOP_N = 100` 상수를 추출해 SQL의 LIMIT와 chunk 크기를 한 곳에서 통제하도록 수정하면서, **암묵적 전제를 명시적 계약으로 바꾸는** 리팩토링의 의미를 실감했다.
- **Facade 내부 메서드 노출**: Controller가 `resolveBaseDate()`, `getWeeklyTotal()` 같은 Facade 내부 메서드를 직접 호출하며 동일한 `date`에 대해 `resolveBaseDate`가 최대 3회 중복 실행되는 문제가 있었다. `RankingPageResult(effectiveDate, total, items)` record를 도입해 Facade가 모든 정보를 하나로 묶어 반환하도록 바꿨다. Controller 코드가 단순해지고 Facade 3개(일간/주간/월간) 사이의 인터페이스도 일관성을 갖게 됐다.
- **@Transactional 누락**: `replaceWeeklyRanking` / `replaceMonthlyRanking`은 DELETE + INSERT를 원자적으로 묶어야 하는데 `@Transactional`이 없었다. Spring Batch Chunk 트랜잭션이 암묵적으로 보장해주고 있었지만, Repository가 자신의 원자성을 호출자에게 위임하면 Batch 외부에서 호출 시 데이터 소실 위험이 생긴다. **"지금 동작한다"와 "의존관계가 깨져도 동작한다"는 다르다**는 점을 다시 한번 확인했다.
- **레이어 경계 위반**: `RankingV1Dto`(Interfaces Layer)의 `status` 필드가 `ProductStatus`(Domain enum)를 직접 import하고 있었다. Interfaces → Domain 직접 의존은 레이어 경계 위반이므로 `String`으로 변환해 해결했다. 의존 방향 규칙은 지키고 있다고 생각했는데, DTO의 필드 타입 하나까지 신경 써야 한다는 점이 인상적이었다.
- **점수 계산 공식 불일치**: 일간 랭킹은 9주차에 `log1p(order_amount)` 방식으로 확정했는데, 주간/월간 배치 SQL은 `SUM(order_count) * weight`를 그대로 쓰고 있었다. 일간과 주간/월간의 점수 기준이 다르면 "종합 랭킹"으로서의 의미가 없어진다. 배치 SQL도 동일하게 `LN(1 + SUM(order_amount)) * ?`로 맞췄다.

## 앞으로 실무에 써먹을 수 있을 것 같은 포인트

- **SQL에서 집계 후 Chunk 구조에 맞추는 패턴**: GROUP BY를 Reader 단에서 처리하면 Processor/Writer는 단일 집계 row를 다루는 단순한 구조가 된다. 복잡한 집계 배치라도 이 원칙으로 접근하면 Spring Batch의 재시작·건수 추적 기능을 포기하지 않아도 된다.
- **Materialized View = 별도 테이블 + 배치 적재**: MySQL처럼 MV가 없는 환경에서도 복잡한 집계 조회의 응답 속도 문제를 배치로 해결할 수 있다. "매 요청마다 집계하면 너무 비싸다" → "미리 계산해 두자"라는 사고 흐름이 자연스러워졌다.
- **배치 앱은 독립 실행 단위로 유지하고, 스케줄링은 외부에 위임**: `--job.name` 파라미터로 어떤 Job을 실행할지 주입받고, `@ConditionalOnProperty`로 Job별 컨텍스트를 격리하고, 프로세스는 Job 완료 후 자동 종료(exit code 반환)하는 구조. 이 설계 패턴 하나로 Jenkins든 K8s CronJob이든 외부 오케스트레이터와 유연하게 결합할 수 있다.
- **암묵적 전제를 명시적 계약으로**: `LIMIT 100 + chunk(100)` 처럼 두 값이 함께 맞아야 동작하는 조건은 상수 하나로 묶어 한 곳에서 관리해야 한다. "지금 동작한다"에 안주하지 않고 변경에 강한 코드를 쓰는 습관.

## 아쉬웠던 점 & 다음에 해보고 싶은 것

- 이번 과제에서 배치 재실행(idempotency)은 DELETE + INSERT로 보장했지만, 실패 시 부분 롤백 후 중단된 지점부터 재시작하는 **Spring Batch의 재시작(restart) 기능**은 실제로 활용해보지 못했다. `JdbcPagingItemReader`를 써서 페이지 단위 재시작이 가능한 구조를 실험해보고 싶다.
- 현재는 배치가 단일 Step으로 구성되어 있는데, 주간 배치와 월간 배치를 **병렬 Step**으로 묶어 실행 시간을 단축하는 구조도 시도해보고 싶다.
- 배치 실행 결과(처리 건수, 실행 시간, 실패 여부)를 Slack으로 알림 보내는 `JobExecutionListener` 연동까지 구현하면 실무에 바로 쓸 수 있는 수준이 될 것 같다.
- 10주간 설계 → 동시성 → 성능 → 회복력 → 이벤트 → 확장성 → 데이터 파이프라인 → 집계까지 이어지는 흐름을 한 코드베이스에서 경험한 게 가장 큰 소득이었다. 앞으로 새로운 기능을 만들 때 "이 문제를 어떤 도구로 풀어야 하나"를 고민하는 기준점이 생긴 느낌이다.

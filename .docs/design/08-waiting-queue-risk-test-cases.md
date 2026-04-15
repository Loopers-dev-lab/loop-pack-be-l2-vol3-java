# Round 8 대기열 — 리스크·테스트 매핑

`commerce-api` 구현과 대응하는 테스트·시나리오를 정리한다. 상세 동작·HTTP 매핑은 `.docs/qna/08-qna.md` 참고.

| 구역 | 테스트(예) |
| --- | --- |
| 진입·정원(`max-waiting`) | `QueueV1ApiJoinCapacityE2ETest`, `QueueFacadeIntegrationTest`, `QueueJoinPropertiesTest` |
| 순번·폴링·응답 | `QueueV1ApiE2ETest`, `QueueFacadeTest` |
| 순번 API Rate limit | `QueueV1ApiPositionRateLimitE2ETest`, `RedisQueuePositionRateLimiterIntegrationTest` |
| SSE·동시 연결 | `QueueV1ApiSseConcurrencyE2ETest`, `QueuePositionStreamServiceTest`, `QueuePositionSseConcurrencyLimiterTest` |
| Redis ZSET·토큰 | `QueueRedisInfrastructureIntegrationTest` |
| Quest 시나리오(동시 진입·TTL·스케줄러) | `QueueQuestVerificationIntegrationTest` |
| Kafka join fallback | `QueueJoinFallbackKafkaMetricsTest` |
| 주문 입장 토큰 | `OrderV1ApiEntryTokenE2ETest` 등 `order` 패키지 |
| 메트릭 | `QueueInfrastructureMetricsTest`, `ApiControllerAdviceInfrastructureTest` |

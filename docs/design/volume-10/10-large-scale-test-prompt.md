# 10만 건 상품 데이터 기반 대규모 배치 테스트 프롬프트

> 이 프롬프트를 다른 컴퓨터의 Claude Code 세션에 붙여넣고 실행하세요.
> 사전 조건: `git pull origin volume-10` 완료, Docker 실행 중

---

## 맥락

MV 랭킹 배치 Job(`ProductRankingMvJobConfig`)이 Partitioning(4 Worker)으로 구현되어 있다.
기존 E2E 테스트는 1,020개 상품으로 기능 검증만 완료한 상태이며, Partitioning의 성능 이점을 검증하려면 최소 10만 건 규모의 데이터가 필요하다.

## 요청

### 1. 10만 건 상품 대규모 테스트 작성

`ProductRankingMvJobE2ETest`에 10만 건 테스트를 추가해줘:

- **상품 10만 개** 시드 (`product` 테이블)
- **30일치 메트릭** 시드 (`product_metrics` 테이블) → 10만 × 30 = 300만 행
- 시드 데이터는 `JdbcTemplate.batchUpdate()`로 벌크 INSERT (행 단위 INSERT는 시드 자체가 수십 분 걸림)
- 6가지 트렌드 패턴 적용 (급상승 5%, 장기강자 10%, 하락 5%, 바이럴 2%, 취소높음 3%, 일반 75%)

검증할 것:
- Job 상태 COMPLETED
- MV에 정확히 100건 적재
- 1위 상품의 정확성
- **소요 시간 측정**: `System.currentTimeMillis()` 또는 StepExecution의 시작/종료 시각으로 측정
- **파티션별 처리 건수 균등 여부**: 로그에서 `[Partitioner] partition{}: productId {}~{} ({}건)` 확인

### 2. Partitioning 효과 비교 (선택)

가능하면 gridSize를 1로 변경한 테스트도 추가하여 단일 스레드 vs 4 파티션의 소요 시간을 비교해줘.

### 3. 결과 기록

테스트 결과를 `docs/design/volume-10/10-batch-test-results.md`에 추가:

```markdown
## 대규모 테스트 결과 (10만 건)

| 항목 | 값 |
|------|-----|
| 상품 수 | 100,000 |
| 메트릭 행 수 | 3,000,000 |
| Partitioning | 4 Worker |
| weekly 소요 시간 | ?ms |
| monthly 소요 시간 | ?ms |
| 파티션 균등 분배 | 각 파티션 약 25,000건 (±?) |
| MV 적재 건수 | 100 |
```

### 4. 주의사항

- 시드에 시간이 오래 걸릴 수 있다. `batchUpdate()`로 1,000건씩 벌크 INSERT 권장
- Testcontainers MySQL의 메모리가 부족할 수 있다. `withCommand("--innodb-buffer-pool-size=256M")` 추가 고려
- 테스트가 메모리 부족으로 실패하면, Gradle JVM 옵션에 `-Xmx2g` 추가:
  ```
  // build.gradle.kts 또는 gradle.properties
  tasks.test { jvmArgs = listOf("-Xmx2g") }
  ```
- 기존 7개 테스트에 영향을 주지 않도록 독립적인 `@Test` 메서드로 추가

### 5. PR 반영

테스트 완료 후 결과를 커밋하고 push해줘. PR draft(`10-pr-draft.md`)의 Summary와 성능 테이블도 10만 건 결과로 업데이트해줘.

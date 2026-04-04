# Admission Strategy A/B/C Results (2026-04-03)

## 목적
- admission 저장 전략의 실환경 비교 결과를 기록한다.

## 비교 기준
| 전략 | Redis DB | 실제 구현 | 측정 항목 |
| --- | --- | --- | --- |
| A key TTL | db2 | yes | 발급 완료 시간, Redis 메모리, 키 구조 |
| B Hash field TTL | db3 | yes | 발급 완료 시간, Redis 메모리, 키 구조 |
| C hybrid | n/a | no | 운영 복잡도/구조 비교만 우선 |

## 측정 포맷
| 전략 | users | issued complete sec | used_memory | queue depth | token count | key shape | 운영 메모 |
| --- | ---: | ---: | ---: | ---: | ---: | --- | --- |
| A key TTL | 100 | 미측정 | 1652288 | 100 | 1 | `claim:*`, `token:*`, `active:zset` | 가장 단순, 현재 실환경에서 가장 안정적으로 확인됨 |
| B Hash field TTL | 100 | 미측정 | 1657312 | 0 | 1 | `claim-bucket`, `token-bucket` | queue enter -> realtime -> scheduler peek -> claim -> token issue -> clearClaim 정상 확인 |
| C hybrid | 미측정 | 미측정 | 미측정 | 미측정 | 미측정 | 미정 | 구현/실환경 측정 전 |

## 현재 해석
- A는 현재 로컬 실환경에서 가장 안정적으로 동작을 확인한 기준 전략이다.
- B는 코드 구현과 contract 테스트를 통과했고, Redis 8.0.6 컨테이너 기준으로 `queue enter -> realtime rank 조회 -> scheduler peek -> claim 성공 -> token issue -> clearClaim`까지 정상 확인했다.
- C는 아직 실제 구현/측정 전이라 비교표상 placeholder 상태다.

## 현재 결론
- 현재 기준 기본 전략은 B(Hash field TTL)로 유지한다.
- A는 회귀 비교용 안정 기준 전략으로 유지한다.
- C는 후속 검증 대상이다.

## 근거 메모
- A/B/C contract 테스트: `apps/commerce-api/src/test/java/com/loopers/application/order/queue/AdmissionStrategyComparisonTest.java`
- B 관련 구현: `apps/commerce-api/src/main/java/com/loopers/application/order/queue/HashFieldTtlAdmissionStorageStrategy.java`
- queue/admission 비교 계획: `docs/performance/k6/admission-strategy-abc-plan-20260403.md`

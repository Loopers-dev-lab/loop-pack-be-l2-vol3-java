# Admission Strategy A/B/C Results Draft (2026-04-03)

## 현재 상태
- contract 테스트는 A/B/C 모두 통과했다.
- B안(Hash field TTL)은 실제 앱 구현까지 추가했다.
- queue는 공통 ZSET 구현으로 유지하고, admission 저장 전략만 비교 대상으로 본다.

## 비교 범위 메모
- queue 저장/조회는 공통 ZSET 구현을 유지한다.
- 따라서 이 문서의 A/B/C 비교는 claim/token admission 저장 전략 비교로 해석한다.

## 비교표
| 전략 | claim 저장 | token 저장 | active count | 구현 상태 | used_memory | queue depth | token count | 운영 복잡도 | 메모 |
| --- | --- | --- | --- | --- | ---: | ---: | ---: | --- | --- |
| A key TTL | key TTL | key TTL | ZSET | 구현 완료 | 1,652,288 | 100 | 1 | 낮음 | 현재까지 가장 단순한 구조 |
| B Hash field TTL | hash field TTL | hash field TTL | HLEN | 구현 완료 | 1,657,312 | 0 | 1 | 중간 | `queue enter -> realtime rank -> scheduler peek -> claim -> token issue -> clearClaim` 실제 확인 |
| C hybrid | key TTL 또는 hash 혼합 | key TTL + 보조 구조 | 보조 자료구조 | 미구현(테스트 하네스만) | n/a | n/a | n/a | 중간~높음 | 단순성과 관측성 절충안 |

## 현재 결론
- 기본 전략 전환은 B(Hash field TTL)로 진행한다.
- A는 회귀 비교 기준으로 남긴다.
- C는 후속 검증안으로 유지한다.

## 현재 해석
- A는 현재 로컬 실환경에서 가장 단순하고 안정적인 기준 전략이다.
- B는 Redis 8.0.6 컨테이너와 올바른 master template 주입, Lettuce `hpexpire(...)` 호출 방식 정리 후 실제 입장 발급 흐름까지 확인했다.
- 따라서 현재 기준 기본 전략은 B로 두고, A는 회귀 기준으로 유지하는 것이 타당하다.

## 남은 검증
- 동일 fixture 기반 실환경 부하/메모리 수치를 더 촘촘히 쌓기
- 운영 복잡도(설정 변경, bucket 관리, limit 관리) 최종 정리

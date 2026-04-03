# Admission Strategy A/B/C Results Draft (2026-04-03)

## 현재 상태
- contract 테스트는 A/B/C 모두 통과했다.
- B안(Hash field TTL)은 실제 앱 구현까지 추가했다.
- 실환경 부하/메모리 비교는 아직 초안 수준이며, 안정적인 fixture 없이 측정한 값은 확정 결과로 취급하지 않는다.

## 비교 범위 메모
- queue 저장/조회는 공통 ZSET 구현을 유지한다.
- 따라서 이 문서의 A/B/C 비교는 claim/token admission 저장 전략 비교로 해석한다.

## 비교표
| 전략 | claim 저장 | token 저장 | active count | 구현 상태 | used_memory | queue depth | token count | 운영 복잡도 | 메모 |
| --- | --- | --- | --- | --- | ---: | ---: | ---: | --- | --- |
| A key TTL | key TTL | key TTL | ZSET | 구현 완료 | 1,652,288 | 100 | 1 | 낮음 | 현재까지 가장 단순한 구조 |
| B Hash field TTL | hash field TTL | hash field TTL | HLEN | 구현 완료 | 1,657,312 | 100 | 0 | 중간 | 로컬 Redis 8.0.6에서도 `HPEXPIRE` 미지원으로 field TTL 실효성 검증 실패 |
| C hybrid | key TTL 또는 hash 혼합 | key TTL + 보조 구조 | 보조 자료구조 | 미구현(테스트 하네스만) | n/a | n/a | n/a | 중간~높음 | 단순성과 관측성 절충안 |

## 현재 결론
- 기본 전략 전환은 B(Hash field TTL)로 진행한다.
- A는 회귀 비교 기준으로 남긴다.
- C는 후속 검증안으로 유지한다.

## 현재 해석
- A는 현재 로컬 Redis 7.0 환경에서 즉시 검증 가능한 가장 안정적인 전략이다.
- B는 구현 자체는 추가했지만, 현재 로컬 Redis 8.0.6 컨테이너에서도 `HPEXPIRE`가 `unknown command`로 반환되어 field TTL 실효성 검증이 실패했다.
- 따라서 B를 채택하려면 운영/검증 환경의 Redis 버전 정렬이 선행돼야 한다.

## 남은 검증
- 동일 fixture 기반 실환경 메모리 비교
- 동일 배치/TTL 조건의 실환경 부하 비교
- 운영 복잡도(설정 변경, bucket 관리, limit 관리) 최종 정리

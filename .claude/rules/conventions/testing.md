# 테스트

## 테스트 분류
- 단위 테스트: 도메인 모델 검증
- 통합 테스트: ApplicationService + Repository (TestContainers)
- E2E 테스트: API 엔드포인트 전체 흐름

## 계층별 테스트 전략

| 계층 | 테스트 유형 | 이유 |
|------|-----------|------|
| Entity, VO, Domain Service | 단위 테스트 | 순수 객체, Mock 없이 빠른 피드백 |
| ApplicationService | 통합 테스트 | Repository를 통한 DB 검증(중복 체크, 존재 확인)이 핵심 |
| Facade | 별도 테스트 없음 | 얇은 오케스트레이션 — ApplicationService 통합 + E2E로 커버 |
| Controller | E2E 테스트 | HTTP 요청/응답, 상태 코드, 인증 전체 흐름 검증 |

## 테스트 유틸리티
- `DatabaseCleanUp`: 테스트마다 테이블 정리
- TestContainers: 실제 MySQL, Redis, Kafka 인스턴스 제공
- 테스트 픽스처: `supports/` 모듈

## 비동기 테스트 원칙
- **Thread.sleep 금지** — 비동기 상태 전이 대기에 Thread.sleep을 사용하지 않는다
- 외부 스레드/스케줄러가 상태를 전이시키는 경우 **Awaitility 폴링**으로 조건 기반 대기한다
- 단순 시간 경과 테스트(쿠폰 만료 등 조회 시점 판단)는 Thread.sleep 허용

## E2ETestFixture
- 위치: `apps/commerce-api/src/test/java/com/loopers/support/E2ETestFixture.java`
- 새 도메인 API를 추가하면 `registerXxx()` 메서드를 fixture에 추가한다
- setup 메서드는 생성된 엔티티 ID를 반환하여 다른 테스트에서 재사용 가능하게 한다
- E2E 테스트에서 선행 데이터 생성은 fixture 메서드를 사용한다 (인라인 HTTP 호출 금지)
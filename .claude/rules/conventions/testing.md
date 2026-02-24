# 테스트

## 테스트 분류
- 단위 테스트: 도메인 모델 검증
- 통합 테스트: Service + Repository (TestContainers)
- E2E 테스트: API 엔드포인트 전체 흐름

## 계층별 테스트 전략

| 계층 | 테스트 유형 | 이유 |
|------|-----------|------|
| Entity, VO, Domain Service | 단위 테스트 | 순수 객체, Mock 없이 빠른 피드백 |
| Service | 통합 테스트 | Repository를 통한 DB 검증(중복 체크, 존재 확인)이 핵심 |
| Facade | 별도 테스트 없음 | 얇은 오케스트레이션 — Service 통합 + E2E로 커버 |
| Controller | E2E 테스트 | HTTP 요청/응답, 상태 코드, 인증 전체 흐름 검증 |

## 테스트 유틸리티
- `DatabaseCleanUp`: 테스트마다 테이블 정리
- TestContainers: 실제 MySQL, Redis, Kafka 인스턴스 제공
- 테스트 픽스처: `supports/` 모듈
# 테스트

## 테스트 분류
- 단위 테스트: 도메인 모델 검증
- 통합 테스트: Service + Repository (TestContainers)
- E2E 테스트: API 엔드포인트 전체 흐름

## 테스트 유틸리티
- `DatabaseCleanUp`: 테스트마다 테이블 정리
- TestContainers: 실제 MySQL, Redis, Kafka 인스턴스 제공
- 테스트 픽스처: `supports/` 모듈
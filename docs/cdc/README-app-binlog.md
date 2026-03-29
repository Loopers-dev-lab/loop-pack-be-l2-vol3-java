# CDC 경로 B: Connect 미사용 (앱 직접 binlog -> Kafka)

이 문서는 `feature/kafka-cdc-app` 브랜치 기준의 실행 절차를 설명한다.

## 역할 분담

- Spring Boot 앱(`apps/commerce-cdc-reader`)이 MySQL binlog를 직접 읽음
- 이벤트를 `KafkaTemplate`로 `cdc-app-*` 토픽에 발행
- Consumer는 기존과 동일하게 CDC 전용 토픽을 구독

## 실행

1. Kafka + CDC MySQL 준비
   - Kafka: `docker compose -f docker/infra-compose.yml up -d kafka`
   - MySQL(CDC 옵션): `docker compose -f docker/cdc/connect-compose.yml up -d mysql-cdc`
2. CDC reader 실행
   - `./gradlew bootRun -p apps/commerce-cdc-reader`

## 기본 설정

- 파일: `apps/commerce-cdc-reader/src/main/resources/application.yml`
- 기본 접속:
  - host: `localhost`
  - port: `3307` (`mysql-cdc`)
- 대상 테이블:
  - `loopers.product_metrics`
  - `loopers.outbox_event`
- 발행 토픽:
  - `cdc-app-loopers-product_metrics`
  - `cdc-app-loopers-outbox_event`

## 주의

- 경로 A와 마찬가지로 Polling Outbox 경로와 동일 토픽을 공유하면 이중 발행이 발생한다.
- 로컬 학습/검증 목적에서는 CDC 토픽을 별도로 유지한다.

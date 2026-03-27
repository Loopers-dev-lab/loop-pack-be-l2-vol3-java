# 로드맵 3단계 운영 Runbook (DLQ · 관측 · 알람)

## 목표

- `commerce-streamer` Consumer: DLQ 전송·메트릭·lag 게이지
- `commerce-batch` Outbox DLQ 재주입(redrive)
- Prometheus 알림 규칙 + 로컬 docker compose 연동

## 메트릭 (Prometheus `/actuator/prometheus`)

| 지표 | 의미 |
| --- | --- |
| `kafka_collector_events_dlq_total` | DLQ로 전송된 메시지 수. `topic` 태그 |
| `kafka_collector_events_processed_total` / `duplicate` / `failed` | collector 처리 결과 |
| `kafka_consumer_topic_lag_sum` | 그룹·토픽별 lag 합계. `group`, `topic` 태그 |
| `kafka_outbox_dlq_redrive_success_total` 등 | batch DLQ 재주입 결과 |

**참고**: 실제 이름은 Prometheus에 노출된 스냅을 기준으로 확인한다. Micrometer 네이밍 규칙에 따라 `_total` 접미사가 붙는다.

## 알림 규칙

- 파일: `docker/grafana/rules/kafka-collector-alerts.yml`
- `docker/grafana/prometheus.yml` 의 `rule_files` 로 로드
- `docker-compose -f docker/monitoring-compose.yml` 에서 `./grafana/rules` 마운트

임계값은 환경에 맞게 조정한다.

## DLQ 재주입 (batch)

- **dev 프로필**: `outbox.dlq-redrive.enabled: true` (기본 local/test는 false)
- Poison pill·PARK·한도: `KAFKA_APPLICATION.md` §8.5 및 `application.yml` `outbox.dlq-redrive.*`

## 운영 체크리스트

1. 브로커·컨슈머 그룹이 기동했는지
2. lag 게이지가 주기적으로 갱신되는지 (`collector.metrics.lag.poll-interval-ms`)
3. DLQ 토픽에 메시지가 쌓이면 redrive 로그·메트릭 확인
4. 알람이 연속 발생하면 원본 토픽·스키마·DB 상태 확인

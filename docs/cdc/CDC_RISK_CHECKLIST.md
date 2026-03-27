# CDC 운영 리스크 체크리스트 (Connect + Debezium)

## 1) 운영/관리

- [x] MySQL binlog 보존 기간이 장애 허용시간(RTO/RPO)보다 길다  
      - 로컬 기본값: `--binlog-expire-logs-seconds=259200`(3일)
- [x] Snapshot 부하 전략이 명시되어 있다 (`snapshot.mode=when_needed`)
- [x] Connect 장애 알림 룰 파일이 있다 (`docker/grafana/rules/cdc-connect-alerts.yml`)
- [x] Connect 상태 점검 절차가 있다 (`register-connector.sh`, `/status`, RUNNING 검증 포함)

## 2) 데이터/스키마

- [x] `table.include.list`로 캡처 범위를 최소화했다
- [x] Schema 변경 시 Consumer 역직렬화 방어(unknown field 허용 등)가 있다  
      - `apps/commerce-streamer/.../CdcConnectCollectorService`가 `JsonNode` 유연 파싱으로 unknown field에 내성을 가진다
- [x] Debezium envelope 오버헤드를 줄이기 위해 SMT(`ExtractNewRecordState`)를 사용한다

## 3) 일관성/설계

- [x] Polling 경로와 CDC 경로의 토픽이 분리되어 있다 (`cdc-connect-*`)
- [x] 메시지 키 전략이 명시되어 있다 (`message.key.columns`)
- [x] 중복 전달(at-least-once) 대비 Consumer 멱등 처리가 있다  
      - CDC 멱등키는 `source.file+source.pos` 우선, 미존재 시 `topic+partition+offset` fallback을 사용한다
      - `event_handled` PK로 중복 저장을 차단한다

## 4) 필수 점검 명령

```bash
# connector 등록/상태
./docker/cdc/register-connector.sh

# connector 상태 확인
curl -sS http://localhost:8083/connectors/loopers-mysql-cdc/status
```

#!/usr/bin/env bash
# 로드맵 0단계: 로컬 Kafka( docker/infra-compose.yml 의 kafka 서비스 )에 도메인 토픽을 생성한다.
#
# 사용 전:
#   docker compose -f docker/infra-compose.yml up -d kafka
#
# 실행 (권장 — 호스트에 kafka CLI 불필요):
#   ./docker/kafka-topics-create.sh
#
# 환경변수:
#   KAFKA_CONTAINER  기본값 kafka (docker compose 의 container_name)
#   KAFKA_TOPIC_PARTITIONS   기본값 3
#   KAFKA_TOPIC_REPLICATION  기본값 1

set -euo pipefail

CONTAINER="${KAFKA_CONTAINER:-kafka}"
PARTITIONS="${KAFKA_TOPIC_PARTITIONS:-3}"
REPLICATION="${KAFKA_TOPIC_REPLICATION:-1}"
# 컨테이너 내부에서는 브로커 리스너 PLAINTEXT 가 9092
INTERNAL_BOOTSTRAP="${KAFKA_INTERNAL_BOOTSTRAP:-localhost:9092}"

TOPICS=(
  "product-events"
  "product-events.DLQ"
  "order-events"
  "order-events.DLQ"
  "user-events"
  "user-events.DLQ"
  "coupon-issue-requests"
)

if ! docker ps --format '{{.Names}}' | grep -qx "${CONTAINER}"; then
  echo "Kafka 컨테이너 '${CONTAINER}' 가 실행 중이 아닙니다." >&2
  echo "실행: docker compose -f docker/infra-compose.yml up -d kafka" >&2
  exit 1
fi

echo "Container: ${CONTAINER}, internal bootstrap: ${INTERNAL_BOOTSTRAP}"

for topic in "${TOPICS[@]}"; do
  docker exec "${CONTAINER}" kafka-topics.sh \
    --bootstrap-server "${INTERNAL_BOOTSTRAP}" \
    --create \
    --if-not-exists \
    --topic "${topic}" \
    --partitions "${PARTITIONS}" \
    --replication-factor "${REPLICATION}"
  echo "OK: ${topic}"
done

echo "Done."

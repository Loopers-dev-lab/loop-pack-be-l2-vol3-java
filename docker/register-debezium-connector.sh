#!/bin/bash
set -e

echo "Waiting for Kafka Connect to be ready..."
until curl -s http://localhost:8083/connectors > /dev/null 2>&1; do
  sleep 2
done

echo "Registering Debezium MySQL Connector..."
curl -X POST http://localhost:8083/connectors \
  -H "Content-Type: application/json" \
  -d @- << 'EOF'
{
  "name": "loopers-outbox-connector",
  "config": {
    "connector.class": "io.debezium.connector.mysql.MySqlConnector",
    "tasks.max": "1",
    "database.hostname": "mysql",
    "database.port": "3306",
    "database.user": "root",
    "database.password": "root",
    "database.server.id": "184054",
    "topic.prefix": "loopers",
    "database.include.list": "loopers",
    "table.include.list": "loopers.event_outbox",
    "schema.history.internal.kafka.bootstrap.servers": "kafka:9092",
    "schema.history.internal.kafka.topic": "_schema_history",
    "transforms": "outbox",
    "transforms.outbox.type": "io.debezium.transforms.outbox.EventRouter",
    "transforms.outbox.table.field.event.id": "id",
    "transforms.outbox.table.field.event.key": "aggregate_id",
    "transforms.outbox.table.field.event.type": "event_type",
    "transforms.outbox.table.field.event.payload": "payload",
    "transforms.outbox.route.by.field": "aggregate_type",
    "transforms.outbox.route.topic.replacement": "${routedByValue}-events",
    "transforms.outbox.table.fields.additional.placement": "event_type:header:eventType",
    "tombstones.on.delete": "false"
  }
}
EOF

echo ""
echo "Connector registered successfully!"
curl -s http://localhost:8083/connectors/loopers-outbox-connector/status | python3 -m json.tool

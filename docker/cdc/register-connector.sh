#!/usr/bin/env bash
set -euo pipefail

CONNECT_URL="${CONNECT_URL:-http://localhost:8083}"
CONNECTOR_FILE="${CONNECTOR_FILE:-docker/cdc/connectors/mysql-loopers-connector.json}"

if [ ! -f "${CONNECTOR_FILE}" ]; then
  echo "connector file not found: ${CONNECTOR_FILE}" >&2
  exit 1
fi

echo "[1/2] Register connector from ${CONNECTOR_FILE}"
curl -sS -X POST \
  -H "Content-Type: application/json" \
  --data @"${CONNECTOR_FILE}" \
  "${CONNECT_URL}/connectors" || true

CONNECTOR_NAME="$(python3 - <<'PY'
import json
import os
path = os.environ.get("CONNECTOR_FILE", "docker/cdc/connectors/mysql-loopers-connector.json")
with open(path, "r", encoding="utf-8") as f:
    print(json.load(f)["name"])
PY
)"

echo "[2/2] Connector status: ${CONNECTOR_NAME}"
STATUS_JSON=""
for i in {1..15}; do
  STATUS_JSON="$(curl -sS "${CONNECT_URL}/connectors/${CONNECTOR_NAME}/status")"
  export STATUS_JSON
  if python3 - <<'PY'
import json
import os
import sys
s = json.loads(os.environ["STATUS_JSON"])
tasks = s.get("tasks", [])
if tasks and all(t.get("state") == "RUNNING" for t in tasks):
    sys.exit(0)
sys.exit(1)
PY
  then
    break
  fi
  sleep 2
done

echo "${STATUS_JSON}"
echo

python3 - <<'PY'
import json
import os
import sys

status = json.loads(os.environ["STATUS_JSON"])
connector_state = status.get("connector", {}).get("state", "")
tasks = status.get("tasks", [])
task_states = [t.get("state", "") for t in tasks]

if connector_state != "RUNNING":
    print(f"connector state is not RUNNING: {connector_state}", file=sys.stderr)
    sys.exit(2)

if not tasks or any(state != "RUNNING" for state in task_states):
    print(f"task states are not all RUNNING: {task_states}", file=sys.stderr)
    sys.exit(3)
PY

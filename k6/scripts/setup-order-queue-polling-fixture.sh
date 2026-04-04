#!/usr/bin/env bash

set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
LOGIN_ID_PREFIX="${LOGIN_ID_PREFIX:-orderqueue}"
LOGIN_PW="${LOGIN_PW:-Password1!}"
USER_COUNT="${USER_COUNT:-100}"

register_user() {
  local login_id="$1"
  local code
  code=$(curl -sS -o /tmp/queue_fixture_register.json -w "%{http_code}" \
    -X POST "${BASE_URL}/api/v1/members" \
    -H "Content-Type: application/json" \
    -d "{\"loginId\":\"${login_id}\",\"password\":\"${LOGIN_PW}\",\"name\":\"Queueuser\",\"birthDate\":\"19900101\",\"email\":\"${login_id}@test.com\",\"phone\":\"010-1111-1111\"}")
  if [[ "$code" != "201" && "$code" != "409" ]]; then
    echo "failed to register ${login_id}: ${code}" >&2
    cat /tmp/queue_fixture_register.json >&2
    exit 1
  fi
}

enter_queue() {
  local login_id="$1"
  local code
  code=$(curl -sS -o /tmp/queue_fixture_enter.json -w "%{http_code}" \
    -X POST "${BASE_URL}/api/v1/order-queue" \
    -H "X-Loopers-LoginId: ${login_id}" \
    -H "X-Loopers-LoginPw: ${LOGIN_PW}")
  if [[ "$code" != "201" ]]; then
    echo "failed to enter queue ${login_id}: ${code}" >&2
    cat /tmp/queue_fixture_enter.json >&2
    exit 1
  fi
}

for i in $(seq 1 "${USER_COUNT}"); do
  login_id="${LOGIN_ID_PREFIX}$(printf "%05d" "$i")"
  register_user "${login_id}" >/dev/null 2>&1 || true
  enter_queue "${login_id}" >/dev/null
  printf 'prepared %s\n' "${login_id}"
done

printf 'prepared_users=%s\n' "${USER_COUNT}"

#!/bin/bash
# ================================================================
# Session 8 — k6 점진적 부하 테스트 자동 실행
# ================================================================
# 사전 조건:
#   1. docker compose -f docker/infra-compose.yml up -d
#   2. ./gradlew :apps:commerce-api:bootRun (WSL 네이티브 경로에서)
#   3. bash k6/scripts/session8/seed-session8.sh 10000
#   4. k6 설치 (https://k6.io/docs/get-started/installation/)
# ================================================================

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
RESET="$SCRIPT_DIR/reset-queue.sh"
RESULTS_DIR="$SCRIPT_DIR/results"
mkdir -p "$RESULTS_DIR"
TIMESTAMP=$(date +%Y%m%d_%H%M%S)

echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "  Session 8 — k6 부하 테스트 시작"
echo "  $(date)"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

run_test() {
    local label="$1"
    local script="$2"
    shift 2
    local env_args="$@"

    echo ""
    echo "▶▶▶ $label"
    echo "────────────────────────────────"

    bash "$RESET" 2>/dev/null || true
    sleep 2

    local outfile="$RESULTS_DIR/${label// /_}_${TIMESTAMP}.txt"

    if k6 run $env_args "$SCRIPT_DIR/$script" 2>&1 | tee "$outfile"; then
        echo "✅ $label — PASS"
    else
        echo "❌ $label — FAIL (threshold 위반 또는 오류)"
    fi

    echo "  결과 저장: $outfile"
    echo "────────────────────────────────"
    sleep 3
}

# ━━ Level 1: Smoke ━━
echo ""
echo "═══════════════════════════════════"
echo "  LEVEL 1 — Smoke (10명)"
echo "═══════════════════════════════════"
run_test "L1_Smoke" "L1-queue-smoke.js"

# ━━ Level 2: Scale ━━
echo ""
echo "═══════════════════════════════════"
echo "  LEVEL 2 — Polling Scale Up"
echo "═══════════════════════════════════"
for SCALE in 100 500 1000 2000 5000 10000; do
    run_test "L2_Scale_${SCALE}" "L2-queue-polling-scale.js" "-e SCALE=$SCALE"

    # 이전 레벨 실패 시 자동 중단 (선택적)
    if [ $? -ne 0 ] && [ "$STOP_ON_FAIL" = "true" ]; then
        echo "⚠ SCALE=$SCALE 에서 실패. STOP_ON_FAIL=true이므로 중단."
        break
    fi
done

# ━━ Level 3: Mixed ━━
echo ""
echo "═══════════════════════════════════"
echo "  LEVEL 3 — Mixed Load"
echo "═══════════════════════════════════"
run_test "L3_Mixed_500P_20O" "L3-queue-mixed-load.js" "-e POLLERS=500 -e ORDERERS=20"
run_test "L3_Mixed_2000P_50O" "L3-queue-mixed-load.js" "-e POLLERS=2000 -e ORDERERS=50"

# ━━ Level 4: Spike ━━
echo ""
echo "═══════════════════════════════════"
echo "  LEVEL 4 — Spike (진입 폭주)"
echo "═══════════════════════════════════"
for SPIKE in 1000 3000 5000 10000; do
    run_test "L4_Spike_${SPIKE}" "L4-queue-enter-spike.js" "-e SPIKE=$SPIKE"
done

# ━━ Level 5: Lifecycle ━━
echo ""
echo "═══════════════════════════════════"
echo "  LEVEL 5 — E2E Lifecycle"
echo "═══════════════════════════════════"
run_test "L5_Lifecycle_100" "L5-queue-lifecycle.js" "-e TOTAL=100"
run_test "L5_Lifecycle_500" "L5-queue-lifecycle.js" "-e TOTAL=500"
run_test "L5_Lifecycle_2000" "L5-queue-lifecycle.js" "-e TOTAL=2000"

echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "  전체 테스트 완료: $(date)"
echo "  결과 디렉토리: $RESULTS_DIR"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

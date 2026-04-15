#!/usr/bin/env bash
#
# 랭킹 배치 선형성·스파이크 측정 스크립트.
#
# 설계.md Phase 5 (#26~29) 의 측정을 자동화한다:
#   - S/M/L 단계별 실행 시간 → 선형성 검증 (입력 N배 → 시간 N배)
#   - XL_SPIKE 단계 → worst-case SLA 확인
#
# 사용법:
#   ./scripts/measure-ranking-batch.sh
#
# benchmark 테스트는 결과를 apps/commerce-batch/build/benchmark-results.txt 에 append.
# 본 스크립트는 그 파일을 읽어 표 형식으로 출력한다.
# 결과 정리는 사람이 week10/측정결과.md 에 직접 기록한다 (자동 생성 아님).

set -euo pipefail

cd "$(dirname "$0")/.."

# gradle test 의 working dir 는 모듈 root 라 결과 파일은 모듈 build/ 아래에 생성됨
OUT_FILE="apps/commerce-batch/build/benchmark-results.txt"
rm -f "$OUT_FILE"

echo "▶ benchmarkTest 실행 중 (수 분 ~ 수십 분 소요 가능)..."
./gradlew :apps:commerce-batch:benchmarkTest --console=plain --rerun-tasks

if [[ ! -f "$OUT_FILE" ]]; then
    echo "❌ 결과 파일이 생성되지 않았습니다: $OUT_FILE"
    exit 1
fi

echo
echo "==================================================="
echo " 측정 결과 요약 (raw: $OUT_FILE)"
echo "==================================================="
printf "%-10s %-10s %-10s %-10s %-10s %-10s %-12s\n" \
    "label" "products" "active" "seedRows" "seedMs" "jobMs" "tps(rows/s)"
echo "---------------------------------------------------"

while read -r line; do
    [[ "$line" =~ ^BENCH\| ]] || continue
    label=$(echo    "$line" | sed -n 's/.*label=\([^ ]*\).*/\1/p')
    products=$(echo "$line" | sed -n 's/.*totalProducts=\([^ ]*\).*/\1/p')
    active=$(echo   "$line" | sed -n 's/.*activeProducts=\([^ ]*\).*/\1/p')
    seedRows=$(echo "$line" | sed -n 's/.*seedRows=\([^ ]*\).*/\1/p')
    seedMs=$(echo   "$line" | sed -n 's/.*seedMs=\([^ ]*\).*/\1/p')
    jobMs=$(echo    "$line" | sed -n 's/.*jobMs=\([^ ]*\).*/\1/p')
    tps=$(echo      "$line" | sed -n 's/.*tpsRowsPerSec=\([^ ]*\).*/\1/p')
    printf "%-10s %-10s %-10s %-10s %-10s %-10s %-12s\n" \
        "$label" "$products" "$active" "$seedRows" "$seedMs" "$jobMs" "$tps"
done < "$OUT_FILE"

echo
echo "▶ 결과를 week10/측정결과.md 에 정리해 기록하세요."

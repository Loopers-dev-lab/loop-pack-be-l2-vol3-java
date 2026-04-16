#!/usr/bin/env bash
# run-weekly-ranking.sh
# 주간 랭킹 배치 실행 스크립트
#
# 사용법:
#   ./scripts/run-weekly-ranking.sh [targetDate]
#
# targetDate 미입력 시 오늘 날짜(today)를 기본값으로 사용한다.
# 슬라이딩 윈도우는 [targetDate-7, targetDate) 이므로
# 매일 자정 직후 실행하면 어제까지의 7일 데이터를 집계한다.
#
# Jenkins Pipeline 예시:
#   stage('Weekly Ranking Batch') {
#     steps {
#       sh './scripts/run-weekly-ranking.sh'
#     }
#   }
#
# Cron 예시 (매일 새벽 2시):
#   0 2 * * * /app/scripts/run-weekly-ranking.sh >> /var/log/batch/weekly-ranking.log 2>&1

set -euo pipefail

JAR_PATH="${JAR_PATH:-apps/commerce-batch/build/libs/commerce-batch.jar}"
SPRING_PROFILE="${SPRING_PROFILE:-prd}"
TARGET_DATE="${1:-$(date +%Y-%m-%d)}"

echo "[$(date '+%Y-%m-%d %H:%M:%S')] Starting weeklyRankingJob | targetDate=${TARGET_DATE}"

java -jar "${JAR_PATH}" \
  --spring.profiles.active="${SPRING_PROFILE}" \
  --job.name=weeklyRankingJob \
  targetDate="${TARGET_DATE}"

EXIT_CODE=$?

echo "[$(date '+%Y-%m-%d %H:%M:%S')] weeklyRankingJob finished | exitCode=${EXIT_CODE}"
exit ${EXIT_CODE}

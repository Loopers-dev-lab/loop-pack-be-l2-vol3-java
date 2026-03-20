#!/bin/bash
# =============================================================================
# 인덱스 성능 벤치마크 실행 스크립트
# =============================================================================

set -e

# 설정
MYSQL_HOST="${MYSQL_HOST:-localhost}"
MYSQL_PORT="${MYSQL_PORT:-3306}"
MYSQL_USER="${MYSQL_USER:-application}"
MYSQL_PASSWORD="${MYSQL_PASSWORD:-application}"
MYSQL_DATABASE="${MYSQL_DATABASE:-loopers}"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SQL_FILE="$SCRIPT_DIR/index-performance-test.sql"
REPORT_FILE="$SCRIPT_DIR/benchmark-report-$(date +%Y%m%d-%H%M%S).txt"

echo "=== 인덱스 성능 벤치마크 ==="
echo "Host: $MYSQL_HOST:$MYSQL_PORT"
echo "Database: $MYSQL_DATABASE"
echo "Report: $REPORT_FILE"
echo ""

# Docker MySQL이 실행 중인지 확인
if ! docker ps | grep -q mysql; then
    echo "MySQL Docker 컨테이너가 실행 중이 아닙니다."
    echo "docker-compose -f docker/infra-compose.yml up -d mysql 명령으로 시작하세요."
    exit 1
fi

# SQL 파일 로드 및 벤치마크 실행
echo "벤치마크 프로시저 로드 중..."
docker exec -i mysql mysql -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" "$MYSQL_DATABASE" < "$SQL_FILE"

echo "벤치마크 실행 중... (약 5-10분 소요)"
docker exec -i mysql mysql -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" "$MYSQL_DATABASE" \
    -e "CALL run_full_benchmark();" 2>&1 | tee "$REPORT_FILE"

echo ""
echo "=== 벤치마크 완료 ==="
echo "리포트 저장됨: $REPORT_FILE"

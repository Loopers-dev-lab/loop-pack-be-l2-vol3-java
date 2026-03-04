#!/usr/bin/env bash
# skill-validate: 스킬 구조 및 프론트매터 자동 검증
# 사용법: bash validate.sh <skill-directory-path>

set -euo pipefail

# ─────────────────────────────────────────────
# 색상 정의
# ─────────────────────────────────────────────
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[0;33m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color
BOLD='\033[1m'

# ─────────────────────────────────────────────
# 카운터
# ─────────────────────────────────────────────
PASS=0
FAIL=0
WARN=0

pass() {
    echo -e "  ${GREEN}✓${NC} $1"
    PASS=$((PASS + 1))
}

fail() {
    echo -e "  ${RED}✗${NC} $1"
    FAIL=$((FAIL + 1))
}

warn() {
    echo -e "  ${YELLOW}⚠${NC} $1"
    WARN=$((WARN + 1))
}

# ─────────────────────────────────────────────
# 인수 검증
# ─────────────────────────────────────────────
if [ $# -eq 0 ]; then
    echo -e "${RED}사용법: bash validate.sh <skill-directory-path>${NC}"
    echo "예시: bash validate.sh ./my-skill"
    exit 1
fi

SKILL_DIR="$1"

if [ ! -d "$SKILL_DIR" ]; then
    echo -e "${RED}에러: '$SKILL_DIR'는 유효한 디렉토리가 아닙니다.${NC}"
    exit 1
fi

SKILL_DIR_NAME=$(basename "$SKILL_DIR")

echo ""
echo -e "${BOLD}${CYAN}═══════════════════════════════════════════${NC}"
echo -e "${BOLD}${CYAN}  스킬 검증 리포트: ${SKILL_DIR_NAME}${NC}"
echo -e "${BOLD}${CYAN}═══════════════════════════════════════════${NC}"
echo ""

# ═════════════════════════════════════════════
# 1. 파일 구조 검증
# ═════════════════════════════════════════════
echo -e "${BOLD}[1/5] 파일 구조 검증${NC}"

# SKILL.md 존재 확인 (대소문자 정확히)
if [ -f "$SKILL_DIR/SKILL.md" ]; then
    pass "SKILL.md 파일 존재"
else
    # 대소문자 변형 체크
    FOUND=$(find "$SKILL_DIR" -maxdepth 1 -iname "skill.md" -type f 2>/dev/null)
    if [ -n "$FOUND" ]; then
        fail "SKILL.md 파일명 대소문자 오류 → 발견된 파일: $(basename "$FOUND")"
    else
        fail "SKILL.md 파일 없음 (필수)"
    fi
    echo -e "\n${RED}SKILL.md가 없어 검증을 계속할 수 없습니다.${NC}"
    exit 1
fi

# README.md 금지
if [ -f "$SKILL_DIR/README.md" ]; then
    fail "README.md가 스킬 폴더 안에 있음 → 스킬 폴더 내 README.md 금지"
else
    pass "스킬 폴더 내 README.md 없음"
fi

# 폴더명 케밥케이스 검증
if echo "$SKILL_DIR_NAME" | grep -qE '^[a-z0-9]+(-[a-z0-9]+)*$'; then
    pass "폴더명 케밥케이스: $SKILL_DIR_NAME"
else
    fail "폴더명이 케밥케이스가 아님: $SKILL_DIR_NAME (소문자+하이픈만 허용)"
fi

echo ""

# ═════════════════════════════════════════════
# 2. 프론트매터 구조 검증
# ═════════════════════════════════════════════
echo -e "${BOLD}[2/5] 프론트매터 구조 검증${NC}"

SKILL_FILE="$SKILL_DIR/SKILL.md"
CONTENT=$(cat "$SKILL_FILE")

# --- 구분자 확인
FIRST_LINE=$(head -1 "$SKILL_FILE")
if [ "$FIRST_LINE" = "---" ]; then
    pass "프론트매터 시작 구분자 (---) 존재"
else
    fail "프론트매터 시작 구분자 (---) 없음 → 첫 번째 줄이 '---'이어야 함"
fi

# 닫는 --- 확인 (두 번째 --- 찾기)
CLOSING_LINE=$(awk 'NR>1 && /^---$/{print NR; exit}' "$SKILL_FILE")
if [ -n "$CLOSING_LINE" ]; then
    pass "프론트매터 종료 구분자 (---) 존재 (${CLOSING_LINE}번째 줄)"
else
    fail "프론트매터 종료 구분자 (---) 없음"
fi

# 프론트매터 추출
if [ -n "$CLOSING_LINE" ]; then
    FRONTMATTER=$(sed -n "2,$((CLOSING_LINE - 1))p" "$SKILL_FILE")
else
    FRONTMATTER=""
fi

echo ""

# ═════════════════════════════════════════════
# 3. name 필드 검증
# ═════════════════════════════════════════════
echo -e "${BOLD}[3/5] name 필드 검증${NC}"

NAME=$(echo "$FRONTMATTER" | sed -n 's/^name:[[:space:]]*//p' | xargs 2>/dev/null || echo "")

if [ -z "$NAME" ]; then
    fail "name 필드 없음 (필수)"
else
    pass "name 필드 존재: $NAME"

    # 케밥케이스 검증
    if echo "$NAME" | grep -qE '^[a-z0-9]+(-[a-z0-9]+)*$'; then
        pass "name 케밥케이스 준수"
    else
        fail "name이 케밥케이스가 아님: $NAME"
        # 구체적 원인 진단
        if echo "$NAME" | grep -qE '[A-Z]'; then
            echo -e "      → 대문자 포함됨"
        fi
        if echo "$NAME" | grep -qE '[[:space:]]'; then
            echo -e "      → 공백 포함됨"
        fi
        if echo "$NAME" | grep -qE '_'; then
            echo -e "      → 언더스코어 포함됨"
        fi
    fi

    # 64자 제한
    NAME_LEN=${#NAME}
    if [ "$NAME_LEN" -le 64 ]; then
        pass "name 길이: ${NAME_LEN}자 (≤64)"
    else
        fail "name이 64자 초과: ${NAME_LEN}자"
    fi

    # 예약어 검증
    if echo "$NAME" | grep -qiE '^(claude|anthropic)'; then
        fail "name에 예약어 접두사 사용: $NAME (claude/anthropic 금지)"
    else
        pass "예약어 접두사 없음"
    fi

    # 폴더명 일치 검증
    if [ "$NAME" = "$SKILL_DIR_NAME" ]; then
        pass "name과 폴더명 일치"
    else
        warn "name($NAME)과 폴더명($SKILL_DIR_NAME) 불일치 → 일치 권장"
    fi
fi

echo ""

# ═════════════════════════════════════════════
# 4. description 필드 검증
# ═════════════════════════════════════════════
echo -e "${BOLD}[4/5] description 필드 검증${NC}"

# 멀티라인 description 추출 (sed 기반, UTF-8 호환)
# 단일 라인: description: 값
# 멀티 라인: description: 값\n  이어지는 줄 (들여쓰기로 판단)
DESC_FIRST=$(echo "$FRONTMATTER" | sed -n 's/^description:[ \t]*//p')
if [ -n "$DESC_FIRST" ]; then
    # description 라인 번호 찾기
    DESC_LINE=$(echo "$FRONTMATTER" | grep -n '^description:' | head -1 | cut -d: -f1)
    DESC="$DESC_FIRST"
    # 다음 줄부터 들여쓰기된 연속 라인 병합 (멀티라인 YAML)
    TOTAL_LINES=$(echo "$FRONTMATTER" | wc -l)
    NEXT_LINE=$((DESC_LINE + 1))
    while [ "$NEXT_LINE" -le "$TOTAL_LINES" ]; do
        LINE=$(echo "$FRONTMATTER" | sed -n "${NEXT_LINE}p")
        # 들여쓰기로 시작하면 연속 라인
        if echo "$LINE" | grep -qE '^[[:space:]]'; then
            LINE_TRIMMED=$(echo "$LINE" | sed 's/^[ \t]*//')
            DESC="$DESC $LINE_TRIMMED"
        else
            break
        fi
        NEXT_LINE=$((NEXT_LINE + 1))
    done
fi
# 따옴표 제거
DESC=$(echo "$DESC" | sed 's/^["'"'"']//;s/["'"'"']$//')

if [ -z "$DESC" ]; then
    fail "description 필드 없음 (필수)"
else
    pass "description 필드 존재"

    # 1024자 제한
    DESC_LEN=${#DESC}
    if [ "$DESC_LEN" -lt 1024 ]; then
        pass "description 길이: ${DESC_LEN}자 (<1024)"
    else
        fail "description이 1024자 이상: ${DESC_LEN}자"
    fi

    # XML 태그 검사
    if echo "$DESC" | grep -qE '[<>]'; then
        fail "description에 XML 태그 문자 (<>) 포함 → 보안 제한 위반"
    else
        pass "XML 태그 문자 없음"
    fi

    # "무엇을 하는지" 존재 여부 (휴리스틱)
    # description이 20자 미만이면 너무 짧아서 내용이 부족할 가능성
    if [ "$DESC_LEN" -lt 20 ]; then
        warn "description이 매우 짧음 (${DESC_LEN}자) → 무엇을 하는지/언제 사용하는지 포함 확인 필요"
    else
        pass "description 최소 길이 충족"
    fi

    # 트리거 문구 포함 여부 (휴리스틱)
    TRIGGER_KEYWORDS="사용합니다|사용하세요|Use when|use this|트리거|trigger|요청할 때|asks for|mentions"
    if echo "$DESC" | grep -qiE "$TRIGGER_KEYWORDS"; then
        pass "트리거 조건 문구 감지됨"
    else
        warn "트리거 조건 문구가 명시적이지 않음 → '...할 때 사용합니다' 패턴 권장"
    fi
fi

echo ""

# ═════════════════════════════════════════════
# 5. 콘텐츠 품질 검증
# ═════════════════════════════════════════════
echo -e "${BOLD}[5/5] 콘텐츠 품질 검증${NC}"

# SKILL.md 본문 추출 (프론트매터 이후)
if [ -n "$CLOSING_LINE" ]; then
    BODY=$(tail -n +"$((CLOSING_LINE + 1))" "$SKILL_FILE")
else
    BODY="$CONTENT"
fi

# 본문 존재 여부
BODY_TRIMMED=$(echo "$BODY" | sed '/^$/d' | head -1)
if [ -n "$BODY_TRIMMED" ]; then
    pass "SKILL.md 본문 콘텐츠 존재"
else
    fail "SKILL.md 본문이 비어있음"
fi

# 단어 수 체크 (5000단어 권장 상한)
WORD_COUNT=$(echo "$BODY" | wc -w | xargs)
if [ "$WORD_COUNT" -le 5000 ]; then
    pass "SKILL.md 본문 크기: ${WORD_COUNT}단어 (≤5000 권장)"
else
    warn "SKILL.md 본문이 ${WORD_COUNT}단어 → 5000단어 이하 권장, references/로 분리 고려"
fi

# 예시 섹션 존재 여부
if echo "$BODY" | grep -qiE '##.*예시|##.*example'; then
    pass "예시(example) 섹션 존재"
else
    warn "예시 섹션 없음 → 사용자 요청 → 동작 → 결과 예시 권장"
fi

# 에러 처리/트러블슈팅 섹션 존재 여부
if echo "$BODY" | grep -qiE '##.*에러|##.*트러블슈팅|##.*troubleshoot|##.*error'; then
    pass "에러 처리/트러블슈팅 섹션 존재"
else
    warn "에러 처리 섹션 없음 → 일반적인 실패 시나리오 문서화 권장"
fi

# 하위 폴더 파일 참조 확인 (references/, templates/, examples/ 등)
SUB_FILES=$(find "$SKILL_DIR" -mindepth 2 -type f -name "*.md" 2>/dev/null)
if [ -n "$SUB_FILES" ]; then
    UNLINKED=0
    while IFS= read -r sub_file; do
        # SKILL_DIR 기준 상대 경로 (예: references/erd-syntax.md)
        rel_path="${sub_file#$SKILL_DIR/}"
        sub_basename=$(basename "$sub_file")
        # 상대 경로 또는 파일명으로 참조 확인
        if ! grep -q "$sub_basename" "$SKILL_FILE"; then
            warn "${rel_path}이 SKILL.md에서 참조되지 않음"
            UNLINKED=$((UNLINKED + 1))
        fi
    done <<< "$SUB_FILES"
    if [ "$UNLINKED" -eq 0 ]; then
        pass "모든 하위 폴더 파일이 SKILL.md에서 참조됨"
    fi
fi

echo ""

# ═════════════════════════════════════════════
# 결과 요약
# ═════════════════════════════════════════════
echo -e "${BOLD}${CYAN}═══════════════════════════════════════════${NC}"
echo -e "${BOLD}  검증 결과 요약${NC}"
echo -e "${CYAN}═══════════════════════════════════════════${NC}"
echo -e "  ${GREEN}통과: ${PASS}개${NC}"
echo -e "  ${RED}실패: ${FAIL}개${NC}"
echo -e "  ${YELLOW}경고: ${WARN}개${NC}"
echo ""

if [ "$FAIL" -eq 0 ] && [ "$WARN" -eq 0 ]; then
    echo -e "  ${GREEN}${BOLD}🎉 모든 검증 통과! 스킬이 배포 준비 완료되었습니다.${NC}"
elif [ "$FAIL" -eq 0 ]; then
    echo -e "  ${YELLOW}${BOLD}⚠ 실패 없음, 경고 ${WARN}개. 경고 항목 개선을 권장합니다.${NC}"
else
    echo -e "  ${RED}${BOLD}✗ 실패 ${FAIL}개 발견. 실패 항목을 수정한 후 재검증하세요.${NC}"
fi

echo ""
exit $FAIL

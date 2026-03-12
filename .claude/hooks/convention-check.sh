#!/bin/bash
# ============================================================================
# convention-check.sh — Command Hook (PostToolUse)
# 프로젝트 컨벤션 패턴 위반을 자동 감지하는 셸 스크립트
#
# 실행 위치: .claude/hooks/convention-check.sh
# 트리거:    PostToolUse (Write|Edit) — Java 파일 수정 시 자동 실행
# exit 0 = 통과, exit 1 = 경고(계속), exit 2 = 차단(Claude에게 피드백)
# ============================================================================

set -euo pipefail

# ── stdin에서 Hook JSON 데이터 읽기 ──

INPUT=$(cat)
FILE_PATH=$(echo "$INPUT" | jq -r '.tool_input.file_path // .tool_input.command // ""' 2>/dev/null)

# Java 파일이 아니면 스킵
if ! echo "$FILE_PATH" | grep -q '\.java$'; then
  exit 0
fi

# 프로젝트 루트 결정
PROJECT_DIR="${CLAUDE_PROJECT_DIR:-$(pwd)}"
SRC="$PROJECT_DIR/apps/commerce-api/src/main/java/com/loopers"

# src 디렉토리가 없으면 스킵 (프로젝트 외부 파일)
if [ ! -d "$SRC" ]; then
  exit 0
fi

ERRORS=""
WARNINGS=""

# ============================================================================
# 규칙 1: 계층 의존 방향 위반
# 출처: package-convention.md § 5. 의존 방향 규칙
# ============================================================================

# 1-1. domain → infrastructure 의존 금지
if grep -rn "import com\.loopers\.infrastructure" "$SRC/domain/" 2>/dev/null | grep -v "^Binary" | head -5; then
  ERRORS+="[위반] domain → infrastructure 의존 금지\n"
  ERRORS+="  → domain 계층은 순수 Java로만 구성. infrastructure를 import할 수 없음\n"
  ERRORS+="  → 참고: package-convention.md § 5. 의존 방향 규칙\n\n"
fi

# 1-2. domain → application 의존 금지
if grep -rn "import com\.loopers\.application" "$SRC/domain/" 2>/dev/null | grep -v "^Binary" | head -5; then
  ERRORS+="[위반] domain → application 의존 금지\n"
  ERRORS+="  → domain은 application 계층을 알 수 없음\n"
  ERRORS+="  → 참고: package-convention.md § 5. 의존 방향 규칙\n\n"
fi

# 1-3. domain → interfaces 의존 금지
if grep -rn "import com\.loopers\.interfaces" "$SRC/domain/" 2>/dev/null | grep -v "^Binary" | head -5; then
  ERRORS+="[위반] domain → interfaces 의존 금지\n"
  ERRORS+="  → domain은 interfaces 계층을 알 수 없음\n"
  ERRORS+="  → 참고: package-convention.md § 5. 의존 방향 규칙\n\n"
fi

# 1-4. interfaces → infrastructure 직접 의존 금지
if grep -rn "import com\.loopers\.infrastructure" "$SRC/interfaces/" 2>/dev/null | grep -v "^Binary" | head -5; then
  ERRORS+="[위반] interfaces → infrastructure 직접 의존 금지\n"
  ERRORS+="  → Controller는 Facade를 통해 접근. Repository 직접 접근 불가\n"
  ERRORS+="  → 참고: package-convention.md § 5. 의존 방향 규칙\n\n"
fi

# 1-5. application → interfaces 역방향 의존 금지
if grep -rn "import com\.loopers\.interfaces" "$SRC/application/" 2>/dev/null | grep -v "^Binary" | head -5; then
  ERRORS+="[위반] application → interfaces 역방향 의존 금지\n"
  ERRORS+="  → Facade가 Controller/Request/Response DTO를 알면 안 됨\n"
  ERRORS+="  → 참고: package-convention.md § 5. 의존 방향 규칙\n\n"
fi

# ============================================================================
# 규칙 2: domain 계층 Spring 의존 금지
# 출처: package-convention.md § 5, infrastructure-convention.md § 1
# ============================================================================

# 2-1. domain에 @Repository 금지
if grep -rn "^import org\.springframework\.stereotype\.Repository;" "$SRC/domain/" 2>/dev/null | head -3; then
  ERRORS+="[위반] domain 패키지에 Spring @Repository 금지\n"
  ERRORS+="  → @Repository는 infrastructure의 RepositoryImpl에만 사용\n"
  ERRORS+="  → 참고: infrastructure-convention.md § 1. Repository 패턴\n\n"
fi

# 2-2. domain에 @Service 허용 (컨벤션)
# 컨벤션: domain Service는 @Service 어노테이션 사용 가능.
# @Component, @Repository(Spring)는 여전히 금지. @Service만 허용.

# 2-3. domain에 @Transactional 허용 (컨벤션)
# 컨벤션: domain Service는 @Transactional 사용 가능.
# Entity, VO, Repository 인터페이스에서는 사용하지 않는다.

# 2-4. domain에 Spring Data Page/Pageable 허용 (컨벤션)
# 컨벤션: domain Repository 인터페이스에서 Page, Pageable 사용 가능.
# JpaRepository 상속은 금지 (infrastructure에서만 상속).

# ============================================================================
# 규칙 3: @OneToMany 사용 금지
# 출처: infrastructure-convention.md § 4. DB 제약조건 규칙
# ============================================================================

if grep -rn "@OneToMany" "$SRC/" 2>/dev/null | grep -v "^Binary" | head -3; then
  ERRORS+="[위반] @OneToMany 사용 금지\n"
  ERRORS+="  → ID 참조 + 별도 Repository 조회로 대체\n"
  ERRORS+="  → 참고: infrastructure-convention.md § 4. DB 제약조건 규칙\n\n"
fi

# ============================================================================
# 규칙 4: Entity에 public setter 금지
# 출처: entity-vo-convention.md § 1. Entity 작성 규칙
# ============================================================================

if grep -rn "public void set[A-Z]" "$SRC/domain/" 2>/dev/null | grep -v "^Binary" | head -3; then
  ERRORS+="[위반] Entity에 public setter 금지\n"
  ERRORS+="  → 도메인 메서드(cancel(), update() 등)로 상태를 변경할 것\n"
  ERRORS+="  → 참고: entity-vo-convention.md § 1. Entity 작성 규칙\n\n"
fi

# ============================================================================
# 규칙 5: Facade → Facade 호출 금지
# 출처: service-layer-convention.md § 5. 계층 간 호출 규칙
# ============================================================================

# application 패키지의 Facade 파일에서 다른 Facade를 import하는지 검사
for FACADE_FILE in $(find "$SRC/application/" -name "*Facade.java" 2>/dev/null); do
  FACADE_NAME=$(basename "$FACADE_FILE" .java)
  # 자기 자신이 아닌 다른 Facade를 import하는지 확인
  OTHER_FACADE_IMPORTS=$(grep -n "import.*Facade;" "$FACADE_FILE" 2>/dev/null | grep -v "$FACADE_NAME" || true)
  if [ -n "$OTHER_FACADE_IMPORTS" ]; then
    ERRORS+="[위반] Facade → Facade 호출 금지: $FACADE_NAME\n"
    ERRORS+="  → $OTHER_FACADE_IMPORTS\n"
    ERRORS+="  → Facade는 타 도메인의 Domain Service를 직접 호출해야 함\n"
    ERRORS+="  → 참고: service-layer-convention.md § 5. 계층 간 호출 규칙\n\n"
  fi
done

# ============================================================================
# 규칙 6: @ManyToOne 시 NO_CONSTRAINT 필수
# 출처: infrastructure-convention.md § 4. DB 제약조건 규칙
# ============================================================================

# @ManyToOne이 있는 파일에서 NO_CONSTRAINT가 없는 경우 경고
for ENTITY_FILE in $(grep -rl "@ManyToOne" "$SRC/" 2>/dev/null); do
  if ! grep -q "NO_CONSTRAINT\|ConstraintMode" "$ENTITY_FILE" 2>/dev/null; then
    WARNINGS+="[경고] @ManyToOne에 NO_CONSTRAINT 누락: $(basename $ENTITY_FILE)\n"
    WARNINGS+="  → @JoinColumn(foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT)) 필수\n"
    WARNINGS+="  → 참고: infrastructure-convention.md § 4. DB 제약조건 규칙\n\n"
  fi
done

# ============================================================================
# 규칙 7: @Entity에 @NoArgsConstructor(access = PROTECTED) 필수
# 출처: entity-vo-convention.md § 1. Entity 작성 규칙
# ============================================================================

for ENTITY_FILE in $(grep -rl "^@Entity" "$SRC/domain/" 2>/dev/null); do
  if ! grep -q "NoArgsConstructor" "$ENTITY_FILE" 2>/dev/null; then
    WARNINGS+="[경고] @Entity에 @NoArgsConstructor(access = PROTECTED) 누락: $(basename $ENTITY_FILE)\n"
    WARNINGS+="  → JPA 프록시를 위해 protected 기본 생성자 필수\n"
    WARNINGS+="  → 참고: entity-vo-convention.md § 1. Entity 작성 규칙\n\n"
  elif ! grep -q "PROTECTED\|AccessLevel.PROTECTED" "$ENTITY_FILE" 2>/dev/null; then
    WARNINGS+="[경고] @NoArgsConstructor의 access가 PROTECTED가 아님: $(basename $ENTITY_FILE)\n"
    WARNINGS+="  → @NoArgsConstructor(access = AccessLevel.PROTECTED) 필수\n"
    WARNINGS+="  → 참고: entity-vo-convention.md § 1. Entity 작성 규칙\n\n"
  fi
done

# ============================================================================
# 규칙 9: Controller → Facade 직접 호출 확인 (Domain Service 직접 호출 금지)
# 출처: service-layer-convention.md § 5. 계층 간 호출 규칙
# ============================================================================

for CTRL_FILE in $(find "$SRC/interfaces/" -name "*Controller.java" 2>/dev/null); do
  SERVICE_IMPORT=$(grep -n "import com\.loopers\.domain.*Service;" "$CTRL_FILE" 2>/dev/null || true)
  if [ -n "$SERVICE_IMPORT" ]; then
    WARNINGS+="[경고] Controller → Domain Service 직접 호출 의심: $(basename $CTRL_FILE)\n"
    WARNINGS+="  → $SERVICE_IMPORT\n"
    WARNINGS+="  → Controller는 Facade만 호출해야 함. Domain Service 직접 접근 금지\n"
    WARNINGS+="  → 참고: service-layer-convention.md § 5. 계층 간 호출 규칙\n\n"
  fi
done

# ============================================================================
# 규칙 10: Domain Service에서 타 도메인 Repository 직접 접근 금지
# 출처: service-layer-convention.md § 5. 계층 간 호출 규칙
# ============================================================================

for SERVICE_FILE in $(find "$SRC/domain/" -name "*Service.java" 2>/dev/null); do
  SERVICE_DOMAIN=$(echo "$SERVICE_FILE" | grep -oP 'domain/\K[^/]+')
  # 타 도메인 Repository import 검사
  OTHER_REPO=$(grep -n "import com\.loopers\.domain\." "$SERVICE_FILE" 2>/dev/null \
    | grep "Repository;" \
    | grep -v "domain\.$SERVICE_DOMAIN\." || true)
  if [ -n "$OTHER_REPO" ]; then
    ERRORS+="[위반] Domain Service → 타 도메인 Repository 직접 접근 금지: $(basename $SERVICE_FILE)\n"
    ERRORS+="  → $OTHER_REPO\n"
    ERRORS+="  → 타 도메인 데이터는 Facade에서 조율해야 함\n"
    ERRORS+="  → 참고: service-layer-convention.md § 5. 계층 간 호출 규칙\n\n"
  fi
done

# ============================================================================
# 규칙 11: @Builder 사용 금지 (Entity)
# 출처: entity-vo-convention.md § 1. 생성 패턴
# ============================================================================

for ENTITY_FILE in $(grep -rl "^@Entity" "$SRC/domain/" 2>/dev/null); do
  if grep -q "@Builder" "$ENTITY_FILE" 2>/dev/null; then
    ERRORS+="[위반] Entity에 @Builder 사용 금지: $(basename $ENTITY_FILE)\n"
    ERRORS+="  → 정적 팩토리 메서드(create, register 등) + private 생성자를 사용할 것\n"
    ERRORS+="  → 참고: entity-vo-convention.md § 1. Entity 작성 규칙\n\n"
  fi
done

# ============================================================================
# 규칙 12: Facade에 private 메서드 금지
# 출처: service-layer-convention.md § 2. Facade에 넣지 않는 것
# ============================================================================

for FACADE_FILE in $(find "$SRC/application/" -name "*Facade.java" 2>/dev/null); do
  PRIVATE_METHODS=$(grep -n "private .*(.*)" "$FACADE_FILE" 2>/dev/null \
    | grep -v "private final\|private static final" || true)
  if [ -n "$PRIVATE_METHODS" ]; then
    ERRORS+="[위반] Facade에 private 메서드 금지: $(basename $FACADE_FILE)\n"
    ERRORS+="  → $PRIVATE_METHODS\n"
    ERRORS+="  → private 메서드가 필요하면 Domain Service 또는 Entity로 이동할 것\n"
    ERRORS+="  → 참고: service-layer-convention.md § 2. Facade에 넣지 않는 것\n\n"
  fi
done

# ============================================================================
# 결과 출력
# ============================================================================

if [ -n "$ERRORS" ]; then
  echo -e "🚫 컨벤션 위반 감지 (차단):\n" >&2
  echo -e "$ERRORS" >&2
  if [ -n "$WARNINGS" ]; then
    echo -e "⚠️  추가 경고:\n" >&2
    echo -e "$WARNINGS" >&2
  fi
  echo "📖 전체 컨벤션: .claude/skills/project-convention/SKILL.md 참조" >&2
  exit 2  # 차단 — Claude에게 피드백 전달하여 자동 수정 유도
fi

if [ -n "$WARNINGS" ]; then
  echo -e "⚠️  컨벤션 경고 (계속 진행 가능):\n" >&2
  echo -e "$WARNINGS" >&2
  echo "📖 전체 컨벤션: .claude/skills/project-convention/SKILL.md 참조" >&2
  exit 1  # 경고만 — 사용자에게 표시하고 계속 진행
fi

# 모든 검사 통과
exit 0

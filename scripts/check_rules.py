#!/usr/bin/env python3

from __future__ import annotations

import re
import sys
from dataclasses import dataclass
from pathlib import Path
from typing import Iterable


@dataclass(frozen=True)
class Violation:
    rule_id: str
    path: Path
    line: int
    message: str


ROOT = Path(__file__).resolve().parents[1]
MAIN_JAVA = ROOT / "apps/commerce-api/src/main/java/com/loopers"
TEST_ROOT = ROOT / "apps/commerce-api/src/test"

PARAMETER_COUNT_WHITELIST: set[tuple[str, str]] = {
    (
        "apps/commerce-api/src/main/java/com/loopers/infrastructure/order/OrderRepositoryImpl.java",
        "findByUserId",
    ),
    (
        "apps/commerce-api/src/main/java/com/loopers/interfaces/auth/AdminLdapInterceptor.java",
        "preHandle",
    ),
}


def read_lines(path: Path) -> list[str]:
    return path.read_text(encoding="utf-8").splitlines()


def java_files(base: Path) -> list[Path]:
    if not base.exists():
        return []
    return sorted(base.rglob("*.java"))


def rel(path: Path) -> Path:
    return path.relative_to(ROOT)


def add_simple_pattern_violations(
    violations: list[Violation],
    files: Iterable[Path],
    rule_id: str,
    pattern: re.Pattern[str],
    message: str,
) -> None:
    for file_path in files:
        for idx, line in enumerate(read_lines(file_path), start=1):
            if pattern.search(line):
                violations.append(Violation(rule_id, rel(file_path), idx, message))


def check_controller_orchestration(violations: list[Violation]) -> None:
    controllers = sorted((MAIN_JAVA / "interfaces/api").rglob("*Controller.java"))
    patterns = [
        (re.compile(r"\.stream\("), "Controller must not orchestrate stream composition"),
        (re.compile(r"Collectors\."), "Controller must not orchestrate collectors composition"),
        (re.compile(r"\.collect\("), "Controller must not orchestrate collection mapping"),
    ]
    for file_path in controllers:
        lines = read_lines(file_path)
        for idx, line in enumerate(lines, start=1):
            for pattern, message in patterns:
                if pattern.search(line):
                    violations.append(
                        Violation("CONTROLLER_ORCHESTRATION", rel(file_path), idx, message)
                    )
        for idx, line in enumerate(lines, start=1):
            if re.search(r"private\s+final\s+.*Repository\b", line):
                violations.append(
                    Violation(
                        "CONTROLLER_REPOSITORY_DEP",
                        rel(file_path),
                        idx,
                        "Controller must not depend on Repository directly",
                    )
                )


def check_private_methods_in_services(violations: list[Violation]) -> None:
    targets = []
    targets.extend(sorted((MAIN_JAVA / "application").rglob("*Service.java")))
    targets.extend(sorted((MAIN_JAVA / "domain").rglob("*Service.java")))

    for file_path in targets:
        class_name = file_path.stem
        for idx, line in enumerate(read_lines(file_path), start=1):
            stripped = line.strip()
            if not stripped.startswith("private "):
                continue
            if "(" not in stripped or ")" not in stripped:
                continue
            if stripped.startswith("private final") or stripped.startswith("private static"):
                continue
            method_name = stripped.split("(")[0].split()[-1]
            if method_name == class_name:
                continue
            violations.append(
                Violation(
                    "NO_PRIVATE_IN_SERVICE",
                    rel(file_path),
                    idx,
                    "Application/Domain Service must not declare private methods",
                )
            )


def extract_service_method_names(lines: list[str], class_name: str) -> set[str]:
    names: set[str] = set()
    decl = re.compile(
        r"^\s*(?:public|protected|private)\s+(?:static\s+)?[\w<>,\[\]?\s]+\s+(\w+)\s*\("
    )
    for line in lines:
        stripped = line.strip()
        if stripped.startswith("public record") or stripped.startswith("record "):
            continue
        m = decl.match(line)
        if not m:
            continue
        name = m.group(1)
        if name == class_name:
            continue
        names.add(name)
    return names


def is_method_declaration_line(line: str, method_name: str) -> bool:
    pattern = re.compile(
        rf"^\s*(?:public|protected|private)\s+(?:static\s+)?[\w<>,\[\]?\s]+\s+{re.escape(method_name)}\s*\("
    )
    return bool(pattern.match(line))


def check_intra_service_method_calls(violations: list[Violation]) -> None:
    targets = []
    targets.extend(sorted((MAIN_JAVA / "application").rglob("*Service.java")))
    targets.extend(sorted((MAIN_JAVA / "domain").rglob("*Service.java")))

    for file_path in targets:
        lines = read_lines(file_path)
        method_names = extract_service_method_names(lines, file_path.stem)
        if not method_names:
            continue

        for idx, line in enumerate(lines, start=1):
            stripped = line.strip()
            if stripped.startswith("//"):
                continue
            for method_name in method_names:
                if is_method_declaration_line(line, method_name):
                    continue

                direct_call = re.search(rf"(?<![\w.]){re.escape(method_name)}\s*\(", line)
                this_call = re.search(rf"\bthis\.{re.escape(method_name)}\s*\(", line)
                if direct_call or this_call:
                    violations.append(
                        Violation(
                            "NO_INTRA_SERVICE_METHOD_CALL",
                            rel(file_path),
                            idx,
                            "Application/Domain Service must not call another method in the same class",
                        )
                    )
                    break


def check_facade_dependencies(violations: list[Violation]) -> None:
    facades = sorted((MAIN_JAVA / "application").rglob("*Facade.java"))
    for file_path in facades:
        for idx, line in enumerate(read_lines(file_path), start=1):
            if re.search(r"import\s+com\.loopers\.domain\..*(Repository|Service);", line):
                violations.append(
                    Violation(
                        "FACADE_DOMAIN_DEP",
                        rel(file_path),
                        idx,
                        "Facade must depend on Application Service only",
                    )
                )


def own_application_domain(file_path: Path) -> str | None:
    parts = file_path.parts
    if "application" not in parts:
        return None
    idx = parts.index("application")
    if idx + 1 >= len(parts):
        return None
    return parts[idx + 1]


def check_application_service_dependencies(violations: list[Violation]) -> None:
    services = sorted((MAIN_JAVA / "application").rglob("*Service.java"))
    for file_path in services:
        own_domain = own_application_domain(file_path)
        if own_domain is None:
            continue
        for idx, line in enumerate(read_lines(file_path), start=1):
            m_domain = re.search(
                r"import\s+com\.loopers\.domain\.([a-z0-9_]+)\..*(Repository|Service);", line
            )
            if m_domain and m_domain.group(1) != own_domain:
                violations.append(
                    Violation(
                        "APP_SERVICE_CROSS_DOMAIN_DEP",
                        rel(file_path),
                        idx,
                        (
                            "Application Service must depend on own-domain Repository/Domain Service only "
                            f"(own={own_domain}, imported={m_domain.group(1)})"
                        ),
                    )
                )

            m_app = re.search(
                r"import\s+com\.loopers\.application\.([a-z0-9_]+)\..*Service;", line
            )
            if m_app and m_app.group(1) != own_domain:
                violations.append(
                    Violation(
                        "APP_SERVICE_DIRECT_APP_CALL",
                        rel(file_path),
                        idx,
                        (
                            "Application Service direct dependency on other Application Service is forbidden "
                            f"(own={own_domain}, imported={m_app.group(1)})"
                        ),
                    )
                )


def check_transactional_scope(violations: list[Violation]) -> None:
    for file_path in java_files(MAIN_JAVA):
        allowed = "/application/" in file_path.as_posix() and file_path.name.endswith("Service.java")
        for idx, line in enumerate(read_lines(file_path), start=1):
            if "@Transactional" in line and not allowed:
                violations.append(
                    Violation(
                        "TX_SCOPE",
                        rel(file_path),
                        idx,
                        "@Transactional is allowed only in Application Service",
                    )
                )


def check_non_admin_deleted_visibility(violations: list[Violation]) -> None:
    controllers = sorted((MAIN_JAVA / "interfaces/api").rglob("*Controller.java"))
    include_deleted_pattern = re.compile(r"IncludingDeleted|getIncludingDeleted|listIncludingDeleted")
    for file_path in controllers:
        if "/interfaces/api/admin/" in file_path.as_posix():
            continue
        for idx, line in enumerate(read_lines(file_path), start=1):
            if include_deleted_pattern.search(line):
                violations.append(
                    Violation(
                        "NON_ADMIN_INCLUDE_DELETED",
                        rel(file_path),
                        idx,
                        "Non-admin query path must not include deleted entities",
                    )
                )


def check_misc_coding_rules(violations: list[Violation]) -> None:
    all_java = java_files(MAIN_JAVA)
    add_simple_pattern_violations(
        violations,
        all_java,
        "NO_SYSTEM_OUT",
        re.compile(r"System\.out\.println\("),
        "System.out.println is forbidden",
    )
    add_simple_pattern_violations(
        violations,
        all_java,
        "NO_SUPPRESS_WARNINGS",
        re.compile(r"@SuppressWarnings"),
        "@SuppressWarnings overuse is forbidden",
    )

    case_pattern = re.compile(r"\.to(?:Lower|Upper)Case\(([^)]*)\)")
    for file_path in all_java:
        for idx, line in enumerate(read_lines(file_path), start=1):
            for match in case_pattern.finditer(line):
                arg = match.group(1).strip()
                if "Locale.ROOT" not in arg:
                    violations.append(
                        Violation(
                            "LOCALE_ROOT_REQUIRED",
                            rel(file_path),
                            idx,
                            "Case conversion must use Locale.ROOT",
                        )
                    )


def check_test_rules(violations: list[Violation]) -> None:
    if not TEST_ROOT.exists():
        return
    java_tests = sorted(TEST_ROOT.rglob("*.java"))
    add_simple_pattern_violations(
        violations,
        java_tests,
        "NO_H2_IN_TEST",
        re.compile(r"\bH2\b|jdbc:h2", re.IGNORECASE),
        "H2 in-memory DB is forbidden in integration tests",
    )


def check_parameter_count_rule(violations: list[Violation]) -> None:
    method_decl = re.compile(
        r"^\s*(public|protected)\s+(?:static\s+)?[\w<>,\[\]?\s]+\s+(\w+)\s*\(([^)]*)\)\s*\{"
    )

    for file_path in java_files(MAIN_JAVA):
        rel_path = rel(file_path)
        if "/domain/" in rel_path.as_posix():
            continue

        class_name = file_path.stem
        for idx, line in enumerate(read_lines(file_path), start=1):
            stripped = line.strip()
            if " class " in stripped or " interface " in stripped or " record " in stripped:
                continue

            m = method_decl.match(line)
            if not m:
                continue

            method_name = m.group(2)
            params = m.group(3).strip()
            if method_name == class_name:
                continue

            param_count = 0 if not params else params.count(",") + 1
            if param_count < 3:
                continue

            if (str(rel_path), method_name) in PARAMETER_COUNT_WHITELIST:
                continue

            violations.append(
                Violation(
                    "MAX_PARAMS_NON_DOMAIN",
                    rel_path,
                    idx,
                    "Non-domain method with 3+ parameters must use DTO/query object",
                )
            )


def print_report(violations: list[Violation]) -> None:
    if not violations:
        print("No rule violations found.")
        return

    grouped: dict[str, list[Violation]] = {}
    for v in violations:
        grouped.setdefault(v.rule_id, []).append(v)

    print(f"Found {len(violations)} rule violation(s).")
    for rule_id in sorted(grouped):
        print(f"\n[{rule_id}] {len(grouped[rule_id])}")
        for v in sorted(grouped[rule_id], key=lambda x: (str(x.path), x.line)):
            print(f"- {v.path}:{v.line} :: {v.message}")


def main() -> int:
    violations: list[Violation] = []
    check_controller_orchestration(violations)
    check_private_methods_in_services(violations)
    check_intra_service_method_calls(violations)
    check_facade_dependencies(violations)
    check_application_service_dependencies(violations)
    check_transactional_scope(violations)
    check_non_admin_deleted_visibility(violations)
    check_misc_coding_rules(violations)
    check_test_rules(violations)
    check_parameter_count_rule(violations)
    print_report(violations)
    return 1 if violations else 0


if __name__ == "__main__":
    sys.exit(main())

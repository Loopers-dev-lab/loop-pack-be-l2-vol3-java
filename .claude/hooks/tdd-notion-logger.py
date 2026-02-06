#!/usr/bin/env python3
"""
TDD Notion Logger Hook for Claude Code.

PostToolUse hook that logs TDD Red-Green-Refactor cycles to a Notion page
with AI reasoning extracted from the conversation transcript.
"""

import json
import os
import re
import sys
import urllib.request
import urllib.error
from datetime import datetime, timezone, timedelta

NOTION_PAGE_ID = "2fc2e1bd53b2809cbd5ed9009dc775bd"
NOTION_API_VERSION = "2022-06-28"
KST = timezone(timedelta(hours=9))

TEST_FILE_PATTERN = re.compile(r".*Test\.java$")
JAVA_FILE_PATTERN = re.compile(r".*\.java$")


def main():
    hook_input = json.loads(sys.stdin.read())

    tool_name = hook_input.get("tool_name", "")
    if tool_name != "Bash":
        return

    command = hook_input.get("tool_input", {}).get("command", "")
    if not re.search(r"gradlew.*test", command):
        return

    tool_response = hook_input.get("tool_response", {})
    stdout = extract_stdout(tool_response)

    if "BUILD SUCCESSFUL" not in stdout:
        return

    notion_api_key = os.environ.get("NOTION_API_KEY")
    if not notion_api_key:
        sys.stderr.write("NOTION_API_KEY environment variable not set\n")
        return

    transcript_path = hook_input.get("transcript_path", "")
    if not transcript_path or not os.path.exists(transcript_path):
        sys.stderr.write(f"Transcript not found: {transcript_path}\n")
        return

    phases = parse_tdd_phases(transcript_path)

    test_class = extract_test_class(command)
    test_methods = extract_test_methods(stdout)
    timestamp = datetime.now(KST).strftime("%Y-%m-%d %H:%M")

    blocks = build_notion_blocks(test_class, timestamp, phases, test_methods)
    append_blocks_to_notion(notion_api_key, blocks)


# ---------------------------------------------------------------------------
# Stdout / metadata extraction (unchanged)
# ---------------------------------------------------------------------------

def extract_stdout(tool_response):
    """Extract stdout text from tool_response, handling various formats."""
    if isinstance(tool_response, str):
        return tool_response
    if isinstance(tool_response, dict):
        if "stdout" in tool_response:
            return tool_response["stdout"]
        if "content" in tool_response:
            return str(tool_response["content"])
    if isinstance(tool_response, list):
        parts = []
        for item in tool_response:
            if isinstance(item, dict) and item.get("type") == "text":
                parts.append(item.get("text", ""))
        return "\n".join(parts)
    return str(tool_response)


def extract_test_class(command):
    """Extract test class name from gradlew test command."""
    match = re.search(r'--tests\s+"?\*?([A-Za-z0-9_.]+)"?', command)
    if match:
        name = match.group(1)
        return name.rsplit(".", 1)[-1] if "." in name else name
    return "UnknownTest"


def extract_test_methods(stdout):
    """Extract executed test method names from test output."""
    methods = []
    for line in stdout.split("\n"):
        match = re.search(r">\s+(\w+)\(\)\s+PASSED", line)
        if match:
            methods.append(match.group(1))
    return methods


# ---------------------------------------------------------------------------
# Transcript parsing — TDD phase extraction with AI reasoning
# ---------------------------------------------------------------------------

def read_recent_entries(transcript_path, max_entries=500):
    """Read the most recent entries from a JSONL transcript file."""
    entries = []
    try:
        with open(transcript_path, "r", encoding="utf-8") as f:
            for line in f:
                line = line.strip()
                if not line:
                    continue
                try:
                    entries.append(json.loads(line))
                except json.JSONDecodeError:
                    continue
    except (OSError, IOError):
        return []
    return entries[-max_entries:]


def find_tdd_cycle_entries(entries):
    """Return entries between the last two gradlew test Bash commands.

    This scopes the parsing to only the current TDD cycle.
    """
    test_run_indices = []
    for i, entry in enumerate(entries):
        if entry.get("type") != "assistant":
            continue
        for content in entry.get("message", {}).get("content", []):
            if (content.get("type") == "tool_use"
                    and content.get("name") == "Bash"
                    and re.search(r"gradlew.*test",
                                  content.get("input", {}).get("command", ""))):
                test_run_indices.append(i)
                break

    if not test_run_indices:
        return entries

    if len(test_run_indices) < 2:
        return entries[: test_run_indices[-1]]

    start = test_run_indices[-2] + 1
    end = test_run_indices[-1]
    return entries[start:end]


def extract_reasoning_from_entry(entry):
    """Extract visible reasoning text from an assistant message.

    Prefers ``text`` blocks (visible to user). Falls back to a truncated
    ``thinking`` block summary when no text is available.
    """
    content_list = entry.get("message", {}).get("content", [])
    if not isinstance(content_list, list):
        return ""

    text_parts = []
    thinking_parts = []

    for content in content_list:
        if content.get("type") == "text":
            t = content.get("text", "").strip()
            if t:
                text_parts.append(t)
        elif content.get("type") == "thinking":
            t = content.get("thinking", "").strip()
            if t:
                thinking_parts.append(t)

    if text_parts:
        return "\n".join(text_parts)
    if thinking_parts:
        combined = "\n".join(thinking_parts)
        return combined[:800] + ("..." if len(combined) > 800 else "")
    return ""


def extract_test_names(tool_input):
    """Extract test method names from Write/Edit content."""
    names = []
    content = tool_input.get("content", "") or tool_input.get("new_string", "")
    if not content:
        return names

    for match in re.finditer(
        r"(?:@Test|@DisplayName)\s*(?:\(\"([^\"]+)\"\))?\s*\n\s*(?:void\s+(\w+))?",
        content,
    ):
        display_name = match.group(1)
        method_name = match.group(2)
        name = display_name or method_name
        if name and name not in names:
            names.append(name)

    if not names:
        for match in re.finditer(r"void\s+(\w+)\s*\(", content):
            name = match.group(1)
            if name not in names:
                names.append(name)

    return names


def parse_tdd_phases(transcript_path):
    """Parse transcript JSONL into TDD phases with AI reasoning.

    Returns ``{"red": [...], "green": [...], "refactor": [...]}``.
    Each entry: ``{"reasoning": str, "files": [str], "test_names": [str]}``.
    """
    entries = read_recent_entries(transcript_path, max_entries=500)
    cycle_entries = find_tdd_cycle_entries(entries)

    phases = {"red": [], "green": [], "refactor": []}
    green_files_seen = set()

    for entry in cycle_entries:
        if entry.get("type") != "assistant":
            continue

        content_list = entry.get("message", {}).get("content", [])
        if not isinstance(content_list, list):
            continue

        test_files = []
        source_files = []
        test_names = []

        for content in content_list:
            if content.get("type") != "tool_use":
                continue
            if content.get("name") not in ("Write", "Edit"):
                continue

            file_path = content.get("input", {}).get("file_path", "")
            if not file_path:
                continue

            filename = os.path.basename(file_path)

            if TEST_FILE_PATTERN.match(filename):
                if filename not in test_files:
                    test_files.append(filename)
                test_names.extend(
                    n for n in extract_test_names(content.get("input", {}))
                    if n not in test_names
                )
            elif JAVA_FILE_PATTERN.match(filename):
                if filename not in source_files:
                    source_files.append(filename)

        if not test_files and not source_files:
            continue

        reasoning = extract_reasoning_from_entry(entry)

        if test_files:
            phases["red"].append({
                "reasoning": reasoning,
                "files": test_files,
                "test_names": test_names,
            })

        if source_files:
            all_seen = all(f in green_files_seen for f in source_files)
            if green_files_seen and all_seen:
                phases["refactor"].append({
                    "reasoning": reasoning,
                    "files": source_files,
                    "test_names": [],
                })
            else:
                phases["green"].append({
                    "reasoning": reasoning,
                    "files": source_files,
                    "test_names": [],
                })
                green_files_seen.update(source_files)

    return phases


# ---------------------------------------------------------------------------
# Notion block builders
# ---------------------------------------------------------------------------

def truncate_text(text, max_len=1900):
    """Truncate text to fit Notion rich_text limit (2000 chars)."""
    if len(text) <= max_len:
        return text
    return text[:max_len] + "..."


def make_heading2(text):
    return {
        "object": "block",
        "type": "heading_2",
        "heading_2": {
            "rich_text": [{"type": "text", "text": {"content": truncate_text(text)}}]
        },
    }


def make_paragraph(text, bold_prefix=None):
    rich_text = []
    if bold_prefix:
        rich_text.append({
            "type": "text",
            "text": {"content": bold_prefix},
            "annotations": {"bold": True},
        })

    parts = re.split(r"(`[^`]+`)", text)
    for part in parts:
        if part.startswith("`") and part.endswith("`"):
            rich_text.append({
                "type": "text",
                "text": {"content": part[1:-1]},
                "annotations": {"code": True},
            })
        elif part:
            rich_text.append({
                "type": "text",
                "text": {"content": truncate_text(part)},
            })

    return {
        "object": "block",
        "type": "paragraph",
        "paragraph": {"rich_text": rich_text},
    }


def make_bulleted_list(text):
    return {
        "object": "block",
        "type": "bulleted_list_item",
        "bulleted_list_item": {
            "rich_text": [{"type": "text", "text": {"content": text}}]
        },
    }


def make_toggle(title, children_blocks, color="default"):
    """Create a Notion toggle block with nested children."""
    return {
        "object": "block",
        "type": "toggle",
        "toggle": {
            "rich_text": [{"type": "text", "text": {"content": title}}],
            "color": color,
            "children": children_blocks,
        },
    }


def make_divider():
    return {"object": "block", "type": "divider", "divider": {}}


# ---------------------------------------------------------------------------
# Notion block assembly
# ---------------------------------------------------------------------------

def build_notion_blocks(test_class, timestamp, phases, test_methods):
    """Build Notion API block children payload with AI reasoning per phase."""
    blocks = []

    blocks.append(make_heading2(f"{test_class} ({timestamp})"))

    # --- Red phase ---
    red_summary = _format_red_summary(phases["red"], test_methods)
    blocks.append(make_paragraph(red_summary, bold_prefix="Red: "))
    for entry in phases["red"]:
        if entry["reasoning"]:
            blocks.append(make_toggle(
                "AI Reasoning",
                [make_paragraph(truncate_text(entry["reasoning"]))],
                color="red_background",
            ))
        for f in entry["files"]:
            blocks.append(make_bulleted_list(f"파일: {f}"))

    # --- Green phase ---
    green_summary = "테스트 통과를 위한 구현" if phases["green"] else "테스트 통과 확인"
    blocks.append(make_paragraph(green_summary, bold_prefix="Green: "))
    for entry in phases["green"]:
        if entry["reasoning"]:
            blocks.append(make_toggle(
                "AI Reasoning",
                [make_paragraph(truncate_text(entry["reasoning"]))],
                color="green_background",
            ))
        for f in entry["files"]:
            blocks.append(make_bulleted_list(f"파일: {f}"))

    # --- Refactor phase ---
    if phases["refactor"]:
        blocks.append(make_paragraph("코드 품질 개선", bold_prefix="Refactor: "))
        for entry in phases["refactor"]:
            if entry["reasoning"]:
                blocks.append(make_toggle(
                    "AI Reasoning",
                    [make_paragraph(truncate_text(entry["reasoning"]))],
                    color="blue_background",
                ))
            for f in entry["files"]:
                blocks.append(make_bulleted_list(f"파일: {f}"))

    # --- Result ---
    blocks.append(make_paragraph("BUILD SUCCESSFUL", bold_prefix="Result: "))
    blocks.append(make_divider())

    # Safety: Notion allows max 100 blocks per request
    return blocks[:100]


def _format_red_summary(red_entries, test_methods):
    """Format the Red phase summary line."""
    all_names = []
    for entry in red_entries:
        all_names.extend(entry["test_names"])

    if all_names:
        names = ", ".join(f"`{n}`" for n in all_names[:5])
        return f"{names} 테스트 작성"
    if test_methods:
        names = ", ".join(f"`{m}`" for m in test_methods[:5])
        return f"{names} 테스트 작성"
    return "테스트 작성"


# ---------------------------------------------------------------------------
# Notion API call (unchanged)
# ---------------------------------------------------------------------------

def append_blocks_to_notion(api_key, blocks):
    """Append blocks to the Notion page using urllib (no external deps)."""
    url = f"https://api.notion.com/v1/blocks/{NOTION_PAGE_ID}/children"
    payload = json.dumps({"children": blocks}).encode("utf-8")

    req = urllib.request.Request(
        url,
        data=payload,
        method="PATCH",
        headers={
            "Authorization": f"Bearer {api_key}",
            "Notion-Version": NOTION_API_VERSION,
            "Content-Type": "application/json",
        },
    )

    try:
        with urllib.request.urlopen(req, timeout=15) as resp:
            if resp.status != 200:
                sys.stderr.write(f"Notion API returned status {resp.status}\n")
    except urllib.error.HTTPError as e:
        body = e.read().decode("utf-8", errors="replace")
        sys.stderr.write(f"Notion API error {e.code}: {body}\n")
    except urllib.error.URLError as e:
        sys.stderr.write(f"Notion API connection error: {e.reason}\n")


if __name__ == "__main__":
    main()

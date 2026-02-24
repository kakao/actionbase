#!/usr/bin/env python3
"""
translation-memory.py — Translation Memory (TM) tool for Actionbase docs.

Usage:
  python translation-memory.py [--lang LANG] {init,update,build,status}

Subcommands:
  init   — Create empty TM files for EN docs that don't have one yet
  update — Sync existing TM files with updated EN source docs
  build  — Build {lang}/*.mdx from en/*.mdx using TM lookup (exact string match)
  status — Show translation status (HIT/MISS counts per document)

The --lang flag (default: ko) determines TM, glossary, and output paths.
"""

from __future__ import annotations

import argparse
import os
import re
import sys
import unicodedata
from dataclasses import dataclass, field
from enum import Enum, auto
from pathlib import Path

import yaml


# ---------------------------------------------------------------------------
# Constants
# ---------------------------------------------------------------------------

OSS_ROOT = Path(__file__).resolve().parent.parent
DOCS_DIR = OSS_ROOT / "website" / "src" / "content" / "docs"

# All known translation target languages (used to exclude from EN doc scan)
KNOWN_LANGS = ("ko", "zh", "ja", "es", "fr", "de")


def _target_docs_dir(lang: str) -> Path:
    return DOCS_DIR / lang


def _tm_dir(lang: str) -> Path:
    return OSS_ROOT / "tm" / lang


def _glossary_path(lang: str) -> Path:
    return OSS_ROOT / "glossary" / f"{lang}.yaml"


# ---------------------------------------------------------------------------
# Segment types
# ---------------------------------------------------------------------------

class SegmentType(Enum):
    FRONTMATTER_TITLE = auto()
    FRONTMATTER_DESCRIPTION = auto()
    HEADING = auto()
    PARAGRAPH = auto()
    LIST_ITEM = auto()
    TABLE_ROW = auto()
    HTML_SUMMARY = auto()
    BLOCKQUOTE = auto()


# ---------------------------------------------------------------------------
# Data classes
# ---------------------------------------------------------------------------

@dataclass
class Segment:
    text: str
    segment_type: SegmentType
    heading_level: int = 0
    heading_id: str = ""


@dataclass
class TMEntry:
    source: str
    target: str
    contributors: list[str] = field(default_factory=list)
    context: str = ""


# ---------------------------------------------------------------------------
# YAML helpers
# ---------------------------------------------------------------------------

def load_yaml_file(path: Path) -> list | dict | None:
    if not path.exists():
        return None
    with open(path, encoding="utf-8") as f:
        return yaml.safe_load(f)


class _NoAliasDumper(yaml.SafeDumper):
    def ignore_aliases(self, data):
        return True


def save_yaml_file(path: Path, data: list | dict) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        yaml.dump(
            data,
            f,
            Dumper=_NoAliasDumper,
            allow_unicode=True,
            default_flow_style=False,
            sort_keys=False,
            width=float("inf"),
        )


def load_glossary(lang: str) -> list[str]:
    """Load glossary preserve list (terms to keep as-is in translation)."""
    data = load_yaml_file(_glossary_path(lang))
    if not data or "preserve" not in data:
        return []
    return data["preserve"]


# ---------------------------------------------------------------------------
# MDX parsing
# ---------------------------------------------------------------------------

_FRONTMATTER_RE = re.compile(r"^---\s*\n(.*?)\n---\s*\n", re.DOTALL)
_HEADING_RE = re.compile(r"^(#{1,6})\s+(.*?)(?:\s+\{#([\w-]+)\})?\s*$")
_TABLE_SEPARATOR_RE = re.compile(r"^\|[\s\-:|]+\|$")
_TABLE_ROW_RE = re.compile(r"^\|(.+)\|$")
_LIST_ITEM_RE = re.compile(r"^(\s*[-*])\s+(.*)")
_CODE_FENCE_RE = re.compile(r"^(`{3,}|~{3,})")
_IMPORT_EXPORT_RE = re.compile(r"^(import|export)\s+")
_HTML_BLOCK_START_RE = re.compile(r"^<(?!Aside|details|summary|/)([\w.-]+)")
_JSX_SELF_CLOSING_RE = re.compile(r"^<[\w.-]+[^>]*/>\s*$")
_SUMMARY_RE = re.compile(r"^<summary>(.*?)</summary>$")
_ASIDE_INLINE_RE = re.compile(r"^<Aside(?:\s[^>]*)?>(.+)</Aside>\s*$")
_DETAILS_RE = re.compile(r"^</?details>\s*$")
_ASIDE_OPEN_RE = re.compile(r"^<Aside(?:\s[^>]*)?>$")
_ASIDE_CLOSE_RE = re.compile(r"^</Aside>\s*$")
_IMG_RE = re.compile(r"^<img\s")
_EMPTY_LINE_RE = re.compile(r"^\s*$")
_ORDERED_LIST_RE = re.compile(r"^(\s*\d+[.)]\s)(.*)")


def parse_frontmatter(content: str) -> tuple[dict | None, str]:
    """Parse YAML frontmatter from MDX content. Returns (metadata, body)."""
    m = _FRONTMATTER_RE.match(content)
    if not m:
        return None, content
    fm_text = m.group(1)
    body = content[m.end():]
    try:
        metadata = yaml.safe_load(fm_text)
    except yaml.YAMLError:
        metadata = None
    return metadata, body



def extract_segments(content: str) -> list[Segment]:
    """Extract translatable segments from MDX body content."""
    segments: list[Segment] = []
    fm, body = parse_frontmatter(content)

    if fm:
        if "title" in fm:
            segments.append(Segment(
                text=str(fm["title"]),
                segment_type=SegmentType.FRONTMATTER_TITLE,
            ))
        if "description" in fm:
            segments.append(Segment(
                text=str(fm["description"]),
                segment_type=SegmentType.FRONTMATTER_DESCRIPTION,
            ))

    lines = body.split("\n")
    i = 0
    in_code_block = False
    code_fence_marker = ""
    in_aside = False
    aside_lines: list[str] = []
    paragraph_lines: list[str] = []

    def flush_paragraph():
        if paragraph_lines:
            text = " ".join(paragraph_lines).strip()
            if text:
                segments.append(Segment(text=text, segment_type=SegmentType.PARAGRAPH))
            paragraph_lines.clear()

    while i < len(lines):
        line = lines[i]

        # --- Code blocks ---
        if in_code_block:
            fence_match = _CODE_FENCE_RE.match(line)
            if fence_match and line.startswith(code_fence_marker):
                in_code_block = False
            i += 1
            continue

        fence_match = _CODE_FENCE_RE.match(line)
        if fence_match:
            flush_paragraph()
            in_code_block = True
            code_fence_marker = fence_match.group(1)
            i += 1
            continue

        # --- Multi-line Aside ---
        if in_aside:
            if _ASIDE_CLOSE_RE.match(line.strip()):
                text = " ".join(aside_lines).strip()
                if text:
                    segments.append(Segment(text=text, segment_type=SegmentType.PARAGRAPH))
                aside_lines.clear()
                in_aside = False
                i += 1
                continue
            stripped = line.strip()
            if stripped:
                aside_lines.append(stripped)
            i += 1
            continue

        stripped = line.strip()

        # --- Empty line ---
        if _EMPTY_LINE_RE.match(line):
            flush_paragraph()
            i += 1
            continue

        # --- Import/export ---
        if _IMPORT_EXPORT_RE.match(stripped):
            flush_paragraph()
            i += 1
            continue

        # --- Image tags ---
        if _IMG_RE.match(stripped):
            flush_paragraph()
            i += 1
            continue

        # --- JSX self-closing ---
        if _JSX_SELF_CLOSING_RE.match(stripped):
            flush_paragraph()
            i += 1
            continue

        # --- details open/close ---
        if _DETAILS_RE.match(stripped):
            flush_paragraph()
            i += 1
            continue

        # --- Aside inline ---
        aside_inline_m = _ASIDE_INLINE_RE.match(stripped)
        if aside_inline_m:
            flush_paragraph()
            segments.append(Segment(
                text=aside_inline_m.group(1).strip(),
                segment_type=SegmentType.PARAGRAPH,
            ))
            i += 1
            continue

        # --- Aside open (multi-line) ---
        if _ASIDE_OPEN_RE.match(stripped):
            flush_paragraph()
            in_aside = True
            aside_lines.clear()
            i += 1
            continue

        # --- Summary tag ---
        summary_m = _SUMMARY_RE.match(stripped)
        if summary_m:
            flush_paragraph()
            segments.append(Segment(
                text=summary_m.group(1).strip(),
                segment_type=SegmentType.HTML_SUMMARY,
            ))
            i += 1
            continue

        # --- HTML/JSX block elements (div, etc.) ---
        if _HTML_BLOCK_START_RE.match(stripped) and not stripped.endswith("/>"):
            flush_paragraph()
            i += 1
            continue

        # closing HTML tags
        if stripped.startswith("</") and stripped.endswith(">"):
            flush_paragraph()
            i += 1
            continue

        # --- Headings ---
        heading_m = _HEADING_RE.match(stripped)
        if heading_m:
            flush_paragraph()
            level = len(heading_m.group(1))
            text = heading_m.group(2).strip()
            explicit_id = heading_m.group(3) or ""
            segments.append(Segment(
                text=text,
                segment_type=SegmentType.HEADING,
                heading_level=level,
                heading_id=explicit_id,
            ))
            i += 1
            continue

        # --- Table rows ---
        if _TABLE_SEPARATOR_RE.match(stripped):
            flush_paragraph()
            i += 1
            continue

        table_m = _TABLE_ROW_RE.match(stripped)
        if table_m:
            flush_paragraph()
            cells = table_m.group(1).split("|")
            cell_texts = [c.strip() for c in cells if c.strip()]
            if cell_texts:
                segments.append(Segment(
                    text=" | ".join(cell_texts),
                    segment_type=SegmentType.TABLE_ROW,
                ))
            i += 1
            continue

        # --- List items (with continuation lines) ---
        list_m = _LIST_ITEM_RE.match(stripped) or _ORDERED_LIST_RE.match(stripped)
        if list_m:
            flush_paragraph()
            item_text = list_m.group(2).strip()
            i += 1
            while i < len(lines):
                next_raw = lines[i]
                if not next_raw or not next_raw[0:1].isspace():
                    break
                next_stripped = next_raw.strip()
                if not next_stripped:
                    break
                if _LIST_ITEM_RE.match(next_stripped) or _ORDERED_LIST_RE.match(next_stripped):
                    break
                item_text += " " + next_stripped
                i += 1
            segments.append(Segment(
                text=item_text,
                segment_type=SegmentType.LIST_ITEM,
            ))
            continue

        # --- Blockquotes ---
        if stripped.startswith(">"):
            flush_paragraph()
            bq_text = stripped[1:].strip()
            if bq_text:
                segments.append(Segment(text=bq_text, segment_type=SegmentType.BLOCKQUOTE))
            i += 1
            continue

        # --- Regular paragraph lines ---
        paragraph_lines.append(stripped)
        i += 1

    flush_paragraph()

    return segments


# ---------------------------------------------------------------------------
# TM operations
# ---------------------------------------------------------------------------

def _context_label(seg: Segment) -> str:
    if seg.segment_type == SegmentType.FRONTMATTER_TITLE:
        return "frontmatter:title"
    if seg.segment_type == SegmentType.FRONTMATTER_DESCRIPTION:
        return "frontmatter:description"
    if seg.segment_type == SegmentType.HEADING:
        return "heading"
    if seg.segment_type == SegmentType.TABLE_ROW:
        return "table"
    if seg.segment_type == SegmentType.LIST_ITEM:
        return "list_item"
    if seg.segment_type == SegmentType.HTML_SUMMARY:
        return "summary"
    if seg.segment_type == SegmentType.BLOCKQUOTE:
        return "blockquote"
    return "paragraph"


def _tm_path_for_doc(doc_rel: str, lang: str) -> Path:
    """Compute TM YAML path for a given doc-relative path."""
    stem = doc_rel
    if stem.endswith(".mdx"):
        stem = stem[:-4]
    elif stem.endswith(".md"):
        stem = stem[:-3]
    return _tm_dir(lang) / f"{stem}.yaml"


def load_tm(doc_rel: str, lang: str) -> tuple[dict[str, TMEntry], list[str]]:
    """Load TM entries and document-level contributors for a doc.

    Returns (entries_by_source, contributors).
    """
    tm_path = _tm_path_for_doc(doc_rel, lang)
    data = load_yaml_file(tm_path)
    if not data:
        return {}, []

    # support both new format (meta + entries) and legacy flat list
    if isinstance(data, dict):
        entries = data.get("entries", [])
        contributors = data.get("meta", {}).get("contributors", [])
    else:
        entries = data
        contributors = []

    result: dict[str, TMEntry] = {}
    for entry in entries:
        result[entry["source"]] = TMEntry(
            source=entry["source"],
            target=entry.get("target", ""),
            contributors=contributors,
            context=entry.get("context", ""),
        )
    return result, contributors


# ---------------------------------------------------------------------------
# Build helpers
# ---------------------------------------------------------------------------


def _display_width(text: str) -> int:
    """Compute display width accounting for East Asian wide characters."""
    width = 0
    for ch in text:
        eaw = unicodedata.east_asian_width(ch)
        width += 2 if eaw in ("W", "F") else 1
    return width


def _pad_cell(text: str, target_width: int) -> str:
    """Pad text with spaces to reach target display width."""
    pad = target_width - _display_width(text)
    return text + " " * pad if pad > 0 else text


def _emit_table_block(
    table_lines: list[str],
    tm: dict[str, TMEntry],
) -> list[str]:
    """Translate a table block and re-emit with aligned column widths."""
    sep_indices: list[int] = []
    translated_rows: list[tuple[int, list[str]]] = []

    for idx, raw_line in enumerate(table_lines):
        stripped = raw_line.strip()
        if _TABLE_SEPARATOR_RE.match(stripped):
            sep_indices.append(idx)
            continue
        row_m = _TABLE_ROW_RE.match(stripped)
        if row_m:
            cells = row_m.group(1).split("|")
            cell_texts = [c.strip() for c in cells if c.strip()]
            key = " | ".join(cell_texts)
            if key in tm:
                translated_rows.append((idx, tm[key].target.split(" | ")))
            else:
                translated_rows.append((idx, cell_texts))

    if not translated_rows:
        return table_lines

    num_cols = max(len(cells) for _, cells in translated_rows)
    col_widths = [3] * num_cols
    for _, cells in translated_rows:
        for j, cell in enumerate(cells):
            if j < num_cols:
                col_widths[j] = max(col_widths[j], _display_width(cell))

    result: list[str] = []
    for idx, raw_line in enumerate(table_lines):
        if idx in sep_indices:
            sep = "| " + " | ".join("-" * w for w in col_widths) + " |"
            result.append(sep)
        else:
            row_data = next((r for r in translated_rows if r[0] == idx), None)
            if row_data:
                _, cells = row_data
                padded = [_pad_cell(c, col_widths[j]) if j < len(col_widths) else c
                          for j, c in enumerate(cells)]
                result.append("| " + " | ".join(padded) + " |")
            else:
                result.append(raw_line)
    return result



def _add_heading_anchor(translated_heading: str, explicit_id: str) -> str:
    """Preserve explicit {#anchor} from the English source."""
    if not explicit_id:
        return translated_heading
    return f"{translated_heading} {{#{explicit_id}}}"


def _update_links_for_lang(text: str, lang: str) -> str:
    """Update internal links: /foo/ -> /{lang}/foo/, exclude external URLs."""

    def _replace_link(m: re.Match) -> str:
        prefix = m.group(1)
        path = m.group(2)
        suffix = m.group(3)
        # skip external, anchor-only, or already lang-prefixed paths
        if path.startswith("http") or path.startswith("#") or path.startswith(f"/{lang}/"):
            return m.group(0)
        # skip /images/ paths
        if path.startswith("/images/"):
            return m.group(0)
        lang_path = f"/{lang}{path}"
        return f"{prefix}{lang_path}{suffix}"

    return re.sub(r"(\[.*?\]\()(/[^)]*?)(\))", _replace_link, text)


def build_translated_doc(
    en_content: str,
    tm: dict[str, TMEntry],
    glossary_preserve: list[str],
    lang: str,
    contributors: list[str] | None = None,
) -> str:
    """Build translated MDX from English source using TM lookup."""
    fm, body = parse_frontmatter(en_content)
    output_parts: list[str] = []

    # --- Frontmatter ---
    if fm:
        translated_fm = dict(fm)
        title_key = str(fm.get("title", ""))
        if title_key in tm:
            translated_fm["title"] = tm[title_key].target
        desc_key = str(fm.get("description", ""))
        if desc_key in tm:
            translated_fm["description"] = tm[desc_key].target
        # Preserve translated-by-{contributor} frontmatter flags.
        # kanana-2 is the LLM translator used for ko docs; this flag lets
        # downstream tooling know the file was machine-translated.
        if contributors and "kanana-2" in contributors:
            translated_fm["translated-by-kanana-2"] = True
        output_parts.append("---")
        fm_text = yaml.dump(
            translated_fm,
            allow_unicode=True,
            default_flow_style=False,
            sort_keys=False,
        ).rstrip()
        output_parts.append(fm_text)
        output_parts.append("---")
        output_parts.append("")

    # --- Body ---
    lines = body.split("\n")
    i = 0
    in_code_block = False
    code_fence_marker = ""
    in_aside = False
    aside_lines_en: list[str] = []
    paragraph_lines: list[str] = []

    def flush_paragraph():
        if paragraph_lines:
            text = " ".join(paragraph_lines).strip()
            if text and text in tm:
                translated = _update_links_for_lang(tm[text].target, lang)
            elif text:
                translated = _update_links_for_lang(text, lang)
            else:
                paragraph_lines.clear()
                return
            output_parts.append(translated)
            paragraph_lines.clear()

    while i < len(lines):
        line = lines[i]

        # --- Code blocks (pass through) ---
        if in_code_block:
            output_parts.append(line)
            fence_match = _CODE_FENCE_RE.match(line)
            if fence_match and line.startswith(code_fence_marker):
                in_code_block = False
            i += 1
            continue

        fence_match = _CODE_FENCE_RE.match(line)
        if fence_match:
            flush_paragraph()
            in_code_block = True
            code_fence_marker = fence_match.group(1)
            output_parts.append(line)
            i += 1
            continue

        stripped = line.strip()

        # --- Multi-line Aside ---
        if in_aside:
            if _ASIDE_CLOSE_RE.match(stripped):
                text = " ".join(aside_lines_en).strip()
                if text and text in tm:
                    translated = _update_links_for_lang(tm[text].target, lang)
                    output_parts.append(f"  {translated}")
                elif text:
                    output_parts.append(f"  {_update_links_for_lang(text, lang)}")
                aside_lines_en.clear()
                in_aside = False
                output_parts.append(line)
                i += 1
                continue
            s = stripped
            if s:
                aside_lines_en.append(s)
            i += 1
            continue

        # --- Empty line ---
        if _EMPTY_LINE_RE.match(line):
            flush_paragraph()
            output_parts.append("")
            i += 1
            continue

        # --- Import/export (pass through) ---
        if _IMPORT_EXPORT_RE.match(stripped):
            flush_paragraph()
            output_parts.append(line)
            i += 1
            continue

        # --- Image (pass through) ---
        if _IMG_RE.match(stripped):
            flush_paragraph()
            output_parts.append(line)
            i += 1
            continue

        # --- JSX self-closing (pass through) ---
        if _JSX_SELF_CLOSING_RE.match(stripped):
            flush_paragraph()
            output_parts.append(line)
            i += 1
            continue

        # --- details (pass through) ---
        if _DETAILS_RE.match(stripped):
            flush_paragraph()
            output_parts.append(line)
            i += 1
            continue

        # --- Aside inline ---
        aside_inline_m = _ASIDE_INLINE_RE.match(stripped)
        if aside_inline_m:
            flush_paragraph()
            inner_text = aside_inline_m.group(1).strip()
            if inner_text in tm:
                translated = _update_links_for_lang(tm[inner_text].target, lang)
                # reconstruct the Aside tag
                tag_end = stripped.index(">") + 1
                tag_open = stripped[:tag_end]
                output_parts.append(f"{tag_open}{translated}</Aside>")
            else:
                output_parts.append(line)
            i += 1
            continue

        # --- Aside open (multi-line) ---
        if _ASIDE_OPEN_RE.match(stripped):
            flush_paragraph()
            in_aside = True
            aside_lines_en.clear()
            output_parts.append(line)
            i += 1
            continue

        # --- Summary ---
        summary_m = _SUMMARY_RE.match(stripped)
        if summary_m:
            flush_paragraph()
            inner = summary_m.group(1).strip()
            if inner in tm:
                output_parts.append(f"<summary>{tm[inner].target}</summary>")
            else:
                output_parts.append(line)
            i += 1
            continue

        # --- HTML block elements (pass through) ---
        if _HTML_BLOCK_START_RE.match(stripped) and not stripped.endswith("/>"):
            flush_paragraph()
            output_parts.append(line)
            i += 1
            continue

        if stripped.startswith("</") and stripped.endswith(">"):
            flush_paragraph()
            output_parts.append(line)
            i += 1
            continue

        # --- Headings ---
        heading_m = _HEADING_RE.match(stripped)
        if heading_m:
            flush_paragraph()
            level = len(heading_m.group(1))
            text = heading_m.group(2).strip()
            explicit_id = heading_m.group(3) or ""
            hashes = "#" * level
            if text in tm:
                translated = tm[text].target
                translated = _add_heading_anchor(translated, explicit_id)
                output_parts.append(f"{hashes} {translated}")
            else:
                output_parts.append(line)
            i += 1
            continue

        # --- Table block (collect consecutive table lines) ---
        if _TABLE_SEPARATOR_RE.match(stripped) or _TABLE_ROW_RE.match(stripped):
            flush_paragraph()
            table_lines: list[str] = []
            while i < len(lines):
                s = lines[i].strip()
                if _TABLE_SEPARATOR_RE.match(s) or _TABLE_ROW_RE.match(s):
                    table_lines.append(lines[i])
                    i += 1
                else:
                    break
            for tl in _emit_table_block(table_lines, tm):
                output_parts.append(tl)
            continue

        # --- List items (with continuation lines) ---
        list_m = _LIST_ITEM_RE.match(stripped)
        ord_m = _ORDERED_LIST_RE.match(stripped) if not list_m else None
        if list_m or ord_m:
            flush_paragraph()
            m = list_m or ord_m
            prefix = m.group(1)
            text = m.group(2).strip()
            prefix = prefix.rstrip() + " "
            i += 1
            while i < len(lines):
                next_raw = lines[i]
                if not next_raw or not next_raw[0:1].isspace():
                    break
                next_stripped = next_raw.strip()
                if not next_stripped:
                    break
                if _LIST_ITEM_RE.match(next_stripped) or _ORDERED_LIST_RE.match(next_stripped):
                    break
                text += " " + next_stripped
                i += 1
            if text in tm:
                translated = _update_links_for_lang(tm[text].target, lang)
            else:
                translated = _update_links_for_lang(text, lang)
            output_parts.append(f"{prefix}{translated}")
            continue

        # --- Blockquotes ---
        if stripped.startswith(">"):
            flush_paragraph()
            bq_text = stripped[1:].strip()
            if bq_text and bq_text in tm:
                translated = _update_links_for_lang(tm[bq_text].target, lang)
                output_parts.append(f"> {translated}")
            else:
                output_parts.append(line)
            i += 1
            continue

        # --- Regular text ---
        paragraph_lines.append(stripped)
        i += 1

    flush_paragraph()

    result = "\n".join(output_parts)
    # ensure file ends with newline
    if not result.endswith("\n"):
        result += "\n"
    return result


# ---------------------------------------------------------------------------
# Subcommand: init
# ---------------------------------------------------------------------------

def cmd_init(args: argparse.Namespace) -> int:
    """Create empty TM files for EN docs that don't have one yet."""
    lang = args.lang
    en_docs = _find_en_docs()
    created = 0
    skipped = 0

    for doc_rel in sorted(en_docs):
        tm_path = _tm_path_for_doc(doc_rel, lang)
        if tm_path.exists():
            skipped += 1
            continue

        en_path = DOCS_DIR / doc_rel
        en_content = en_path.read_text(encoding="utf-8")
        segments = extract_segments(en_content)

        entries: list[dict] = []
        seen: set[str] = set()
        for seg in segments:
            if seg.text in seen:
                continue
            seen.add(seg.text)
            entries.append({
                "source": seg.text,
                "target": "",
                "context": _context_label(seg),
            })

        save_yaml_file(tm_path, {"meta": {"contributors": []}, "entries": entries})
        created += 1
        print(f"  init: {doc_rel} ({len(entries)} segments)")

    print(f"\nInitialized {created} TM files, skipped {skipped} (already exist).")
    return 0


# ---------------------------------------------------------------------------
# Subcommand: update
# ---------------------------------------------------------------------------

def cmd_update(args: argparse.Namespace) -> int:
    """Sync existing TM files with updated EN source docs.

    For each EN doc that already has a TM file:
    - Add new segments (empty target) that appeared in the EN source.
    - Remove stale entries whose source text no longer exists in EN.
    - Preserve all existing translations intact.
    """
    lang = args.lang
    en_docs = _find_en_docs()
    updated = 0
    skipped = 0

    for doc_rel in sorted(en_docs):
        tm_path = _tm_path_for_doc(doc_rel, lang)
        if not tm_path.exists():
            skipped += 1
            continue

        en_path = DOCS_DIR / doc_rel
        en_content = en_path.read_text(encoding="utf-8")
        segments = extract_segments(en_content)

        # current EN source texts (deduplicated, preserving order)
        en_sources: list[str] = []
        en_seen: set[str] = set()
        for seg in segments:
            if seg.text not in en_seen:
                en_seen.add(seg.text)
                en_sources.append(seg.text)

        # build context lookup from current segments
        context_by_source: dict[str, str] = {}
        for seg in segments:
            if seg.text not in context_by_source:
                context_by_source[seg.text] = _context_label(seg)

        # load existing TM
        data = load_yaml_file(tm_path)
        if not data:
            skipped += 1
            continue

        if isinstance(data, dict):
            old_entries = data.get("entries", [])
            meta = data.get("meta", {"contributors": []})
        else:
            old_entries = data
            meta = {"contributors": []}

        # index existing entries by source text
        old_by_source: dict[str, dict] = {}
        for entry in old_entries:
            old_by_source[entry["source"]] = entry

        # build new entry list in EN segment order
        new_entries: list[dict] = []
        added = 0
        removed = 0
        for src in en_sources:
            if src in old_by_source:
                new_entries.append(old_by_source[src])
            else:
                new_entries.append({
                    "source": src,
                    "target": "",
                    "context": context_by_source.get(src, "paragraph"),
                })
                added += 1

        # count removed (in old but not in current EN)
        removed = sum(1 for src in old_by_source if src not in en_seen)

        if added == 0 and removed == 0:
            skipped += 1
            continue

        save_yaml_file(tm_path, {"meta": meta, "entries": new_entries})
        updated += 1
        print(f"  update: {doc_rel} (+{added} new, -{removed} stale)")

    print(f"\nUpdated {updated} TM files, skipped {skipped} (no TM or no changes).")
    return 0


# ---------------------------------------------------------------------------
# Subcommand: build
# ---------------------------------------------------------------------------

def cmd_build(args: argparse.Namespace) -> int:
    """Build {lang}/*.mdx from en/*.mdx using TM lookup."""
    lang = args.lang
    target_dir = _target_docs_dir(lang)
    glossary_preserve = load_glossary(lang)
    en_docs = _find_en_docs()
    built = 0
    skipped = 0

    for doc_rel in sorted(en_docs):
        en_path = DOCS_DIR / doc_rel
        tm, contributors = load_tm(doc_rel, lang)
        if not tm:
            skipped += 1
            continue

        en_content = en_path.read_text(encoding="utf-8")
        translated = build_translated_doc(en_content, tm, glossary_preserve, lang, contributors)

        target_path = target_dir / doc_rel
        target_path.parent.mkdir(parents=True, exist_ok=True)
        target_path.write_text(translated, encoding="utf-8")
        built += 1
        print(f"  built: {doc_rel}")

    print(f"\nBuilt {built} files, skipped {skipped} (no TM).")
    return 0


# ---------------------------------------------------------------------------
# Subcommand: status
# ---------------------------------------------------------------------------

def cmd_status(args: argparse.Namespace) -> int:
    """Show translation status (HIT/MISS counts per document)."""
    lang = args.lang
    en_docs = _find_en_docs()
    total_hits = 0
    total_misses = 0
    total_segments = 0

    print(f"[{lang}] {'Document':<55} {'Segments':>8} {'HIT':>6} {'MISS':>6} {'Coverage':>9}")
    print("-" * 92)

    for doc_rel in sorted(en_docs):
        en_path = DOCS_DIR / doc_rel
        en_content = en_path.read_text(encoding="utf-8")
        segments = extract_segments(en_content)
        tm, _ = load_tm(doc_rel, lang)

        hits = sum(1 for s in segments if s.text in tm)
        misses = len(segments) - hits
        total = len(segments)
        coverage = f"{hits / total * 100:.0f}%" if total > 0 else "N/A"

        total_hits += hits
        total_misses += misses
        total_segments += total

        if total > 0:
            print(f"  {doc_rel:<53} {total:>8} {hits:>6} {misses:>6} {coverage:>9}")

    print("-" * 92)
    overall = f"{total_hits / total_segments * 100:.0f}%" if total_segments > 0 else "N/A"
    print(f"  {'TOTAL':<53} {total_segments:>8} {total_hits:>6} {total_misses:>6} {overall:>9}")

    return 0


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

def _find_en_docs() -> list[str]:
    """Find all English .mdx/.md docs (relative to DOCS_DIR), excluding translated dirs."""
    docs: list[str] = []
    for root, dirs, files in os.walk(DOCS_DIR):
        dirs[:] = [d for d in dirs if d not in KNOWN_LANGS]
        for f in files:
            if f.endswith((".mdx", ".md")):
                rel = os.path.relpath(os.path.join(root, f), DOCS_DIR)
                docs.append(rel)
    return docs


# ---------------------------------------------------------------------------
# CLI
# ---------------------------------------------------------------------------

def main() -> int:
    parser = argparse.ArgumentParser(
        description="Translation Memory mapping tool for Actionbase docs.",
    )
    parser.add_argument(
        "--lang", default="ko",
        help="Target language code (default: ko). Determines TM, glossary, and output paths.",
    )
    subparsers = parser.add_subparsers(dest="command", required=True)

    subparsers.add_parser("init", help="Create empty TM files for EN docs without one")
    subparsers.add_parser("update", help="Sync existing TM files with updated EN source docs")
    subparsers.add_parser("build", help="Build {lang}/*.mdx from en/*.mdx using TM lookup")
    subparsers.add_parser("status", help="Show translation status (HIT/MISS counts)")

    args = parser.parse_args()

    commands = {
        "init": cmd_init,
        "update": cmd_update,
        "build": cmd_build,
        "status": cmd_status,
    }

    return commands[args.command](args)


if __name__ == "__main__":
    sys.exit(main())

#!/usr/bin/env tsx
/**
 * translation-memory.ts — Translation Memory (TM) tool for Actionbase docs.
 *
 * Usage:
 *   npx tsx translation-memory.ts [--lang LANG] {init,update,build,validate,status}
 *
 * Subcommands:
 *   init     — Create empty TM files for EN docs that don't have one yet
 *   update   — Sync existing TM files with updated EN source docs
 *   build    — Build {lang}/*.mdx from en/*.mdx using TM lookup (exact string match)
 *   validate — Validate TM build without writing files (exit 1 on errors)
 *   status   — Show translation status (HIT/MISS counts per document)
 *
 * The --lang flag (default: ko) determines TM and output paths.
 */

import * as fs from 'node:fs';
import * as path from 'node:path';
import { fileURLToPath } from 'node:url';
import yaml from 'js-yaml';

// ---------------------------------------------------------------------------
// Constants
// ---------------------------------------------------------------------------

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const I18N_DIR = path.resolve(__dirname, '..');
const DOCS_DIR = path.resolve(I18N_DIR, '..', 'src', 'content', 'docs');

// All known translation target languages (used to exclude from EN doc scan)
const KNOWN_LANGS = ['ko', 'zh', 'ja', 'es', 'fr', 'de'];

function targetDocsDir(lang: string): string {
  return path.join(DOCS_DIR, lang);
}

function tmDir(lang: string): string {
  return path.join(I18N_DIR, 'tm', lang);
}

// ---------------------------------------------------------------------------
// Segment types
// ---------------------------------------------------------------------------

enum SegmentType {
  FRONTMATTER_TITLE = 'FRONTMATTER_TITLE',
  FRONTMATTER_DESCRIPTION = 'FRONTMATTER_DESCRIPTION',
  HEADING = 'HEADING',
  PARAGRAPH = 'PARAGRAPH',
  LIST_ITEM = 'LIST_ITEM',
  TABLE_ROW = 'TABLE_ROW',
  HTML_SUMMARY = 'HTML_SUMMARY',
  BLOCKQUOTE = 'BLOCKQUOTE',
}

// ---------------------------------------------------------------------------
// Data types
// ---------------------------------------------------------------------------

interface Segment {
  text: string;
  segmentType: SegmentType;
  headingLevel: number;
  headingId: string;
}

interface TMEntry {
  source: string;
  target: string;
  contributors: string[];
  context: string;
}

// ---------------------------------------------------------------------------
// YAML helpers
// ---------------------------------------------------------------------------

function loadYamlFile(filePath: string): unknown {
  if (!fs.existsSync(filePath)) {
    return null;
  }
  const content = fs.readFileSync(filePath, 'utf-8');
  return yaml.load(content) ?? null;
}

function saveYamlFile(filePath: string, data: Record<string, unknown> | unknown[]): void {
  const dir = path.dirname(filePath);
  fs.mkdirSync(dir, { recursive: true });
  const text = yaml.dump(data, {
    noRefs: true,
    lineWidth: -1,
    sortKeys: false,
    quotingType: '"',
  });
  fs.writeFileSync(filePath, text, 'utf-8');
}

// ---------------------------------------------------------------------------
// MDX parsing
// ---------------------------------------------------------------------------

const FRONTMATTER_RE = /^---\s*\n([\s\S]*?)\n---\s*\n/;
const HEADING_RE = /^(#{1,6})\s+(.*?)(?:\s+\{#([\w-]+)\})?\s*$/;
const TABLE_SEPARATOR_RE = /^\|[\s\-:|]+\|$/;
const TABLE_ROW_RE = /^\|(.+)\|$/;
const LIST_ITEM_RE = /^(\s*[-*])\s+(.*)/;
const CODE_FENCE_RE = /^(`{3,}|~{3,})/;
const IMPORT_EXPORT_RE = /^(import|export)\s+/;
const HTML_BLOCK_START_RE = /^<(?!Aside|details|summary|\/)([\w.-]+)/;
const JSX_SELF_CLOSING_RE = /^<[\w.-]+[^>]*\/>\s*$/;
const SUMMARY_RE = /^<summary>(.*?)<\/summary>$/;
const ASIDE_INLINE_RE = /^<Aside(?:\s[^>]*)?>(.+)<\/Aside>\s*$/;
const DETAILS_RE = /^<\/?details>\s*$/;
const ASIDE_OPEN_RE = /^<Aside(?:\s[^>]*)?>$/;
const ASIDE_CLOSE_RE = /^<\/Aside>\s*$/;
const IMG_RE = /^<img\s/;
const EMPTY_LINE_RE = /^\s*$/;
const ORDERED_LIST_RE = /^(\s*\d+[.)]\s)(.*)/;

function parseFrontmatter(content: string): [Record<string, unknown> | null, string] {
  const m = FRONTMATTER_RE.exec(content);
  if (!m) {
    return [null, content];
  }
  const fmText = m[1];
  const body = content.slice(m[0].length);
  try {
    const metadata = yaml.load(fmText) as Record<string, unknown> | null;
    return [metadata, body];
  } catch {
    return [null, body];
  }
}

function extractSegments(content: string): Segment[] {
  const segments: Segment[] = [];
  const [fm, body] = parseFrontmatter(content);

  if (fm) {
    if ('title' in fm) {
      segments.push({
        text: String(fm.title),
        segmentType: SegmentType.FRONTMATTER_TITLE,
        headingLevel: 0,
        headingId: '',
      });
    }
    if ('description' in fm) {
      segments.push({
        text: String(fm.description),
        segmentType: SegmentType.FRONTMATTER_DESCRIPTION,
        headingLevel: 0,
        headingId: '',
      });
    }
  }

  const lines = body.split('\n');
  let i = 0;
  let inCodeBlock = false;
  let codeFenceMarker = '';
  let inAside = false;
  const asideLines: string[] = [];
  const paragraphLines: string[] = [];

  function flushParagraph(): void {
    if (paragraphLines.length > 0) {
      const text = paragraphLines.join(' ').trim();
      if (text) {
        segments.push({
          text,
          segmentType: SegmentType.PARAGRAPH,
          headingLevel: 0,
          headingId: '',
        });
      }
      paragraphLines.length = 0;
    }
  }

  while (i < lines.length) {
    const line = lines[i];

    // --- Code blocks ---
    if (inCodeBlock) {
      const fenceMatch = CODE_FENCE_RE.exec(line);
      if (fenceMatch && line.startsWith(codeFenceMarker)) {
        inCodeBlock = false;
      }
      i++;
      continue;
    }

    const fenceMatch = CODE_FENCE_RE.exec(line);
    if (fenceMatch) {
      flushParagraph();
      inCodeBlock = true;
      codeFenceMarker = fenceMatch[1];
      i++;
      continue;
    }

    // --- Multi-line Aside ---
    if (inAside) {
      if (ASIDE_CLOSE_RE.test(line.trim())) {
        const text = asideLines.join(' ').trim();
        if (text) {
          segments.push({
            text,
            segmentType: SegmentType.PARAGRAPH,
            headingLevel: 0,
            headingId: '',
          });
        }
        asideLines.length = 0;
        inAside = false;
        i++;
        continue;
      }
      const stripped = line.trim();
      if (stripped) {
        asideLines.push(stripped);
      }
      i++;
      continue;
    }

    const stripped = line.trim();

    // --- Empty line ---
    if (EMPTY_LINE_RE.test(line)) {
      flushParagraph();
      i++;
      continue;
    }

    // --- Import/export ---
    if (IMPORT_EXPORT_RE.test(stripped)) {
      flushParagraph();
      i++;
      continue;
    }

    // --- Image tags ---
    if (IMG_RE.test(stripped)) {
      flushParagraph();
      i++;
      continue;
    }

    // --- JSX self-closing ---
    if (JSX_SELF_CLOSING_RE.test(stripped)) {
      flushParagraph();
      i++;
      continue;
    }

    // --- details open/close ---
    if (DETAILS_RE.test(stripped)) {
      flushParagraph();
      i++;
      continue;
    }

    // --- Aside inline ---
    const asideInlineM = ASIDE_INLINE_RE.exec(stripped);
    if (asideInlineM) {
      flushParagraph();
      segments.push({
        text: asideInlineM[1].trim(),
        segmentType: SegmentType.PARAGRAPH,
        headingLevel: 0,
        headingId: '',
      });
      i++;
      continue;
    }

    // --- Aside open (multi-line) ---
    if (ASIDE_OPEN_RE.test(stripped)) {
      flushParagraph();
      inAside = true;
      asideLines.length = 0;
      i++;
      continue;
    }

    // --- Summary tag ---
    const summaryM = SUMMARY_RE.exec(stripped);
    if (summaryM) {
      flushParagraph();
      segments.push({
        text: summaryM[1].trim(),
        segmentType: SegmentType.HTML_SUMMARY,
        headingLevel: 0,
        headingId: '',
      });
      i++;
      continue;
    }

    // --- HTML/JSX block elements (div, etc.) ---
    if (HTML_BLOCK_START_RE.test(stripped) && !stripped.endsWith('/>')) {
      flushParagraph();
      i++;
      continue;
    }

    // closing HTML tags
    if (stripped.startsWith('</') && stripped.endsWith('>')) {
      flushParagraph();
      i++;
      continue;
    }

    // --- Headings ---
    const headingM = HEADING_RE.exec(stripped);
    if (headingM) {
      flushParagraph();
      const level = headingM[1].length;
      const text = headingM[2].trim();
      const explicitId = headingM[3] || '';
      segments.push({
        text,
        segmentType: SegmentType.HEADING,
        headingLevel: level,
        headingId: explicitId,
      });
      i++;
      continue;
    }

    // --- Table rows ---
    if (TABLE_SEPARATOR_RE.test(stripped)) {
      flushParagraph();
      i++;
      continue;
    }

    const tableM = TABLE_ROW_RE.exec(stripped);
    if (tableM) {
      flushParagraph();
      const cells = tableM[1].split('|');
      const cellTexts = cells.map((c) => c.trim()).filter((c) => c);
      if (cellTexts.length > 0) {
        segments.push({
          text: cellTexts.join(' | '),
          segmentType: SegmentType.TABLE_ROW,
          headingLevel: 0,
          headingId: '',
        });
      }
      i++;
      continue;
    }

    // --- List items (with continuation lines) ---
    const listM = LIST_ITEM_RE.exec(stripped) || ORDERED_LIST_RE.exec(stripped);
    if (listM) {
      flushParagraph();
      let itemText = listM[2].trim();
      i++;
      while (i < lines.length) {
        const nextRaw = lines[i];
        if (!nextRaw || !nextRaw[0]?.match(/\s/)) {
          break;
        }
        const nextStripped = nextRaw.trim();
        if (!nextStripped) {
          break;
        }
        if (LIST_ITEM_RE.test(nextStripped) || ORDERED_LIST_RE.test(nextStripped)) {
          break;
        }
        itemText += ' ' + nextStripped;
        i++;
      }
      segments.push({
        text: itemText,
        segmentType: SegmentType.LIST_ITEM,
        headingLevel: 0,
        headingId: '',
      });
      continue;
    }

    // --- Blockquotes ---
    if (stripped.startsWith('>')) {
      flushParagraph();
      const bqText = stripped.slice(1).trim();
      if (bqText) {
        segments.push({
          text: bqText,
          segmentType: SegmentType.BLOCKQUOTE,
          headingLevel: 0,
          headingId: '',
        });
      }
      i++;
      continue;
    }

    // --- Regular paragraph lines ---
    paragraphLines.push(stripped);
    i++;
  }

  flushParagraph();

  return segments;
}

// ---------------------------------------------------------------------------
// TM operations
// ---------------------------------------------------------------------------

function contextLabel(seg: Segment): string {
  switch (seg.segmentType) {
    case SegmentType.FRONTMATTER_TITLE:
      return 'frontmatter:title';
    case SegmentType.FRONTMATTER_DESCRIPTION:
      return 'frontmatter:description';
    case SegmentType.HEADING:
      return 'heading';
    case SegmentType.TABLE_ROW:
      return 'table';
    case SegmentType.LIST_ITEM:
      return 'list_item';
    case SegmentType.HTML_SUMMARY:
      return 'summary';
    case SegmentType.BLOCKQUOTE:
      return 'blockquote';
    default:
      return 'paragraph';
  }
}

function tmPathForDoc(docRel: string, lang: string): string {
  let stem = docRel;
  if (stem.endsWith('.mdx')) {
    stem = stem.slice(0, -4);
  } else if (stem.endsWith('.md')) {
    stem = stem.slice(0, -3);
  }
  return path.join(tmDir(lang), `${stem}.yaml`);
}

function loadTm(docRel: string, lang: string): [Map<string, TMEntry>, string[]] {
  const tmPath = tmPathForDoc(docRel, lang);
  const data = loadYamlFile(tmPath);
  if (!data) {
    return [new Map(), []];
  }

  let entries: Record<string, unknown>[];
  let contributors: string[];

  // support both new format (meta + entries) and legacy flat list
  if (typeof data === 'object' && !Array.isArray(data)) {
    const d = data as Record<string, unknown>;
    entries = (d.entries as Record<string, unknown>[]) || [];
    const meta = d.meta as Record<string, unknown> | undefined;
    contributors = (meta?.contributors as string[]) || [];
  } else {
    entries = data as Record<string, unknown>[];
    contributors = [];
  }

  const result = new Map<string, TMEntry>();
  for (const entry of entries) {
    const source = entry.source as string;
    result.set(source, {
      source,
      target: (entry.target as string) || '',
      contributors,
      context: (entry.context as string) || '',
    });
  }
  return [result, contributors];
}

// ---------------------------------------------------------------------------
// Build helpers
// ---------------------------------------------------------------------------

/**
 * Compute display width accounting for East Asian wide characters.
 * Uses Unicode code point ranges instead of an external package.
 */
function displayWidth(text: string): number {
  let width = 0;
  for (const ch of text) {
    const cp = ch.codePointAt(0)!;
    if (isEastAsianWide(cp)) {
      width += 2;
    } else {
      width += 1;
    }
  }
  return width;
}

function isEastAsianWide(cp: number): boolean {
  // CJK Unified Ideographs
  if (cp >= 0x4e00 && cp <= 0x9fff) return true;
  // CJK Unified Ideographs Extension A
  if (cp >= 0x3400 && cp <= 0x4dbf) return true;
  // CJK Unified Ideographs Extension B
  if (cp >= 0x20000 && cp <= 0x2a6df) return true;
  // CJK Compatibility Ideographs
  if (cp >= 0xf900 && cp <= 0xfaff) return true;
  // Hangul Syllables
  if (cp >= 0xac00 && cp <= 0xd7af) return true;
  // Hangul Jamo
  if (cp >= 0x1100 && cp <= 0x115f) return true;
  // Hangul Compatibility Jamo
  if (cp >= 0x3130 && cp <= 0x318f) return true;
  // Hangul Jamo Extended-A
  if (cp >= 0xa960 && cp <= 0xa97f) return true;
  // Hangul Jamo Extended-B
  if (cp >= 0xd7b0 && cp <= 0xd7ff) return true;
  // Katakana
  if (cp >= 0x30a0 && cp <= 0x30ff) return true;
  // Hiragana
  if (cp >= 0x3040 && cp <= 0x309f) return true;
  // CJK Symbols and Punctuation
  if (cp >= 0x3000 && cp <= 0x303f) return true;
  // Enclosed CJK Letters and Months
  if (cp >= 0x3200 && cp <= 0x32ff) return true;
  // CJK Compatibility
  if (cp >= 0x3300 && cp <= 0x33ff) return true;
  // Fullwidth Forms
  if (cp >= 0xff01 && cp <= 0xff60) return true;
  if (cp >= 0xffe0 && cp <= 0xffe6) return true;
  // Bopomofo
  if (cp >= 0x3100 && cp <= 0x312f) return true;
  // Bopomofo Extended
  if (cp >= 0x31a0 && cp <= 0x31bf) return true;
  // Kanbun
  if (cp >= 0x3190 && cp <= 0x319f) return true;

  return false;
}

function padCell(text: string, targetWidth: number): string {
  const pad = targetWidth - displayWidth(text);
  return pad > 0 ? text + ' '.repeat(pad) : text;
}

function emitTableBlock(tableLines: string[], tm: Map<string, TMEntry>): string[] {
  const sepIndices: number[] = [];
  const translatedRows: [number, string[]][] = [];

  for (let idx = 0; idx < tableLines.length; idx++) {
    const stripped = tableLines[idx].trim();
    if (TABLE_SEPARATOR_RE.test(stripped)) {
      sepIndices.push(idx);
      continue;
    }
    const rowM = TABLE_ROW_RE.exec(stripped);
    if (rowM) {
      const cells = rowM[1].split('|');
      const cellTexts = cells.map((c) => c.trim()).filter((c) => c);
      const key = cellTexts.join(' | ');
      if (tm.has(key)) {
        translatedRows.push([idx, tm.get(key)!.target.split(' | ')]);
      } else {
        translatedRows.push([idx, cellTexts]);
      }
    }
  }

  if (translatedRows.length === 0) {
    return tableLines;
  }

  const numCols = Math.max(...translatedRows.map(([, cells]) => cells.length));
  const colWidths = new Array(numCols).fill(3);
  for (const [, cells] of translatedRows) {
    for (let j = 0; j < cells.length; j++) {
      if (j < numCols) {
        colWidths[j] = Math.max(colWidths[j], displayWidth(cells[j]));
      }
    }
  }

  const result: string[] = [];
  for (let idx = 0; idx < tableLines.length; idx++) {
    if (sepIndices.includes(idx)) {
      const sep = '| ' + colWidths.map((w: number) => '-'.repeat(w)).join(' | ') + ' |';
      result.push(sep);
    } else {
      const rowData = translatedRows.find((r) => r[0] === idx);
      if (rowData) {
        const [, cells] = rowData;
        const padded = cells.map((c, j) => (j < colWidths.length ? padCell(c, colWidths[j]) : c));
        result.push('| ' + padded.join(' | ') + ' |');
      } else {
        result.push(tableLines[idx]);
      }
    }
  }
  return result;
}

function githubSlug(text: string): string {
  return text
    .trim()
    .toLowerCase()
    .replace(/[^\p{L}\p{M}\p{N}\p{Pc} -]/gu, '')
    .replace(/ /g, '-');
}

function addHeadingAnchor(
  translatedHeading: string,
  explicitId: string,
  sourceText: string
): string {
  const id = explicitId || githubSlug(sourceText);
  return `${translatedHeading} {#${id}}`;
}

function updateLinksForLang(text: string, lang: string): string {
  return text.replace(
    /(\[.*?\]\()([^)]*?)(\))/g,
    (_match, prefix: string, linkPath: string, suffix: string) => {
      // skip external, anchor-only, or already lang-prefixed paths
      if (
        linkPath.startsWith('http') ||
        linkPath.startsWith('#') ||
        linkPath.startsWith(`/${lang}/`)
      ) {
        return `${prefix}${linkPath}${suffix}`;
      }
      // skip /images/ paths
      if (linkPath.startsWith('/images/')) {
        return `${prefix}${linkPath}${suffix}`;
      }
      const langPath = `/${lang}${linkPath}`;
      return `${prefix}${langPath}${suffix}`;
    }
  );
}

function buildTranslatedDoc(
  enContent: string,
  tm: Map<string, TMEntry>,
  lang: string,
  model?: string,
  contributors?: string[]
): string {
  const [fm, body] = parseFrontmatter(enContent);
  const outputParts: string[] = [];

  // --- Frontmatter ---
  if (fm) {
    const translatedFm: Record<string, unknown> = { ...fm };
    const titleKey = String(fm.title ?? '');
    if (tm.has(titleKey)) {
      translatedFm.title = tm.get(titleKey)!.target;
    }
    const descKey = String(fm.description ?? '');
    if (tm.has(descKey)) {
      translatedFm.description = tm.get(descKey)!.target;
    }
    if (model && (!contributors || contributors.length === 0)) {
      translatedFm[`translated-by-${model}`] = true;
    }
    outputParts.push('---');
    const fmText = yaml
      .dump(translatedFm, {
        noRefs: true,
        sortKeys: false,
        lineWidth: -1,
      })
      .trimEnd();
    outputParts.push(fmText);
    outputParts.push('---');
    outputParts.push('');
  }

  // --- Body ---
  const lines = body.split('\n');
  let i = 0;
  let inCodeBlock = false;
  let codeFenceMarker = '';
  let inAside = false;
  const asideLinesEn: string[] = [];
  const paragraphLines: string[] = [];

  function flushParagraph(): void {
    if (paragraphLines.length > 0) {
      const text = paragraphLines.join(' ').trim();
      if (text && tm.has(text)) {
        const translated = updateLinksForLang(tm.get(text)!.target, lang);
        outputParts.push(translated);
      } else if (text) {
        const translated = updateLinksForLang(text, lang);
        outputParts.push(translated);
      }
      paragraphLines.length = 0;
    }
  }

  while (i < lines.length) {
    const line = lines[i];

    // --- Code blocks (pass through) ---
    if (inCodeBlock) {
      outputParts.push(line);
      const fenceMatch = CODE_FENCE_RE.exec(line);
      if (fenceMatch && line.startsWith(codeFenceMarker)) {
        inCodeBlock = false;
      }
      i++;
      continue;
    }

    const fenceMatch = CODE_FENCE_RE.exec(line);
    if (fenceMatch) {
      flushParagraph();
      inCodeBlock = true;
      codeFenceMarker = fenceMatch[1];
      outputParts.push(line);
      i++;
      continue;
    }

    const stripped = line.trim();

    // --- Multi-line Aside ---
    if (inAside) {
      if (ASIDE_CLOSE_RE.test(stripped)) {
        const text = asideLinesEn.join(' ').trim();
        if (text && tm.has(text)) {
          const translated = updateLinksForLang(tm.get(text)!.target, lang);
          outputParts.push(`  ${translated}`);
        } else if (text) {
          outputParts.push(`  ${updateLinksForLang(text, lang)}`);
        }
        asideLinesEn.length = 0;
        inAside = false;
        outputParts.push(line);
        i++;
        continue;
      }
      const s = stripped;
      if (s) {
        asideLinesEn.push(s);
      }
      i++;
      continue;
    }

    // --- Empty line ---
    if (EMPTY_LINE_RE.test(line)) {
      flushParagraph();
      outputParts.push('');
      i++;
      continue;
    }

    // --- Import/export (pass through) ---
    if (IMPORT_EXPORT_RE.test(stripped)) {
      flushParagraph();
      outputParts.push(line);
      i++;
      continue;
    }

    // --- Image (pass through) ---
    if (IMG_RE.test(stripped)) {
      flushParagraph();
      outputParts.push(line);
      i++;
      continue;
    }

    // --- JSX self-closing (pass through) ---
    if (JSX_SELF_CLOSING_RE.test(stripped)) {
      flushParagraph();
      outputParts.push(line);
      i++;
      continue;
    }

    // --- details (pass through) ---
    if (DETAILS_RE.test(stripped)) {
      flushParagraph();
      outputParts.push(line);
      i++;
      continue;
    }

    // --- Aside inline ---
    const asideInlineM = ASIDE_INLINE_RE.exec(stripped);
    if (asideInlineM) {
      flushParagraph();
      const innerText = asideInlineM[1].trim();
      if (tm.has(innerText)) {
        const translated = updateLinksForLang(tm.get(innerText)!.target, lang);
        // reconstruct the Aside tag
        const tagEnd = stripped.indexOf('>') + 1;
        const tagOpen = stripped.slice(0, tagEnd);
        outputParts.push(`${tagOpen}${translated}</Aside>`);
      } else {
        outputParts.push(line);
      }
      i++;
      continue;
    }

    // --- Aside open (multi-line) ---
    if (ASIDE_OPEN_RE.test(stripped)) {
      flushParagraph();
      inAside = true;
      asideLinesEn.length = 0;
      outputParts.push(line);
      i++;
      continue;
    }

    // --- Summary ---
    const summaryM = SUMMARY_RE.exec(stripped);
    if (summaryM) {
      flushParagraph();
      const inner = summaryM[1].trim();
      if (tm.has(inner)) {
        outputParts.push(`<summary>${tm.get(inner)!.target}</summary>`);
      } else {
        outputParts.push(line);
      }
      i++;
      continue;
    }

    // --- HTML block elements (pass through) ---
    if (HTML_BLOCK_START_RE.test(stripped) && !stripped.endsWith('/>')) {
      flushParagraph();
      outputParts.push(line);
      i++;
      continue;
    }

    if (stripped.startsWith('</') && stripped.endsWith('>')) {
      flushParagraph();
      outputParts.push(line);
      i++;
      continue;
    }

    // --- Headings ---
    const headingM = HEADING_RE.exec(stripped);
    if (headingM) {
      flushParagraph();
      const level = headingM[1].length;
      const text = headingM[2].trim();
      const explicitId = headingM[3] || '';
      const hashes = '#'.repeat(level);
      if (tm.has(text)) {
        let translated = tm.get(text)!.target;
        translated = addHeadingAnchor(translated, explicitId, text);
        outputParts.push(`${hashes} ${translated}`);
      } else {
        outputParts.push(line);
      }
      i++;
      continue;
    }

    // --- Table block (collect consecutive table lines) ---
    if (TABLE_SEPARATOR_RE.test(stripped) || TABLE_ROW_RE.test(stripped)) {
      flushParagraph();
      const tableLines: string[] = [];
      while (i < lines.length) {
        const s = lines[i].trim();
        if (TABLE_SEPARATOR_RE.test(s) || TABLE_ROW_RE.test(s)) {
          tableLines.push(lines[i]);
          i++;
        } else {
          break;
        }
      }
      for (const tl of emitTableBlock(tableLines, tm)) {
        outputParts.push(tl);
      }
      continue;
    }

    // --- List items (with continuation lines) ---
    const listM = LIST_ITEM_RE.exec(stripped);
    const ordM = listM ? null : ORDERED_LIST_RE.exec(stripped);
    if (listM || ordM) {
      flushParagraph();
      const m = (listM || ordM)!;
      let prefix = m[1];
      let text = m[2].trim();
      prefix = prefix.trimEnd() + ' ';
      i++;
      while (i < lines.length) {
        const nextRaw = lines[i];
        if (!nextRaw || !nextRaw[0]?.match(/\s/)) {
          break;
        }
        const nextStripped = nextRaw.trim();
        if (!nextStripped) {
          break;
        }
        if (LIST_ITEM_RE.test(nextStripped) || ORDERED_LIST_RE.test(nextStripped)) {
          break;
        }
        text += ' ' + nextStripped;
        i++;
      }
      if (tm.has(text)) {
        const translated = updateLinksForLang(tm.get(text)!.target, lang);
        outputParts.push(`${prefix}${translated}`);
      } else {
        const translated = updateLinksForLang(text, lang);
        outputParts.push(`${prefix}${translated}`);
      }
      continue;
    }

    // --- Blockquotes ---
    if (stripped.startsWith('>')) {
      flushParagraph();
      const bqText = stripped.slice(1).trim();
      if (bqText && tm.has(bqText)) {
        const translated = updateLinksForLang(tm.get(bqText)!.target, lang);
        outputParts.push(`> ${translated}`);
      } else {
        outputParts.push(line);
      }
      i++;
      continue;
    }

    // --- Regular text ---
    paragraphLines.push(stripped);
    i++;
  }

  flushParagraph();

  let result = outputParts.join('\n');
  // ensure file ends with newline
  if (!result.endsWith('\n')) {
    result += '\n';
  }
  return result;
}

// ---------------------------------------------------------------------------
// Subcommand: init
// ---------------------------------------------------------------------------

function cmdInit(lang: string): number {
  const enDocs = findEnDocs();
  let created = 0;
  let skipped = 0;

  for (const docRel of enDocs.sort()) {
    const tmPath = tmPathForDoc(docRel, lang);
    if (fs.existsSync(tmPath)) {
      skipped++;
      continue;
    }

    const enPath = path.join(DOCS_DIR, docRel);
    const enContent = fs.readFileSync(enPath, 'utf-8');
    const segments = extractSegments(enContent);

    const entries: Record<string, string>[] = [];
    const seen = new Set<string>();
    for (const seg of segments) {
      if (seen.has(seg.text)) {
        continue;
      }
      seen.add(seg.text);
      entries.push({
        source: seg.text,
        target: '',
        context: contextLabel(seg),
      });
    }

    saveYamlFile(tmPath, { meta: { contributors: [] }, entries });
    created++;
    console.log(`  init: ${docRel} (${entries.length} segments)`);
  }

  console.log(`\nInitialized ${created} TM files, skipped ${skipped} (already exist).`);
  return 0;
}

// ---------------------------------------------------------------------------
// Subcommand: update
// ---------------------------------------------------------------------------

function cmdUpdate(lang: string): number {
  const enDocs = findEnDocs();
  let updated = 0;
  let skipped = 0;

  for (const docRel of enDocs.sort()) {
    const tmPath = tmPathForDoc(docRel, lang);
    if (!fs.existsSync(tmPath)) {
      skipped++;
      continue;
    }

    const enPath = path.join(DOCS_DIR, docRel);
    const enContent = fs.readFileSync(enPath, 'utf-8');
    const segments = extractSegments(enContent);

    // current EN source texts (deduplicated, preserving order)
    const enSources: string[] = [];
    const enSeen = new Set<string>();
    for (const seg of segments) {
      if (!enSeen.has(seg.text)) {
        enSeen.add(seg.text);
        enSources.push(seg.text);
      }
    }

    // build context lookup from current segments
    const contextBySource = new Map<string, string>();
    for (const seg of segments) {
      if (!contextBySource.has(seg.text)) {
        contextBySource.set(seg.text, contextLabel(seg));
      }
    }

    // load existing TM
    const data = loadYamlFile(tmPath);
    if (!data) {
      skipped++;
      continue;
    }

    let oldEntries: Record<string, unknown>[];
    let meta: Record<string, unknown>;

    if (typeof data === 'object' && !Array.isArray(data)) {
      const d = data as Record<string, unknown>;
      oldEntries = (d.entries as Record<string, unknown>[]) || [];
      meta = (d.meta as Record<string, unknown>) || { contributors: [] };
    } else {
      oldEntries = data as Record<string, unknown>[];
      meta = { contributors: [] };
    }

    // index existing entries by source text
    const oldBySource = new Map<string, Record<string, unknown>>();
    for (const entry of oldEntries) {
      oldBySource.set(entry.source as string, entry);
    }

    // build new entry list in EN segment order
    const newEntries: Record<string, unknown>[] = [];
    let added = 0;
    for (const src of enSources) {
      if (oldBySource.has(src)) {
        newEntries.push(oldBySource.get(src)!);
      } else {
        newEntries.push({
          source: src,
          target: '',
          context: contextBySource.get(src) || 'paragraph',
        });
        added++;
      }
    }

    // count removed (in old but not in current EN)
    let removed = 0;
    for (const src of oldBySource.keys()) {
      if (!enSeen.has(src)) {
        removed++;
      }
    }

    if (added === 0 && removed === 0) {
      skipped++;
      continue;
    }

    saveYamlFile(tmPath, { meta, entries: newEntries });
    updated++;
    console.log(`  update: ${docRel} (+${added} new, -${removed} stale)`);
  }

  console.log(`\nUpdated ${updated} TM files, skipped ${skipped} (no TM or no changes).`);
  return 0;
}

// ---------------------------------------------------------------------------
// Subcommand: build
// ---------------------------------------------------------------------------

function cmdBuild(lang: string, model?: string): number {
  const targetDir = targetDocsDir(lang);
  const enDocs = findEnDocs();
  let built = 0;
  let skipped = 0;

  for (const docRel of enDocs.sort()) {
    const enPath = path.join(DOCS_DIR, docRel);
    const [tm, contributors] = loadTm(docRel, lang);
    if (tm.size === 0) {
      skipped++;
      continue;
    }

    const enContent = fs.readFileSync(enPath, 'utf-8');
    const translated = buildTranslatedDoc(enContent, tm, lang, model, contributors);

    const targetPath = path.join(targetDir, docRel);
    fs.mkdirSync(path.dirname(targetPath), { recursive: true });
    fs.writeFileSync(targetPath, translated, 'utf-8');
    built++;
    console.log(`  built: ${docRel}`);
  }

  console.log(`\nBuilt ${built} files, skipped ${skipped} (no TM).`);
  return 0;
}

// ---------------------------------------------------------------------------
// Subcommand: validate
// ---------------------------------------------------------------------------

function cmdValidate(lang: string, model?: string): number {
  const enDocs = findEnDocs();
  let validated = 0;
  let skipped = 0;
  const errors: string[] = [];

  for (const docRel of enDocs.sort()) {
    const enPath = path.join(DOCS_DIR, docRel);
    const [tm, contributors] = loadTm(docRel, lang);
    if (tm.size === 0) {
      skipped++;
      continue;
    }

    const enContent = fs.readFileSync(enPath, 'utf-8');
    try {
      buildTranslatedDoc(enContent, tm, lang, model, contributors);
    } catch (e) {
      errors.push(`${docRel}: ${e}`);
      continue;
    }

    validated++;
    console.log(`  ok: ${docRel}`);
  }

  console.log(`\nValidated ${validated} files, skipped ${skipped} (no TM).`);

  if (errors.length > 0) {
    console.log(`\nERRORS (${errors.length}):`);
    for (const err of errors) {
      console.log(`  ${err}`);
    }
    return 1;
  }

  return 0;
}

// ---------------------------------------------------------------------------
// Subcommand: status
// ---------------------------------------------------------------------------

function cmdStatus(lang: string, format: 'table' | 'summary', filterDocs: string[] | null): number {
  let enDocs = findEnDocs();
  if (filterDocs) {
    const filterSet = new Set(filterDocs);
    enDocs = enDocs.filter((d) => filterSet.has(d));
  }

  const rows: [string, number, number, number][] = [];

  for (const docRel of enDocs.sort()) {
    const enPath = path.join(DOCS_DIR, docRel);
    const enContent = fs.readFileSync(enPath, 'utf-8');
    const segments = extractSegments(enContent);
    const [tm] = loadTm(docRel, lang);

    const hits = segments.filter((s) => tm.has(s.text) && tm.get(s.text)!.target).length;
    const total = segments.length;
    const misses = total - hits;

    if (total > 0) {
      rows.push([docRel, total, hits, misses]);
    }
  }

  const totalSegments = rows.reduce((sum, r) => sum + r[1], 0);
  const totalHits = rows.reduce((sum, r) => sum + r[2], 0);
  const totalMisses = rows.reduce((sum, r) => sum + r[3], 0);

  if (format === 'summary') {
    printSummary(lang, rows, totalSegments, totalHits, totalMisses, filterDocs);
  } else {
    printTable(lang, rows, totalSegments, totalHits, totalMisses);
  }

  return 0;
}

function printTable(
  lang: string,
  rows: [string, number, number, number][],
  totalSegments: number,
  totalHits: number,
  totalMisses: number
): void {
  console.log(
    `[${lang}] ${'Document'.padEnd(55)} ${'Segments'.padStart(8)} ${'HIT'.padStart(6)} ${'MISS'.padStart(6)} ${'Coverage'.padStart(9)}`
  );
  console.log('-'.repeat(92));

  for (const [docRel, total, hits, misses] of rows) {
    const coverage = total > 0 ? `${Math.round((hits / total) * 100)}%` : 'N/A';
    console.log(
      `  ${docRel.padEnd(53)} ${String(total).padStart(8)} ${String(hits).padStart(6)} ${String(misses).padStart(6)} ${coverage.padStart(9)}`
    );
  }

  console.log('-'.repeat(92));
  const overall = totalSegments > 0 ? `${Math.round((totalHits / totalSegments) * 100)}%` : 'N/A';
  console.log(
    `  ${'TOTAL'.padEnd(53)} ${String(totalSegments).padStart(8)} ${String(totalHits).padStart(6)} ${String(totalMisses).padStart(6)} ${overall.padStart(9)}`
  );
}

function printSummary(
  lang: string,
  rows: [string, number, number, number][],
  totalSegments: number,
  totalHits: number,
  totalMisses: number,
  filterDocs: string[] | null
): void {
  const docCount = rows.length;
  const scope = filterDocs ? 'affected' : 'total';
  console.log(`## Translation Status (${lang})\n`);
  console.log(
    `Your changes affect **${docCount} document${docCount !== 1 ? 's' : ''}** (${scope}).\n`
  );

  console.log('| Document | Segments | Translated | Coverage |');
  console.log('|---|---|---|---|');

  for (const [docRel, total, hits] of rows) {
    const coverage = total > 0 ? `${Math.round((hits / total) * 100)}%` : 'N/A';
    console.log(`| ${docRel} | ${total} | ${hits}/${total} | ${coverage} |`);
  }

  const overall = totalSegments > 0 ? `${Math.round((totalHits / totalSegments) * 100)}%` : 'N/A';
  console.log(
    `| **Total** | **${totalSegments}** | **${totalHits}/${totalSegments}** | **${overall}** |`
  );

  console.log();
  if (totalMisses === 0) {
    console.log('All segments are translated!');
  } else {
    console.log(
      'To find remaining untranslated segments, look for `target: ""` in the TM files above.'
    );
  }
}

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

function findEnDocs(): string[] {
  const docs: string[] = [];

  function walk(dir: string): void {
    const entries = fs.readdirSync(dir, { withFileTypes: true });
    for (const entry of entries) {
      if (entry.isDirectory()) {
        if (!KNOWN_LANGS.includes(entry.name)) {
          walk(path.join(dir, entry.name));
        }
      } else if (entry.name.endsWith('.mdx') || entry.name.endsWith('.md')) {
        docs.push(path.relative(DOCS_DIR, path.join(dir, entry.name)));
      }
    }
  }

  walk(DOCS_DIR);
  return docs;
}

// ---------------------------------------------------------------------------
// CLI
// ---------------------------------------------------------------------------

function parseArgs(argv: string[]): {
  lang: string;
  command: string;
  format: 'table' | 'summary';
  docs: string[] | null;
  model: string | undefined;
} {
  let lang = 'ko';
  let command = '';
  let format: 'table' | 'summary' = 'table';
  let docs: string[] | null = null;
  let model: string | undefined;

  const args = argv.slice(2); // skip node + script path
  let i = 0;

  while (i < args.length) {
    const arg = args[i];
    if (arg === '--lang' && i + 1 < args.length) {
      lang = args[i + 1];
      i += 2;
      continue;
    }
    if (arg.startsWith('--format=')) {
      const val = arg.split('=')[1];
      if (val === 'summary' || val === 'table') {
        format = val;
      }
      i++;
      continue;
    }
    if (arg === '--format' && i + 1 < args.length) {
      const val = args[i + 1];
      if (val === 'summary' || val === 'table') {
        format = val;
      }
      i += 2;
      continue;
    }
    if (arg === '--model' && i + 1 < args.length) {
      model = args[i + 1];
      i += 2;
      continue;
    }
    if (arg === '--docs' && i + 1 < args.length) {
      docs = [];
      i++;
      while (i < args.length && !args[i].startsWith('-')) {
        docs.push(args[i]);
        i++;
      }
      continue;
    }
    if (!arg.startsWith('-')) {
      command = arg;
    }
    i++;
  }

  return { lang, command, format, docs, model };
}

function main(): number {
  const { lang, command, format, docs, model } = parseArgs(process.argv);

  switch (command) {
    case 'init':
      return cmdInit(lang);
    case 'update':
      return cmdUpdate(lang);
    case 'build':
      return cmdBuild(lang, model);
    case 'validate':
      return cmdValidate(lang, model);
    case 'status':
      return cmdStatus(lang, format, docs);
    default:
      console.error(
        'Usage: translation-memory.ts [--lang LANG] {init,update,build,validate,status}'
      );
      return 1;
  }
}

process.exit(main());

# Handoff — Site redesign (landing + docs reskin)

> **Temporary.** This file exists to bootstrap a fresh working session. **Remove it before merge** — it is not site content.

## Goal

Recreate the "Actionbase landing page redesign" design handoff (an Iris/Cloud design system delivered as HTML prototypes + a README, in a zip) inside the real Astro + Starlight site. Take the **design system only**; all textual content follows the **current repo**, never the prototype's copy.

## Locked decisions

- **Palette:** Iris `#5D5FEF` accent + Cloud background gradient (light) / dark gradient (dark). The prototype's palette/personalization bar is dropped.
- **Landing** is a standalone `website/src/pages/index.astro` with its own chrome, replacing the old Starlight splash `website/src/content/docs/index.mdx` (removed). Its theme syncs with Starlight through the shared `starlight-theme` localStorage key + `<html data-theme>`.
- **Docs** are reskinned, not rewritten: Starlight structure/nav/search/TOC kept; `website/src/assets/landing.css` overrides Starlight CSS custom properties (Iris/Cloud), fonts are added via `astro.config.mjs` `head`, code blocks are themed via Expressive Code (single dark theme in both site themes).
- **Content is current-repo:** hero = follows / likes / related items (U2U/U2I/I2I); no Blog links; live GitHub star badge (not a hardcoded count); storage = HBase today + SlateDB planned (per `design/storage-backends` and the SlateDB tracking issue); the architecture write/read paths mirror `design/mutation` (WAL → storage → CDC, Kafka-backed) and `design/query` (direct lookups), both converging on one shared Storage Backend.

## Done

**Landing** (`website/src/pages/index.astro`):

- Sections: nav (brand SVG logo, live GitHub star badge, theme toggle) · hero (copy + live console) · trust band · The engine (6 toned-down cards) · Architecture (write path left / read path right → one wide shared Storage Backend) · Quick start (3 dark code blocks) · CTA · footer.
- Console is a guided **sequential** demo: the 3 preset buttons (`get` → `scan` → `count`) unlock one at a time; a Reset button restores step one. Keyboard input is disabled (static prompt + blinking cursor + "run a step below" hint). No scrollbar — content is bottom-anchored (the banner clips off the top as lines accumulate).
- Removed during review: marginalia strip, "DB" chip, hero eyebrow, "Live console / Precomputed reads" labels, feature chips, the "HBase-backed" card (→ "Pluggable Storage"), "Pangyo" and "Docs" from the footer, the architecture footnote.

**Docs reskin** (`website/src/assets/landing.css`, `website/astro.config.mjs`):

- Starlight token overrides for Iris/Cloud in both themes; fonts (Space Grotesk headings, Manrope body, JetBrains Mono mono) via head links; page background gradient; translucent blurred header.
- Signature components: sidebar mono-uppercase group labels + accent active pill with a left bar; minimal callouts (mono label + bottom hairline, no icon/box); code blocks forced onto the dark terminal surface (`#1b1c2b`) in both themes; TOC accent-active; prev/next cards; light-surface search button.

## Verified

`cd website && npm run build` passes (41 pages, internal links valid). Screenshots captured for the landing (light/dark + console sequence) and docs quick-start (light/dark).

## Preview / verify

```bash
cd website
npm run build && npm run preview   # serves dist (first free port from 4321)
```

Visual capture during dev used Playwright: put a small script **inside `website/`** so node resolves `website/node_modules`, seed `localStorage['starlight-theme']` before `goto`, then `screenshot({ fullPage: true })`. Two contexts (light/dark). The script was throwaway and is not committed.

## Remaining / review before merge

- **Delete this HANDOFF.md.**
- Fonts load from the Google Fonts CDN — consider self-hosting (the design README suggested it).
- Expressive Code uses a single dark theme (`github-dark`) for both site themes to match the always-dark mock code; syntax colors are github-dark's, not the mock's exact palette.
- Sweep the remaining docs surfaces under the new theme: asides of every type (note/tip/caution/danger), tables, `<details>`, images, and API-reference pages; mermaid is still `theme: 'forest'` + autoTheme — consider aligning to Iris.
- Mobile/responsive: Starlight drawer nav on docs; landing narrow breakpoints.
- Accessibility: muted-text contrast over the gradient, focus-visible states.
- Confirm the retained claims ("a million requests per minute", "in production at Kakao") are still acceptable marketing copy.

## Key files

- `website/src/pages/index.astro` — standalone landing (markup + scoped styles + console/theme scripts).
- `website/src/assets/landing.css` — docs theme (Starlight token overrides + component rules + retained utilities).
- `website/astro.config.mjs` — fonts in `head`, Expressive Code dark-code config.
- `website/src/content/docs/index.mdx` — **removed** (old splash; `/` is now the standalone page).
- Design source of truth: the handoff zip (`Actionbase Pangyo.dc.html` = landing, `Actionbase Docs.dc.html` = docs, `README.md` = tokens/spec, `screenshots/`).

## Language

`kakao/actionbase` is non-fork: all artifacts (code, comments, commits, PR, this file) are **English**; only live conversation is Korean.

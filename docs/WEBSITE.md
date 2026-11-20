# WuWaConfig — Repository Layout

```
.
├── app/                     Android module (Kotlin, Compose, Navigation 3)
├── site/                    GitHub Pages artifact root — every public web file
│   ├── *.html               index, download, presets, fps-lab, faq, troubleshooting
│   ├── styles.css  sw.js  register-sw.js  manifest.json
│   ├── robots.txt  sitemap.xml  og-image.png  favicon.png  icon-*.{png,webp}
│   ├── fonts/               self-hosted Inter + JetBrains Mono (woff2)
│   ├── mall/                character full sprites (runtime-probed, not precached)
│   └── screenshots/         app screenshots used by the README and manifest
├── docs/                    design, audit and reference documents (this folder)
├── tools/                   Python generators for assets and calculator data
├── gradle/  gradlew  *.gradle.kts  gradle.properties
└── .github/                 CI, Dependabot, Pages deploy, calculator-data cron
```

## Why `site/` exists

GitHub Pages is deployed from `site/` by `.github/workflows/pages.yml`
(`upload-pages-artifact` + `deploy-pages`), not from the branch root. The web
root therefore contains only web files — no Gradle project, no docs, no build
output — and `Settings → Pages → Source` must be **GitHub Actions**.

Two rules follow from the deploy root being `site/` rather than the repository
root:

- **All internal links stay relative** (`styles.css`, `mall/…`), never
  root-absolute (`/styles.css`). The site is served from
  `/WuWa-Config-Android/`, not from a domain root.
- **A file that must be reachable at the site root** (search-console
  verification, `robots.txt`, `sitemap.xml`) lives directly in `site/`.

Preview locally:

```bash
python3 -m http.server -d site 8000
```

## Documents

| File | Contents |
| --- | --- |
| `CONFIG-GENERATOR.md` | Preset tiers, `GeneratorOptions`, `GameMode`, output files |
| `AUDIT_REPORT.md` | Phase 1 UI audit — spacing, contrast, touch targets, hardcoded values |
| `DESIGN_PROPOSAL.md` | Phase 2 design-system proposal |
| `UI_REDESIGN_SUMMARY.md` | Branch log of the Phase 1–4 redesign |
| `CODE_AUDIT_FINDINGS.md` | Whole-module code audit: build config, tests, lint, findings to fix |

## Not in the repository

Local-only material is excluded on purpose: `apk_extract*/` (datamining
scratch, ~3.7 GB), `logs/`, `graphify-out/`, `build/`, signing material
(`keystore.properties`, `release.jks`), `local.properties`, and the agent
guidance files (`AGENTS.md`, `CLAUDE.md`, `.claude/`, `.agents/`,
`.clinerules/`). See `.gitignore`.

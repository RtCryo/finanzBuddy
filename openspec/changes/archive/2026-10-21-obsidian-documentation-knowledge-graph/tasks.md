# Tasks

## 1. Audit documentation and traceability

- [x] 1.1 Inventory and classify tracked Markdown across the root, `docs/`, module folders, all 18 current capability specs, and all 18 archived change folders; compare current factual claims and implementation references with gateway/parser/UI source, Compose configuration, and monitoring files, and record the canonical document, source locations, spec, and existing archive artifacts for each area.
- [x] 1.2 Identify broken relative links, missing module/spec/archive connections, stale current documentation, and any conflicts between a current spec and implementation/configuration; verify each reported issue against the target path or source before proposing a correction.

## 2. Build the repository knowledge map

- [x] 2.1 Create `docs/README.md` as the Obsidian-friendly landing page with navigation for project/architecture, API Gateway, parser service, UI/design system, sample documents, deployment/storage/Kafka, and monitoring; include relevant implementation paths, exact current capability links, and links to every archived change folder with only the artifacts that actually exist, and document the portable relative-Markdown-link maintenance convention.
- [x] 2.2 Add or correct return links to `docs/README.md` and targeted links among the root README, architecture guide, module READMEs, and UI design system; verify that each module entry point reaches its relevant implementation, current specs, and historical changes without duplicating their content.
- [x] 2.3 Add concise navigation references from each relevant current capability spec to its implementation/documentation and related archive entries; preserve all normative requirements and scenarios, and verify the link targets exist.
- [x] 2.4 Correct verified stale or broken statements in current documentation, keeping implementation facts aligned with code/configuration and behavior requirements in their existing specs; document any unresolved spec-versus-implementation discrepancy in the knowledge map without changing the requirement or archive history.

## 3. Validate navigation

- [x] 3.1 Verify every added or changed relative Markdown link in the landing page and edited documents resolves to an existing file or directory; use an existing repository checker if available, otherwise perform a one-off local path-resolution check, and fix all broken targets.
- [x] 3.2 Review navigation starting from the root README, each module entry point, representative current specs, and the archive index; if Obsidian is available, open the repository as a vault and confirm the landing-page links navigate correctly, otherwise record that the Obsidian smoke test was unavailable.

## Verification notes

- A one-off local checker resolved 364 relative Markdown links across 127 Markdown files and found no broken paths; all 25 Markdown heading fragments resolved.
- The knowledge map links all 18 archived change folders and their listed extant artifacts.
- Obsidian was unavailable in this environment (`where.exe obsidian` found no executable), so the vault smoke test could not be run; navigation was reviewed from the root README, module READMEs, current specs, and archive index.

# Design

## Context

See `proposal.md` for motivation and scope. The repository already has a root README, module READMEs for the API Gateway, parser service, UI, and sample documents, plus `docs/ARCHITECTURE.md` and `ui/DESIGN_SYSTEM.md`. The architecture document describes the system boundaries and points to several of these documents; the UI design guide already links to UI capability specs. However, there is no complete landing page mapping all documented modules to their implementation areas, current OpenSpec capabilities, and relevant archived changes. Monitoring and deployment are documented primarily by repository configuration and dashboards rather than a dedicated README.

The current OpenSpec inventory contains capabilities for authentication, document upload/lifecycle/consumption/parsing, Kafka, dashboard and UI behavior, deployment, observability, and transaction normalization/viewing. The archive contains change folders with uneven artifact sets; these are historical records and should be linked as they exist rather than normalized or rewritten. `docs/ARCHITECTURE.md` establishes that code and deployment configuration are authoritative when they conflict with architecture documentation.

## Goals / Non-Goals

**Goals:**

- Give readers opening the repository in Obsidian a stable project-level documentation map, with a clear path into module documentation and cross-service architecture.
- Connect each documented subsystem to its main implementation areas, relevant current capabilities under `openspec/specs/`, and related archived changes where a traceable history exists.
- Audit existing tracked Markdown for navigation gaps, broken relative links, stale statements, and mismatches with implementation/configuration; record corrections in the owning documentation rather than duplicating authoritative details.
- Keep navigation portable across Obsidian, GitHub, and ordinary Markdown viewers.

**Non-Goals:**

- Add `.obsidian` settings, community plugins, templates, frontmatter metadata, or a new documentation-generation dependency.
- Change application behavior, OpenSpec capability requirements, or the contents/meaning of historical archived change artifacts.
- Turn every implementation file into a separate knowledge note; link to relevant source directories and representative files at subsystem boundaries.

## Decisions

1. **Use a repository-relative Markdown knowledge map, linked from the root README.**
   Create `docs/README.md` as the documentation landing page. It will organize links by project/module and by cross-cutting areas (architecture, deployment/monitoring, OpenSpec), and explain which documents are entry points versus implementation references. The root `README.md` will link to it. This keeps the repository root familiar to contributors and gives Obsidian a clear note to open without requiring vault settings.
   - Alternative considered: put the entire knowledge graph in the root README. Rejected because it would mix onboarding and product information with the detailed documentation inventory and grow the already broad entry point.

2. **Use relative Markdown links rather than Obsidian-only wikilinks.**
   New and corrected links will be standard relative Markdown links, including links to local source paths where useful. Obsidian resolves these links while GitHub and other Markdown renderers preserve useful navigation. Do not introduce fragile machine-specific absolute paths or depend on display-name resolution.
   - Alternative considered: use `[[wikilinks]]` and configure Obsidian to resolve them. Rejected because this adds viewer-specific syntax/configuration and reduces portability.

3. **Make the map a traceability index, not a second source of truth.**
   For each module and cross-cutting area, record its README or architecture note, relevant implementation roots/files, current capability spec paths, and related archived change folders. Link to the actual current spec and historical artifacts rather than copying their requirements or design details. Use the exact existing capability paths. Cover gateway authentication/documents/outbox/transactions, parser/event/format handling, UI screens and design system, shared Compose/database/storage/Kafka boundaries, and monitoring configuration/dashboard files.
   - Alternative considered: add prose summaries of each spec and archive to the landing page. Rejected because copied details would drift from code, current specs, and the historical change record.

4. **Audit documentation against code and configuration, preserving explicit authority boundaries.**
   Review root and module Markdown, architecture and UI design documentation, all current capability specs, and archived change records for relevant links and consistency. Where a claim appears stale or conflicts with current implementation/configuration, verify the source and update the current owning document or flag an unresolved discrepancy in the map; do not rewrite historical archive content. Keep normative behavior in the current OpenSpec specs and describe implementation facts in module/architecture docs.
   - Alternative considered: treat existing READMEs/specs as universally authoritative. Rejected because the architecture guide explicitly names code and deployment configuration as authoritative for current behavior.

5. **Keep module links bidirectional at useful boundaries.**
   The landing map will link into each module's canonical documentation; module documentation and relevant architecture/spec documents will link back to the map and to related implementation/spec/archive references where that link adds context. Do not add redundant links to every source file or every unrelated archive.
   - Alternative considered: only add a central index. Rejected because readers often enter through a module README, spec, or architecture document and need onward navigation from that context.

## Risks / Trade-offs

- [Links can break when directories or capability names move] → Use repository-relative paths and verify every added/changed destination as part of the audit; update the map when a documented module or spec path changes.
- [A manually curated index can become stale] → Keep entries concise, point to canonical sources, and include the link-maintenance convention in the landing page and relevant contributor guidance.
- [Current specs and implementation may disagree] → Preserve the existing code/configuration authority rule for implementation facts, avoid silently changing normative requirements, and document any discovered specification discrepancy for a separately scoped decision.
- [Historical archive folders have incomplete or varying artifact sets] → Link only to files that exist in each archive; do not imply that missing artifacts exist or modify archived content solely to make navigation uniform.

## Migration Plan

1. Audit the existing Markdown and map modules, code/configuration areas, current specs, and archive folders.
2. Add the repository documentation landing page and targeted cross-links in existing documentation.
3. Verify relative link destinations and review the result from both the root README path and the module/spec/archive entry points. If Obsidian is available, open the repository as a vault and smoke-test the landing-page navigation.

Rollback is a documentation-only revert of the landing page and link edits; no data or runtime migration is required.

# Proposal

## Why

The repository's documentation is distributed across the root README, service and UI modules, architecture guidance, current OpenSpec capabilities, and archived changes. Existing relative links cover some entry points, but there is no complete navigation map connecting module documentation to the implementation, current requirements, and relevant history, making the repository difficult to explore as an Obsidian vault.

## What Changes

- Audit tracked Markdown documentation across repository modules, architecture documentation, current OpenSpec specs, and archived changes; identify each document's scope and authoritative role.
- Establish a repository-level documentation map suitable as an Obsidian starting point, with module, cross-service, implementation, specification, and archive navigation.
- Add and correct relative Markdown links in existing documentation so readers can move between module documentation, relevant source areas, current capability specs, and related archived changes.
- Document lightweight link and maintenance conventions so new or materially changed module documentation remains discoverable.
- Keep the repository usable in GitHub and other Markdown viewers by using relative Markdown links supported by Obsidian; do not require Obsidian plugins or vault-specific configuration.

## Capabilities

### New Capabilities

None. This change is documentation and navigation only; it does not introduce or modify product behavior.

### Modified Capabilities

None. Existing capability requirements remain authoritative and unchanged.

## Impact

- Documentation: root `README.md`, `docs/`, module READMEs (`api-gateway/`, `parser-service/`, `ui/`, and `documents/`), and the navigation links to `openspec/specs/` and `openspec/changes/archive/`.
- Implementation references: gateway, parser, UI, deployment, and monitoring source/configuration paths as applicable to documented capabilities.
- OpenSpec: planning-only documentation change marked `skip_specs: true`; no product capability specification or archived change content is to be rewritten.
- Runtime code, APIs, data, dependencies, and deployment behavior are unaffected.

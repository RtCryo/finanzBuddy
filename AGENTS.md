<!-- headroom:rtk-instructions -->
# RTK (Rust Token Killer) - Token-Optimized Commands

When running shell commands, **always prefix with `rtk`**. This reduces context
usage by 60-90% with zero behavior change. If rtk has no filter for a command,
it passes through unchanged — so it is always safe to use.

## Key Commands
```bash
# Git (59-80% savings)
rtk git status          rtk git diff            rtk git log

# Files & Search (60-75% savings)
rtk ls <path>           rtk read <file>         rtk grep <pattern>
rtk find <pattern>      rtk diff <file>

# Test (90-99% savings) — shows failures only
rtk pytest tests/       rtk cargo test          rtk test <cmd>

# Build & Lint (80-90% savings) — shows errors only
rtk tsc                 rtk lint                rtk cargo build
rtk prettier --check    rtk mypy                rtk ruff check

# Analysis (70-90% savings)
rtk err <cmd>           rtk log <file>          rtk json <file>
rtk summary <cmd>       rtk deps                rtk env

# GitHub (26-87% savings)
rtk gh pr view <n>      rtk gh run list         rtk gh issue list

# Infrastructure (85% savings)
rtk docker ps           rtk kubectl get         rtk docker logs <c>

# Package managers (70-90% savings)
rtk pip list            rtk pnpm install        rtk npm run <script>
```

## Rules
- In command chains, prefix each segment: `rtk git add . && rtk git commit -m "msg"`
- For debugging, use raw command without rtk prefix
- `rtk proxy <cmd>` runs command without filtering but tracks usage
<!-- /headroom:rtk-instructions -->

## Architecture documentation

- Before work that changes or depends on cross-service boundaries, APIs, data ownership or migrations, Kafka/event flows, security boundaries, or deployment topology, read [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) and the relevant module README/OpenSpec requirements.
- When a change materially alters any of those architecture facts, update `docs/ARCHITECTURE.md` in the same change. Keep it aligned with the source code and deployment configuration; if they disagree, treat code/configuration as authoritative and correct the documentation.

## Documentation map maintenance

- Before implementing a task that changes application behavior, a module/source entry point, an API or cross-service contract, deployment, monitoring, UI patterns, an OpenSpec capability, or module documentation, review [`docs/README.md`](docs/README.md) and its relevant subsystem row.
- Update `docs/README.md` in the same change when a subsystem's canonical documentation, implementation paths, current capabilities, or related archived changes are added, removed, renamed, or materially changed. Add every new current capability spec to the map and add every completed/archived change folder when it is archived. Link only to paths and artifacts that exist.
- Add or maintain a return path from affected module entry-point documentation and current capability specs to the documentation map and relevant implementation/history references. Preserve normative OpenSpec requirements and do not rewrite archive history for navigation.
- Before reporting completion, run `rtk proxy python scripts/check_documentation_map.py` and fix any failures. If the map was reviewed and no update was warranted, state why in the final summary.

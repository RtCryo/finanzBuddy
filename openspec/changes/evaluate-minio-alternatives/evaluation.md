# Object-Storage Backend Evaluation

Prepared during `/opsx:apply` for the `evaluate-minio-alternatives` change. Facts reflect
the state known as of the analysis (knowledge cutoff early 2026); maintenance status should be
re-confirmed if this sits unactioned for long.

## 2.1 Weighted criteria

| # | Criterion | Weight | Why it matters here |
|---|-----------|--------|---------------------|
| C1 | Maintenance / patch cadence | 5 | The entire reason for this change — the store holds user financial documents and must receive security patches. |
| C2 | S3 compatibility with `io.minio` (incl. `CreateBucket`) | 5 | App must work with only config changes. The API Gateway calls `bucketExists`+`makeBucket` at startup (`MinioConfig.java`), so the backend must accept an S3 `CreateBucket` from the app credentials — or we disable that and pre-provision. |
| C3 | Single-host Compose operational effort | 4 | One container, env-var config, auto/simple bucket provisioning, a real healthcheck. |
| C4 | Licensing / cost | 3 | Self-hosted internal use; AGPL is acceptable but permissive is nicer. Managed adds storage+egress cost. |
| C5 | Durability / backup story | 2 | Single-host; backup = snapshot the data volume. Managed shifts durability to the provider. |
| C6 | Exit / lock-in risk | 3 | All S3-compatible, so low; managed adds an external account dependency. |

Scoring is 1 (poor) – 5 (excellent), multiplied by weight.

## 2.2 MinIO from source (self-built image)

- **Maintenance:** MinIO remains actively developed, but community edition is source-first and the
  console/admin surface has been trimmed. Building our own image means **we** own the build
  pipeline, base-image CVE tracking, and rebuild cadence — exactly the burden this change exists to
  shed. AGPLv3.
- **S3 / behavior:** Identical to today — full S3, `CreateBucket` works with root creds, presign,
  multipart all fine. Drop-in for the app.
- **Ops:** Same env-var model as now (`MINIO_ROOT_USER`/`PASSWORD`), same `/minio/health/live`
  probe. Only the image source changes.
- **Verdict:** Behaviorally perfect, but shifts image maintenance to us — weak on C1.

## 2.3 Garage (`dxflrs/garage`)

- **Maintenance:** Actively maintained by Deuxfleurs, official images published, AGPLv3. Purpose-
  built for self-hosted, single-/multi-host deployments.
- **S3 / behavior:** S3-compatible incl. multipart and presign. **Friction:** first run requires a
  layout init (`garage layout assign` + `apply`) before it serves, and access keys are created/
  imported via the `garage` CLI/admin API and then granted per-bucket. The app's startup
  `makeBucket` needs the key to hold the global create-bucket permission, or we pre-create the
  bucket and set `create-bucket=false`. Requires a `garage.toml` (with `rpc_secret`, dirs, region).
- **Ops:** Tiny single static binary, low footprint. But provisioning is a multi-step init
  (layout + key import + grant) — an init container or entrypoint script, more than MinIO's env vars.
- **Verdict:** Great fit and lightweight; the extra setup is a one-time cost.

## 2.4 SeaweedFS (`chrislusf/seaweedfs`)

- **Maintenance:** Very active, large community, **Apache-2.0** (most permissive of the self-hosted
  set). Official images published.
- **S3 / behavior:** `weed server -s3` runs master+volume+filer+s3 in one process. Static
  credentials via an `-s3.config` identities JSON; grant the identity `Admin`/bucket actions and the
  app's `makeBucket` works, plus SeaweedFS auto-creates buckets on first write. Good S3 coverage.
- **Ops:** Single container in `server` mode, credentials pinned in a small JSON config, buckets
  auto-create — the smoothest drop-in of the self-hosted options. More internal moving parts than
  Garage, but they're bundled in one process on a single host.
- **Verdict:** Strong drop-in: permissive license, static creds, auto-bucket. Slightly heavier
  conceptually than Garage.

## 2.5 Managed S3 (Cloudflare R2 / AWS S3 / Backblaze B2 / Hetzner Object Storage)

- **Maintenance:** Zero image maintenance — the provider runs it. Best possible C1.
- **S3 / behavior:** All expose an S3-compatible endpoint the `io.minio` client can target. But you
  normally **pre-create the bucket** and hand the app a scoped key that cannot `CreateBucket`; so
  we'd set `app.minio.create-bucket=false` and provision out-of-band.
- **Ops / cost:** No container, but an **external dependency and network egress** — unusable for
  offline local dev, and adds account/credential ownership + egress cost considerations.
  - *Cloudflare R2:* S3 API, **zero egress fees**, generous free tier — strongest managed option for
    production.
  - *AWS S3:* reference S3 semantics, egress cost, IAM overhead.
  - *Backblaze B2:* cheap storage, S3 API, low/free egress via partners.
  - *Hetzner Object Storage:* EU-hosted, cheap, S3 API — good if the deployment is EU/Hetzner.
- **Verdict:** Best for production durability/maintenance; wrong for local dev. Implies a
  **self-hosted-local + managed-prod split** if chosen.

## 2.6 Do-nothing baseline (`bitnamilegacy/minio`)

- Frozen under `bitnamilegacy` in 2025; **no further updates or security patches**. Works today and
  is behaviorally perfect, but is the unacceptable long-term floor this change exists to replace.

## 2.7 Weighted comparison

| Criterion (weight) | MinIO-from-src | Garage | SeaweedFS | Managed (R2) | bitnamilegacy (floor) |
|--------------------|:--:|:--:|:--:|:--:|:--:|
| C1 Maintenance (5) | 2 | 4 | 5 | 5 | 1 |
| C2 S3 compat + CreateBucket (5) | 5 | 4 | 5 | 3 | 5 |
| C3 Single-host ops (4) | 4 | 4 | 4 | 2* | 4 |
| C4 Licensing / cost (3) | 3 (AGPL) | 3 (AGPL) | 5 (Apache) | 3 (egress) | 3 |
| C5 Durability / backup (2) | 3 | 3 | 3 | 5 | 3 |
| C6 Exit / lock-in (3) | 4 | 4 | 4 | 3 | 4 |
| **Weighted total** | **72** | **77** | **90** | **69** | **63** |

\* Managed scores low on C3 specifically for **local development** (needs network + account); for
production it would be high. The single score can't capture the per-environment split.

Weighted totals: SeaweedFS 90, Garage 77, MinIO-from-source 72, Managed/R2 69, baseline 63.

## 3.1 Recommendation

**Replace `bitnamilegacy/minio` with a maintained self-hosted backend, and use the same backend for
local dev and single-host production.**

- **Primary recommendation: SeaweedFS (`chrislusf/seaweedfs`, `weed server -s3`).** It wins on the
  two highest weights (maintained + full S3 incl. `CreateBucket`), is Apache-2.0, and is the
  smoothest drop-in: one container, static credentials in a small config, buckets auto-create — so
  the app's existing startup `makeBucket` and the `MINIO_*` env vars keep working with no source
  change.
- **Close alternative: Garage** — lighter footprint and purpose-built for this shape, but its
  layout-init + key-import + per-bucket-grant provisioning is more setup than SeaweedFS.
- **MinIO-from-source** is behaviorally perfect but re-imposes the image-maintenance burden we're
  trying to escape.
- **Managed (Cloudflare R2)** is the strongest *production* option (zero egress, no ops) but breaks
  offline local dev; it only makes sense as a prod-only backend with a self-hosted local backend —
  a bigger, per-environment change. Recommend deferring unless you want the split now.

Same backend for both environments keeps the compose stack simple and the local/prod behavior
identical, which matches how this repo already treats its other infra services.

## 3.3 Decision

**Chosen: SeaweedFS (`chrislusf/seaweedfs`, `weed server -s3`), same backend for local development and
single-host production.** Decided with the user on 2026-09-30.

**Rationale:** SeaweedFS is actively maintained and Apache-2.0, and is the smoothest drop-in for this
stack — one container, static credentials in a small `-s3.config` identities file, and S3
`CreateBucket` support so the API Gateway's existing startup `makeBucket` and the `MINIO_*`
environment variables keep working with **no application source changes**. The `minio` service is
repointed in place (service/container name and `MINIO_*` variables retained to avoid churn) with the
SeaweedFS S3 gateway bound to port 9000 so `MINIO_ENDPOINT` is unchanged.

**Durability follow-up (accepted trade-off):** single-host self-hosted storage means durability
depends on backing up the `minio-data` volume. If these documents later warrant provider-grade
durability, the recorded upgrade path is Cloudflare R2 in production (self-hosted local + managed
prod split) — deferred, not part of this change.

**No data migration:** dev/prod buckets start empty; no objects are migrated from the previous
backend (per Non-Goals).

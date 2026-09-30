## 1. Branch setup

- [ ] 1.1 Create and check out a feature branch (e.g. `evaluate-minio-alternatives`) from `main`

## 2. Evaluate options

- [ ] 2.1 Define the weighted comparison criteria (maintenance/patch cadence, S3 compatibility with `io.minio`, single-host Compose operational effort, licensing/cost, durability/backup, exit/lock-in)
- [ ] 2.2 Gather current facts for MinIO-from-source (source ref, build effort, image maintenance implications)
- [ ] 2.3 Gather current facts for Garage (`dxflrs/garage`): maintenance status, S3 compatibility, deployment shape
- [ ] 2.4 Gather current facts for SeaweedFS (`chrislusf/seaweedfs`): maintenance status, S3 gateway, deployment shape
- [ ] 2.5 Gather current facts for managed S3 options (Cloudflare R2, AWS S3, Backblaze B2, Hetzner Object Storage): S3 compatibility, cost/egress, credential/bucket ownership
- [ ] 2.6 Record the do-nothing baseline (`bitnamilegacy/minio`) as the comparison floor
- [ ] 2.7 Produce a pros/cons comparison table scoring each candidate against the criteria

## 3. Decide

- [ ] 3.1 Form a recommendation from the comparison, including whether the backend should differ between local development and production
- [ ] 3.2 Present the comparison and recommendation to the user and get an explicit decision (replace vs keep; if replace, which backend)
- [ ] 3.3 Record the decision and its rationale in the change (and note any follow-up, e.g. data migration or a time-bound stopgap)

## 4. Replace the backend (only if the decision is to replace)

- [ ] 4.1 Verify the chosen backend's image pulls (or builds from source) and inspect its runtime contract (data path, entrypoint/command, ports, health endpoint)
- [ ] 4.2 Repoint the `minio` service in `docker-compose.yml` (image/command/volume/health) or remove it in favor of a managed endpoint; keep all tags explicit
- [ ] 4.3 Update `docker-compose.override.yml` host-port publishing for local development as needed for the new backend
- [ ] 4.4 Update `MINIO_ENDPOINT` / `MINIO_ACCESS_KEY` / `MINIO_SECRET_KEY` / `MINIO_BUCKET` handling in `.env.example` and confirm no application source change is required
- [ ] 4.5 Ensure the required bucket exists on the new backend (auto-create at startup or documented provisioning step)

## 5. Verify the stack (only if the backend was replaced)

- [ ] 5.1 Run `docker compose config` and confirm it is valid
- [ ] 5.2 Build/pull images and start the local stack; wait for all healthchecks to pass
- [ ] 5.3 Perform an end-to-end round-trip against the new backend: upload a CSV document, confirm the parser processes it, and download the original file
- [ ] 5.4 If the switch breaks a service, revert the object-storage change to the prior backend and record the blocker as an open question

## 6. Docs and wrap-up

- [ ] 6.1 Update `docker-compose.yml` comments, README, and `docs/ARCHITECTURE.md` to reflect the decision (chosen replacement, or why MinIO is retained for now)
- [ ] 6.2 Commit the changes on the feature branch with an English commit message summarizing the decision and any replacement

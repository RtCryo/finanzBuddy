## ADDED Requirements

### Requirement: Pin images to current supported versions
The Compose deployment SHALL pin its third-party service images and its Dockerfile base images to explicit tags that correspond to current, upstream-supported releases. Image version bumps SHALL be verified against a running stack before the change is considered complete, and SHALL NOT change application behavior other than picking up upstream fixes.

#### Scenario: Pinned image has a newer supported release
- **WHEN** a pinned image tag is behind the current upstream-supported release within a compatible version line
- **THEN** the pin is updated to the current supported tag rather than left on the stale tag

#### Scenario: Bump would require a breaking major upgrade
- **WHEN** the only newer release for an image is a major version that would require application or data-migration changes
- **THEN** the image stays on its current supported major line and the newer major is recorded as an open question rather than applied silently

#### Scenario: Stack is verified after version bumps
- **WHEN** image pins have been updated
- **THEN** the local Compose stack builds and pulls the updated images, `docker compose config` is valid, all services reach a healthy state, and the application is reachable end-to-end before the change is considered done

#### Scenario: Tags remain explicit
- **WHEN** an image pin is updated
- **THEN** the tag remains an explicit version and is not replaced with a floating tag such as `latest`

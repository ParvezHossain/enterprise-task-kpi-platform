# ADR 0015: GitHub Actions verification and container delivery

## Context

Three independent Maven projects require verification; only Auth has a runnable
image. No production hosting target or deployment credentials are defined.

## Options

- Verify only, leaving image distribution manual.
- Verify every project and publish the tested Auth image to GHCR.
- Deploy directly to a host, requiring a hosting and runtime operations decision.

## Decision

Use a Java 25 matrix on GitHub-hosted Ubuntu 24.04 for complete Maven verification,
then build and smoke-test Auth with the existing Dockerfile and smoke script.
Default-branch pushes deliver that same image through a current-run artifact to
GHCR under a full-commit tag. Separate publication from source execution and grant
package write permission only to publication. Use the built-in token, pin actions
to full commits, and maintain them with Dependabot. PRs use `pull_request` with
read-only permissions. Manual runs verify without publication.

## Consequences

Every delivered image has passed all module checks and container smoke testing.
Only linux/amd64 is published because that is the tested runner architecture.
Task/KPI verification covers skeleton builds, not application behavior. Artifact
transfer adds storage and latency but avoids rebuilding a different image for
publication. Deployment and rollback remain a future hosting decision. Branch
protection and GHCR repository access require repository settings configuration.

## Compatibility and API changes

Temurin 25.0.4+7 matches the Docker runtime baseline. Hosted Ubuntu supplies Docker,
Maven, Python and OpenSSL. Application dependencies and APIs are unchanged. Action
release evidence and pinned versions are recorded in the architecture document.

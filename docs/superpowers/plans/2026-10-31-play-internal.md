# Sinkpoint Google Play Implementation Plan

**Goal:** Mirror Minescape's automatic Google Play internal testing deployment.

**Architecture:** Public main checks gate a scheduled private build workflow.
Shared source-derived versioning identifies each commit; the publisher changes
only `com.cbulock.sinkpoint`'s internal track.

**Tech Stack:** Gradle Kotlin DSL, GitHub Actions, Python, Google Play API.

**Spec:** `docs/superpowers/specs/2026-10-31-play-internal-design.md`.

## Constraints and review focus

Keep production manual, credentials private, and existing upload/preview keys.
Verify full-history versioning, certificate identity, duplicate/retry behavior,
rollback refusal, embedded mapping match, and failed main build deferral.

## Tasks

- [x] Port publisher tests first, observe missing publisher, then port publisher,
  pinned dependencies, and private workflow with Sinkpoint identifiers.
- [x] Port source-derived versioning and its integration checks. Set baseline
  above all distributed builds, update public checkout/checks, and run checks.
- [ ] Document setup and recovery. Validate both workflows with actionlint,
  publisher tests, version integration checks, Android unit tests/lint/AAB build.
- [ ] Create private build repository, configure existing signing material,
  install workflow disabled, and prepare Google testing-only access.
- [ ] After required account authorization, activate uploads and verify the first
  signed bundle and publication marker against Play internal testing.

## Execution record

Existing main checkout was clean. Work is performed here for the user's selected
repository. The existing tested Minescape implementation is the reference.
No changes to gameplay or the website are required.

Validation: all 11 publisher tests pass; actionlint passes both workflows;
versioning integration cases pass with exit code 0; release lint and AAB build
pass; local signing verifies with the registered shared upload certificate.
The full Android unit suite is running its existing hard-AI simulations.
Independent review found an archive marker ordering issue, now fixed and
reviewed: payload files are stored successfully before `upload-ready.json`.

Private repository and workflow are installed, with `PLAY_UPLOAD_ENABLED=false`
and the workflow paused. Credential transfer was rejected by automatic approval
review pending explicit authorization for the private destination. Google project,
service account, and testing-only permissions also await authorization. No
credentials have been transferred and no Google resources created.

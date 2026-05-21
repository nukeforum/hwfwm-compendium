# Changelog

All notable changes to this project are recorded here. Dates are in `YYYY-MM-DD`.

The format is loosely based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).
This project uses [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.1.0] — 2026-05-21

### Added
- **Share / Export split** on detail screens: Share sends a plain-text summary, Export sends the Wire-format file used to re-import contributions in another install.
- Edit moved to a floating action button on every detail screen.

### Changed
- `renderAsText` for each entity now lives in its own UI module rather than in `:share`; `:share` is the transport, not the formatter.
- Share intents propagate the chooser title in their analytics payload.

### Fixed
- Detail screens for contributed entities reserve bottom space so the FAB no longer covers the last row of content.
- The Edit FAB exposes an entity-specific `contentDescription` ("Edit essence", "Edit awakening stone", etc.) for TalkBack.

### Tests
- Deepened `renderAsText` coverage on the per-entity renderers extracted from `:share`.

## [1.0.2] — 2026-05-19

### Fixed
- `SearchFilter` display names are now hardcoded so R8 can't obfuscate them away in release builds.

### Changed
- Status-effect search filter button restyled to match the essence and awakening-stone search screens.

## [1.0.1] — 2026-05-18

First tagged release. This entry summarises the work that immediately preceded the tag; pre-tag development history is in `git log`.

### Added
- **Google Drive backup**: a new `:drive-backup` module providing OAuth-backed backup and restore of the user's contributions database and preferences.
  - `BackupCoordinator` orchestrating snapshot / upload / download.
  - `WorkManagerBackupScheduler` driving the periodic `DriveBackupWorker`.
  - `DriveAuth` via Credential Manager + the GMS Authorization API (with resolution-intent plumbing).
  - `DriveAppFolderClient` implemented against the Drive REST v3 API over OkHttp (`Dispatchers.IO`-wrapped).
  - `WirePayloadCodec` for full-domain Wire encoding of the backup bundle.
  - `BackupStatusStore` + `driveBackupEnabled` / `accountEmail` preferences.
  - Settings UI: a Cloud Backup section exposing enable / disable / sign-in / restore actions.
  - Hilt module wiring and `HiltWorkerFactory` registration.
  - Manifest declarations and R8 keep rules for the new code.
- Adaptive launcher icon with a themed monochrome layer; legacy mipmap webp fallbacks removed.
- Auto Backup config explicitly includes the contributions DB and DataStore files.
- GitHub Pages site under `docs/site/` (landing page + screenshot gallery), published via the Pages workflow.


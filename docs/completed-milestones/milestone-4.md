# Milestone 4: Structural Cleanups and Conventions

## Goal

Address fundamental concerns around project naming, module conventions, package structures, and general project hygiene before moving to automated pipelines and advanced gameplay features.

The immediate focus of this milestone is to strip all direct mentions of the original retail game name and its common abbreviations or suffixes from project documentation, modules, packages, and class names, establishing a neutral project identity (using "catalyst").

## Scope

### 1. Stripping Game Name Mentions

- Scan all documentation files (excluding `docs/project-objective.md`) and remove or replace terms referring to the original retail game or its abbreviations.
- Replace variables, properties, log strings, and comments containing these terms.

### 2. Module, Directory, and Package Renaming

- Rename modules/directories to align with the new project name (e.g., using hierarchical directories under the root).
- Refactor package names (e.g., using `catalyst.*` namespaces).
- Rename classes that contain prefix/suffix names of the original game.
- Update Maven `pom.xml` files, dependency lists, and plugin definitions.

### 3. Build & Configuration Updates

- Update environment variables and Docker configuration (e.g., database names, usernames, passwords in `Dockerfile`).
- Update local shell helper scripts (`scripts/*.sh`) to point to new module directory names and main class paths.

## Deliverables

- Renamed Maven modules and updated root `pom.xml`.
- Refactored Java package namespace structure.
- Updated database and configuration property names.
- Updated launch and verification scripts.

## Acceptance Criteria

- [x] All direct mentions of the original retail game and its abbreviations are removed from the project code, configurations, directories, build configurations, and documentation (except for `docs/project-objective.md`).
- [x] The refactored client builds, boots, and launches ImGui overlay interface successfully.
- [x] The refactored server builds, boots, and establishes database connections successfully.
- [x] The local validation script runs and passes under the new naming conventions.

---

## Milestone 4 Status: CLOSED

**Closed:** 2026-08-05

Milestone 4 is formally closed. All structural cleanups, re-modularization, and stripping of the original retail game name mentions are complete.

# Contributing to Emberr

Thank you for considering contributing to Emberr, an open-source, offline-first notes and productivity app. This document covers what you need to know to get started.

## Getting Started

1. Fork the repository and clone your fork.
2. Open the project in Android Studio (or IntelliJ IDEA with the Kotlin Multiplatform plugin).
3. Let Gradle sync finish, then run the `app` module (Android) or `shared/src/desktopMain/kotlin/com/emberr/DesktopMain.kt` (Desktop) to confirm everything builds.

## Project Overview

* **Framework:** Compose Multiplatform, targeting Android and Desktop.
* **Language:** Kotlin, using Coroutines and Flow for asynchronous work.
* **Databases:** Room KMP (`AppDatabase`) for app data, and SQLDelight (`EmberrDatabase`) for the AI search index.
* **Data model:** The editor is block-based. Every piece of content (text, checkboxes, images, tables, etc.) is a distinct `NoteBlock`.
* **Dependency injection:** Koin.

Shared code lives under `com.emberr.*` in the `shared` module and must stay platform-agnostic - no Android-only or JVM-only imports in `commonMain`.

## Making Changes

* Keep pull requests focused. Smaller, single-purpose PRs are easier to review than large ones that mix unrelated changes.
* Match the existing code style: clear, descriptive names for functions, variables, and classes so the code reads on its own.
* Test your change on at least one device or platform before opening a PR, and mention which one in the PR description.
* If your PR fixes an open issue, reference it in your first commit message using `Fixes #1234` so it closes automatically when merged.

## Submitting a Pull Request

1. Create a branch off `main` for your change.
2. Make your changes and commit them.
3. Open a pull request against `main` and fill out the PR template.
4. Be responsive to review feedback please - it usually just takes a round or two to get merged.

## Reporting Bugs and Requesting Features

Please use the issue templates when opening a new issue - pick **Bug report** or **Feature request** depending on what you're filing. Search existing issues first to avoid duplicates.

## License

Emberr is licensed under the GNU Affero General Public License v3.0. By contributing, you agree that your contributions will be licensed under the same license. See [LICENSE](LICENSE) for details.
